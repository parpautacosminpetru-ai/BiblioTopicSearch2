package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LensDisplayPolicyTest {
    @Test
    public void queryNeverShowsMoreThanThreeColoredLayers() {
        for (LensDisplayPolicy.Level level : LensDisplayPolicy.Level.values()) {
            LensDisplayPolicy.Plan plan = LensDisplayPolicy.plan(level, true);
            assertTrue(plan.coloredLayers() <= 3);
            assertTrue(plan.target);
            assertTrue(plan.answer);
            assertFalse(plan.function);
        }
    }

    @Test
    public void blankParagraphModeShowsOnlySubjectAndFunction() {
        LensDisplayPolicy.Plan plan = LensDisplayPolicy.plan(LensDisplayPolicy.Level.PARAGRAPH, false);
        assertTrue(plan.subject);
        assertTrue(plan.function);
        assertFalse(plan.target);
        assertFalse(plan.answer);
        assertEquals(2, plan.coloredLayers());
    }

    @Test
    public void semanticZoomStopsAtBoundaries() {
        assertEquals(LensDisplayPolicy.Level.SOURCE,
                LensDisplayPolicy.Level.SOURCE.farther());
        assertEquals(LensDisplayPolicy.Level.SEGMENT,
                LensDisplayPolicy.Level.SEGMENT.closer());
        assertEquals(LensDisplayPolicy.Level.SENTENCE,
                LensDisplayPolicy.Level.PARAGRAPH.closer());
    }
}
