package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Regression checks for multiple paragraphs inside an ML Kit OCR block. */
public final class ExactCartographyCaptureTest {
    @Test
    public void eachExplicitOcrParagraphIsRetainedInOriginalOrder() {
        List<UniversalParagraphDetector.Detection> detections =
                ExactCartographyCapture.detectParagraphs(Arrays.asList(
                        "Împăratul controlează armata.\n\nBiserica răspunde.",
                        "Comerțul se dezvoltă."
                ));
        assertEquals(3, detections.size());
        assertEquals(0, detections.get(0).paragraphIndex());
        assertEquals(1, detections.get(1).paragraphIndex());
        assertEquals(2, detections.get(2).paragraphIndex());
        assertTrue(detections.get(1).paragraph().contains("Biserica"));
    }

    @Test
    public void blankOcrBlocksDoNotConsumeParagraphIndices() {
        List<UniversalParagraphDetector.Detection> detections =
                ExactCartographyCapture.detectParagraphs(Arrays.asList(
                        " ", "\n\n", "Pagina are text."
                ));
        assertEquals(1, detections.size());
        assertEquals(0, detections.get(0).paragraphIndex());
    }
}
