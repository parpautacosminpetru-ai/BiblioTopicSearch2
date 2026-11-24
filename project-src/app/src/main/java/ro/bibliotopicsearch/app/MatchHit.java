package ro.bibliotopicsearch.app;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MatchHit {
    public enum Kind { LITERAL, LINE_JOIN, OCR_REPAIR }
    public final Kind kind;
    public final boolean retained;
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
        this(boxes, originalText, searchTerm, node, detectedAt, Kind.LITERAL, false);
    }

    public MatchHit(List<RectF> boxes, String originalText, String searchTerm, TopicNode node,
                    long detectedAt, Kind kind, boolean retained) {
        this.kind = kind;
        this.retained = retained;
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

    MatchHit withGeometry(List<RectF> boxes, boolean held) {
        return new MatchHit(boxes, originalText, searchTerm, node, detectedAt, kind, held);
    }

    public String identityKey() {
        return node.identityKey() + "|" + searchTerm;
    }
}
