package ro.bibliotopicsearch.app;

import java.util.HashMap;
import java.util.Map;

/** Conservative page/pose comparison using distinct OCR words and actual
 * view-referenced coordinates. No image is saved and no text is invented. */
final class PageContinuity {
    enum State { STABLE, MOVED, CHANGED, UNKNOWN }

    static final class Anchor {
        final float x, y, height;
        Anchor(float x, float y, float height) { this.x = x; this.y = y; this.height = height; }
    }

    static final class Sample {
        final Map<String, Anchor> anchors = new HashMap<>();
        private final java.util.Set<String> repeated = new java.util.HashSet<>();

        void add(String word, float x, float y, float height) {
            if (word == null || word.length() < 3 || height <= 0 || repeated.contains(word)) return;
            if (anchors.containsKey(word)) {
                anchors.remove(word);
                repeated.add(word);
            } else if (anchors.size() < 384) {
                anchors.put(word, new Anchor(x, y, height));
            }
        }
    }

    static State compare(Sample before, Sample after) {
        if (before == null || after == null) return State.UNKNOWN;
        int smaller = Math.min(before.anchors.size(), after.anchors.size());
        if (smaller < 3) return State.UNKNOWN;
        int common = 0, aligned = 0;
        for (Map.Entry<String, Anchor> entry : before.anchors.entrySet()) {
            Anchor next = after.anchors.get(entry.getKey());
            if (next == null) continue;
            common++;
            Anchor old = entry.getValue();
            float distance = (float) Math.hypot(next.x - old.x, next.y - old.y);
            float scale = next.height / old.height;
            float tolerance = Math.max(8f, Math.min(32f, old.height * 0.7f));
            if (distance <= tolerance && scale >= 0.78f && scale <= 1.28f) aligned++;
        }
        if (smaller >= 6 && common < smaller * 0.25f) return State.CHANGED;
        if (common < 3 || common < smaller * 0.45f) return State.UNKNOWN;
        return aligned >= common * 0.70f ? State.STABLE : State.MOVED;
    }
}
