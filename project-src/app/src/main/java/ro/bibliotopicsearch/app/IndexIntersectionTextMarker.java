package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps deterministic intersection evidence surfaces back to concrete OCR boxes. */
public final class IndexIntersectionTextMarker {
    private IndexIntersectionTextMarker() {}

    private static final class Token {
        final String raw;
        final String normalized;
        final RectF box;

        Token(String raw, RectF box) {
            this.raw = raw == null ? "" : raw;
            this.normalized = TopicMatcher.normalize(this.raw, true);
            this.box = box;
        }
    }

    public static List<IndexIntersectionTextMark> build(
            Text text,
            List<DeterministicIndexIntersectionEngine.Intersection> intersections
    ) {
        if (text == null || intersections == null || intersections.isEmpty()) {
            return Collections.emptyList();
        }

        List<Text.TextBlock> blocks = text.getTextBlocks();
        List<IndexIntersectionTextMark> out = new ArrayList<>();
        for (DeterministicIndexIntersectionEngine.Intersection intersection : intersections) {
            if (intersection == null) continue;
            int p = intersection.paragraphIndex();
            if (p < 0 || p >= blocks.size()) continue;

            List<Token> tokens = tokensFor(blocks.get(p));
            if (tokens.isEmpty()) continue;

            RectF left = findBox(tokens, intersection.left().surface());
            RectF right = findBox(tokens, intersection.right().surface());
            if (left == null || right == null || RectF.intersects(left, right)) continue;

            TopicNode leftNode = intersection.left().node();
            TopicNode rightNode = intersection.right().node();
            out.add(new IndexIntersectionTextMark(
                    left,
                    right,
                    leftNode == null ? 0 : leftNode.color,
                    rightNode == null ? 0 : rightNode.color,
                    intersection.label(),
                    intersection.rule().code(),
                    intersection.relation(),
                    p,
                    intersection.propositionConfidence()
            ));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Token> tokensFor(Text.TextBlock block) {
        List<Token> out = new ArrayList<>();
        if (block == null) return out;
        for (Text.Line line : block.getLines()) {
            for (Text.Element element : line.getElements()) {
                Rect rect = element.getBoundingBox();
                if (rect == null) continue;
                Token token = new Token(element.getText(), new RectF(rect));
                if (!token.normalized.isEmpty()) out.add(token);
            }
        }
        return out;
    }

    private static RectF findBox(List<Token> tokens, String phrase) {
        String normalized = TopicMatcher.normalize(phrase, true);
        if (normalized.isEmpty()) return null;
        String[] wanted = normalized.split("\\s+");
        if (wanted.length == 0 || wanted.length > tokens.size()) return null;

        for (int start = 0; start + wanted.length <= tokens.size(); start++) {
            boolean match = true;
            for (int offset = 0; offset < wanted.length; offset++) {
                if (!wanted[offset].equals(tokens.get(start + offset).normalized)) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;

            RectF union = new RectF(tokens.get(start).box);
            for (int offset = 1; offset < wanted.length; offset++) {
                union.union(tokens.get(start + offset).box);
            }
            return union;
        }
        return null;
    }
}
