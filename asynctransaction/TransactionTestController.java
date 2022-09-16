package com.zyj.asynctransaction;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author lulx
 * @date 2022-08-30 19:44
 **/
@RestController
public class TransactionTestController {

    @Resource
    ThreadPoolTaskExecutor executor;

    @Resource
    ITestService testService;

    @GetMapping("/test/asyncTask")
    public String Test(@RequestParam("size") int size) {
        List<AsyncTransactionTask> tasks = new ArrayList<>(size);
        for (int i = 0; i < size - 1; i++) {
            long j = i + 1;
            AsyncTransactionTask task = new AsyncTransactionTask(() -> {
                TestEntity entity = new TestEntity();
                entity.setId(j);
                entity.setName(UUID.randomUUID().toString());
                testService.insert(entity);
            }, "task" + j);
            tasks.add(task);
        }
        AsyncTransactionTask task = new AsyncTransactionTask(() -> {
            TestEntity entity = new TestEntity();
            entity.setId((long) size);
            entity.setName(UUID.randomUUID().toString());
            testService.insert(entity);
            throw new RuntimeException("fail...");
        }, "task" + size);
        tasks.add(task);
        CompletableFutureTransactionManager futureTransaction = new CompletableFutureTransactionManager(tasks, executor);
        AsyncTaskResult result = futureTransaction.execute();
        System.out.println("任务执行结果：" + result);
        return result + "";
    }
}
