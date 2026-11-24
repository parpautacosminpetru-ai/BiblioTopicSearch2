package ro.bibliotopicsearch.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveOcrPolicyTest {
    @Test public void permitAndTimestampPreventOverlappingRecognition() {
        AdaptiveOcrPolicy p = new AdaptiveOcrPolicy();
        assertTrue(p.tryStart(1, 100, 0));
        assertFalse(p.tryStart(2, 110, 0));
        assertEquals(-1L, p.finish(2, 120, true));
        assertEquals(100L, p.finish(1, 180, true));
        assertFalse(p.tryStart(2, 181, 0));
        assertTrue(p.tryStart(2, 500, 0));
    }

    @Test public void heatSlowsOcrAndRequestsLowerResolution() {
        AdaptiveOcrPolicy p = new AdaptiveOcrPolicy();
        assertTrue(p.tryStart(1, 0, 4));
        p.finish(1, 100, true);
        assertTrue(p.shouldReduceResolution());
        assertFalse(p.tryStart(2, 1399, 4));
        assertTrue(p.tryStart(2, 1500, 4));
    }

    @Test public void onlySustainedCoolPerformanceRestoresResolution() {
        AdaptiveOcrPolicy p = new AdaptiveOcrPolicy();
        for (int i = 0; i < 18; i++) {
            assertTrue(p.tryStart(i, i * 500L, 0));
            p.finish(i, i * 500L + 80, true);
            if (i < 17) assertFalse(p.canRestoreResolution());
        }
        assertTrue(p.canRestoreResolution());
    }

    @Test public void failuresBackOffAndSuccessResetsCount() {
        AdaptiveOcrPolicy p = new AdaptiveOcrPolicy();
        for (int i = 0; i < 3; i++) {
            assertTrue(p.tryStart(i, i * 3000L, 0));
            p.finish(i, i * 3000L + 100, false);
        }
        assertEquals(3, p.consecutiveFailures());
        assertTrue(p.tryStart(9, 10000, 0));
        p.finish(9, 10100, true);
        assertEquals(0, p.consecutiveFailures());
    }
}
