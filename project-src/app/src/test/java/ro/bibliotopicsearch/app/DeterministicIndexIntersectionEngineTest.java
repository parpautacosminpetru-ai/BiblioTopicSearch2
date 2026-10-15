package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

public final class DeterministicIndexIntersectionEngineTest {

    @Test
    public void subjectToObjectCreatesAuditableIntersection() {
        TopicNode imp = node("Împărat", "împărat");
        TopicNode arm = node("Armată", "armată");
        SemanticGraph graph = graph(proposition(
                "Împăratul controlează armata.",
                "Împăratul", "controlează", "armata"
        ));

        List<DeterministicIndexIntersectionEngine.Intersection> values =
                DeterministicIndexIntersectionEngine.detect(map(imp, arm), graph);

        assertEquals(1, values.size());
        assertEquals(DeterministicIndexIntersectionEngine.Rule.SUBJECT_TO_COMPLEMENT,
                values.get(0).rule());
        assertTrue(values.get(0).auditLabel().contains("[R1]"));
    }

    @Test
    public void twoObjectsDoNotCrossJustBecauseTheyCoOccur() {
        TopicNode imp = node("Împărat", "împărat");
        TopicNode arm = node("Armată", "armată");
        TopicNode bis = node("Biserică", "patriarh");
        SemanticGraph graph = graph(proposition(
                "Împăratul controlează armata și patriarhul.",
                "Împăratul", "controlează", "armata și patriarhul"
        ));

        List<DeterministicIndexIntersectionEngine.Intersection> values =
                DeterministicIndexIntersectionEngine.detect(map(imp, arm, bis), graph);

        assertTrue(hasPair(values, "Împărat", "Armată"));
        assertTrue(hasPair(values, "Împărat", "Biserică"));
        assertFalse(hasPair(values, "Armată", "Biserică"));
    }

    @Test
    public void sharedLexicalAnchorCannotProveTwoIndices() {
        TopicNode a = node("Putere politică", "putere");
        TopicNode b = node("Putere religioasă", "putere");
        SemanticGraph graph = graph(proposition(
                "Puterea organizează instituția.",
                "Puterea", "organizează", "instituția"
        ));

        assertTrue(DeterministicIndexIntersectionEngine.detect(map(a, b), graph).isEmpty());
    }

    @Test
    public void hierarchyIsNotReportedAsIntersection() {
        TopicNode arm = nodeWithPath("Instituții > Armată", "Armată", "armată");
        TopicNode rec = nodeWithPath("Instituții > Armată > Recrutare", "Recrutare", "recrutare");
        SemanticGraph graph = graph(proposition(
                "Armata organizează recrutarea.",
                "Armata", "organizează", "recrutarea"
        ));

        assertTrue(DeterministicIndexIntersectionEngine.detect(map(arm, rec), graph).isEmpty());
    }

    @Test
    public void explicitDintreFrameCanCrossTwoIndicesInsideSameField() {
        TopicNode imp = node("Împărat", "împărat");
        TopicNode bis = node("Biserică", "patriarh");
        SemanticGraph graph = graph(proposition(
                "Relația dintre împărat și patriarh este tensionată.",
                "Relația dintre împărat și patriarh", "este", "tensionată"
        ));

        List<DeterministicIndexIntersectionEngine.Intersection> values =
                DeterministicIndexIntersectionEngine.detect(map(imp, bis), graph);

        assertEquals(1, values.size());
        assertEquals(DeterministicIndexIntersectionEngine.Rule.EXPLICIT_RELATIONAL_FRAME,
                values.get(0).rule());
        assertTrue(values.get(0).auditLabel().contains("[R2]"));
    }

    @Test
    public void narrowRomanianInflectionCountsAsExplicitAnchor() {
        TopicNode imp = node("Împărat", "împărat");
        TopicNode arm = node("Armată", "armată");
        SemanticGraph graph = graph(proposition(
                "Împărații comandă armatele.",
                "Împărații", "comandă", "armatele"
        ));

        List<DeterministicIndexIntersectionEngine.Intersection> values =
                DeterministicIndexIntersectionEngine.detect(map(imp, arm), graph);

        assertEquals(1, values.size());
        assertEquals(DeterministicIndexIntersectionEngine.MatchKind.INFLECTIONAL_FAMILY,
                values.get(0).left().matchKind() == DeterministicIndexIntersectionEngine.MatchKind.INFLECTIONAL_FAMILY
                        ? values.get(0).left().matchKind()
                        : values.get(0).right().matchKind());
    }

    private static boolean hasPair(
            List<DeterministicIndexIntersectionEngine.Intersection> values,
            String a,
            String b
    ) {
        for (DeterministicIndexIntersectionEngine.Intersection value : values) {
            String left = value.left().node().title;
            String right = value.right().node().title;
            if ((left.equals(a) && right.equals(b)) || (left.equals(b) && right.equals(a))) return true;
        }
        return false;
    }

    private static TopicNode node(String title, String term) {
        return nodeWithPath(title, title, term);
    }

    private static TopicNode nodeWithPath(String path, String title, String term) {
        TopicNode out = new TopicNode(path, title, path.split(" > ").length);
        out.enabled = true;
        out.terms.add(term);
        return out;
    }

    private static TopicMap map(TopicNode... nodes) {
        return new TopicMap("test", "", Arrays.asList(nodes));
    }

    private static SemanticGraph graph(SemanticGraph.Proposition... propositions) {
        return new SemanticGraph(Arrays.asList(propositions), "");
    }

    private static SemanticGraph.Proposition proposition(
            String raw,
            String subject,
            String predicate,
            String object
    ) {
        return new SemanticGraph.Proposition(
                0, 0, 0,
                raw,
                subject,
                predicate,
                object,
                SemanticGraph.Relation.GENERIC,
                EnumSet.noneOf(SemanticGraph.Operator.class),
                new EnumMap<>(SemanticGraph.Slot.class),
                1.0,
                false
        );
    }
}
