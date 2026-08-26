package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SemanticMarkConfidenceTest {
    @Test
    public void confidenceIsClamped() {
        SemanticTextMark high = new SemanticTextMark(SemanticTextMark.Kind.SUBJECT, null, "x", "S", 2.0, 0);
        SemanticTextMark low = new SemanticTextMark(SemanticTextMark.Kind.FUNCTION, null, "y", "F", -1.0, 0);
        assertEquals(1.0, high.confidence, 0.0);
        assertEquals(0.0, low.confidence, 0.0);
    }
}
