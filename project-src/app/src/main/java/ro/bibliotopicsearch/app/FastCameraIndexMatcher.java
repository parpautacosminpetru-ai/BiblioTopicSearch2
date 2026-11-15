package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts one-pass canonical token hits to ML Kit text rectangles.
 * Uses exact/inflectional matching and source character spans, not a separate
 * full dictionary scan for each OCR word. Never guesses coordinates when an
 * OCR element cannot be aligned with the Text.Line source string.
 */
public final class FastCameraIndexMatcher {
    private FastCameraIndexMatcher() {}

    private static final class ElementSpan {
        final int start, end;
        final RectF rectangle;

        ElementSpan(int start, int end, RectF rectangle) {
            this.start = start;
            this.end = end;
            this.rectangle = rectangle;
        }
    }

    public static List<MatchHit> find(
            Text text,
            DeterministicMultiIndexMatcher.Plan plan
    ) {
        List<MatchHit> result = new ArrayList<>();
        if (text == null || plan == null || plan.isEmpty()) return result;
        long timestamp = System.currentTimeMillis();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String original = line.getText();
                if (original == null || original.isEmpty()) continue;
                List<DeterministicMultiIndexMatcher.Occurrence> occurrences =
                        DeterministicMultiIndexMatcher.allOccurrences(plan, original);
                if (occurrences.isEmpty()) continue;

                List<ElementSpan> spans = new ArrayList<>();
                int scanFrom = 0;
                for (Text.Element element : line.getElements()) {
                    String surface = element.getText();
                    Rect box = element.getBoundingBox();
                    if (surface == null || surface.isEmpty() || box == null) continue;
                    int start = original.indexOf(surface, scanFrom);
                    if (start < 0) start = findCaseInsensitive(original, surface, scanFrom);
                    if (start < 0) continue;
                    int end = start + surface.length();
                    spans.add(new ElementSpan(start, end, new RectF(box)));
                    scanFrom = end;
                }

                for (DeterministicMultiIndexMatcher.Occurrence occurrence : occurrences) {
                    RectF union = null;
                    for (ElementSpan element : spans) {
                        if (element.end <= occurrence.start()
                                || element.start >= occurrence.end()) continue;
                        if (union == null) union = new RectF(element.rectangle);
                        else union.union(element.rectangle);
                    }
                    if (union == null) continue;
                    result.add(new MatchHit(union, occurrence.original(),
                            occurrence.canonicalTerm(), occurrence.node(), timestamp));
                }
            }
        }
        return result;
    }

    private static int findCaseInsensitive(String text, String part, int from) {
        for (int i = Math.max(0, from); i + part.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, part, 0, part.length())) return i;
        }
        return -1;
    }
}
