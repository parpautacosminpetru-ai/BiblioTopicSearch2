package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** One OCR token belonging to the explicit segment that answers the research target. */
public final class ResearchTextMark {
    public final RectF box;
    public final String text;
    public final double relevance;
    public final int paragraphIndex;

    public ResearchTextMark(RectF box, String text, double relevance, int paragraphIndex) {
        this.box = box == null ? new RectF() : new RectF(box);
        this.text = text == null ? "" : text;
        this.relevance = Math.max(0.0, Math.min(1.0, relevance));
        this.paragraphIndex = paragraphIndex;
    }
}
