package ro.bibliotopicsearch.app;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera-safe, bounded OCR-result processing. At most one detection task exists
 * (including its UI publication). No backlog is allowed when frames arrive fast.
 *
 * This class does not hold Android/ML Kit types, so the scheduling contract is
 * independently testable. The owner must close it at lifecycle destruction.
 */
public final class LiveDetectionPipeline<I, O> implements AutoCloseable {
    public interface Matcher<I, O> {
        O match(I input) throws Exception;
    }

    public interface Listener<O> {
        void onResult(O output);
        void onError(Exception failure);
    }

    private final ExecutorService worker;
    private final Executor uiExecutor;
    private final Matcher<I, O> matcher;
    private final Listener<O> listener;
    private final long intervalNanos;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile long lastStarted = Long.MIN_VALUE;

    public LiveDetectionPipeline(
            long minIntervalMillis,
            Executor uiExecutor,
            Matcher<I, O> matcher,
            Listener<O> listener
    ) {
        if (minIntervalMillis < 0 || uiExecutor == null || matcher == null || listener == null) {
            throw new IllegalArgumentException("Invalid live-detection configuration");
        }
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(minIntervalMillis);
        this.uiExecutor = uiExecutor;
        this.matcher = matcher;
        this.listener = listener;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "biblio-live-match");
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        });
    }

    /** Returns false for an intentionally skipped frame, never enqueues it. */
    public boolean offer(I input) {
        if (input == null || closed.get() || !busy.compareAndSet(false, true)) return false;
        long now = System.nanoTime();
        long last = lastStarted;
        if (last != Long.MIN_VALUE && now - last >= 0 && now - last < intervalNanos) {
            busy.set(false);
            return false;
        }
        lastStarted = now;
        try {
            worker.execute(() -> {
                O output = null;
                Exception failure = null;
                try {
                    if (!closed.get()) output = matcher.match(input);
                } catch (Exception error) {
                    failure = error;
                }
                final O completed = output;
                final Exception error = failure;
                try {
                    uiExecutor.execute(() -> {
                        try {
                            if (!closed.get()) {
                                if (error == null) listener.onResult(completed);
                                else listener.onError(error);
                            }
                        } catch (RuntimeException callbackError) {
                            if (!closed.get()) {
                                try { listener.onError(callbackError); }
                                catch (RuntimeException ignored) { /* Don't break the gate. */ }
                            }
                        } finally {
                            busy.set(false);
                        }
                    });
                } catch (RuntimeException rejectedUi) {
                    busy.set(false);
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            busy.set(false);
            return false;
        }
    }

    public boolean isBusy() { return busy.get(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            worker.shutdownNow();
        }
    }
}
