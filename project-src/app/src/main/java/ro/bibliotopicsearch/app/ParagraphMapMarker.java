package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps cartography nodes to the first visible OCR token of each paragraph block. */
public final class ParagraphMapMarker {
    private ParagraphMapMarker() {}

    public static List<ParagraphMapMark> build(Text text, ParagraphCartography.Map map) {
        if (text == null || map == null || map.isEmpty()) return Collections.emptyList();
        List<Text.TextBlock> blocks = text.getTextBlocks();
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<ParagraphMapMark> out = new ArrayList<>();
        for (ParagraphCartography.Node node : map.nodes()) {
            int index = node.paragraphIndex();
            if (index < 0 || index >= blocks.size()) continue;
            RectF anchor = firstTokenBox(blocks.get(index));
            if (anchor == null || anchor.isEmpty()) continue;
            out.add(new ParagraphMapMark(
                    anchor,
                    index,
                    node.depth(),
                    node.link(),
                    node.confidence(),
                    node.subject()
            ));
        }
        return out;
    }

    private static RectF firstTokenBox(Text.TextBlock block) {
        if (block == null) return null;
        for (Text.Line line : block.getLines()) {
            for (Text.Element element : line.getElements()) {
                Rect rect = element.getBoundingBox();
                if (rect != null) return new RectF(rect);
            }
        }
        Rect rect = block.getBoundingBox();
        return rect == null ? null : new RectF(rect);
    }
}
