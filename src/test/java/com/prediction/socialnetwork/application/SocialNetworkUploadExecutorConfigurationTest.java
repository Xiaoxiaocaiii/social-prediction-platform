package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SocialNetworkUploadExecutorConfigurationTest {

    @Test
    void boundsConcurrentWorkAndRejectsBeyondFourQueuedTasks() throws Exception {
        ThreadPoolTaskExecutor executor =
                new SocialNetworkUploadExecutorConfiguration().socialNetworkUploadExecutor();
        executor.initialize();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch queuedTasksCompleted = new CountDownLatch(4);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger peakActiveTasks = new AtomicInteger();

        try {
            executor.execute(() -> {
                peakActiveTasks.accumulateAndGet(activeTasks.incrementAndGet(), Math::max);
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    activeTasks.decrementAndGet();
                }
            });
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));

            for (int index = 0; index < 4; index++) {
                executor.execute(() -> {
                    peakActiveTasks.accumulateAndGet(activeTasks.incrementAndGet(), Math::max);
                    try {
                        queuedTasksCompleted.countDown();
                    } finally {
                        activeTasks.decrementAndGet();
                    }
                });
            }

            assertEquals(1, executor.getMaxPoolSize());
            assertEquals(4, executor.getThreadPoolExecutor().getQueue().size());
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
            }));

            releaseWorker.countDown();
            assertTrue(queuedTasksCompleted.await(5, TimeUnit.SECONDS));
            assertEquals(1, peakActiveTasks.get());
        } finally {
            releaseWorker.countDown();
            executor.destroy();
        }
    }
}
