package ro.bibliotopicsearch.app;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** One running task and at most one pending item. New inputs replace old pending
 * inputs, so OCR and UI cannot accumulate an unbounded frame queue. */
final class LatestOnly<T> implements AutoCloseable {
    interface Consumer<T> { void accept(T value); }
    private final Executor executor;
    private final Consumer<T> consumer;
    private T pending;
    private boolean scheduled;
    private boolean closed;

    LatestOnly(Executor executor, Consumer<T> consumer) {
        this.executor = executor;
        this.consumer = consumer;
    }

    void offer(T value) {
        if (value == null) return;
        synchronized (this) {
            if (closed) return;
            pending = value;
            if (scheduled) return;
            scheduled = true;
        }
        schedule();
    }

    synchronized void clear() { pending = null; }

    @Override public synchronized void close() {
        closed = true;
        pending = null;
    }

    private void schedule() {
        try {
            executor.execute(this::drainOne);
        } catch (RejectedExecutionException rejected) {
            synchronized (this) {
                scheduled = false;
                pending = null;
            }
        }
    }

    private void drainOne() {
        T value;
        synchronized (this) {
            value = closed ? null : pending;
            pending = null;
        }
        try {
            if (value != null) consumer.accept(value);
        } finally {
            boolean again;
            synchronized (this) {
                again = !closed && pending != null;
                if (!again) scheduled = false;
            }
            if (again) schedule();
        }
    }
}
