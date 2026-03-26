package ch.so.agi.terrainvis.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;

public final class BoundedCompletionExecutor {
    private BoundedCompletionExecutor() {
    }

    public static <I, T, E extends Exception> void process(
            ExecutorCompletionService<T> completionService,
            Iterator<I> items,
            int maxInFlight,
            Submitter<? super I, T> submitter,
            CheckedConsumer<? super T, E> consumer) throws InterruptedException, ExecutionException, E {
        Objects.requireNonNull(completionService, "completionService");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(submitter, "submitter");
        Objects.requireNonNull(consumer, "consumer");
        if (maxInFlight <= 0) {
            throw new IllegalArgumentException("maxInFlight must be > 0");
        }

        int inFlight = 0;
        while (inFlight < maxInFlight && items.hasNext()) {
            submitter.submit(completionService, items.next());
            inFlight++;
        }

        while (inFlight > 0) {
            T result = completionService.take().get();
            inFlight--;
            consumer.accept(result);
            while (inFlight < maxInFlight && items.hasNext()) {
                submitter.submit(completionService, items.next());
                inFlight++;
            }
        }
    }

    @FunctionalInterface
    public interface Submitter<I, T> {
        void submit(ExecutorCompletionService<T> completionService, I item);
    }

    @FunctionalInterface
    public interface CheckedConsumer<T, E extends Exception> {
        void accept(T value) throws E;
    }
}
