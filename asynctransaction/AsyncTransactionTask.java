package com.zyj.asynctransaction;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 异步事务任务项
 *
 * @author lulx
 * @date 2022-08-29 23:15
 **/
public class AsyncTransactionTask {

    /** 要执行的操作 */
    private final Runnable target;

    /** 执行结果 */
    private volatile AsyncTaskResult result;

    /** 锁，控制事务 */
    private Lock lock;

    /** 超时时间，默认10秒 */
    private long timeOut = 10 * 1000L;

    // 通过工具类获取bean
    /** 事务管理器 */
    private final DataSourceTransactionManager transactionManager = SpringContextHolder.getBean(DataSourceTransactionManager.class);

    private final TransactionDefinition transactionDefinition = SpringContextHolder.getBean(TransactionDefinition.class);

    /** 事务状态 */
    private TransactionStatus transactionStatus;

    /** 任务名称 */
    private String name;

    public AsyncTransactionTask(Runnable target) {
        this.target = target;
    }

    public AsyncTransactionTask(Runnable target, String name) {
        this.target = target;
        this.name = name;
    }

    protected AsyncTaskResult getResult() {
        return result;
    }

    protected void setResult(AsyncTaskResult result) {
        this.result = result;
    }

    protected void setLock(Lock lock) {
        this.lock = lock;
    }

    protected void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }

    public String getName() {
        return name;
    }

    /**
     * 执行
     *
     * @return 任务执行状态
     */
    public AsyncTaskResult execute() {
        // 手动开启事务
        transactionStatus = transactionManager.getTransaction(transactionDefinition);
        try {
            result = AsyncTaskResult.RUNNING;
            if (target != null) {
                target.run();
            }
            result = AsyncTaskResult.SUCCESS;
        } catch (Exception e) {
            result = AsyncTaskResult.FAIL;
            rollback();
        }
        return result;
    }

    /**
     * 等待其他事务执行
     */
    protected void waitOtherTransactionTask() {
        boolean tryLock = false;
        long waitLockTime = 100L;
        long total = 0L;
        long sleepTime = 100L;
        // 获取锁判断任务是否全部成功
        try {
            while (!(tryLock = lock.tryLock(waitLockTime, TimeUnit.MILLISECONDS))) {
                if (total < timeOut) {
                    // 200毫秒后再次尝试获取锁
                    TimeUnit.MILLISECONDS.sleep(sleepTime);
                    total += waitLockTime + sleepTime;
                } else {
                    // 任务超时
                    result = AsyncTaskResult.TIMEOUT;
                    rollback();
                    return;
                }
            }
            if (result == AsyncTaskResult.SUCCESS) {
                transactionManager.commit(transactionStatus);
            } else {
                rollback();
            }
        } catch (Exception e) {
            rollback();
        } finally {
            if (tryLock) {
                lock.unlock();
            }
        }
    }

    /**
     * 事务回滚
     */
    private void rollback() {
        if (transactionStatus != null && !transactionStatus.isCompleted()) {
            System.out.println(name + " rollback...");
            transactionManager.rollback(transactionStatus);
        }
    }
}
