package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertArrayEquals;
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
    public void aQuestionWithYearCannotMarkAnUndatedClauseAsDirectEvidence() {
        ResearchSemanticEngine.Profile question = ResearchSemanticEngine.compile(
                "De ce crește inflația în 1929?", null);
        UniversalParagraphDetector.Detection undated = UniversalParagraphDetector.detect(
                "Inflația crește deoarece cererea depășește oferta.", 0);
        ResearchSemanticEngine.Answer partial = ResearchSemanticEngine.findBest(
                question, Collections.singletonList(undated));
        assertNotNull("The undated cause can still be browsed as related evidence", partial);
        assertFalse("An explicitly requested year is a required condition",
                QuestionEvidenceSearch.isDirectCandidate(question, partial));
    }

    @Test
    public void allQuestionAnchorsAreRequiredForDirectEvidence() {
        ResearchSemanticEngine.Profile question = ResearchSemanticEngine.compile(
                "De ce crește inflația în 1929?", null);
        ResearchSemanticEngine.Answer full = ResearchSemanticEngine.findBest(
                question, Collections.singletonList(UniversalParagraphDetector.detect(
                        "În 1929, inflația crește deoarece cererea depășește oferta.", 0)));
        assertNotNull(full);
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(question, full));
    }

    @Test
    public void multiIntentQuestionCannotBePassedByOneSelectedRelation() {
        ResearchSemanticEngine.Profile question = ResearchSemanticEngine.compile(
                "De ce și când a crescut inflația?", null);
        assertTrue(question.intents().size() > 1);
        ResearchSemanticEngine.Answer partial = ResearchSemanticEngine.findBest(
                question, Collections.singletonList(UniversalParagraphDetector.detect(
                        "Inflația a crescut în 1929 deoarece cererea depășea oferta.", 0)));
        if (partial != null) {
            assertFalse("Compound questions need facet-by-facet validation",
                    QuestionEvidenceSearch.isDirectCandidate(question, partial));
        }
    }

    @Test
    public void negatedQuestionDoesNotAcceptPositiveEvidenceAsDirect() {
        ResearchSemanticEngine.Profile question = ResearchSemanticEngine.compile(
                "De ce nu crește inflația?", null);
        assertTrue(question.asksNegation());
        ResearchSemanticEngine.Answer positive = ResearchSemanticEngine.findBest(
                question, Collections.singletonList(UniversalParagraphDetector.detect(
                        "Inflația crește deoarece cererea depășește oferta.", 0)));
        if (positive != null) {
            assertFalse("Negation is not an optional keyword",
                    QuestionEvidenceSearch.isDirectCandidate(question, positive));
        }
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
    @Test
    public void compoundQuestionLabelsEachExplicitFacetSeparately() {
        ResearchSemanticEngine.Profile query = ResearchSemanticEngine.compile(
                "De ce și când crește inflația?", null);
        assertTrue(query.intents().contains(ResearchSemanticEngine.Intent.WHY));
        assertTrue(query.intents().contains(ResearchSemanticEngine.Intent.WHEN));

        List<UniversalParagraphDetector.Detection> text = Arrays.asList(
                UniversalParagraphDetector.detect(
                        "Inflația crește deoarece cererea depășește oferta.", 0),
                UniversalParagraphDetector.detect(
                        "Inflația crește în 2020.", 1));
        List<ResearchSemanticEngine.Answer> answers = ResearchSemanticEngine.findAll(
                query, text, null, Integer.MAX_VALUE);
        boolean cause = false, date = false;
        for (ResearchSemanticEngine.Answer answer : answers) {
            if (!QuestionEvidenceSearch.isDirectCandidate(query, answer)) continue;
            if (answer.intent() == ResearchSemanticEngine.Intent.WHY
                    && answer.segment().contains("deoarece")) cause = true;
            if (answer.intent() == ResearchSemanticEngine.Intent.WHEN
                    && answer.segment().contains("2020")) date = true;
        }
        assertTrue("The explicit WHY component must be retrievable", cause);
        assertTrue("The explicit WHEN component must be retrievable", date);
    }

    @Test
    public void additionalSpecificConstraintCannotSilentlyDisappear() {
        UniversalParagraphDetector.Detection source = UniversalParagraphDetector.detect(
                "Inflația crește deoarece cererea depășește oferta.", 0);
        ResearchSemanticEngine.Profile broad = ResearchSemanticEngine.compile(
                "De ce crește inflația?", null);
        ResearchSemanticEngine.Profile narrow = ResearchSemanticEngine.compile(
                "De ce crește inflația în România în 2022?", null);

        ResearchSemanticEngine.Answer original = ResearchSemanticEngine.findBest(
                broad, Collections.singletonList(source));
        assertNotNull(original);
        assertTrue(QuestionEvidenceSearch.isDirectCandidate(broad, original));
        ResearchSemanticEngine.Answer partial = ResearchSemanticEngine.findBest(
                narrow, Collections.singletonList(source));
        if (partial != null) {
            assertFalse("A specific missing location or date must not become explicit",
                    QuestionEvidenceSearch.isDirectCandidate(narrow, partial));
        }
    }

    @Test
    public void marksTwoOriginalOcrLinesForOneLiteralSourceSentence() {
        List<SourceArchiveStore.OcrLine> lines = Arrays.asList(
                new SourceArchiveStore.OcrLine("18", 0,
                        "Inflația crește deoarece", .1f, .1f, .85f, .16f),
                new SourceArchiveStore.OcrLine("18", 1,
                        "cererea depășește oferta.", .1f, .17f, .85f, .23f),
                new SourceArchiveStore.OcrLine("18", 2,
                        "Alte date nu privesc cauza.", .1f, .3f, .85f, .36f));
        assertArrayEquals(new int[]{0, 1}, QuestionEvidenceSearch.matchingLineIndices(
                lines, "Inflația crește deoarece cererea depășește oferta."));
        assertArrayEquals(new int[0], QuestionEvidenceSearch.matchingLineIndices(
                Collections.emptyList(), "Inflația crește."));
    }

    @Test
    public void generatedWordsNeverReplaceTheLiteralSentence() {
        String original = "Inflația crește deoarece cererea depășește oferta.";
        ResearchSemanticEngine.Profile question = ResearchSemanticEngine.compile(
                "De ce crește inflația?", null);
        ResearchSemanticEngine.Answer answer = ResearchSemanticEngine.findBest(
                question, Collections.singletonList(
                        UniversalParagraphDetector.detect(original, 0)));
        assertNotNull(answer);
        assertEquals("The returned span must be the literal source sentence",
                original, answer.segment());
    }

}
