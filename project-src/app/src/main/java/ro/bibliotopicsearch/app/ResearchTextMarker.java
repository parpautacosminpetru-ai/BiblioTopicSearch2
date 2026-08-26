package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps the selected explicit research answer segment back to concrete ML Kit token boxes. */
public final class ResearchTextMarker {
    private ResearchTextMarker() {}

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

    public static List<ResearchTextMark> build(Text text, ResearchSemanticEngine.Answer answer) {
        if (text == null || answer == null || answer.segment() == null || answer.segment().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Text.TextBlock> blocks = text.getTextBlocks();
        int index = answer.paragraphIndex();
        if (index < 0 || index >= blocks.size()) return Collections.emptyList();

        List<Token> tokens = tokensFor(blocks.get(index));
        if (tokens.isEmpty()) return Collections.emptyList();

        String normalizedSegment = TopicMatcher.normalize(answer.segment(), true);
        if (normalizedSegment.isEmpty()) return Collections.emptyList();
        String[] wanted = normalizedSegment.split("\\s+");
        if (wanted.length == 0 || wanted.length > tokens.size()) return Collections.emptyList();

        int bestStart = -1;
        int bestLength = 0;
        for (int start = 0; start < tokens.size(); start++) {
            int matched = 0;
            while (start + matched < tokens.size() && matched < wanted.length
                    && wanted[matched].equals(tokens.get(start + matched).normalized)) {
                matched++;
            }
            if (matched > bestLength) {
                bestLength = matched;
                bestStart = start;
            }
            if (matched == wanted.length) break;
        }

        // Require almost the whole explicit span. This prevents highlighting a coincidental fragment.
        int minimum = Math.max(2, (int) Math.ceil(wanted.length * 0.78));
        if (bestStart < 0 || bestLength < minimum) return Collections.emptyList();

        int drawLength = Math.min(wanted.length, bestLength);
        List<ResearchTextMark> out = new ArrayList<>(drawLength);
        for (int offset = 0; offset < drawLength; offset++) {
            Token token = tokens.get(bestStart + offset);
            out.add(new ResearchTextMark(token.box, token.raw, answer.score(), index));
        }
        return out;
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
}
