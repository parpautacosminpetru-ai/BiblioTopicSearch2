package ro.bibliotopicsearch.app;

import android.graphics.RectF;

public final class MatchHit {
    public RectF box;
    public final String originalText;
    public final String searchTerm;
    public final TopicNode node;
    public final long detectedAt;

    public MatchHit(RectF box, String originalText, String searchTerm, TopicNode node, long detectedAt) {
        this.box = new RectF(box);
        this.originalText = originalText;
        this.searchTerm = searchTerm;
        this.node = node;
        this.detectedAt = detectedAt;
    }

    public String identityKey() {
        return node.path + "|" + searchTerm;
    }
}
