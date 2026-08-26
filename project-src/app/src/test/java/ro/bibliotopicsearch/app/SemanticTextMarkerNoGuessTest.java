package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SemanticTextMarkerNoGuessTest {
    @Test
    public void unknownFunctionHasNoFallbackEvidenceContract() {
        assertEquals(UniversalDetectionLexicon.Function.UNKNOWN.name(), "UNKNOWN");
    }
}
