package ro.bibliotopicsearch.app;

import android.content.Context;

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

/**
 * Evidence-only global index across all One-Pass sessions.
 * No conclusions are synthesized here: the engine only organizes literal evidence,
 * provenance, coverage, convergence and tension candidates for the user's synthesis.
 */
public final class ResearchWorkspaceEngine {
    private ResearchWorkspaceEngine() {}

    public static final class EvidenceItem {
        private final String id;
        private final long sessionId;
        private final int paragraphIndex;
        private final int claimIndex;
        private final String sourceTitle;
        private final String sourceAuthor;
        private final String locator;
        private final String head;
        private final SemanticGraph.Relation relation;
        private final String raw;
        private final String subject;
        private final String predicate;
        private final String object;
        private final Set<SemanticGraph.Operator> operators;
        private final double confidence;
        private final boolean pinned;
        private final String userNote;

        EvidenceItem(String id, long sessionId, int paragraphIndex, int claimIndex,
                     String sourceTitle, String sourceAuthor, String locator, String head,
                     SemanticGraph.Relation relation, String raw, String subject,
                     String predicate, String object, Set<SemanticGraph.Operator> operators,
                     double confidence, boolean pinned, String userNote) {
            this.id = safe(id);
            this.sessionId = sessionId;
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.claimIndex = Math.max(-1, claimIndex);
            this.sourceTitle = safe(sourceTitle);
            this.sourceAuthor = safe(sourceAuthor);
            this.locator = safe(locator);
            this.head = safe(head);
            this.relation = relation == null ? SemanticGraph.Relation.GENERIC : relation;
            this.raw = safe(raw);
            this.subject = safe(subject);
            this.predicate = safe(predicate);
            this.object = safe(object);
            this.operators = Collections.unmodifiableSet(new LinkedHashSet<>(
                    operators == null ? Collections.emptySet() : operators));
            this.confidence = clamp01(confidence);
            this.pinned = pinned;
            this.userNote = userNote == null ? "" : userNote;
        }

        public String id() { return id; }
        public long sessionId() { return sessionId; }
        public int paragraphIndex() { return paragraphIndex; }
        public int claimIndex() { return claimIndex; }
        public String sourceTitle() { return sourceTitle; }
        public String sourceAuthor() { return sourceAuthor; }
        public String locator() { return locator; }
        public String head() { return head; }
        public SemanticGraph.Relation relation() { return relation; }
        public String raw() { return raw; }
        public String subject() { return subject; }
        public String predicate() { return predicate; }
        public String object() { return object; }
        public Set<SemanticGraph.Operator> operators() { return operators; }
        public double confidence() { return confidence; }
        public boolean pinned() { return pinned; }
        public String userNote() { return userNote; }
        public boolean negated() { return operators.contains(SemanticGraph.Operator.NEGATION); }
    }

    public static final class DossierGroup {
        private final String key;
        private final String head;
        private final Set<UniversalSubjectFrame.Axis> axes;
        private final Set<SemanticQueryMatrix.QuerySlot> requiredSlots;
        private final Set<SemanticQueryMatrix.QuerySlot> answeredSlots;
        private final Set<SemanticQueryMatrix.QuerySlot> gaps;
        private final List<EvidenceItem> evidence;
        private final int sourceCount;
        private final int convergenceCount;
        private final int tensionCandidateCount;
        private final int pinnedCount;

        DossierGroup(String key, String head, Set<UniversalSubjectFrame.Axis> axes,
                     Set<SemanticQueryMatrix.QuerySlot> requiredSlots,
                     Set<SemanticQueryMatrix.QuerySlot> answeredSlots,
                     List<EvidenceItem> evidence, int sourceCount, int convergenceCount,
                     int tensionCandidateCount, int pinnedCount) {
            this.key = safe(key);
            this.head = safe(head);
            this.axes = immutableSet(axes);
            this.requiredSlots = immutableSet(requiredSlots);
            this.answeredSlots = immutableSet(answeredSlots);
            LinkedHashSet<SemanticQueryMatrix.QuerySlot> missing = new LinkedHashSet<>(requiredSlots);
            missing.removeAll(answeredSlots);
            this.gaps = Collections.unmodifiableSet(missing);
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
            this.sourceCount = Math.max(0, sourceCount);
            this.convergenceCount = Math.max(0, convergenceCount);
            this.tensionCandidateCount = Math.max(0, tensionCandidateCount);
            this.pinnedCount = Math.max(0, pinnedCount);
        }

        public String key() { return key; }
        public String head() { return head; }
        public Set<UniversalSubjectFrame.Axis> axes() { return axes; }
        public Set<SemanticQueryMatrix.QuerySlot> requiredSlots() { return requiredSlots; }
        public Set<SemanticQueryMatrix.QuerySlot> answeredSlots() { return answeredSlots; }
        public Set<SemanticQueryMatrix.QuerySlot> gaps() { return gaps; }
        public List<EvidenceItem> evidence() { return evidence; }
        public int sourceCount() { return sourceCount; }
        public int convergenceCount() { return convergenceCount; }
        public int tensionCandidateCount() { return tensionCandidateCount; }
        public int pinnedCount() { return pinnedCount; }
    }

    public static final class Workspace {
        private final ResearchWorkspaceStore.State state;
        private final List<OnePassSemanticOrganizer.Snapshot> sessions;
        private final List<DossierGroup> groups;
        private final int evidenceCount;
        private final int sourceCount;
        private final int totalGaps;
        private final int tensionCandidates;
        private final int pinnedCount;

        Workspace(ResearchWorkspaceStore.State state,
                  List<OnePassSemanticOrganizer.Snapshot> sessions,
                  List<DossierGroup> groups, int evidenceCount, int sourceCount,
                  int totalGaps, int tensionCandidates, int pinnedCount) {
            this.state = state;
            this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.evidenceCount = evidenceCount;
            this.sourceCount = sourceCount;
            this.totalGaps = totalGaps;
            this.tensionCandidates = tensionCandidates;
            this.pinnedCount = pinnedCount;
        }

        public ResearchWorkspaceStore.State state() { return state; }
        public List<OnePassSemanticOrganizer.Snapshot> sessions() { return sessions; }
        public List<DossierGroup> groups() { return groups; }
        public int evidenceCount() { return evidenceCount; }
        public int sourceCount() { return sourceCount; }
        public int totalGaps() { return totalGaps; }
        public int tensionCandidates() { return tensionCandidates; }
        public int pinnedCount() { return pinnedCount; }
        public boolean isEmpty() { return sessions.isEmpty(); }
    }

    private static final class MutableGroup {
        String key;
        String head;
        final Set<UniversalSubjectFrame.Axis> axes = new LinkedHashSet<>();
        final Set<SemanticQueryMatrix.QuerySlot> required = new LinkedHashSet<>();
        final Set<SemanticQueryMatrix.QuerySlot> answered = new LinkedHashSet<>();
        final List<EvidenceItem> evidence = new ArrayList<>();
    }

    public static Workspace build(Context context) {
        return build(OrganizedSessionStore.loadAll(context), ResearchWorkspaceStore.load(context));
    }

    static Workspace build(List<OnePassSemanticOrganizer.Snapshot> sessions,
                           ResearchWorkspaceStore.State state) {
        List<OnePassSemanticOrganizer.Snapshot> safeSessions = sessions == null
                ? Collections.emptyList() : sessions;
        ResearchWorkspaceStore.State safeState = state == null
                ? new ResearchWorkspaceStore.State("Cercetare", "", Collections.emptyMap(),
                Collections.emptySet(), Collections.emptyMap()) : state;

        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        Set<String> sourceIds = new LinkedHashSet<>();
        int evidenceCount = 0;
        int pinnedCount = 0;

        for (OnePassSemanticOrganizer.Snapshot snapshot : safeSessions) {
            if (snapshot == null) continue;
            long sessionId = snapshot.startedAt();
            sourceIds.add(String.valueOf(sessionId));
            ResearchWorkspaceStore.SourceMeta source = safeState.source(sessionId);
            String sourceTitle = source.title().isEmpty()
                    ? defaultSourceTitle(snapshot, sessionId) : source.title();
            MultiAxisSemanticRuntime.Index index = MultiAxisSemanticRuntime.build(snapshot);

            for (OnePassSemanticOrganizer.Paragraph paragraph : snapshot.paragraphs()) {
                MultiAxisSemanticRuntime.Entry entry = index.entryForParagraph(paragraph.index());
                String head = entry == null ? paragraph.subject() : entry.frame().head();
                if (safe(head).isEmpty()) head = paragraph.subject();
                if (safe(head).isEmpty()) head = snapshot.globalSubject();
                if (safe(head).isEmpty()) head = "subiect nedeterminat";
                String key = fold(head);
                MutableGroup group = groups.get(key);
                if (group == null) {
                    group = new MutableGroup();
                    group.key = key;
                    group.head = head;
                    groups.put(key, group);
                }

                if (entry != null) {
                    group.axes.addAll(entry.frame().axes().keySet());
                    group.required.addAll(entry.matrix().slots());
                    markAxes(group.answered, entry.frame());
                }

                int claimIndex = 0;
                for (OnePassSemanticOrganizer.Claim claim : paragraph.claims()) {
                    String id = evidenceId(sessionId, paragraph.index(), claimIndex);
                    boolean pinned = safeState.isPinned(id);
                    if (pinned) pinnedCount++;
                    group.evidence.add(new EvidenceItem(
                            id, sessionId, paragraph.index(), claimIndex,
                            sourceTitle, source.author(), source.locator(), head,
                            claim.relation(), claim.raw(), claim.subject(), claim.predicate(),
                            claim.object(), claim.operators(), claim.confidence(), pinned,
                            safeState.note(id)));
                    evidenceCount++;
                    markClaim(group.answered, claim);
                    claimIndex++;
                }

                if (paragraph.claims().isEmpty()) {
                    String id = evidenceId(sessionId, paragraph.index(), -1);
                    boolean pinned = safeState.isPinned(id);
                    if (pinned) pinnedCount++;
                    String raw = paragraph.answerSegment().isEmpty()
                            ? paragraph.text() : paragraph.answerSegment();
                    group.evidence.add(new EvidenceItem(
                            id, sessionId, paragraph.index(), -1,
                            sourceTitle, source.author(), source.locator(), head,
                            paragraph.answerRelation(), raw, paragraph.subject(), "", "",
                            Collections.emptySet(),
                            Math.max(paragraph.subjectConfidence(), paragraph.answerScore()),
                            pinned, safeState.note(id)));
                    evidenceCount++;
                    if (!paragraph.answerSegment().isEmpty()) {
                        group.answered.add(slotForRelation(paragraph.answerRelation()));
                    }
                }
            }
        }

        List<DossierGroup> dossier = new ArrayList<>();
        int totalGaps = 0;
        int totalTensions = 0;
        for (MutableGroup group : groups.values()) {
            Set<Long> sources = new LinkedHashSet<>();
            int localPins = 0;
            for (EvidenceItem item : group.evidence) {
                sources.add(item.sessionId());
                if (item.pinned()) localPins++;
            }
            int convergence = convergenceCount(group.evidence);
            int tensions = tensionCandidateCount(group.evidence);
            DossierGroup value = new DossierGroup(
                    group.key, group.head, group.axes, group.required, group.answered,
                    group.evidence, sources.size(), convergence, tensions, localPins);
            totalGaps += value.gaps().size();
            totalTensions += tensions;
            dossier.add(value);
        }

        dossier.sort((a, b) -> {
            int bySources = Integer.compare(b.sourceCount(), a.sourceCount());
            if (bySources != 0) return bySources;
            int byEvidence = Integer.compare(b.evidence().size(), a.evidence().size());
            return byEvidence != 0 ? byEvidence : a.head().compareToIgnoreCase(b.head());
        });

        return new Workspace(safeState, safeSessions, dossier, evidenceCount,
                sourceIds.size(), totalGaps, totalTensions, pinnedCount);
    }

    private static void markAxes(Set<SemanticQueryMatrix.QuerySlot> answered,
                                 UniversalSubjectFrame.Frame frame) {
        if (frame == null) return;
        for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
            switch (axis) {
                case CAUSE: answered.add(SemanticQueryMatrix.QuerySlot.WHY); break;
                case EFFECT: answered.add(SemanticQueryMatrix.QuerySlot.EFFECT); break;
                case MECHANISM: answered.add(SemanticQueryMatrix.QuerySlot.HOW); break;
                case CONDITION: answered.add(SemanticQueryMatrix.QuerySlot.CONDITION); break;
                case PURPOSE: answered.add(SemanticQueryMatrix.QuerySlot.PURPOSE); break;
                case COMPARISON: answered.add(SemanticQueryMatrix.QuerySlot.COMPARISON); break;
                case QUANTITY: answered.add(SemanticQueryMatrix.QuerySlot.QUANTITY); break;
                case LOCATION: answered.add(SemanticQueryMatrix.QuerySlot.WHERE); break;
                case TIME: answered.add(SemanticQueryMatrix.QuerySlot.WHEN); break;
                case TARGET:
                case POPULATION: answered.add(SemanticQueryMatrix.QuerySlot.TARGET); break;
                case DOMAIN: answered.add(SemanticQueryMatrix.QuerySlot.DOMAIN); break;
                case EVIDENCE: answered.add(SemanticQueryMatrix.QuerySlot.EVIDENCE); break;
                case PROBLEM: answered.add(SemanticQueryMatrix.QuerySlot.PROBLEM); break;
                case SOLUTION: answered.add(SemanticQueryMatrix.QuerySlot.SOLUTION); break;
                case RISK: answered.add(SemanticQueryMatrix.QuerySlot.RISK); break;
                default: break;
            }
        }
    }

    private static void markClaim(Set<SemanticQueryMatrix.QuerySlot> answered,
                                  OnePassSemanticOrganizer.Claim claim) {
        if (claim == null) return;
        answered.add(slotForRelation(claim.relation()));
        for (SemanticGraph.Slot slot : claim.slots().keySet()) {
            switch (slot) {
                case WHAT: answered.add(SemanticQueryMatrix.QuerySlot.WHAT); break;
                case WHO: answered.add(SemanticQueryMatrix.QuerySlot.WHO); break;
                case WHERE: answered.add(SemanticQueryMatrix.QuerySlot.WHERE); break;
                case WHEN: answered.add(SemanticQueryMatrix.QuerySlot.WHEN); break;
                case WHY: answered.add(SemanticQueryMatrix.QuerySlot.WHY); break;
                case HOW: answered.add(SemanticQueryMatrix.QuerySlot.HOW); break;
                case CONDITION: answered.add(SemanticQueryMatrix.QuerySlot.CONDITION); break;
                case EFFECT: answered.add(SemanticQueryMatrix.QuerySlot.EFFECT); break;
                case PURPOSE: answered.add(SemanticQueryMatrix.QuerySlot.PURPOSE); break;
                case EVIDENCE: answered.add(SemanticQueryMatrix.QuerySlot.EVIDENCE); break;
                case QUANTITY: answered.add(SemanticQueryMatrix.QuerySlot.QUANTITY); break;
                case COMPARISON: answered.add(SemanticQueryMatrix.QuerySlot.COMPARISON); break;
                default: break;
            }
        }
    }

    private static SemanticQueryMatrix.QuerySlot slotForRelation(SemanticGraph.Relation relation) {
        if (relation == null) return SemanticQueryMatrix.QuerySlot.WHAT;
        switch (relation) {
            case CAUSE: return SemanticQueryMatrix.QuerySlot.WHY;
            case EFFECT: return SemanticQueryMatrix.QuerySlot.EFFECT;
            case MECHANISM:
            case SEQUENCE: return SemanticQueryMatrix.QuerySlot.HOW;
            case CONDITION: return SemanticQueryMatrix.QuerySlot.CONDITION;
            case PURPOSE: return SemanticQueryMatrix.QuerySlot.PURPOSE;
            case COMPARISON: return SemanticQueryMatrix.QuerySlot.COMPARISON;
            case EVIDENCE: return SemanticQueryMatrix.QuerySlot.EVIDENCE;
            case PROBLEM: return SemanticQueryMatrix.QuerySlot.PROBLEM;
            case SOLUTION: return SemanticQueryMatrix.QuerySlot.SOLUTION;
            case DEFINITION:
            case ATTRIBUTE:
            case GENERIC:
            default: return SemanticQueryMatrix.QuerySlot.WHAT;
        }
    }

    private static int convergenceCount(List<EvidenceItem> evidence) {
        Map<String, Set<Long>> signatures = new HashMap<>();
        for (EvidenceItem item : evidence) {
            String core = !safe(item.object()).isEmpty() ? item.object() : item.raw();
            String folded = fold(core);
            if (folded.isEmpty()) continue;
            String signature = item.relation().name() + "|" + folded;
            signatures.computeIfAbsent(signature, ignored -> new LinkedHashSet<>())
                    .add(item.sessionId());
        }
        int count = 0;
        for (Set<Long> sources : signatures.values()) if (sources.size() >= 2) count++;
        return count;
    }

    private static int tensionCandidateCount(List<EvidenceItem> evidence) {
        int count = 0;
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < evidence.size(); i++) {
            EvidenceItem a = evidence.get(i);
            for (int j = i + 1; j < evidence.size(); j++) {
                EvidenceItem b = evidence.get(j);
                if (a.sessionId() == b.sessionId()) continue;
                if (a.relation() != b.relation() || a.relation() == SemanticGraph.Relation.GENERIC) continue;
                boolean polarity = a.negated() != b.negated();
                boolean differentValues = !safe(a.object()).isEmpty() && !safe(b.object()).isEmpty()
                        && tokenOverlap(fold(a.object()), fold(b.object())) < 0.22;
                if (polarity || differentValues) {
                    String pair = Math.min(a.sessionId(), b.sessionId()) + "|"
                            + Math.max(a.sessionId(), b.sessionId()) + "|" + a.relation().name();
                    if (pairs.add(pair)) count++;
                }
            }
        }
        return count;
    }

    private static double tokenOverlap(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        for (String value : a) if (b.contains(value)) intersection++;
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : intersection / (double) union;
    }

    private static Set<String> tokens(String value) {
        Set<String> out = new LinkedHashSet<>();
        for (String token : safe(value).split("\\s+")) if (token.length() >= 3) out.add(token);
        return out;
    }

    private static String evidenceId(long sessionId, int paragraphIndex, int claimIndex) {
        return sessionId + ":p" + paragraphIndex + ":c" + claimIndex;
    }

    private static String defaultSourceTitle(OnePassSemanticOrganizer.Snapshot snapshot,
                                             long sessionId) {
        String subject = safe(snapshot.globalSubject());
        return subject.isEmpty() ? "Sursa " + sessionId : subject + " · " + sessionId;
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
