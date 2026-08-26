package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/**
 * One evidence span drawn directly over OCR text for automatic paragraph analysis.
 * SUBJECT marks the detected target subject; FUNCTION marks lexical/structural
 * evidence that supports the detected discourse function.
 */
public final class SemanticTextMark {
    public enum Kind { SUBJECT, FUNCTION }

    public final Kind kind;
    public final RectF box;
    public final String text;
    public final String label;
    public final double confidence;
    public final int paragraphIndex;

    public SemanticTextMark(
            Kind kind,
            RectF box,
            String text,
            String label,
            double confidence,
            int paragraphIndex
    ) {
        this.kind = kind;
        this.box = box == null ? new RectF() : new RectF(box);
        this.text = text == null ? "" : text;
        this.label = label == null ? "" : label;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.paragraphIndex = paragraphIndex;
    }
}
