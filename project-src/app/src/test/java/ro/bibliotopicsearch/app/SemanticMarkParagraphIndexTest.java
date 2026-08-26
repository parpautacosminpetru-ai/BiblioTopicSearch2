package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SemanticMarkParagraphIndexTest {
    @Test
    public void keepsParagraphIndex() {
        SemanticTextMark mark = new SemanticTextMark(SemanticTextMark.Kind.SUBJECT, null, "x", "S", 0.5, 7);
        assertEquals(7, mark.paragraphIndex);
    }
}
