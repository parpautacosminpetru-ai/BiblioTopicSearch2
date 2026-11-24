package ro.bibliotopicsearch.app;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MatchHit {
    public final RectF box;
    public final List<RectF> segments;
    public final String originalText;
    public final String searchTerm;
    public final TopicNode node;
    public final long detectedAt;

    public MatchHit(RectF box, String originalText, String searchTerm, TopicNode node, long detectedAt) {
        this(Collections.singletonList(box), originalText, searchTerm, node, detectedAt);
    }

    public MatchHit(List<RectF> boxes, String originalText, String searchTerm, TopicNode node, long detectedAt) {
        List<RectF> copied = new ArrayList<>(boxes.size());
        RectF bounds = new RectF(boxes.get(0));
        for (RectF part : boxes) {
            copied.add(new RectF(part));
            bounds.union(part);
        }
        this.box = bounds;
        this.segments = Collections.unmodifiableList(copied);
        this.originalText = originalText;
        this.searchTerm = searchTerm;
        this.node = node;
        this.detectedAt = detectedAt;
    }

    public String identityKey() {
        return node.identityKey() + "|" + searchTerm;
    }
}
