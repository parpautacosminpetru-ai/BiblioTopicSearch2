package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Testable temporal confirmation with spatially indexed occurrences.
 * Each old observation can support at most one new occurrence. */
final class TemporalEvidence<T> {
    private static final float CELL = 48f;
    private static final long MAX_CONFIRM_MS = 2400L;
    private static final long HOLD_MS = 350L;

    static final class Observation<T> {
        final String key;
        final float x, y, height;
        final boolean needsConfirmation, held;
        final T value;
        Observation(String key, float x, float y, float height, boolean needsConfirmation, T value) {
            this(key, x, y, height, needsConfirmation, false, value);
        }
        Observation(String key, float x, float y, float height, boolean needsConfirmation, boolean held, T value) {
            this.key = key; this.x = x; this.y = y; this.height = height;
            this.needsConfirmation = needsConfirmation; this.held = held; this.value = value;
        }
    }

    static final class Result<T> {
        final Observation<T> current;
        final Observation<T> previous;
        final boolean held;
        Result(Observation<T> current, Observation<T> previous, boolean held) {
            this.current = current; this.previous = previous; this.held = held;
        }
    }

    private static final class Track<T> {
        final Observation<T> observation;
        final boolean confirmed;
        Track(Observation<T> observation, boolean confirmed) { this.observation = observation; this.confirmed = confirmed; }
    }

    private List<Track<T>> last = new ArrayList<>();
    private long lastTime = -1;

    void clear() { last.clear(); lastTime = -1; }

    private String bucket(String key, int x, int y) { return key + "\u0001" + x + ":" + y; }
    private int cell(float x) { return (int) Math.floor(x / CELL); }

    List<Result<T>> update(List<Observation<T>> observations, PageContinuity.State continuity, long now) {
        List<Result<T>> out = new ArrayList<>();
        List<Track<T>> next = new ArrayList<>();
        long age = lastTime < 0 ? Long.MAX_VALUE : now - lastTime;
        boolean comparable = age >= 0 && age <= MAX_CONFIRM_MS &&
                continuity != PageContinuity.State.CHANGED && continuity != PageContinuity.State.MOVED;
        Map<String, List<Track<T>>> index = new HashMap<>();
        if (comparable) for (Track<T> track : last) {
            Observation<T> old = track.observation;
            index.computeIfAbsent(bucket(old.key, cell(old.x), cell(old.y)), ignored -> new ArrayList<>()).add(track);
        }
        Set<Track<T>> used = new HashSet<>();
        for (Observation<T> fresh : observations) {
            Track<T> nearest = null;
            float best = Float.MAX_VALUE;
            int cx = cell(fresh.x), cy = cell(fresh.y);
            float tolerance = Math.max(8f, Math.min(44f, fresh.height * 0.85f));
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                List<Track<T>> group = index.get(bucket(fresh.key, cx + dx, cy + dy));
                if (group == null) continue;
                for (Track<T> track : group) {
                    if (used.contains(track)) continue;
                    Observation<T> old = track.observation;
                    float ratio = fresh.height / Math.max(1f, old.height);
                    if (ratio < 0.70f || ratio > 1.4f) continue;
                    float distance = (float) Math.hypot(fresh.x - old.x, fresh.y - old.y);
                    if (distance <= tolerance && distance < best) { best = distance; nearest = track; }
                }
            }
            if (nearest != null) used.add(nearest);
            boolean confirmed = !fresh.needsConfirmation || nearest != null;
            next.add(new Track<>(fresh, confirmed));
            if (confirmed) out.add(new Result<>(fresh,
                    continuity == PageContinuity.State.STABLE && nearest != null ? nearest.observation : null, false));
        }
        if (continuity == PageContinuity.State.STABLE && age >= 0 && age <= HOLD_MS) {
            for (Track<T> old : last) {
                if (old.confirmed && !used.contains(old) && !old.observation.needsConfirmation)
                    out.add(new Result<>(old.observation, null, true));
            }
        }
        // Only actual fresh observations survive; retained hits cannot be retained twice.
        last = next;
        lastTime = now;
        return out;
    }
}
