package ro.bibliotopicsearch.app;

/** ML Kit reports 0 if a confidence value is unavailable. A positive
 * score is not a calibrated probability that the word is correct. */
final class OcrConfidence {
    static final float MINIMUM = 0.72f;
    static final float HIGH = 0.85f;
    private float minimum = 1f;
    private boolean unknown, seen;

    void add(float score) {
        seen = true;
        if (Float.isNaN(score) || Float.isInfinite(score) || score <= 0f || score > 1f) unknown = true;
        else minimum = Math.min(minimum, score);
    }

    float value() {
        if (minimum < MINIMUM) return minimum;
        return !seen || unknown ? -1f : minimum;
    }

    static int requiredFrames(float confidence, boolean reconstructed) {
        if (confidence > 0f && confidence < MINIMUM) return 0;
        return reconstructed || Float.isNaN(confidence) || Float.isInfinite(confidence) || confidence < HIGH || confidence > 1f ? 3 : 2;
    }
}
