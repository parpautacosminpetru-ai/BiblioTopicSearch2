package ro.bibliotopicsearch.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, evidence-bound map of one explicitly captured OCR frame.
 *
 * This is the "historian mode" core: every OCR paragraph is accounted for even
 * when it contains no configured topic index. Nothing is silently discarded.
 */
public final class HistoricalCartographyEngine {
    private HistoricalCartographyEngine() {}

    public enum UnitKind {
        PARAGRAPH,
        CLAUSE
    }

    public enum Status {
        MAPPED,
        NO_INDEX,
        AMBIGUOUS,
        UNPARSED
    }

    public static final class Unit {
        private final UnitKind kind;
        private final int paragraphIndex;
        private final int sentenceIndex;
        private final int clauseIndex;
        private final String fingerprint;
        private final String function;
        private final String relation;
        private final Status status;
        private final double confidence;
        private final List<DeterministicIndexIntersectionEngine.Anchor> anchors;
        private final List<DeterministicIndexIntersectionEngine.Intersection> intersections;

        Unit(
                UnitKind kind,
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String fingerprint,
                String function,
                String relation,
                Status status,
                double confidence,
                List<DeterministicIndexIntersectionEngine.Anchor> anchors,
                List<DeterministicIndexIntersectionEngine.Intersection> intersections
        ) {
            this.kind = kind == null ? UnitKind.PARAGRAPH : kind;
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.fingerprint = safe(fingerprint);
            this.function = safe(function);
            this.relation = safe(relation);
            this.status = status == null ? Status.UNPARSED : status;
            this.confidence = clamp01(confidence);
            this.anchors = Collections.unmodifiableList(new ArrayList<>(
                    anchors == null ? Collections.emptyList() : anchors
            ));
            this.intersections = Collections.unmodifiableList(new ArrayList<>(
                    intersections == null ? Collections.emptyList() : intersections
            ));
        }

        public UnitKind kind() { return kind; }
        public int paragraphIndex() { return paragraphIndex; }
        public int sentenceIndex() { return sentenceIndex; }
        public int clauseIndex() { return clauseIndex; }
        public String fingerprint() { return fingerprint; }
        public String function() { return function; }
        public String relation() { return relation; }
        public Status status() { return status; }
        public double confidence() { return confidence; }
        public List<DeterministicIndexIntersectionEngine.Anchor> anchors() { return anchors; }
        public List<DeterministicIndexIntersectionEngine.Intersection> intersections() { return intersections; }
    }

    public static final class CaptureMap {
        private final List<Unit> units;
        private final int paragraphCount;
        private final int clauseCount;
        private final int mappedCount;
        private final int noIndexCount;
        private final int ambiguousCount;
        private final int unparsedCount;
        private final int intersectionCount;
        private final int distinctTopics;

        CaptureMap(
                List<Unit> units,
                int paragraphCount,
                int clauseCount,
                int mappedCount,
                int noIndexCount,
                int ambiguousCount,
                int unparsedCount,
                int intersectionCount,
                int distinctTopics
        ) {
            this.units = Collections.unmodifiableList(new ArrayList<>(units));
            this.paragraphCount = paragraphCount;
            this.clauseCount = clauseCount;
            this.mappedCount = mappedCount;
            this.noIndexCount = noIndexCount;
            this.ambiguousCount = ambiguousCount;
            this.unparsedCount = unparsedCount;
            this.intersectionCount = intersectionCount;
            this.distinctTopics = distinctTopics;
        }

        public List<Unit> units() { return units; }
        public int paragraphCount() { return paragraphCount; }
        public int clauseCount() { return clauseCount; }
        public int mappedCount() { return mappedCount; }
        public int noIndexCount() { return noIndexCount; }
        public int ambiguousCount() { return ambiguousCount; }
        public int unparsedCount() { return unparsedCount; }
        public int intersectionCount() { return intersectionCount; }
        public int distinctTopics() { return distinctTopics; }
        public int totalUnits() { return units.size(); }
    }

    public static CaptureMap analyze(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            TopicMap thematicMap
    ) {
        if (detections == null || detections.isEmpty()) {
            return new CaptureMap(Collections.emptyList(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Map<Integer, List<SemanticGraph.Proposition>> propositions = new HashMap<>();
        if (graph != null) {
            for (SemanticGraph.Proposition proposition : graph.propositions()) {
                if (proposition == null) continue;
                propositions.computeIfAbsent(proposition.paragraphIndex(), ignored -> new ArrayList<>())
                        .add(proposition);
            }
        }

        DeterministicMultiIndexMatcher.Plan thematicPlan =
                DeterministicMultiIndexMatcher.forMap(thematicMap);

        List<Unit> units = new ArrayList<>();
        Set<String> topicPaths = new HashSet<>();
        int paragraphs = 0;
        int clauses = 0;
        int mapped = 0;
        int noIndex = 0;
        int ambiguous = 0;
        int unparsed = 0;
        int intersections = 0;

        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            String paragraph = safe(detection.paragraph());
            if (paragraph.isEmpty()) continue;
            paragraphs++;

            List<SemanticGraph.Proposition> paragraphProps = propositions.getOrDefault(
                    detection.paragraphIndex(), Collections.emptyList()
            );
            List<DeterministicIndexIntersectionEngine.Anchor> paragraphAnchors =
                    DeterministicMultiIndexMatcher.matchText(thematicPlan, paragraph);
            Status paragraphStatus = statusFor(paragraphAnchors, !paragraphProps.isEmpty());
            for (DeterministicIndexIntersectionEngine.Anchor anchor : paragraphAnchors) {
                if (anchor != null && anchor.node() != null) topicPaths.add(anchor.node().path);
            }
            units.add(new Unit(
                    UnitKind.PARAGRAPH,
                    detection.paragraphIndex(),
                    -1,
                    -1,
                    fingerprint(paragraph),
                    detection.function() == null ? "" : detection.function().name(),
                    "",
                    paragraphStatus,
                    detection.subjectConfidence() * 0.55 + detection.functionConfidence() * 0.45,
                    paragraphAnchors,
                    Collections.emptyList()
            ));
            if (paragraphStatus == Status.MAPPED) mapped++;
            else if (paragraphStatus == Status.NO_INDEX) noIndex++;
            else if (paragraphStatus == Status.AMBIGUOUS) ambiguous++;
            else unparsed++;

            for (SemanticGraph.Proposition proposition : paragraphProps) {
                String raw = safe(proposition.raw());
                if (raw.isEmpty()) continue;
                clauses++;
                List<DeterministicIndexIntersectionEngine.Anchor> anchors =
                        DeterministicMultiIndexMatcher.matchProposition(thematicPlan, proposition);
                List<DeterministicIndexIntersectionEngine.Intersection> xs =
                        DeterministicIndexIntersectionEngine.detectProposition(proposition, anchors);
                Status status = statusFor(anchors, true);
                for (DeterministicIndexIntersectionEngine.Anchor anchor : anchors) {
                    if (anchor != null && anchor.node() != null) topicPaths.add(anchor.node().path);
                }
                intersections += xs.size();

                units.add(new Unit(
                        UnitKind.CLAUSE,
                        proposition.paragraphIndex(),
                        proposition.sentenceIndex(),
                        proposition.clauseIndex(),
                        fingerprint(raw),
                        detection.function() == null ? "" : detection.function().name(),
                        proposition.relation() == null ? "" : proposition.relation().name(),
                        status,
                        proposition.confidence(),
                        anchors,
                        xs
                ));
                if (status == Status.MAPPED) mapped++;
                else if (status == Status.NO_INDEX) noIndex++;
                else if (status == Status.AMBIGUOUS) ambiguous++;
                else unparsed++;
            }
        }

        return new CaptureMap(
                units,
                paragraphs,
                clauses,
                mapped,
                noIndex,
                ambiguous,
                unparsed,
                intersections,
                topicPaths.size()
        );
    }

    private static Status statusFor(
            List<DeterministicIndexIntersectionEngine.Anchor> anchors,
            boolean parsed
    ) {
        if (anchors == null || anchors.isEmpty()) return parsed ? Status.NO_INDEX : Status.UNPARSED;
        if (DeterministicIndexIntersectionEngine.hasSharedSpanAmbiguity(anchors)) return Status.AMBIGUOUS;
        return Status.MAPPED;
    }

    public static String fingerprint(String value) {
        String normalized = RomanianLanguagePack.fold(value);
        if (normalized.isEmpty()) return "";
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(32);
            for (int i = 0; i < Math.min(16, bytes.length); i++) {
                out.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            }
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
