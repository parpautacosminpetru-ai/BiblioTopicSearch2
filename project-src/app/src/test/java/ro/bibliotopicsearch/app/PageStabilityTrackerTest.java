package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PageStabilityTrackerTest {

    @Test
    public void stableKnownPageTriggersOnlyOnceUntilPageChanges() {
        PageStabilityTracker tracker = new PageStabilityTracker();
        String text = longText("Împăratul conduce armata și organizează administrația imperială.");

        PageStabilityTracker.Observation a = tracker.observe(text, "42", 1000L);
        PageStabilityTracker.Observation b = tracker.observe(text, "42", 1250L);
        PageStabilityTracker.Observation c = tracker.observe(text, "42", 1500L);

        assertFalse(a.shouldCapture);
        assertFalse(b.shouldCapture);
        assertTrue(c.shouldCapture);

        tracker.markCaptured(c);
        PageStabilityTracker.Observation d = tracker.observe(text, "42", 1750L);
        assertTrue(d.alreadyCaptured);
        assertFalse(d.shouldCapture);

        String next = longText("Patriarhul și clerul discută raporturile dintre Biserică și puterea imperială.");
        tracker.observe(next, "43", 2000L);
        tracker.observe(next, "43", 2250L);
        PageStabilityTracker.Observation e = tracker.observe(next, "43", 2500L);
        assertTrue(e.shouldCapture);
    }

    @Test
    public void minorOcrVariationStillCountsAsSamePage() {
        PageStabilityTracker tracker = new PageStabilityTracker();
        String a = longText("Armata bizantină era organizată în unități și comandată de ofițeri imperiali.");
        String b = a.replace("ofițeri", "ofiteri").replace("bizantină", "bizantina");

        tracker.observe(a, "77", 1000L);
        tracker.observe(b, "77", 1250L);
        PageStabilityTracker.Observation stable = tracker.observe(a, "77", 1500L);
        assertTrue(stable.stable);
    }

    @Test
    public void shortNoiseNeverTriggersExactCapture() {
        PageStabilityTracker tracker = new PageStabilityTracker();
        PageStabilityTracker.Observation a = tracker.observe("pagina 4", "4", 1000L);
        PageStabilityTracker.Observation b = tracker.observe("pagina 4", "4", 2000L);
        assertFalse(a.sufficient);
        assertFalse(b.shouldCapture);
    }

    private static String longText(String sentence) {
        return sentence + " " + sentence + " " + sentence + " " + sentence;
    }
}
