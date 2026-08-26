package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public final class SemanticMarkNullBoxTest {
    @Test
    public void nullBoxBecomesEmptyBox() {
        SemanticTextMark mark = new SemanticTextMark(SemanticTextMark.Kind.SUBJECT, null, "x", "S", 0.5, 0);
        assertNotNull(mark.box);
    }
}
