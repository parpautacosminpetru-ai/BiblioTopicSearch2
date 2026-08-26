package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SubjectFunctionMarkerContractTest {
    @Test
    public void exposesTwoSemanticKinds() {
        assertEquals(2, SemanticTextMark.Kind.values().length);
        assertEquals("SUBJECT", SemanticTextMark.Kind.SUBJECT.name());
        assertEquals("FUNCTION", SemanticTextMark.Kind.FUNCTION.name());
    }
}
