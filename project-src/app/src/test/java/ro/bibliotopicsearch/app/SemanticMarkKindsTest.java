package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public final class SemanticMarkKindsTest {
    @Test
    public void subjectAndFunctionKindsDiffer() {
        assertNotEquals(SemanticTextMark.Kind.SUBJECT, SemanticTextMark.Kind.FUNCTION);
    }
}
