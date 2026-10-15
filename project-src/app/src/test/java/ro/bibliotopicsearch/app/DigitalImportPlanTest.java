package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DigitalImportPlanTest {

    @Test
    public void mapsPdfPagesToPrintedPagesDeterministically() {
        DigitalImportPlan plan = new DigitalImportPlan(12, 311, 1, true);

        assertEquals(300, plan.pageCount());
        assertEquals(1, plan.printedPageForPdfPage(12));
        assertEquals(2, plan.printedPageForPdfPage(13));
        assertEquals(300, plan.printedPageForPdfPage(311));
        assertEquals(300, plan.printedPageTo());
        assertTrue(plan.validForPdf(350));
    }

    @Test
    public void rejectsRangePastPdfEnd() {
        DigitalImportPlan plan = new DigitalImportPlan(4, 50, 10, false);
        assertFalse(plan.validForPdf(49));
        assertTrue(plan.validForPdf(50));
    }
}
