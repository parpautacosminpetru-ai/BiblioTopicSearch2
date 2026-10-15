package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class DeterministicMultiIndexMatcherTest {

    @Test
    public void scansManyTopicsAndReturnsIndependentAnchors() {
        TopicNode emperor = node("Împărat", "împărat", "basileus");
        TopicNode army = node("Armată", "armată", "tagmata");
        TopicNode church = node("Biserică", "patriarh", "biserică");
        TopicMap map = map(emperor, army, church);

        DeterministicMultiIndexMatcher.Plan plan = DeterministicMultiIndexMatcher.compile(map);
        List<DeterministicIndexIntersectionEngine.Anchor> anchors =
                DeterministicMultiIndexMatcher.matchText(
                        plan,
                        "Împăratul conduce armata, iar patriarhul negociază cu el."
                );

        assertEquals(3, anchors.size());
        assertEquals(6, plan.termCount());
        assertEquals(3, plan.activeNodes());
    }

    @Test
    public void exactMatchWinsOverFamilyMatchOnSameSpan() {
        TopicNode emperor = node("Împărat", "împărat");
        DeterministicMultiIndexMatcher.Plan plan =
                DeterministicMultiIndexMatcher.compile(map(emperor));

        List<DeterministicIndexIntersectionEngine.Anchor> exact =
                DeterministicMultiIndexMatcher.matchText(plan, "împărat");
        assertEquals(1, exact.size());
        assertEquals(DeterministicIndexIntersectionEngine.MatchKind.EXACT,
                exact.get(0).matchKind());

        List<DeterministicIndexIntersectionEngine.Anchor> inflected =
                DeterministicMultiIndexMatcher.matchText(plan, "împărații");
        assertEquals(1, inflected.size());
        assertEquals(DeterministicIndexIntersectionEngine.MatchKind.INFLECTIONAL_FAMILY,
                inflected.get(0).matchKind());
    }

    @Test
    public void userOwnedSynonymIsMatchedButNotInvented() {
        TopicNode emperor = node("Împărat", "împărat", "basileus");
        DeterministicMultiIndexMatcher.Plan plan =
                DeterministicMultiIndexMatcher.compile(map(emperor));

        assertEquals(1, DeterministicMultiIndexMatcher.matchText(plan, "basileus").size());
        assertTrue(DeterministicMultiIndexMatcher.matchText(
                DeterministicMultiIndexMatcher.compile(map(node("Împărat", "împărat"))),
                "basileus"
        ).isEmpty());
    }

    @Test
    public void propositionRolesStillSupportDeterministicCrossing() {
        TopicNode emperor = node("Împărat", "împărat");
        TopicNode army = node("Armată", "armată");
        TopicMap map = map(emperor, army);
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect("Împăratul conduce armata.", 0)
        );
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        SemanticGraph.Proposition proposition = graph.propositions().get(0);

        DeterministicMultiIndexMatcher.Plan plan = DeterministicMultiIndexMatcher.compile(map);
        List<DeterministicIndexIntersectionEngine.Anchor> anchors =
                DeterministicMultiIndexMatcher.matchProposition(plan, proposition);
        List<DeterministicIndexIntersectionEngine.Intersection> xs =
                DeterministicIndexIntersectionEngine.detectProposition(proposition, anchors);

        assertEquals(2, anchors.size());
        assertFalse(xs.isEmpty());
        assertEquals(DeterministicIndexIntersectionEngine.Rule.SUBJECT_TO_COMPLEMENT,
                xs.get(0).rule());
    }

    private static TopicNode node(String title, String... terms) {
        TopicNode node = new TopicNode(title, title, 1);
        node.enabled = true;
        node.terms.addAll(Arrays.asList(terms));
        return node;
    }

    private static TopicMap map(TopicNode... nodes) {
        return new TopicMap("test", "", Arrays.asList(nodes));
    }
}
