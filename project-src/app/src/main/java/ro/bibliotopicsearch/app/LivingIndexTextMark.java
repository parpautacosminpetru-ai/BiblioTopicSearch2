package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** One visible token span belonging to a deterministic Living Index entry. */
public final class LivingIndexTextMark {
    public final RectF box;
    public final String code;
    public final String text;
    public final LivingIndexStore.Category category;
    public final int color;
    public final double confidence;
    public final int paragraphIndex;
    public final boolean inbox;

    public LivingIndexTextMark(
            RectF box,
            String code,
            String text,
            LivingIndexStore.Category category,
            int color,
            double confidence,
            int paragraphIndex,
            boolean inbox
    ) {
        this.box = box == null ? new RectF() : new RectF(box);
        this.code = code == null ? "" : code;
        this.text = text == null ? "" : text;
        this.category = category == null ? LivingIndexStore.Category.INBOX : category;
        this.color = color;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.paragraphIndex = Math.max(0, paragraphIndex);
        this.inbox = inbox;
    }
}
