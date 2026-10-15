package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** Two explicit OCR anchors that justify one deterministic topic-index crossing. */
public final class IndexIntersectionTextMark {
    public final RectF leftBox;
    public final RectF rightBox;
    public final int leftColor;
    public final int rightColor;
    public final String label;
    public final String ruleCode;
    public final SemanticGraph.Relation relation;
    public final int paragraphIndex;
    public final double confidence;

    public IndexIntersectionTextMark(
            RectF leftBox,
            RectF rightBox,
            int leftColor,
            int rightColor,
            String label,
            String ruleCode,
            SemanticGraph.Relation relation,
            int paragraphIndex,
            double confidence
    ) {
        this.leftBox = leftBox == null ? new RectF() : new RectF(leftBox);
        this.rightBox = rightBox == null ? new RectF() : new RectF(rightBox);
        this.leftColor = leftColor;
        this.rightColor = rightColor;
        this.label = label == null ? "" : label;
        this.ruleCode = ruleCode == null ? "" : ruleCode;
        this.relation = relation == null ? SemanticGraph.Relation.GENERIC : relation;
        this.paragraphIndex = Math.max(0, paragraphIndex);
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
}
