package ro.bibliotopicsearch.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameFreshnessTest {
    @Test public void resultsExpireFromCaptureRatherThanDelivery() {
        FrameFreshness guard = new FrameFreshness();
        assertTrue(guard.accepts(100, 999));
        assertFalse(guard.accepts(100, 1000));
        assertEquals(1000L, guard.deadline(100));
        assertFalse(guard.accepts(1000, 999));
    }
    @Test public void zoomAndTransformInvalidateEvenInFlightFrames() {
        FrameFreshness guard = new FrameFreshness();
        assertTrue(guard.accepts(100, 200));
        guard.invalidate(210);
        assertFalse(guard.accepts(100, 220));
        assertFalse(guard.accepts(210, 220));
        assertTrue(guard.accepts(211, 220));
        assertEquals(210L, guard.invalidate(199));
        assertFalse(guard.accepts(200, 220));
    }
}
