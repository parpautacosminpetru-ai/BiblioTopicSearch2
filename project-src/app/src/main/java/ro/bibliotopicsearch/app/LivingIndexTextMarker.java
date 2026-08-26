package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maps candidate phrases back to the exact ML Kit word boxes; no page image is retained. */
public final class LivingIndexTextMarker {
    private LivingIndexTextMarker() {}

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

    public static List<LivingIndexTextMark> build(
            Text text,
            List<LivingIndexEngine.Candidate> candidates,
            LivingIndexStore.State state
    ) {
        if (text == null || candidates == null || candidates.isEmpty()) return Collections.emptyList();
        List<LivingIndexTextMark> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        List<Text.TextBlock> blocks = text.getTextBlocks();

        for (LivingIndexEngine.Candidate candidate : candidates) {
            int p = candidate.paragraphIndex();
            if (p < 0 || p >= blocks.size()) continue;
            List<Token> tokens = flatten(blocks.get(p));
            String target = TopicMatcher.normalize(candidate.surface(), true);
            if (target.isEmpty()) continue;
            String[] wanted = target.split("\\s+");

            LivingIndexStore.Entry entry = candidate.knownId().isEmpty() || state == null
                    ? null : state.byId(candidate.knownId());
            LivingIndexStore.Category category = entry == null ? candidate.category() : entry.category();
            String id = entry == null ? candidate.surface() : entry.id();
            String code = entry == null
                    ? LivingIndexStore.codeFor(category, id)
                    : entry.code();
            int color = entry == null
                    ? LivingIndexStore.colorFor(category, id)
                    : entry.color();

            for (int start = 0; start + wanted.length <= tokens.size(); start++) {
                boolean match = true;
                for (int j = 0; j < wanted.length; j++) {
                    if (!tokens.get(start + j).normalized.equals(wanted[j])) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;

                for (int j = 0; j < wanted.length; j++) {
                    Token token = tokens.get(start + j);
                    if (token.box == null || token.box.isEmpty()) continue;
                    String key = p + "|" + code + "|" + Math.round(token.box.left) + "|" + Math.round(token.box.top);
                    if (!dedupe.add(key)) continue;
                    out.add(new LivingIndexTextMark(
                            token.box,
                            code,
                            token.raw,
                            category,
                            color,
                            candidate.confidence(),
                            p,
                            candidate.isInboxCandidate()
                    ));
                }
                break;
            }
        }
        return out;
    }

    private static List<Token> flatten(Text.TextBlock block) {
        List<Token> out = new ArrayList<>();
        if (block == null) return out;
        for (Text.Line line : block.getLines()) {
            for (Text.Element element : line.getElements()) {
                Rect rect = element.getBoundingBox();
                if (rect == null) continue;
                out.add(new Token(element.getText(), new RectF(rect)));
            }
        }
        return out;
    }
}
