package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TemporalEvidenceTest {
    private TemporalEvidence.Observation<String> at(String key, float x, boolean repair) {
        return new TemporalEvidence.Observation<>(key, x, 100f, 20f, repair, key);
    }
    @Test public void repairedTextRequiresTwoObservationsWhileLiteralIsImmediate() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        assertEquals(1, tracker.update(Arrays.asList(at("literal", 10, false), at("repair", 100, true)),
                PageContinuity.State.UNKNOWN, 0).size());
        assertEquals(2, tracker.update(Arrays.asList(at("literal", 10, false), at("repair", 103, true)),
                PageContinuity.State.STABLE, 100).size());
    }
    @Test public void changedPageNeverInheritsARepairOrOldHighlight() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        tracker.update(Arrays.asList(at("literal", 10, false), at("repair", 100, true)),
                PageContinuity.State.UNKNOWN, 0);
        assertTrue(tracker.update(Arrays.asList(at("repair", 100, true)),
                PageContinuity.State.CHANGED, 150).isEmpty());
    }
    @Test public void oneMissingFrameCanBeHeldButNotTwice() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        tracker.update(Arrays.asList(at("literal", 10, false)), PageContinuity.State.UNKNOWN, 0);
        assertTrue(tracker.update(Collections.emptyList(), PageContinuity.State.STABLE, 100).get(0).held);
        assertTrue(tracker.update(Collections.emptyList(), PageContinuity.State.STABLE, 200).isEmpty());
    }
    @Test public void oneOldOccurrenceCannotConfirmTwoNewOnes() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        tracker.update(Arrays.asList(at("same", 40, true)), PageContinuity.State.UNKNOWN, 0);
        assertEquals(1, tracker.update(Arrays.asList(at("same", 39, true), at("same", 41, true)),
                PageContinuity.State.STABLE, 100).size());
    }
    @Test public void repeatedTermFarAwayDoesNotConfirmAndMapsStaySeparate() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        tracker.update(Arrays.asList(at("mapA/term", 40, true)), PageContinuity.State.UNKNOWN, 0);
        assertTrue(tracker.update(Arrays.asList(at("mapA/term", 120, true), at("mapB/term", 40, true)),
                PageContinuity.State.STABLE, 100).isEmpty());
    }
    @Test public void staleObservationsCannotConfirmOrRetain() {
        TemporalEvidence<String> tracker = new TemporalEvidence<>();
        tracker.update(Arrays.asList(at("literal", 10, false), at("repair", 40, true)),
                PageContinuity.State.UNKNOWN, 0);
        assertTrue(tracker.update(Arrays.asList(at("repair", 40, true)), PageContinuity.State.STABLE, 3000).isEmpty());
    }
}
