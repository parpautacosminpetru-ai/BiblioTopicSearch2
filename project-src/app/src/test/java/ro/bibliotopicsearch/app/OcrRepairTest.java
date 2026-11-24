package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class OcrRepairTest {
    @Test public void digitConfusionsAreNarrowAndNeverRewriteDates() {
        assertEquals(Arrays.asList("istorie"), OcrRepair.digitVariants("ist0rie"));
        assertEquals(Arrays.asList("biserica", "blserica"), OcrRepair.digitVariants("b1serica"));
        assertTrue(OcrRepair.digitVariants("1900").isEmpty());
        assertTrue(OcrRepair.digitVariants("a1b0c").isEmpty());
        assertTrue(OcrRepair.digitVariants("5ecol").isEmpty());
    }

    @Test public void lineEndHyphensPreserveBothExactSpellings() {
        assertEquals(Arrays.asList("organizare", "organi-zare"), OcrRepair.joinedVariants("organi-", "zare"));
        assertTrue(OcrRepair.joinedVariants("organi", "zare").isEmpty());
        assertTrue(OcrRepair.joinedVariants("1900-", "1901").isEmpty());
    }
}
