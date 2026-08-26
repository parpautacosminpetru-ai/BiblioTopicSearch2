package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class LivingIndexEngineTest {

    @Test
    public void unknownProperNameGoesToInboxThenBecomesPermanentDetectorAfterValidation() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "În dezbatere, Martin Luther critică practica prezentată.", 0
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(detection);
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ParagraphCartography.Map map = ParagraphCartography.build(detections, graph);
        LivingIndexStore.State state = new LivingIndexStore.State();

        LivingIndexEngine.Candidate first = find(
                LivingIndexEngine.detect(detections, graph, map, state), "Martin Luther"
        );
        assertEquals(LivingIndexStore.Category.INBOX, first.category());
        assertFalse(first.validated());

        LivingIndexStore.Entry entry = state.merge(
                "Martin Luther",
                LivingIndexStore.Category.INBOX,
                false,
                new LivingIndexStore.Ref(1L, 0, "12", first.contextCode(), first.axes(), 1L)
        );
        assertTrue(state.validate(entry.id(), LivingIndexStore.Category.PERSON));

        LivingIndexEngine.Candidate learned = find(
                LivingIndexEngine.detect(detections, graph, map, state), "Martin Luther"
        );
        assertEquals(LivingIndexStore.Category.PERSON, learned.category());
        assertTrue(learned.validated());
        assertEquals(entry.id(), learned.knownId());
    }

    @Test
    public void explicitYearIsIndexedDeterministically() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Reforma este discutată în anul 1536.", 0
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(detection);
        LivingIndexEngine.Candidate year = find(
                LivingIndexEngine.detect(
                        detections,
                        SemanticGraphBuilder.build(detections),
                        ParagraphCartography.build(detections),
                        new LivingIndexStore.State()
                ),
                "1536"
        );
        assertEquals(LivingIndexStore.Category.DATE, year.category());
        assertTrue(year.validated());
    }

    @Test
    public void narrowerCalvinQueryIsNotSatisfiedByLutherOnlyParagraph() {
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "Care sunt cauzele reformei calvine?", null
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(
                UniversalParagraphDetector.detect(
                        "Martin Luther contestă practica indulgențelor și publică tezele sale.", 0
                )
        );
        assertNull(ResearchSemanticEngine.findBest(profile, detections));
    }

    @Test
    public void contextCodeKeepsHierarchyAndSemanticTypeWithoutStoringClaim() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Efectele economice ale inflației în România după 2020 sunt analizate.", 0
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(detection);
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ParagraphCartography.Map map = ParagraphCartography.build(detections, graph);
        List<LivingIndexEngine.Candidate> candidates = LivingIndexEngine.detect(
                detections, graph, map, new LivingIndexStore.State()
        );
        assertFalse(candidates.isEmpty());
        boolean structured = false;
        for (LivingIndexEngine.Candidate candidate : candidates) {
            if (candidate.contextCode().contains("F:")
                    && candidate.contextCode().contains("L:")
                    && candidate.contextCode().contains("T:")) {
                structured = true;
                break;
            }
        }
        assertTrue(structured);
    }

    private LivingIndexEngine.Candidate find(List<LivingIndexEngine.Candidate> values, String surface) {
        for (LivingIndexEngine.Candidate value : values) {
            if (value.surface().equalsIgnoreCase(surface)) return value;
        }
        throw new AssertionError("Missing candidate: " + surface + " in " + values.size() + " candidates");
    }
}
