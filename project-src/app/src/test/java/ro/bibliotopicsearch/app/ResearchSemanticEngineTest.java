package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ResearchSemanticEngineTest {

    @Test
    public void whyQuestionSelectsExplicitCausalSentence() {
        String paragraph = "Inflația crește deoarece cererea depășește oferta. Alte efecte apar ulterior.";
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(paragraph, 0);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "De ce crește inflația?", null
        );

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                profile, Collections.singletonList(detection)
        );

        assertNotNull(answer);
        assertEquals(ResearchSemanticEngine.Intent.WHY, answer.intent());
        assertTrue(answer.segment().toLowerCase().contains("deoarece"));
        assertTrue(answer.score() >= 0.54);
    }

    @Test
    public void whyQuestionDoesNotInventCauseWithoutExplicitEvidence() {
        String paragraph = "Inflația crește în ultimii ani. Nivelul prețurilor este mai ridicat.";
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(paragraph, 0);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "De ce crește inflația?", null
        );

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                profile, Collections.singletonList(detection)
        );

        assertNull(answer);
    }

    @Test
    public void topicQueryPrefersRelevantSegmentAcrossParagraphs() {
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect("Munții ocupă o parte importantă a reliefului.", 0),
                UniversalParagraphDetector.detect("Fotosinteza transformă energia luminoasă în energie chimică.", 1)
        );
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile("fotosinteza", null);

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(profile, detections);

        assertNotNull(answer);
        assertEquals(1, answer.paragraphIndex());
        assertTrue(answer.segment().toLowerCase().contains("fotosinteza"));
    }

    @Test
    public void effectResearchPhraseRequiresEffectEvidence() {
        String paragraph = "Inflația reduce puterea de cumpărare și conduce la pierderi pentru gospodării.";
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(paragraph, 0);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile("efectele inflației", null);

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                profile, Collections.singletonList(detection)
        );

        assertNotNull(answer);
        assertEquals(ResearchSemanticEngine.Intent.EFFECT, answer.intent());
        assertTrue(answer.relationEvidence() >= 0.40);
    }

    @Test
    public void blankBarCanUseActiveThemeTerms() {
        TopicNode root = new TopicNode("Fotosinteză", "Fotosinteză", 1);
        root.enabled = true;
        root.terms.add("fotosinteza");
        List<TopicNode> nodes = new ArrayList<>();
        nodes.add(root);
        TopicMap map = new TopicMap("Fotosinteză", "", nodes);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile("", map);

        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                profile,
                Collections.singletonList(UniversalParagraphDetector.detect(
                        "Fotosinteza permite plantelor să transforme energia luminoasă.", 0
                ))
        );

        assertTrue(profile.enabled());
        assertNotNull(answer);
    }
}
