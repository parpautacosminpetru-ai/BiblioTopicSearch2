package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps explicitly detected lexical cues back to the OCR word bounding boxes. */
public final class SyntaxCueTextMarker {
    private SyntaxCueTextMarker() {}

    private static final Pattern WORD = Pattern.compile(
            "[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*"
    );

    public static List<SyntaxCueTextMark> build(Text ocr) {
        if (ocr == null || ocr.getTextBlocks() == null) return Collections.emptyList();
        List<SyntaxCueTextMark> marks = new ArrayList<>();
        List<Text.TextBlock> blocks = ocr.getTextBlocks();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            Text.TextBlock block = blocks.get(blockIndex);
            if (block == null) continue;
            List<String> words = new ArrayList<>();
            List<RectF> boxes = new ArrayList<>();
            for (Text.Line line : block.getLines()) {
                if (line == null) continue;
                for (Text.Element element : line.getElements()) {
                    if (element == null || element.getText() == null) continue;
                    Rect sourceBox = element.getBoundingBox();
                    if (sourceBox == null || sourceBox.isEmpty()) continue;
                    // A Text.Element generally holds one word, but may hold several.
                    Matcher matcher = WORD.matcher(element.getText());
                    while (matcher.find()) {
                        words.add(matcher.group());
                        boxes.add(new RectF(sourceBox));
                    }
                }
            }
            for (SyntaxCueDetector.Cue cue : SyntaxCueDetector.detectWords(words)) {
                for (int i = cue.startToken(); i < cue.endToken(); i++) {
                    if (i >= boxes.size()) break;
                    marks.add(new SyntaxCueTextMark(
                            boxes.get(i), cue.kind(), cue.evidence(),
                            blockIndex, i == cue.startToken()
                    ));
                }
            }
        }
        return Collections.unmodifiableList(marks);
    }
}
