package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session-level organizer for one-pass book/research scanning.
 *
 * Live operation is deliberately incremental: repeated OCR frames are merged and
 * only newly accepted paragraphs receive a provisional cartographic position.
 * Finalization performs one global semantic/cartographic pass over the unique
 * paragraphs and emits an immutable, persistence-ready snapshot.
 */
public final class OnePassSemanticOrganizer {
    private OnePassSemanticOrganizer() {}

    private static final Object LOCK = new Object();
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final int FUZZY_LOOKBACK = 36;
    private static final int LIVE_MAP_LOOKBACK = 19;

    private static boolean active;
    private static long startedAt;
    private static long framesObserved;
    private static int duplicatesMerged;
    private static ResearchSemanticEngine.Profile lastProfile;
    private static final List<LiveRecord> records = new ArrayList<>();
    private static final Map<String, Integer> exactFingerprints = new HashMap<>();
    private static Snapshot latestFinished;

    public static final class LiveState {
        private final boolean active;
        private final int uniqueParagraphs;
        private final int duplicatesMerged;
        private final int maxDepth;
        private final long framesObserved;
        private final String globalSubject;

        LiveState(
                boolean active,
                int uniqueParagraphs,
                int duplicatesMerged,
                int maxDepth,
                long framesObserved,
                String globalSubject
        ) {
            this.active = active;
            this.uniqueParagraphs = Math.max(0, uniqueParagraphs);
            this.duplicatesMerged = Math.max(0, duplicatesMerged);
            this.maxDepth = Math.max(0, maxDepth);
            this.framesObserved = Math.max(0, framesObserved);
            this.globalSubject = safe(globalSubject);
        }

        public boolean active() { return active; }
        public int uniqueParagraphs() { return uniqueParagraphs; }
        public int duplicatesMerged() { return duplicatesMerged; }
        public int maxDepth() { return maxDepth; }
        public long framesObserved() { return framesObserved; }
        public String globalSubject() { return globalSubject; }
    }

    public static final class Claim {
        private final String raw;
        private final String subject;
        private final String predicate;
        private final String object;
        private final SemanticGraph.Relation relation;
        private final Set<SemanticGraph.Operator> operators;
        private final Map<SemanticGraph.Slot, String> slots;
        private final double confidence;

        Claim(
                String raw,
                String subject,
                String predicate,
                String object,
                SemanticGraph.Relation relation,
                Set<SemanticGraph.Operator> operators,
                Map<SemanticGraph.Slot, String> slots,
                double confidence
        ) {
            this.raw = safe(raw);
            this.subject = safe(subject);
            this.predicate = safe(predicate);
            this.object = safe(object);
            this.relation = relation == null ? SemanticGraph.Relation.GENERIC : relation;
            this.operators = Collections.unmodifiableSet(new LinkedHashSet<>(
                    operators == null ? Collections.emptySet() : operators
            ));
            this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(
                    slots == null ? Collections.emptyMap() : slots
            ));
            this.confidence = clamp01(confidence);
        }

        public String raw() { return raw; }
        public String subject() { return subject; }
        public String predicate() { return predicate; }
        public String object() { return object; }
        public SemanticGraph.Relation relation() { return relation; }
        public Set<SemanticGraph.Operator> operators() { return operators; }
        public Map<SemanticGraph.Slot, String> slots() { return slots; }
        public double confidence() { return confidence; }
    }

    public static final class Paragraph {
        private final int index;
        private final int depth;
        private final int parentIndex;
        private final ParagraphCartography.Link link;
        private final String text;
        private final String subject;
        private final UniversalDetectionLexicon.Function function;
        private final UniversalDetectionLexicon.Function secondaryFunction;
        private final double subjectConfidence;
        private final double functionConfidence;
        private final int sightings;
        private final String answerSegment;
        private final double answerScore;
        private final ResearchSemanticEngine.Intent answerIntent;
        private final SemanticGraph.Relation answerRelation;
        private final List<Claim> claims;

        Paragraph(
                int index,
                int depth,
                int parentIndex,
                ParagraphCartography.Link link,
                String text,
                String subject,
                UniversalDetectionLexicon.Function function,
                UniversalDetectionLexicon.Function secondaryFunction,
                double subjectConfidence,
                double functionConfidence,
                int sightings,
                String answerSegment,
                double answerScore,
                ResearchSemanticEngine.Intent answerIntent,
                SemanticGraph.Relation answerRelation,
                List<Claim> claims
        ) {
            this.index = Math.max(0, index);
            this.depth = Math.max(0, depth);
            this.parentIndex = parentIndex;
            this.link = link == null ? ParagraphCartography.Link.ROOT : link;
            this.text = safe(text);
            this.subject = safe(subject);
            this.function = function == null
                    ? UniversalDetectionLexicon.Function.UNKNOWN : function;
            this.secondaryFunction = secondaryFunction == null
                    ? UniversalDetectionLexicon.Function.UNKNOWN : secondaryFunction;
            this.subjectConfidence = clamp01(subjectConfidence);
            this.functionConfidence = clamp01(functionConfidence);
            this.sightings = Math.max(1, sightings);
            this.answerSegment = safe(answerSegment);
            this.answerScore = clamp01(answerScore);
            this.answerIntent = answerIntent == null ? ResearchSemanticEngine.Intent.TOPIC : answerIntent;
            this.answerRelation = answerRelation == null
                    ? SemanticGraph.Relation.GENERIC : answerRelation;
            this.claims = Collections.unmodifiableList(new ArrayList<>(
                    claims == null ? Collections.emptyList() : claims
            ));
        }

        public int index() { return index; }
        public int depth() { return depth; }
        public int parentIndex() { return parentIndex; }
        public ParagraphCartography.Link link() { return link; }
        public String text() { return text; }
        public String subject() { return subject; }
        public UniversalDetectionLexicon.Function function() { return function; }
        public UniversalDetectionLexicon.Function secondaryFunction() { return secondaryFunction; }
        public double subjectConfidence() { return subjectConfidence; }
        public double functionConfidence() { return functionConfidence; }
        public int sightings() { return sightings; }
        public String answerSegment() { return answerSegment; }
        public double answerScore() { return answerScore; }
        public ResearchSemanticEngine.Intent answerIntent() { return answerIntent; }
        public SemanticGraph.Relation answerRelation() { return answerRelation; }
        public List<Claim> claims() { return claims; }
    }

    public static final class Snapshot {
        private final long startedAt;
        private final long finishedAt;
        private final long framesObserved;
        private final int duplicatesMerged;
        private final String query;
        private final String globalSubject;
        private final int maxDepth;
        private final int claimCount;
        private final String bestAnswerSegment;
        private final double bestAnswerScore;
        private final List<Paragraph> paragraphs;

        Snapshot(
                long startedAt,
                long finishedAt,
                long framesObserved,
                int duplicatesMerged,
                String query,
                String globalSubject,
                int maxDepth,
                int claimCount,
                String bestAnswerSegment,
                double bestAnswerScore,
                List<Paragraph> paragraphs
        ) {
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.framesObserved = Math.max(0, framesObserved);
            this.duplicatesMerged = Math.max(0, duplicatesMerged);
            this.query = safe(query);
            this.globalSubject = safe(globalSubject);
            this.maxDepth = Math.max(0, maxDepth);
            this.claimCount = Math.max(0, claimCount);
            this.bestAnswerSegment = safe(bestAnswerSegment);
            this.bestAnswerScore = clamp01(bestAnswerScore);
            this.paragraphs = Collections.unmodifiableList(new ArrayList<>(
                    paragraphs == null ? Collections.emptyList() : paragraphs
            ));
        }

        public long startedAt() { return startedAt; }
        public long finishedAt() { return finishedAt; }
        public long framesObserved() { return framesObserved; }
        public int duplicatesMerged() { return duplicatesMerged; }
        public String query() { return query; }
        public String globalSubject() { return globalSubject; }
        public int maxDepth() { return maxDepth; }
        public int claimCount() { return claimCount; }
        public String bestAnswerSegment() { return bestAnswerSegment; }
        public double bestAnswerScore() { return bestAnswerScore; }
        public List<Paragraph> paragraphs() { return paragraphs; }
        public int uniqueParagraphs() { return paragraphs.size(); }
    }

    private static final class LiveRecord {
        UniversalParagraphDetector.Detection detection;
        int sightings = 1;
        int depth = 0;
        int parentIndex = -1;
        ParagraphCartography.Link link = ParagraphCartography.Link.ROOT;

        LiveRecord(UniversalParagraphDetector.Detection detection) {
            this.detection = detection;
        }
    }

    public static void beginSession() {
        synchronized (LOCK) {
            active = true;
            startedAt = System.currentTimeMillis();
            framesObserved = 0;
            duplicatesMerged = 0;
            lastProfile = null;
            records.clear();
            exactFingerprints.clear();
        }
    }

    public static boolean isActive() {
        synchronized (LOCK) {
            return active;
        }
    }

    /**
     * Ingest one semantic OCR result. This method is called on the semantic sidecar
     * worker; it never performs the expensive full-session final cartography.
     */
    public static void ingest(
            List<UniversalParagraphDetector.Detection> detections,
            ResearchSemanticEngine.Profile profile
    ) {
        if (detections == null || detections.isEmpty()) return;
        synchronized (LOCK) {
            if (!active) return;
            framesObserved++;
            if (profile != null) lastProfile = profile;

            for (UniversalParagraphDetector.Detection incoming : detections) {
                if (incoming == null || safe(incoming.paragraph()).isEmpty()) continue;
                int duplicate = findDuplicate(incoming.paragraph());
                if (duplicate >= 0) {
                    mergeDuplicate(duplicate, incoming);
                    duplicatesMerged++;
                    continue;
                }

                int stableIndex = records.size();
                UniversalParagraphDetector.Detection stable = reindex(incoming, stableIndex);
                LiveRecord record = new LiveRecord(stable);
                records.add(record);
                exactFingerprints.put(fingerprint(stable.paragraph()), stableIndex);
                assignLiveCartography(record);
            }
        }
    }

    public static LiveState liveState() {
        synchronized (LOCK) {
            int maxDepth = 0;
            for (LiveRecord record : records) maxDepth = Math.max(maxDepth, record.depth);
            String global = records.isEmpty() ? "" : safe(records.get(0).detection.subject());
            return new LiveState(
                    active,
                    records.size(),
                    duplicatesMerged,
                    maxDepth,
                    framesObserved,
                    global
            );
        }
    }

    public static Snapshot latestFinished() {
        synchronized (LOCK) {
            return latestFinished;
        }
    }

    /**
     * Freeze the accumulated unique paragraphs and build the definitive organization.
     * Call from a non-UI thread for very large sessions.
     */
    public static Snapshot finishSession() {
        List<LiveRecord> local;
        ResearchSemanticEngine.Profile profile;
        long localStarted;
        long localFrames;
        int localDuplicates;

        synchronized (LOCK) {
            if (!active && latestFinished != null) return latestFinished;
            active = false;
            local = new ArrayList<>(records.size());
            for (LiveRecord record : records) {
                LiveRecord copy = new LiveRecord(record.detection);
                copy.sightings = record.sightings;
                copy.depth = record.depth;
                copy.parentIndex = record.parentIndex;
                copy.link = record.link;
                local.add(copy);
            }
            profile = lastProfile;
            localStarted = startedAt;
            localFrames = framesObserved;
            localDuplicates = duplicatesMerged;
        }

        Snapshot snapshot = buildFinalSnapshot(
                local, profile, localStarted, localFrames, localDuplicates
        );
        synchronized (LOCK) {
            latestFinished = snapshot;
        }
        return snapshot;
    }

    private static Snapshot buildFinalSnapshot(
            List<LiveRecord> local,
            ResearchSemanticEngine.Profile profile,
            long localStarted,
            long localFrames,
            int localDuplicates
    ) {
        long finished = System.currentTimeMillis();
        if (local.isEmpty()) {
            return new Snapshot(
                    localStarted, finished, localFrames, localDuplicates,
                    profile == null ? "" : profile.displayQuery(),
                    "", 0, 0, "", 0.0, Collections.emptyList()
            );
        }

        List<UniversalParagraphDetector.Detection> detections = new ArrayList<>(local.size());
        for (int i = 0; i < local.size(); i++) {
            detections.add(reindex(local.get(i).detection, i));
        }

        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        ParagraphCartography.Map cartography = ParagraphCartography.build(detections, graph);

        Map<Integer, ResearchSemanticEngine.Answer> bestByParagraph = new HashMap<>();
        ResearchSemanticEngine.Answer globalBest = null;
        if (profile != null && profile.enabled()) {
            int answerBudget = Math.max(8, detections.size() * 4);
            List<ResearchSemanticEngine.Answer> answers = ResearchSemanticEngine.findAll(
                    profile, detections, graph, answerBudget
            );
            for (ResearchSemanticEngine.Answer answer : answers) {
                ResearchSemanticEngine.Answer existing = bestByParagraph.get(answer.paragraphIndex());
                if (existing == null || answer.score() > existing.score()) {
                    bestByParagraph.put(answer.paragraphIndex(), answer);
                }
                if (globalBest == null || answer.score() > globalBest.score()) globalBest = answer;
            }
        }

        List<Paragraph> organized = new ArrayList<>(detections.size());
        int claimCount = 0;
        for (int i = 0; i < detections.size(); i++) {
            UniversalParagraphDetector.Detection detection = detections.get(i);
            ParagraphCartography.Node node = cartography.nodeForParagraph(i);
            ResearchSemanticEngine.Answer answer = bestByParagraph.get(i);

            List<Claim> claims = new ArrayList<>();
            for (SemanticGraph.Proposition proposition : graph.propositionsForParagraph(i)) {
                claims.add(new Claim(
                        proposition.raw(),
                        proposition.subject(),
                        proposition.predicate(),
                        proposition.object(),
                        proposition.relation(),
                        proposition.operators(),
                        proposition.slots(),
                        proposition.confidence()
                ));
            }
            claimCount += claims.size();

            organized.add(new Paragraph(
                    i,
                    node == null ? local.get(i).depth : node.depth(),
                    node == null ? local.get(i).parentIndex : node.parentParagraphIndex(),
                    node == null ? local.get(i).link : node.link(),
                    detection.paragraph(),
                    detection.subject(),
                    detection.function(),
                    detection.secondaryFunction(),
                    detection.subjectConfidence(),
                    detection.functionConfidence(),
                    local.get(i).sightings,
                    answer == null ? "" : answer.segment(),
                    answer == null ? 0.0 : answer.score(),
                    answer == null ? ResearchSemanticEngine.Intent.TOPIC : answer.intent(),
                    answer == null ? SemanticGraph.Relation.GENERIC : answer.relation(),
                    claims
            ));
        }

        return new Snapshot(
                localStarted,
                finished,
                localFrames,
                localDuplicates,
                profile == null ? "" : profile.displayQuery(),
                cartography.globalSubject(),
                cartography.maxDepth(),
                claimCount,
                globalBest == null ? "" : globalBest.segment(),
                globalBest == null ? 0.0 : globalBest.score(),
                organized
        );
    }

    private static int findDuplicate(String paragraph) {
        String key = fingerprint(paragraph);
        Integer exact = exactFingerprints.get(key);
        if (exact != null) return exact;

        int start = Math.max(0, records.size() - FUZZY_LOOKBACK);
        for (int i = records.size() - 1; i >= start; i--) {
            if (nearDuplicate(paragraph, records.get(i).detection.paragraph())) return i;
        }
        return -1;
    }

    private static void mergeDuplicate(int index, UniversalParagraphDetector.Detection incoming) {
        LiveRecord record = records.get(index);
        record.sightings++;
        exactFingerprints.put(fingerprint(incoming.paragraph()), index);

        double oldQuality = detectionQuality(record.detection);
        double newQuality = detectionQuality(incoming);
        if (newQuality > oldQuality + 0.025) {
            record.detection = reindex(incoming, index);
        }
    }

    private static void assignLiveCartography(LiveRecord target) {
        int end = records.size();
        int start = Math.max(0, end - LIVE_MAP_LOOKBACK);
        List<UniversalParagraphDetector.Detection> window = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) window.add(records.get(i).detection);

        ParagraphCartography.Map map = ParagraphCartography.build(window);
        ParagraphCartography.Node node = map.nodeForParagraph(target.detection.paragraphIndex());
        if (node == null) return;

        target.parentIndex = node.parentParagraphIndex();
        target.link = node.link();
        if (target.parentIndex < 0) {
            target.depth = 0;
            return;
        }

        LiveRecord parent = target.parentIndex < records.size()
                ? records.get(target.parentIndex) : null;
        int parentDepth = parent == null ? Math.max(0, node.depth()) : parent.depth;
        switch (target.link) {
            case NARROWS:
            case SUPPORTS:
            case EXPLAINS:
            case EXEMPLIFIES:
                target.depth = parentDepth + 1;
                break;
            case BROADENS:
                target.depth = Math.max(0, parentDepth - 1);
                break;
            case SHIFTS:
            case ROOT:
                target.depth = 0;
                break;
            default:
                target.depth = parentDepth;
                break;
        }
    }

    private static UniversalParagraphDetector.Detection reindex(
            UniversalParagraphDetector.Detection source,
            int index
    ) {
        return new UniversalParagraphDetector.Detection(
                index,
                source.paragraph(),
                source.subject(),
                source.function(),
                source.secondaryFunction(),
                source.subjectConfidence(),
                source.functionConfidence(),
                source.querySlots(),
                source.operators(),
                source.matchedMarkers()
        );
    }

    private static boolean nearDuplicate(String a, String b) {
        String fa = fingerprint(a);
        String fb = fingerprint(b);
        if (fa.equals(fb)) return true;
        if (fa.isEmpty() || fb.isEmpty()) return false;

        double lengthRatio = Math.min(fa.length(), fb.length())
                / (double) Math.max(fa.length(), fb.length());
        if (lengthRatio < 0.74) return false;

        Set<String> ta = tokenSet(fa);
        Set<String> tb = tokenSet(fb);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        int intersection = 0;
        for (String token : ta) if (tb.contains(token)) intersection++;
        int union = ta.size() + tb.size() - intersection;
        double jaccard = union == 0 ? 0.0 : intersection / (double) union;
        return jaccard >= 0.86;
    }

    private static Set<String> tokenSet(String value) {
        Set<String> out = new HashSet<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) out.add(token);
        }
        return out;
    }

    private static String fingerprint(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double detectionQuality(UniversalParagraphDetector.Detection detection) {
        if (detection == null) return 0.0;
        double lexical = Math.min(1.0, tokenSet(fingerprint(detection.paragraph())).size() / 24.0);
        return clamp01(
                detection.subjectConfidence() * 0.42
                        + detection.functionConfidence() * 0.38
                        + lexical * 0.20
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
