package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LiveEvidenceAnalyzerTest {
    private static TopicNode index(String id, String... terms) {
        TopicNode n = new TopicNode(id, id, 1);
        n.enabled = true;
        n.terms.addAll(Arrays.asList(terms));
        return n;
    }

    private static DeterministicMultiIndexMatcher.Plan plan(TopicNode... nodes) {
        return DeterministicMultiIndexMatcher.compile(
                new TopicMap("Test", "", Arrays.asList(nodes)));
    }

    @Test public void repeatedCanonicalFormCompiledOnceButEveryOccurrencePreserved() {
        DeterministicMultiIndexMatcher.Plan plan = plan(
                index("Armată", "armata", " armata "),
                index("Reformă", "reforma"));
        assertEquals(2, plan.termCount());

        String quote = "Armata nu a susținut reforma. Armata apare din nou.";
        LiveEvidenceAnalyzer.Snapshot result = LiveEvidenceAnalyzer.inspect(
                Collections.singletonList(quote), plan);
        assertEquals(2, result.groups.size());
        assertEquals(3, result.occurrences);
        assertEquals(1, result.cooccurrences());
        assertEquals(0, result.overlaps());
        assertEquals(" nu a susținut ", result.pairs.get(0).exactBetween);
        assertEquals("nu", result.pairs.get(0).visibleConnector);
        assertEquals("Armata nu a susținut reforma.", result.pairs.get(0).quote);
        assertTrue(result.warning.contains("originală"));
    }

    @Test public void sameSpanTwoTopicsIsOverlapNotRelationship() {
        LiveEvidenceAnalyzer.Snapshot result = LiveEvidenceAnalyzer.inspect(
                Collections.singletonList("armata"),
                plan(index("Militar", "armata"), index("Instituții", "armata")));
        assertEquals(2, result.occurrences);
        assertEquals(1, result.overlaps());
        assertEquals(0, result.cooccurrences());
        assertTrue(result.pairs.get(0).thematicOverlap);
    }

    @Test public void separateSentencesNeverCreateFakeCooccurrence() {
        LiveEvidenceAnalyzer.Snapshot result = LiveEvidenceAnalyzer.inspect(
                Collections.singletonList("Armata. Reforma."),
                plan(index("Armată", "armata"), index("Reformă", "reforma")));
        assertEquals(2, result.occurrences);
        assertTrue(result.pairs.isEmpty());
    }

    @Test public void chronologyIsOnlyProvisionalCandidate() {
        LiveEvidenceAnalyzer.Snapshot result = LiveEvidenceAnalyzer.inspect(
                Collections.singletonList("În anul 1848 s-au schimbat regulile."),
                plan(index("Armată", "armata")));
        assertEquals(0, result.occurrences);
        assertFalse(result.candidates.isEmpty());
        assertEquals("1848", result.candidates.get(0).original);
        assertEquals("POSIBIL AN", result.candidates.get(0).type);
        assertEquals("În anul 1848 s-au schimbat regulile.",
                result.candidates.get(0).quote);
    }

    @Test public void oneIndexTwiceIsNotItsOwnCooccurrence() {
        List<String> blocks = Arrays.asList("armata, armata.", "armata.");
        LiveEvidenceAnalyzer.Snapshot result = LiveEvidenceAnalyzer.inspect(
                blocks, plan(index("Armată", "armata")));
        assertEquals(3, result.occurrences);
        assertEquals(1, result.groups.size());
        assertTrue(result.pairs.isEmpty());
        assertEquals(3, result.groups.get(0).count());
    }
}
