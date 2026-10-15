package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class HistoricalCartographyEngineTest {

    @Test
    public void everyOcrParagraphIsAccountedEvenWithoutTopicMatch() {
        TopicMap map = map(
                node("Împărat", "împărat"),
                node("Armată", "armată")
        );
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect("Împăratul controlează armata.", 0),
                UniversalParagraphDetector.detect("Cerul era senin deasupra orașului.", 1)
        );
        SemanticGraph graph = SemanticGraphBuilder.build(detections);

        HistoricalCartographyEngine.CaptureMap result =
                HistoricalCartographyEngine.analyze(detections, graph, map);

        assertEquals(2, result.paragraphCount());
        int paragraphUnits = 0;
        HistoricalCartographyEngine.Status first = null;
        HistoricalCartographyEngine.Status second = null;
        for (HistoricalCartographyEngine.Unit unit : result.units()) {
            if (unit.kind() != HistoricalCartographyEngine.UnitKind.PARAGRAPH) continue;
            paragraphUnits++;
            if (unit.paragraphIndex() == 0) first = unit.status();
            if (unit.paragraphIndex() == 1) second = unit.status();
        }
        assertEquals(2, paragraphUnits);
        assertEquals(HistoricalCartographyEngine.Status.MAPPED, first);
        assertEquals(HistoricalCartographyEngine.Status.NO_INDEX, second);
    }

    @Test
    public void sameSpanSupportingTwoIndependentTopicsIsAmbiguousNotSilentlyAccepted() {
        TopicMap map = map(
                node("Putere politică", "putere"),
                node("Putere religioasă", "putere")
        );
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect("Puterea organizează instituția.", 0)
        );
        HistoricalCartographyEngine.CaptureMap result =
                HistoricalCartographyEngine.analyze(detections, SemanticGraphBuilder.build(detections), map);

        boolean foundAmbiguousParagraph = false;
        for (HistoricalCartographyEngine.Unit unit : result.units()) {
            if (unit.kind() == HistoricalCartographyEngine.UnitKind.PARAGRAPH
                    && unit.status() == HistoricalCartographyEngine.Status.AMBIGUOUS) {
                foundAmbiguousParagraph = true;
            }
        }
        assertTrue(foundAmbiguousParagraph);
        assertTrue(result.ambiguousCount() > 0);
    }

    @Test
    public void conservativeInflectionalFamilyWorksWithoutSynonymGuessing() {
        String emperor = RomanianLanguagePack.phraseFamilyKey("împărat");
        assertTrue(TopicMatcher.matchesInflectionalFamily("împărații", emperor));
        assertFalse(TopicMatcher.matchesInflectionalFamily("basileus", emperor));
    }

    @Test
    public void propositionCrossingIsCountedSeparatelyFromPlainMapping() {
        TopicMap map = map(
                node("Împărat", "împărat"),
                node("Armată", "armată")
        );
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect("Împăratul controlează armata.", 0)
        );
        HistoricalCartographyEngine.CaptureMap result =
                HistoricalCartographyEngine.analyze(detections, SemanticGraphBuilder.build(detections), map);

        assertTrue(result.mappedCount() > 0);
        assertTrue(result.intersectionCount() >= 1);
    }

    @Test
    public void explicitYearsBecomeDeterministicUnitFacets() {
        TopicMap map = map(node("Imperiu", "imperiu"));
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                UniversalParagraphDetector.detect(
                        "În 1054 imperiul trece printr-o ruptură importantă.",
                        0
                )
        );
        HistoricalCartographyEngine.CaptureMap result =
                HistoricalCartographyEngine.analyze(
                        detections,
                        SemanticGraphBuilder.build(detections),
                        map
                );

        boolean year = false;
        for (HistoricalCartographyEngine.Unit unit : result.units()) {
            if (unit.facets().contains("YEAR=1054")) year = true;
        }
        assertTrue(year);
    }

    private static TopicNode node(String title, String term) {
        TopicNode node = new TopicNode(title, title, 1);
        node.enabled = true;
        node.terms.add(term);
        return node;
    }

    private static TopicMap map(TopicNode... nodes) {
        return new TopicMap("test", "", Arrays.asList(nodes));
    }
}
