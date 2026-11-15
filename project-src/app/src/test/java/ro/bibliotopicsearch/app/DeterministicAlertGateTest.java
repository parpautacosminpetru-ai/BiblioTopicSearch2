package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class DeterministicAlertGateTest {
    private static DeterministicAlertGate.Evidence e(String profile, String term, String ocr) {
        return new DeterministicAlertGate.Evidence(profile, term, ocr);
    }
    private static final DeterministicAlertGate.Mode BAL = DeterministicAlertGate.Mode.ECHILIBRAT;

    @Test public void phraseAlertsOnceUntilAbsentInTwoFrames() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        List<DeterministicAlertGate.Evidence> hits =
                Collections.singletonList(e("A", "reforma agrară", "Reforma agrară"));
        assertTrue(g.shouldAlert(hits, BAL, 10000));
        assertFalse(g.shouldAlert(hits, BAL, 11000));
        assertFalse(g.shouldAlert(hits, BAL, 16000));
        assertFalse(g.shouldAlert(Collections.emptyList(), BAL, 17000));
        assertFalse(g.shouldAlert(Collections.emptyList(), BAL, 18000));
        assertTrue(g.shouldAlert(hits, BAL, 19000));
    }

    @Test public void contextualTermsStayInTheirOwnTopic() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Arrays.asList(
                e("A", "învățământ", "învățământ"),
                e("B", "modernizare", "modernizare")), BAL, 10000));
        assertTrue(g.shouldAlert(Arrays.asList(
                e("A", "învățământ", "învățământ"),
                e("A", "modernizare", "modernizare")), BAL, 12000));
    }

    @Test public void partialAndSilentTermsNeverAlert() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Collections.singletonList(
                e("A", "secularizare", "secularizării")), BAL, 10000));
        assertFalse(g.shouldAlert(Collections.singletonList(
                e("A", "-reforma agrară", "reforma agrară")), BAL, 11000));
        assertTrue(g.shouldAlert(Collections.singletonList(
                e("A", "!Cuza", "Cuza")), BAL, 12000));
        assertEquals("Cuza", DeterministicAlertGate.searchableTerm("!Cuza"));
    }

    @Test public void forcedContextAndStrictModeAreConservative() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Collections.singletonList(
                e("A", "?reforma agrară", "reforma agrară")), BAL, 10000));
        assertTrue(g.shouldAlert(Arrays.asList(
                e("A", "?reforma agrară", "reforma agrară"),
                e("A", "secularizare", "secularizare")), BAL, 12000));
        g.reset();
        assertFalse(g.shouldAlert(Collections.singletonList(
                e("A", "reforma agrară", "reforma agrară")),
                DeterministicAlertGate.Mode.STRICT, 20000));
        assertTrue(g.shouldAlert(Collections.singletonList(
                e("A", "legea reformei agrare", "legea reformei agrare")),
                DeterministicAlertGate.Mode.STRICT, 22000));
    }

    @Test public void contextOnlyNeverAlertsAloneEvenInExploration() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Collections.singletonList(
                e("A", "?învățământ", "învățământ")),
                DeterministicAlertGate.Mode.EXPLORARE, 30000));
        assertTrue(g.shouldAlert(Arrays.asList(
                e("A", "?învățământ", "învățământ"),
                e("A", "modernizare", "modernizare")),
                DeterministicAlertGate.Mode.EXPLORARE, 32000));
    }

    @Test public void twoContextCluesMustComeFromSameActualSubject() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Arrays.asList(
                new DeterministicAlertGate.Evidence("bizant", "C01", "?Constantinopol", "Constantinopol"),
                new DeterministicAlertGate.Evidence("bizant", "C02", "?armata bizantină", "armata bizantină")),
                BAL, 10000));
        assertTrue(g.shouldAlert(Arrays.asList(
                new DeterministicAlertGate.Evidence("bizant", "C02", "?armata bizantină", "armata bizantină"),
                new DeterministicAlertGate.Evidence("bizant", "C02", "?flota bizantină", "flota bizantină")),
                BAL, 12000));
    }

    @Test public void datesAndDuplicatesAreNotStrongEvidence() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        assertFalse(g.shouldAlert(Collections.singletonList(
                new DeterministicAlertGate.Evidence("romani", "S01", "!1691", "1691")),
                BAL, 10000));
        assertFalse(g.shouldAlert(Arrays.asList(
                new DeterministicAlertGate.Evidence("romani", "S01", "?mitropolie", "mitropolie"),
                new DeterministicAlertGate.Evidence("romani", "S01", "?mitropolie", "mitropolie")),
                BAL, 12000));
    }

    @Test public void manySignalsOnePulseOnly() {
        DeterministicAlertGate g = new DeterministicAlertGate();
        List<DeterministicAlertGate.Evidence> frame = Arrays.asList(
                e("A", "reforma agrară", "reforma agrară"),
                e("B", "modernizarea statului", "modernizarea statului"),
                e("B", "administrație", "administrație"));
        assertTrue(g.shouldAlert(frame, BAL, 10000));
        assertFalse(g.shouldAlert(frame, BAL, 15000));
    }
}
