package ro.bibliotopicsearch.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class SyntaxCueDetectorTest {
    private boolean contains(List<SyntaxCueDetector.Cue> cues, SyntaxCueDetector.Kind kind) {
        for (SyntaxCueDetector.Cue cue : cues) if (cue.kind() == kind) return true;
        return false;
    }

    @Test public void separatesPurposeFromCause() {
        List<SyntaxCueDetector.Cue> cues = SyntaxCueDetector.detectText(
                "Diplomații s-au întrunit pentru a negocia după război."
        );
        assertTrue(contains(cues, SyntaxCueDetector.Kind.PURPOSE));
        assertTrue(contains(cues, SyntaxCueDetector.Kind.TIME));
        assertFalse(contains(cues, SyntaxCueDetector.Kind.CAUSE));
    }

    @Test public void detectsExplicitNegationAndCause() {
        List<SyntaxCueDetector.Cue> cues = SyntaxCueDetector.detectText(
                "Nu a plecat deoarece granița era închisă."
        );
        assertTrue(contains(cues, SyntaxCueDetector.Kind.NEGATION));
        assertTrue(contains(cues, SyntaxCueDetector.Kind.CAUSE));
    }

    @Test public void longerConcessionWins() {
        List<SyntaxCueDetector.Cue> cues =
                SyntaxCueDetector.detectText("Chiar dacă a pierdut, a continuat.");
        assertEquals(SyntaxCueDetector.Kind.CONCESSION, cues.get(0).kind());
        assertFalse(contains(cues, SyntaxCueDetector.Kind.CONDITION));
    }

    @Test public void repeatedCuesStayDistinct() {
        List<SyntaxCueDetector.Cue> cues =
                SyntaxCueDetector.detectText("După luptă și după tratat.");
        long count = 0;
        for (SyntaxCueDetector.Cue cue : cues) {
            if (cue.kind() == SyntaxCueDetector.Kind.TIME) count++;
        }
        assertEquals(2, count);
    }
}
