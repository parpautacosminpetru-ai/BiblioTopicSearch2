package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SemanticTextMarkerLogicTest {
    @Test
    public void subjectAndFunctionLabelsRemainDistinct() {
        SemanticTextMark subject = new SemanticTextMark(
                SemanticTextMark.Kind.SUBJECT, null, "X", "SUBIECT", 1.0, 0
        );
        SemanticTextMark function = new SemanticTextMark(
                SemanticTextMark.Kind.FUNCTION, null, "este", "FUNCȚIE · DEFINIRE", 1.0, 0
        );
        assertEquals("SUBIECT", subject.label);
        assertEquals("FUNCȚIE · DEFINIRE", function.label);
    }
}
