package com.zyj.asynctransaction;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步任务事务管理器
 *
 * @author lulx
 * @date 2022-08-29 23:26
 **/
public class CompletableFutureTransactionManager {

    /** 执行结果 */
    volatile AsyncTaskResult ret;
    /** 超时时间，默认10秒 */
    private long timeOut = 10 * 1000L;
    /** 监听间隔，默认100毫秒 */
    private final int interval = 100;
    /** 任务数量 */
    private final int taskNum;
    /** 任务列表 */
    private final List<AsyncTransactionTask> tasks;
    /** 用于判断任务是否都执行完 */
    private final AtomicInteger atomicInteger;
    /** 锁对象 */
    Lock lock = new ReentrantLock();
    /** 线程池 */
    private ThreadPoolTaskExecutor executor;

    public CompletableFutureTransactionManager(List<AsyncTransactionTask> tasks) {
        if (tasks == null) {
            taskNum = 0;
            this.tasks = new ArrayList<>(0);
        } else {
            this.tasks = tasks;
            taskNum = tasks.size();
        }
        atomicInteger = new AtomicInteger(taskNum);
    }

    public CompletableFutureTransactionManager(List<AsyncTransactionTask> tasks, ThreadPoolTaskExecutor executor) {
        this(tasks);
        this.executor = executor;
    }

    public CompletableFutureTransactionManager(List<AsyncTransactionTask> tasks, long timeOut) {
        this(tasks);
        this.timeOut = timeOut * 1000L;
    }

    public CompletableFutureTransactionManager(List<AsyncTransactionTask> tasks, long timeOut, ThreadPoolTaskExecutor executor) {
        this(tasks);
        this.timeOut = timeOut * 1000L;
        this.executor = executor;
    }

    /**
     * 执行任务计划
     *
     * @return
     */
    public AsyncTaskResult execute() {
        if (tasks == null || taskNum == 0) {
            return AsyncTaskResult.SUCCESS;
        }
        ret = AsyncTaskResult.RUNNING;
        lock.lock();
        try {
            // 执行异步任务
            if (executor == null) {
                executeTask();
            } else {
                executeTaskWithThreadPool();
            }

            // 等待任务执行
            waitingCompleted();
        } catch (Throwable e) {
            cancelAll();
        } finally {
            lock.unlock();
        }
        return ret;
    }

    /**
     * 执行异步任务
     */
    public void executeTask() {
        for (AsyncTransactionTask task : tasks) {
            task.setLock(lock);
            CompletableFuture.supplyAsync(task::execute)
                    .thenAccept(result -> callback(result, task));
        }
    }

    /**
     * 执行异步任务,线程池方式
     */
    public void executeTaskWithThreadPool() {
        for (AsyncTransactionTask task : tasks) {
            task.setLock(lock);
            CompletableFuture.supplyAsync(task::execute)
                    .thenAccept(result -> callback(result, task));
        }
    }

    /**
     * 等待所有异步任务执行完成
     */
    private void waitingCompleted() {
        if (taskNum == 0) {
            ret = AsyncTaskResult.SUCCESS;
            return;
        }
        try {
            int total = 0;
            for (; ; ) {
                TimeUnit.MILLISECONDS.sleep(interval);

                // 超时
                if (total >= timeOut) {
                    ret = AsyncTaskResult.TIMEOUT;
                    cancelAll();
                    break;
                }
                total += interval;

                // 任务还在执行
                if (ret == AsyncTaskResult.RUNNING) {
                    continue;
                }

                // 任务都执行成功
                if (atomicInteger.get() == 0) {
                    ret = AsyncTaskResult.SUCCESS;
                    break;
                }

                // 任务执行错误或已取消
                if (ret == AsyncTaskResult.FAIL) {
                    cancelAll();
                    break;
                }
            }
        } catch (Throwable e) {
            ret = AsyncTaskResult.FAIL;
            cancelAll();
            e.printStackTrace();
        }
    }

    /**
     * 异步任务执行回调
     *
     * @param result          任务执行结果
     * @param transactionTask 任务
     */
    public void callback(AsyncTaskResult result, AsyncTransactionTask transactionTask) {
        if (AsyncTaskResult.SUCCESS == result) {
            if (atomicInteger.decrementAndGet() == 0) {
                ret = AsyncTaskResult.SUCCESS;
            }
            // 等待其他事务执行
            transactionTask.waitOtherTransactionTask();
        } else {
            ret = AsyncTaskResult.FAIL;
            // 回滚其他异步任务
            cancelAll();
        }
    }

    /**
     * 取消所有任务
     * 将每个任务的执行状态设置为失败,当线程让各线程自己回滚事务
     */
    public void cancelAll() {
        if (tasks != null && tasks.size() > 0) {
            for (AsyncTransactionTask task : tasks) {
                if (task.getResult() == AsyncTaskResult.SUCCESS) {
                    task.setResult(AsyncTaskResult.CANCELED);
                } else {
                    task.setResult(AsyncTaskResult.FAIL);
                }
            }
        }
    }
}
