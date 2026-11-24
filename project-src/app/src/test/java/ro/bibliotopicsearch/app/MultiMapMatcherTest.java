package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class MultiMapMatcherTest {
    private TopicNode node(String id, String name, String path, boolean enabled, String... terms) {
        TopicNode node = new TopicNode(id, name, path, path, 1);
        node.enabled = enabled;
        node.terms.addAll(Arrays.asList(terms));
        return node;
    }

    private TopicMatcher.SearchPlan compile(AppPrefs.MatchMode mode, boolean ignore, int chars, TopicNode... nodes) {
        return TopicMatcher.compile(new TopicMap("Test", "", Arrays.asList(nodes)), ignore, chars, mode);
    }

    @Test public void samePathAndTermInTwoMapsRemainDistinct() {
        TopicNode history = node("history", "Istorie", "Instituții", true, "biserică");
        TopicNode religion = node("religion", "Religie", "Instituții", true, "biserica");
        TopicNode disabled = node("disabled", "Exclusă", "Instituții", false, "biserica");
        TopicMatcher.SearchPlan plan = compile(AppPrefs.MatchMode.EXACT, true, 0, history, religion, disabled);
        List<TopicMatcher.CompiledTerm> hits = plan.candidates(1, "biserica");
        assertEquals(2, hits.size());
        assertEquals(2, plan.termCount());
        Set<String> identities = new HashSet<>();
        for (TopicMatcher.CompiledTerm hit : hits) identities.add(hit.node.identityKey());
        assertTrue(identities.contains(history.identityKey()));
        assertTrue(identities.contains(religion.identityKey()));
        assertFalse(identities.contains(disabled.identityKey()));
    }

    @Test public void modesAndCompareLengthKeepOriginalSemantics() {
        TopicNode n = node("a", "A", "N", true, "religie", "ordine socială");
        assertTrue(compile(AppPrefs.MatchMode.EXACT, true, 0, n).candidates(1, "religiei").isEmpty());
        assertEquals(1, compile(AppPrefs.MatchMode.PREFIX, true, 0, n).candidates(1, "religiei").size());
        assertEquals(1, compile(AppPrefs.MatchMode.CONTAINS, true, 0, n).candidates(1, "antireligie").size());
        assertEquals(1, compile(AppPrefs.MatchMode.FLEXIBLE, true, 0, n).candidates(1, "antireligie").size());
        assertEquals(1, compile(AppPrefs.MatchMode.EXACT, true, 4, n).candidates(1, "religios").size());
        assertEquals(1, compile(AppPrefs.MatchMode.EXACT, true, 0, n).candidates(2, "ordine sociala").size());
        assertTrue(compile(AppPrefs.MatchMode.EXACT, true, 0, n).candidates(1, "ordine sociala").isEmpty());
    }

    @Test public void normalizationHandlesRomanianUnicodeAndPunctuation() {
        assertEquals("stiinta", TopicMatcher.normalize("ȘTIINȚĂ!", true));
        assertEquals("știință", TopicMatcher.normalize("ŞTIINŢĂ!", false));
        assertEquals("știință", TopicMatcher.normalize("S\u0326TIINT\u0326A\u0306", false));
        assertEquals(Arrays.asList("biserica", "stat"), TopicMatcher.tokenize("Biserică—stat", true));
        assertTrue(TopicMatcher.tokenize("…", true).isEmpty());
        assertEquals("s-a", TopicMatcher.normalize("s‑a", true));
        TopicNode n = node("a", "A", "N", true, "biserică", "BISERICA", "biserica!");
        assertEquals(1, compile(AppPrefs.MatchMode.EXACT, true, 0, n).termCount());
    }

    @Test public void manyProfilesAndTermsAreIndexedTogether() {
        List<TopicNode> maps = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            TopicNode n = node("map" + i, "Hartă " + i, "Temă", true, "comun");
            for (int j = 0; j < 250; j++) n.terms.add("termen" + i + "x" + j);
            maps.add(n);
        }
        TopicMatcher.SearchPlan plan = TopicMatcher.compile(
                new TopicMap("Toate", "", maps), true, 0, AppPrefs.MatchMode.EXACT);
        assertEquals(10040, plan.termCount());
        assertEquals(40, plan.candidates(1, "comun").size());
        assertEquals(1, plan.candidates(1, "termen39x249").size());
        assertTrue(plan.candidates(1, "necunoscut").isEmpty());
    }

    @Test public void substringReturnsEachValueOnceEvenForRepeatedOccurrence() {
        TermIndex<String> index = new TermIndex<>();
        index.add("ana", "map1");
        index.add("ana", "map2");
        assertEquals(Arrays.asList("map1", "map2"), index.contains("ananana"));
    }
}
