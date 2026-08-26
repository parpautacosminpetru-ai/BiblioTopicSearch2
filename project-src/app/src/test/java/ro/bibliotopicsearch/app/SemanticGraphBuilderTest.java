package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class SemanticGraphBuilderTest {

    @Test
    public void operatorsStayBoundToTheirOwnSentence() {
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect(
                        "Inflația nu scade. Ea poate crește rapid.", 0
                )
        );

        SemanticGraph graph = SemanticGraphBuilder.build(detections);

        assertTrue(graph.size() >= 2);
        SemanticGraph.Proposition first = graph.propositions().get(0);
        SemanticGraph.Proposition second = graph.propositions().get(1);
        assertTrue(first.operators().contains(SemanticGraph.Operator.NEGATION));
        assertFalse(first.operators().contains(SemanticGraph.Operator.POSSIBILITY));
        assertTrue(second.operators().contains(SemanticGraph.Operator.POSSIBILITY));
        assertFalse(second.operators().contains(SemanticGraph.Operator.NEGATION));
    }

    @Test
    public void causalClauseBecomesExplicitWhyProposition() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Inflația crește deoarece cererea depășește oferta.", 0
        );
        SemanticGraph graph = SemanticGraphBuilder.build(detection);

        SemanticGraph.Proposition cause = null;
        for (SemanticGraph.Proposition proposition : graph.propositions()) {
            if (proposition.relation() == SemanticGraph.Relation.CAUSE) cause = proposition;
        }

        assertNotNull(cause);
        assertTrue(cause.raw().toLowerCase().contains("deoarece"));
        assertTrue(cause.slots().containsKey(SemanticGraph.Slot.WHY));
    }

    @Test
    public void answerCarriesScopedSemanticStructure() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Inflația poate reduce puterea de cumpărare și conduce la pierderi.", 0
        );
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "Care sunt efectele inflației?", null
        );

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                profile, Arrays.asList(detection)
        );

        assertNotNull(answer);
        assertEquals(ResearchSemanticEngine.Intent.EFFECT, answer.intent());
        assertEquals(SemanticGraph.Relation.EFFECT, answer.relation());
        assertTrue(answer.modal());
        assertFalse(answer.negated());
        assertTrue(answer.slots().containsKey(SemanticGraph.Slot.EFFECT));
    }
}
