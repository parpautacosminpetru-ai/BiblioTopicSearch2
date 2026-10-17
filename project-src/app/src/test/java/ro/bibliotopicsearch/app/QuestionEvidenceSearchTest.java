package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class QuestionEvidenceSearchTest {

    @Test
    public void causalQuestionOnlyReturnsCausalSourceText() {
        ResearchSemanticEngine.Profile query =
                ResearchSemanticEngine.compile("De ce crește inflația?", null);
        UniversalParagraphDetector.Detection direct =
                UniversalParagraphDetector.detect(
                        "Inflația crește deoarece cererea depășește oferta.", 0);
        ResearchSemanticEngine.Answer found = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(direct));
        assertNotNull(found);
        assertEquals(ResearchSemanticEngine.Intent.WHY, found.intent());
        assertTrue(found.segment().contains("deoarece"));
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(query, found));

        UniversalParagraphDetector.Detection mention =
                UniversalParagraphDetector.detect(
                        "Inflația crește în ultimii ani.", 0);
        ResearchSemanticEngine.Answer absent = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(mention));
        assertNull(absent);
    }

    @Test
    public void whenQuestionCanLocateSixteenthCenturyYear() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "Când a fost publicată lucrarea?", null);
        UniversalParagraphDetector.Detection source =
                UniversalParagraphDetector.detect(
                        "Lucrarea a fost publicată în 1543.", 0);
        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(source));
        assertNotNull(answer);
        assertTrue(answer.segment().contains("1543"));
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(query, answer));
    }

    @Test
    public void everyDifferentExplicitParagraphIsReturnedNotOnlyBestOne() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "Când a fost semnat acordul?", null);
        List<UniversalParagraphDetector.Detection> text = Arrays.asList(
                UniversalParagraphDetector.detect("Acordul a fost semnat în 1761.", 0),
                UniversalParagraphDetector.detect("Acordul a fost semnat din nou în 1802.", 1),
                UniversalParagraphDetector.detect("Acordul a avut mai mulți semnatari.", 2)
        );
        List<ResearchSemanticEngine.Answer> matches = ResearchSemanticEngine.findAll(
                query, text, null, Integer.MAX_VALUE);
        boolean older = false, newer = false;
        for (ResearchSemanticEngine.Answer result : matches) {
            if (result.segment().contains("1761")) older = true;
            if (result.segment().contains("1802")) newer = true;
        }
        assertTrue(older);
        assertTrue(newer);
    }

    @Test
    public void sourceWithoutQuestionTargetDoesNotCountAsAnswer() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "De ce a fost construit podul?", null);
        UniversalParagraphDetector.Detection unrelated =
                UniversalParagraphDetector.detect(
                        "Drumul a fost construit pentru transportul grânelor.", 0);
        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(unrelated));
        if (answer != null) {
            assertFalse(QuestionEvidenceSearch.isDirectCandidate(query, answer));
        }
    }

    @Test
    public void whatQuestionRequiresAnExplicitPredicationWithItsTarget() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "Ce include biblioteca?", null);
        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(
                        UniversalParagraphDetector.detect(
                                "Biblioteca include manuscrise și hărți istorice.", 0)));
        assertNotNull(answer);
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(query, answer));
    }

    @Test
    public void quantityQuestionRequiresExplicitSourceNumber() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "Câți membri are consiliul?", null);
        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(
                        UniversalParagraphDetector.detect("Consiliul are 12 membri.", 0)));
        assertNotNull(answer);
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(query, answer));

        ResearchSemanticEngine.Answer withoutNumber = ResearchSemanticEngine.findBest(
                query, Collections.singletonList(
                        UniversalParagraphDetector.detect("Consiliul are mai mulți membri.", 0)));
        if (withoutNumber != null) {
            assertFalse(QuestionEvidenceSearch.isDirectCandidate(query, withoutNumber));
        }
    }
}
