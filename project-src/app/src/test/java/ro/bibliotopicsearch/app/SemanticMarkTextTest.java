package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class SemanticMarkTextTest {
    @Test
    public void keepsText() {
        SemanticTextMark mark = new SemanticTextMark(SemanticTextMark.Kind.SUBJECT, null, "memoria", "S", 0.7, 0);
        assertEquals("memoria", mark.text);
    }
}
