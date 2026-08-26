package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import org.junit.Test;

public final class SemanticMarkBoxCopyTest {
    @Test
    public void copiesInputBox() {
        RectF input = new RectF(1f, 2f, 3f, 4f);
        SemanticTextMark mark = new SemanticTextMark(SemanticTextMark.Kind.SUBJECT, input, "x", "S", 0.5, 0);
        input.left = 99f;
        assertEquals(1f, mark.box.left, 0f);
    }
}
