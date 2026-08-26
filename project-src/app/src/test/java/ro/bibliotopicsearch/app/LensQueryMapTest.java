package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;

public final class LensQueryMapTest {
    @Test
    public void questionKeepsSubjectWordsAndDropsRelationControlWord() {
        TopicMap map = LensQueryMap.build("cauzele inflației", null, Color.YELLOW);
        assertEquals(1, map.nodes.size());
        TopicNode node = map.nodes.get(0);
        assertTrue(node.terms.contains("inflatiei") || node.terms.contains("inflatia"));
        assertFalse(node.terms.contains("cauzele"));
    }

    @Test
    public void plainTopicKeepsFullPhraseForFastLexicalLocation() {
        TopicMap map = LensQueryMap.build("Martin Luther", null, Color.YELLOW);
        assertEquals(1, map.nodes.size());
        TopicNode node = map.nodes.get(0);
        assertTrue(node.terms.contains("Martin Luther"));
        assertTrue(node.terms.contains("martin"));
        assertTrue(node.terms.contains("luther"));
    }
}
