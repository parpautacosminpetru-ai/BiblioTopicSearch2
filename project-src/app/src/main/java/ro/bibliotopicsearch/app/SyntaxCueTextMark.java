package ro.bibliotopicsearch.app;

import android.graphics.RectF;

/** One OCR word under a deterministic grammatical cue, never a generated relation. */
public final class SyntaxCueTextMark {
    public final RectF box;
    public final SyntaxCueDetector.Kind kind;
    public final String evidence;
    public final int paragraphIndex;
    public final boolean firstWord;

    public SyntaxCueTextMark(
            RectF box,
            SyntaxCueDetector.Kind kind,
            String evidence,
            int paragraphIndex,
            boolean firstWord
    ) {
        this.box = box == null ? new RectF() : new RectF(box);
        this.kind = kind;
        this.evidence = evidence == null ? "" : evidence;
        this.paragraphIndex = paragraphIndex;
        this.firstWord = firstWord;
    }
}
