package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class MultiAxisSemanticRuntimeTest {

    private static UniversalParagraphDetector.Detection d(
            int index,
            String text,
            String subject,
            UniversalDetectionLexicon.Function function
    ) {
        return new UniversalParagraphDetector.Detection(
                index,
                text,
                subject,
                function,
                UniversalDetectionLexicon.Function.UNKNOWN,
                0.93,
                0.88,
                Collections.emptyList(),
                EnumSet.noneOf(UniversalDetectionLexicon.Operator.class),
                Collections.emptyList()
        );
    }

    @Test
    public void complexSubjectActivatesOverlappingAxes() {
        UniversalParagraphDetector.Detection detection = d(
                0,
                "Efectele economice ale inflației asupra gospodăriilor din România după 2020 reduc puterea de cumpărare.",
                "efectele economice ale inflației asupra gospodăriilor din România după 2020",
                UniversalDetectionLexicon.Function.CAUSE_EFFECT
        );
        SemanticGraph graph = SemanticGraphBuilder.build(Collections.singletonList(detection));
        UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, graph);

        assertTrue(frame.head().toLowerCase().contains("infla"));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.EFFECT));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.DOMAIN));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.TARGET));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.POPULATION));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.LOCATION));
        assertTrue(frame.hasAxis(UniversalSubjectFrame.Axis.TIME));
        assertTrue(frame.axes().size() >= 6);
    }

    @Test
    public void queryMatrixCombinesQuestionAndSubjectAxes() {
        UniversalParagraphDetector.Detection detection = d(
                0,
                "Inflația produce efecte economice asupra gospodăriilor în România.",
                "efectele economice ale inflației asupra gospodăriilor în România",
                UniversalDetectionLexicon.Function.CAUSE_EFFECT
        );
        SemanticGraph graph = SemanticGraphBuilder.build(Collections.singletonList(detection));
        UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, graph);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "Care sunt efectele economice ale inflației asupra gospodăriilor în România?",
                null
        );
        SemanticQueryMatrix.Matrix matrix = SemanticQueryMatrix.compile(profile, frame, detection.function());

        assertTrue(matrix.slots().contains(SemanticQueryMatrix.QuerySlot.EFFECT));
        assertTrue(matrix.slots().contains(SemanticQueryMatrix.QuerySlot.TARGET));
        assertTrue(matrix.slots().contains(SemanticQueryMatrix.QuerySlot.WHERE));
        assertTrue(matrix.slots().contains(SemanticQueryMatrix.QuerySlot.DOMAIN));
        assertTrue(matrix.slots().contains(SemanticQueryMatrix.QuerySlot.EVIDENCE));
    }

    @Test
    public void routerChangesToolModeFromMeaningCombination() {
        UniversalParagraphDetector.Detection detection = d(
                0,
                "Efectul inflației a crescut cu 12% în 2024 asupra gospodăriilor.",
                "efectul inflației asupra gospodăriilor în 2024",
                UniversalDetectionLexicon.Function.CAUSE_EFFECT
        );
        SemanticGraph graph = SemanticGraphBuilder.build(Collections.singletonList(detection));
        UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, graph);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "Cu cât s-a schimbat efectul inflației în 2024?",
                null
        );
        SemanticQueryMatrix.Matrix matrix = SemanticQueryMatrix.compile(profile, frame, detection.function());
        List<SemanticToolRouter.Route> routes = SemanticToolRouter.route(frame, matrix);

        assertTrue(hasRoute(routes, SemanticToolRouter.Tool.EFFECT_GRAPH, SemanticToolRouter.Mode.TRACE_EFFECT));
        assertTrue(hasRoute(routes, SemanticToolRouter.Tool.TEMPORAL_GRAPH, SemanticToolRouter.Mode.TIME_POINT));
        assertTrue(hasTool(routes, SemanticToolRouter.Tool.EVIDENCE_BINDER));
    }

    @Test
    public void oneParagraphCanBelongToManyHypergraphAxes() {
        List<UniversalParagraphDetector.Detection> detections = Arrays.asList(
                d(0,
                        "Efectele economice ale inflației asupra gospodăriilor din România după 2020 sunt analizate.",
                        "efectele economice ale inflației asupra gospodăriilor din România după 2020",
                        UniversalDetectionLexicon.Function.CAUSE_EFFECT),
                d(1,
                        "Inflația este un fenomen monetar.",
                        "inflația",
                        UniversalDetectionLexicon.Function.DEFINITION)
        );
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        MultiAxisSemanticRuntime.Index index = MultiAxisSemanticRuntime.build(
                detections,
                graph,
                ResearchSemanticEngine.compile("efectele inflației", null)
        );

        assertEquals(2, index.entries().size());
        assertTrue(index.multiAxisParagraphs() >= 1);
        assertTrue(index.axisMembership().get(UniversalSubjectFrame.Axis.EFFECT).contains(0));
        assertTrue(index.axisMembership().get(UniversalSubjectFrame.Axis.DOMAIN).contains(0));
        assertTrue(index.activeToolModes() > 3);
        assertNotNull(index.entryForParagraph(0));
        assertFalse(index.entryForParagraph(0).routes().isEmpty());
    }

    private static boolean hasTool(List<SemanticToolRouter.Route> routes, SemanticToolRouter.Tool tool) {
        for (SemanticToolRouter.Route route : routes) if (route.tool() == tool) return true;
        return false;
    }

    private static boolean hasRoute(
            List<SemanticToolRouter.Route> routes,
            SemanticToolRouter.Tool tool,
            SemanticToolRouter.Mode mode
    ) {
        for (SemanticToolRouter.Route route : routes) {
            if (route.tool() == tool && route.mode() == mode) return true;
        }
        return false;
    }
}
