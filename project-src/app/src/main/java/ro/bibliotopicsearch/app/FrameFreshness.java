package ro.bibliotopicsearch.app;

import java.util.concurrent.atomic.AtomicLong;

/** Reject recognizer results captured before the last viewport change or
 * delivered after their bounded useful lifetime. Monotonic time only. */
final class FrameFreshness {
    static final long MAX_AGE_MS = 900L;
    private final AtomicLong viewportChangedAt = new AtomicLong(-1L);

    long invalidate(long now) {
        long old;
        do {
            old = viewportChangedAt.get();
            if (old >= now) return old;
        } while (!viewportChangedAt.compareAndSet(old, now));
        return now;
    }

    boolean accepts(long capturedAt, long now) {
        return capturedAt > viewportChangedAt.get() && capturedAt <= now
                && now - capturedAt < MAX_AGE_MS;
    }

    long revision() { return viewportChangedAt.get(); }

    long deadline(long capturedAt) {
        return capturedAt + MAX_AGE_MS;
    }
}
