package com.product.thread;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class MyParallelProcessor {

    @Resource(name = "businessExecutor")
    private final ThreadPoolTaskExecutor businessExecutor;

    /**
     * 通用 List 多线程处理模板
     *
     * @param data          待处理的数据
     * @param threadCount   最大任务数量
     * @param processor     每个数据分片的处理函数
     * @param initialResult 初始累计结果
     * @param merger        结果合并函数
     */
    public <T, A> A processInParallel(
            List<T> data,
            int threadCount,
            Function<List<T>, A> processor,
            A initialResult,
            BiFunction<A, A, A> merger
    ) throws InterruptedException, ExecutionException {

        if (initialResult == null) {
            throw new IllegalArgumentException(
                    "initialResult 不能为 null"
            );
        }

        if (data == null || data.isEmpty()) {
            return initialResult;
        }

        if (threadCount <= 0) {
            throw new IllegalArgumentException(
                    "threadCount 必须大于 0"
            );
        }

        int taskCount = Math.min(threadCount, data.size());

        int chunkSize =
                (data.size() + taskCount - 1) / taskCount;

        List<Future<A>> futures = new ArrayList<>();

        // 1. 划分数据并提交任务
        for (int i = 0; i < taskCount; i++) {

            int start = i * chunkSize;
            int end = Math.min(
                    start + chunkSize,
                    data.size()
            );

            List<T> part = new ArrayList<>(
                    data.subList(start, end)
            );

            Future<A> future = businessExecutor.submit(
                    () -> processor.apply(part)
            );

            futures.add(future);
        }

        // 2. 获取每个任务的结果并累计合并
        A result = initialResult;

        for (Future<A> future : futures) {
            A taskResult = future.get();
            result = merger.apply(result, taskResult);
        }

        return result;
    }
}