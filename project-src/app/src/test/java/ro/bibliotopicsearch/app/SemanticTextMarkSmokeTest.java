package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;

public final class SemanticTextMarkSmokeTest {
    @Test
    public void preservesKindBoxAndConfidence() {
        SemanticTextMark mark = new SemanticTextMark(
                SemanticTextMark.Kind.SUBJECT,
                new RectF(1f, 2f, 11f, 12f),
                "memoria",
                "SUBIECT",
                0.82,
                2
        );
        assertEquals(SemanticTextMark.Kind.SUBJECT, mark.kind);
        assertEquals("memoria", mark.text);
        assertEquals(2, mark.paragraphIndex);
        assertTrue(mark.box.width() > 0f);
        assertTrue(mark.confidence > 0.8);
    }
}
