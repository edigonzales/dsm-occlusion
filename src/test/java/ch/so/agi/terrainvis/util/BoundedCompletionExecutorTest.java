package ch.so.agi.terrainvis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BoundedCompletionExecutorTest {
    @Test
    void submitsNextTaskOnlyAfterConsumerReturns() throws Exception {
        ExecutorService workerExecutor = Executors.newFixedThreadPool(2);
        ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor();
        try {
            ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(workerExecutor);
            CountDownLatch firstTwoStarted = new CountDownLatch(2);
            CountDownLatch releaseFirstTask = new CountDownLatch(1);
            CountDownLatch releaseSecondTask = new CountDownLatch(1);
            CountDownLatch consumerEntered = new CountDownLatch(1);
            CountDownLatch releaseConsumer = new CountDownLatch(1);
            CountDownLatch thirdTaskStarted = new CountDownLatch(1);
            AtomicInteger submittedCount = new AtomicInteger();
            AtomicInteger consumedCount = new AtomicInteger();

            Future<?> future = coordinatorExecutor.submit(() -> {
                try {
                    BoundedCompletionExecutor.process(
                            completionService,
                            List.of(0, 1, 2).iterator(),
                            2,
                            (service, item) -> {
                                submittedCount.incrementAndGet();
                                service.submit(() -> executeTask(
                                        item,
                                        firstTwoStarted,
                                        releaseFirstTask,
                                        releaseSecondTask,
                                        thirdTaskStarted));
                            },
                            result -> {
                                if (consumedCount.getAndIncrement() == 0) {
                                    consumerEntered.countDown();
                                    await(releaseConsumer, "release consumer");
                                }
                            });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(firstTwoStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(submittedCount.get()).isEqualTo(2);
            assertThat(thirdTaskStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstTask.countDown();
            assertThat(consumerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(submittedCount.get()).isEqualTo(2);
            assertThat(thirdTaskStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseConsumer.countDown();
            assertThat(thirdTaskStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(submittedCount.get()).isEqualTo(3);

            releaseSecondTask.countDown();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            coordinatorExecutor.shutdownNow();
            workerExecutor.shutdownNow();
        }
    }

    private static Integer executeTask(
            int item,
            CountDownLatch firstTwoStarted,
            CountDownLatch releaseFirstTask,
            CountDownLatch releaseSecondTask,
            CountDownLatch thirdTaskStarted) throws InterruptedException {
        if (item == 0) {
            firstTwoStarted.countDown();
            await(releaseFirstTask, "release first task");
            return item;
        }
        if (item == 1) {
            firstTwoStarted.countDown();
            await(releaseSecondTask, "release second task");
            return item;
        }
        thirdTaskStarted.countDown();
        return item;
    }

    private static void await(CountDownLatch latch, String label) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for " + label + ".");
        }
    }
}
