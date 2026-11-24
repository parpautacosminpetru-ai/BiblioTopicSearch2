package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RepairMatcherTest {
    private TopicNode node(String id, String... terms) {
        TopicNode n = new TopicNode(id, id, "Temă", "Temă", 1);
        n.enabled = true;
        n.terms.addAll(Arrays.asList(terms));
        return n;
    }

    private TopicMatcher.SearchPlan plan(boolean enabled, int length, TopicNode... nodes) {
        return TopicMatcher.compile(new TopicMap("Toate", "", Arrays.asList(nodes)),
                true, length, AppPrefs.MatchMode.EXACT, enabled);
    }

    @Test public void repairKeepsAllMapsButRejectsAmbiguousAlternatives() {
        TopicNode a = node("a", "biserica"), b = node("b", "biserică");
        assertEquals(2, plan(true, 0, a, b).repairedCandidates("b1serica").size());
        assertTrue(plan(true, 0, a, b, node("c", "blserica")).repairedCandidates("b1serica").isEmpty());
        assertTrue(plan(false, 0, a, b).repairedCandidates("b1serica").isEmpty());
    }

    @Test public void literalMatchAlwaysHasPriorityAndRepairRequiresExactFullTerm() {
        assertTrue(plan(true, 0, node("a", "ist0rie", "istorie")).repairedCandidates("ist0rie").isEmpty());
        assertTrue(plan(true, 3, node("a", "istoric")).repairedCandidates("ist0rie").isEmpty());
        assertEquals(1, plan(true, 3, node("a", "organizare", "organism")).exactCandidates(1, "organizare").size());
    }
}
