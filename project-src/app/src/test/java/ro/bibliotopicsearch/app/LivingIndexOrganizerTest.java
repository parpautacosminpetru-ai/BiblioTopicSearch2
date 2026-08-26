package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LivingIndexOrganizerTest {

    @Test
    public void oneOcrOccurrenceReceivesSeveralIndependentCriteria() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "În 1517, Reforma protestantă a schimbat viața religioasă și politică.", 0
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(detection);
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ParagraphCartography.Map map = ParagraphCartography.build(detections, graph);

        List<LivingIndexEngine.Candidate> values = LivingIndexAutoOrganizerEngine.detect(
                detections, graph, map, new LivingIndexStore.State()
        );
        LivingIndexEngine.Candidate year = findExact(values, "1517");

        assertEquals(LivingIndexStore.Category.DATE, year.category());
        assertTrue(year.axes().contains("PRIMARY=DATE"));
        assertTrue(year.axes().contains("TIME=DATE_OR_YEAR"));
        assertTrue(year.axes().contains("DOMAIN=HISTORY"));
        assertTrue(year.axes().contains("DOMAIN=RELIGION"));
        assertTrue(year.axes().contains("DOMAIN=POLITICS"));
        assertTrue(hasPrefix(year.axes(), "CARTOGRAPHY=L"));
        assertTrue(hasPrefix(year.axes(), "DISCOURSE="));
    }

    @Test
    public void sameEntryAppearsInSeveralOrganizerBucketsWithoutDuplication() {
        LivingIndexStore.State state = new LivingIndexStore.State();
        List<String> criteria = Arrays.asList(
                "PRIMARY=EVENT",
                "ONTOLOGY=EVENT",
                "DOMAIN=HISTORY",
                "DOMAIN=RELIGION",
                "RELATION=CAUSE",
                "CARTOGRAPHY=L2:NARROWS",
                "SCOPE=LOCAL_L2"
        );
        LivingIndexStore.Entry entry = state.merge(
                "Reforma protestantă",
                LivingIndexStore.Category.EVENT,
                true,
                new LivingIndexStore.Ref(11L, 4, "137", "F:EXPLANATION", criteria, 1L)
        );

        LivingIndexOrganizer.Index index = LivingIndexOrganizer.build(state);
        assertTrue(index.groups(LivingIndexOrganizer.Dimension.PRIMARY).get("EVENT").contains(entry));
        assertTrue(index.groups(LivingIndexOrganizer.Dimension.DOMAIN).get("HISTORY").contains(entry));
        assertTrue(index.groups(LivingIndexOrganizer.Dimension.DOMAIN).get("RELIGION").contains(entry));
        assertTrue(index.groups(LivingIndexOrganizer.Dimension.RELATION).get("CAUSE").contains(entry));
        assertTrue(index.groups(LivingIndexOrganizer.Dimension.CARTOGRAPHY).get("L2:NARROWS").contains(entry));
        assertTrue(index.multiCriteriaEntries() >= 1);
    }

    @Test
    public void unresolvedProperNameKeepsUsefulContextButStaysInbox() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Johannes Bugenhagen apare în contextul reformei protestante și al vieții religioase.", 0
        );
        List<UniversalParagraphDetector.Detection> detections = Collections.singletonList(detection);
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ParagraphCartography.Map map = ParagraphCartography.build(detections, graph);

        LivingIndexEngine.Candidate value = findStartsWith(
                LivingIndexAutoOrganizerEngine.detect(
                        detections, graph, map, new LivingIndexStore.State()
                ),
                "Johannes Bugenhagen"
        );
        assertEquals(LivingIndexStore.Category.INBOX, value.category());
        assertFalse(value.validated());
        assertTrue(value.axes().contains("ONTOLOGY=UNRESOLVED"));
        assertTrue(value.axes().contains("ROLE=NEEDS_VALIDATION"));
        assertTrue(value.axes().contains("DOMAIN=HISTORY"));
        assertTrue(value.axes().contains("DOMAIN=RELIGION"));
    }

    private LivingIndexEngine.Candidate findExact(List<LivingIndexEngine.Candidate> values, String surface) {
        for (LivingIndexEngine.Candidate value : values) {
            if (value.surface().equalsIgnoreCase(surface)) return value;
        }
        throw new AssertionError("Missing candidate: " + surface);
    }

    private LivingIndexEngine.Candidate findStartsWith(List<LivingIndexEngine.Candidate> values, String surface) {
        for (LivingIndexEngine.Candidate value : values) {
            if (value.surface().toLowerCase().startsWith(surface.toLowerCase())) return value;
        }
        throw new AssertionError("Missing candidate beginning with: " + surface);
    }

    private boolean hasPrefix(List<String> values, String prefix) {
        for (String value : values) if (value.startsWith(prefix)) return true;
        return false;
    }
}
