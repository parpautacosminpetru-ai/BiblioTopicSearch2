package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** Lightweight UI anchor for one paragraph-cartography node. */
public final class ParagraphMapMark {
    public final RectF box;
    public final int paragraphIndex;
    public final int depth;
    public final ParagraphCartography.Link link;
    public final double confidence;
    public final String subject;

    public ParagraphMapMark(
            RectF box,
            int paragraphIndex,
            int depth,
            ParagraphCartography.Link link,
            double confidence,
            String subject
    ) {
        this.box = box == null ? null : new RectF(box);
        this.paragraphIndex = paragraphIndex;
        this.depth = Math.max(0, depth);
        this.link = link == null ? ParagraphCartography.Link.ROOT : link;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.subject = subject == null ? "" : subject.trim();
    }
}
