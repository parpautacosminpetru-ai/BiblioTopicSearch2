package ro.bibliotopicsearch.app;

/** Pure, monotonic-clock policy. One admitted image at a time. Thermal levels
 * use Android's numeric values, but this class has no Android dependencies. */
final class AdaptiveOcrPolicy {
    private static final long NO_FRAME = Long.MIN_VALUE;
    private long inFlight = NO_FRAME, startedAt, nextAllowed;
    private double averageMs = 125;
    private int slowFrames, coolFrames, failures;
    private int thermal;

    synchronized boolean tryStart(long timestamp, long now, int thermalStatus) {
        thermal = Math.max(0, thermalStatus);
        if (inFlight != NO_FRAME) {
            if (now - startedAt < 5000L) return false;
            // A lost callback must not permanently exhaust the permit.
            inFlight = NO_FRAME;
        }
        if (now < nextAllowed) return false;
        inFlight = timestamp;
        startedAt = now;
        return true;
    }

    synchronized long finish(long timestamp, long now, boolean success) {
        if (timestamp != inFlight) return -1L;
        long start = startedAt;
        inFlight = NO_FRAME;
        long duration = Math.max(1L, now - start);
        averageMs = averageMs * 0.8 + Math.min(4000L, duration) * 0.2;
        failures = success ? 0 : failures + 1;
        if (success && (averageMs > 360 || thermal >= 3)) {
            slowFrames = Math.min(30, slowFrames + 1);
            coolFrames = 0;
        } else if (success && averageMs < 180 && thermal <= 1) {
            coolFrames = Math.min(40, coolFrames + 1);
            slowFrames = 0;
        } else {
            slowFrames = 0;
            coolFrames = 0;
        }
        long interval = Math.max(110L, Math.min(1500L, Math.round(averageMs * 1.35)));
        if (thermal == 2) interval = Math.max(interval, 350L);
        if (thermal >= 3) interval = Math.max(interval, 700L);
        if (thermal >= 4) interval = Math.max(interval, 1400L);
        if (!success) interval = Math.max(interval, Math.min(2000L, 250L * failures));
        // Keep a minimum rest after expensive processing as well.
        long rest = thermal >= 3 ? 180L : Math.max(12L, interval / 8);
        nextAllowed = Math.max(start + interval, now + rest);
        return start;
    }

    synchronized boolean shouldReduceResolution() { return slowFrames >= 6 || thermal >= 4; }
    synchronized boolean canRestoreResolution() { return coolFrames >= 18 && thermal <= 1; }
    synchronized int consecutiveFailures() { return failures; }
}
