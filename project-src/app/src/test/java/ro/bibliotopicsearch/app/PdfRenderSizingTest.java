package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PdfRenderSizingTest {

    @Test
    public void typicalA4PageTargetsReadableWidthWithoutBreakingPixelCap() {
        PdfRenderSizing.Size size = PdfRenderSizing.compute(
                595, 842, 1800, 5_500_000L, 4.0f
        );

        assertTrue(size.width >= 1700);
        assertTrue(size.pixels <= 5_500_000L);
        assertTrue(size.height > size.width);
    }

    @Test
    public void hugePageIsDownscaledToPixelBudget() {
        PdfRenderSizing.Size size = PdfRenderSizing.compute(
                5000, 7000, 1800, 5_500_000L, 4.0f
        );

        assertTrue(size.pixels <= 5_500_000L);
        assertTrue(size.width < 5000);
        assertTrue(size.height < 7000);
    }

    @Test
    public void retryCanUseLargerBudget() {
        PdfRenderSizing.Size normal = PdfRenderSizing.compute(
                595, 842, 1800, 5_500_000L, 4.0f
        );
        PdfRenderSizing.Size retry = PdfRenderSizing.compute(
                595, 842, 2400, 8_000_000L, 4.0f
        );

        assertTrue(retry.width >= normal.width);
        assertTrue(retry.pixels <= 8_000_000L);
        assertEquals(595f / 842f, retry.width / (float) retry.height, 0.02f);
    }
}
