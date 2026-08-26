package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared multi-axis semantic index for live OCR and finalized One-Pass sessions.
 * The hierarchy remains one projection; axis memberships form the parallel hypergraph.
 */
public final class MultiAxisSemanticRuntime {
    private MultiAxisSemanticRuntime() {}

    public static final class Entry {
        private final int paragraphIndex;
        private final UniversalSubjectFrame.Frame frame;
        private final SemanticQueryMatrix.Matrix matrix;
        private final List<SemanticToolRouter.Route> routes;

        Entry(
                int paragraphIndex,
                UniversalSubjectFrame.Frame frame,
                SemanticQueryMatrix.Matrix matrix,
                List<SemanticToolRouter.Route> routes
        ) {
            this.paragraphIndex = paragraphIndex;
            this.frame = frame;
            this.matrix = matrix;
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        }

        public int paragraphIndex() { return paragraphIndex; }
        public UniversalSubjectFrame.Frame frame() { return frame; }
        public SemanticQueryMatrix.Matrix matrix() { return matrix; }
        public List<SemanticToolRouter.Route> routes() { return routes; }
    }

    public static final class Index {
        private final List<Entry> entries;
        private final Map<UniversalSubjectFrame.Axis, List<Integer>> axisMembership;
        private final Map<String, List<Integer>> headMembership;
        private final int multiAxisParagraphs;
        private final int activeToolModes;

        Index(
                List<Entry> entries,
                Map<UniversalSubjectFrame.Axis, List<Integer>> axisMembership,
                Map<String, List<Integer>> headMembership,
                int multiAxisParagraphs,
                int activeToolModes
        ) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            EnumMap<UniversalSubjectFrame.Axis, List<Integer>> axisCopy =
                    new EnumMap<>(UniversalSubjectFrame.Axis.class);
            for (Map.Entry<UniversalSubjectFrame.Axis, List<Integer>> entry : axisMembership.entrySet()) {
                axisCopy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.axisMembership = Collections.unmodifiableMap(axisCopy);
            Map<String, List<Integer>> headCopy = new LinkedHashMap<>();
            for (Map.Entry<String, List<Integer>> entry : headMembership.entrySet()) {
                headCopy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.headMembership = Collections.unmodifiableMap(headCopy);
            this.multiAxisParagraphs = Math.max(0, multiAxisParagraphs);
            this.activeToolModes = Math.max(0, activeToolModes);
        }

        public List<Entry> entries() { return entries; }
        public Map<UniversalSubjectFrame.Axis, List<Integer>> axisMembership() { return axisMembership; }
        public Map<String, List<Integer>> headMembership() { return headMembership; }
        public int multiAxisParagraphs() { return multiAxisParagraphs; }
        public int activeToolModes() { return activeToolModes; }
        public boolean isEmpty() { return entries.isEmpty(); }

        public Entry entryForParagraph(int paragraphIndex) {
            for (Entry entry : entries) if (entry.paragraphIndex == paragraphIndex) return entry;
            return null;
        }
    }

    private static volatile Index latestLive = emptyIndex();

    public static Index latestLive() { return latestLive; }

    /** Called from the semantic graph lifecycle for the current OCR sidecar. */
    public static void refreshLive(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ResearchSemanticEngine.Profile profile
    ) {
        if (!isCompatible(detections, graph)) return;
        latestLive = build(detections, graph, profile);
    }

    public static Index build(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ResearchSemanticEngine.Profile profile
    ) {
        if (detections == null || detections.isEmpty()) return emptyIndex();
        if (graph == null) graph = SemanticGraphBuilder.build(detections);

        List<Entry> entries = new ArrayList<>();
        EnumMap<UniversalSubjectFrame.Axis, List<Integer>> axisMembership =
                new EnumMap<>(UniversalSubjectFrame.Axis.class);
        Map<String, List<Integer>> headMembership = new LinkedHashMap<>();
        Set<String> routeModes = new LinkedHashSet<>();
        int multiAxis = 0;

        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            List<SemanticGraph.Proposition> propositions = graph.propositionsForParagraph(detection.paragraphIndex());
            UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, propositions);
            SemanticQueryMatrix.Matrix matrix = SemanticQueryMatrix.compile(profile, frame, detection.function());
            List<SemanticToolRouter.Route> routes = SemanticToolRouter.route(frame, matrix);
            entries.add(new Entry(detection.paragraphIndex(), frame, matrix, routes));

            if (frame.axes().size() > 1) multiAxis++;
            for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
                axisMembership.computeIfAbsent(axis, ignored -> new ArrayList<>())
                        .add(detection.paragraphIndex());
            }
            String head = normalizeHead(frame.head());
            if (!head.isEmpty()) {
                headMembership.computeIfAbsent(head, ignored -> new ArrayList<>())
                        .add(detection.paragraphIndex());
            }
            for (SemanticToolRouter.Route route : routes) routeModes.add(route.compactLabel());
        }

        return new Index(entries, axisMembership, headMembership, multiAxis, routeModes.size());
    }

    /** Rebuild the same multi-axis representation for a persisted One-Pass snapshot. */
    public static Index build(OnePassSemanticOrganizer.Snapshot snapshot) {
        if (snapshot == null || snapshot.paragraphs().isEmpty()) return emptyIndex();
        List<UniversalParagraphDetector.Detection> detections = new ArrayList<>();
        for (OnePassSemanticOrganizer.Paragraph paragraph : snapshot.paragraphs()) {
            detections.add(new UniversalParagraphDetector.Detection(
                    paragraph.index(),
                    paragraph.text(),
                    paragraph.subject(),
                    paragraph.function(),
                    paragraph.secondaryFunction(),
                    paragraph.subjectConfidence(),
                    paragraph.functionConfidence(),
                    Collections.emptyList(),
                    java.util.EnumSet.noneOf(UniversalDetectionLexicon.Operator.class),
                    Collections.emptyList()
            ));
        }
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(snapshot.query(), null);
        return build(detections, graph, profile);
    }

    private static boolean isCompatible(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph
    ) {
        if (detections == null || detections.isEmpty() || graph == null || graph.isEmpty()) return false;
        int matched = 0;
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            List<SemanticGraph.Proposition> values = graph.propositionsForParagraph(detection.paragraphIndex());
            if (!values.isEmpty()) matched++;
        }
        return matched > 0;
    }

    private static String normalizeHead(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    private static Index emptyIndex() {
        return new Index(
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0,
                0
        );
    }
}
