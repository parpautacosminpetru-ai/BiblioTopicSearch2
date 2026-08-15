package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** A single OCR occurrence mapped to a topic/concept. */
public final class MatchHit {
    public RectF box;
    public final String originalText;
    public final String searchTerm;
    public final TopicNode node;
    public final long detectedAt;

    /** True when the hit came through a semantic relation (for example a synonym). */
    public final boolean semantic;

    /** 0..1 relevance used by the live overlay as echo/intensity strength. */
    public final float similarity;

    /** Human-readable origin of the semantic relation: DIRECT, SYNONIM, etc. */
    public final String semanticCategory;

    public MatchHit(RectF box, String originalText, String searchTerm, TopicNode node, long detectedAt) {
        this(box, originalText, searchTerm, node, detectedAt, false, 1.0f, "DIRECT");
    }

    public MatchHit(
            RectF box,
            String originalText,
            String searchTerm,
            TopicNode node,
            long detectedAt,
            boolean semantic,
            float similarity,
            String semanticCategory
    ) {
        this.box = new RectF(box);
        this.originalText = originalText;
        this.searchTerm = searchTerm;
        this.node = node;
        this.detectedAt = detectedAt;
        this.semantic = semantic;
        this.similarity = clamp(similarity);
        this.semanticCategory = semanticCategory == null ? "" : semanticCategory;
    }

    public String identityKey() {
        return node.path + "|" + searchTerm;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
