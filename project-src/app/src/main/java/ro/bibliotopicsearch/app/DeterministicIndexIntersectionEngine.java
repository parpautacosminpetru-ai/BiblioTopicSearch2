package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evidence-bound deterministic intersections between user-owned topic-map indices.
 *
 * Contract:
 * 1) an index is active only through an explicit canonical/alias term or a narrow
 *    Romanian inflectional-family match;
 * 2) two indices never form an intersection from paragraph/page co-presence alone;
 * 3) a crossing requires two independent, non-overlapping anchors in the same
 *    semantic proposition and one of the documented structural rules;
 * 4) ancestor/descendant nodes in the same topic-map branch are hierarchy, not a crossing;
 * 5) the exact rule and evidence spans are retained for auditability.
 */
public final class DeterministicIndexIntersectionEngine {
    private DeterministicIndexIntersectionEngine() {}

    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    public enum MatchKind {
        EXACT,
        INFLECTIONAL_FAMILY,
        INHERITED_COREFERENCE
    }

    public enum AnchorRole {
        SUBJECT,
        OBJECT,
        SLOT,
        PREDICATE,
        CONTEXT
    }

    public enum Rule {
        SUBJECT_TO_COMPLEMENT("R1"),
        EXPLICIT_RELATIONAL_FRAME("R2");

        private final String code;
        Rule(String code) { this.code = code; }
        public String code() { return code; }
    }

    public static final class Anchor {
        private final TopicNode node;
        private final String term;
        private final String surface;
        private final int start;
        private final int end;
        private final MatchKind matchKind;
        private final AnchorRole role;
        private final String fieldKey;
        private final String fieldText;

        Anchor(
                TopicNode node,
                String term,
                String surface,
                int start,
                int end,
                MatchKind matchKind,
                AnchorRole role,
                String fieldKey,
                String fieldText
        ) {
            this.node = node;
            this.term = safe(term);
            this.surface = safe(surface);
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
            this.matchKind = matchKind == null ? MatchKind.EXACT : matchKind;
            this.role = role == null ? AnchorRole.CONTEXT : role;
            this.fieldKey = safe(fieldKey);
            this.fieldText = safe(fieldText);
        }

        public TopicNode node() { return node; }
        public String term() { return term; }
        public String surface() { return surface; }
        public int start() { return start; }
        public int end() { return end; }
        public MatchKind matchKind() { return matchKind; }
        public AnchorRole role() { return role; }
        public String fieldKey() { return fieldKey; }
        public String fieldText() { return fieldText; }
    }

    public static final class Intersection {
        private final int paragraphIndex;
        private final int sentenceIndex;
        private final int clauseIndex;
        private final Anchor left;
        private final Anchor right;
        private final Rule rule;
        private final SemanticGraph.Relation relation;
        private final Set<SemanticGraph.Operator> operators;
        private final double propositionConfidence;

        Intersection(
                SemanticGraph.Proposition proposition,
                Anchor left,
                Anchor right,
                Rule rule
        ) {
            this.paragraphIndex = proposition.paragraphIndex();
            this.sentenceIndex = proposition.sentenceIndex();
            this.clauseIndex = proposition.clauseIndex();
            this.left = left;
            this.right = right;
            this.rule = rule;
            this.relation = proposition.relation() == null
                    ? SemanticGraph.Relation.GENERIC
                    : proposition.relation();
            EnumSet<SemanticGraph.Operator> copy = proposition.operators().isEmpty()
                    ? EnumSet.noneOf(SemanticGraph.Operator.class)
                    : EnumSet.copyOf(proposition.operators());
            this.operators = Collections.unmodifiableSet(copy);
            this.propositionConfidence = proposition.confidence();
        }

        public int paragraphIndex() { return paragraphIndex; }
        public int sentenceIndex() { return sentenceIndex; }
        public int clauseIndex() { return clauseIndex; }
        public Anchor left() { return left; }
        public Anchor right() { return right; }
        public Rule rule() { return rule; }
        public SemanticGraph.Relation relation() { return relation; }
        public Set<SemanticGraph.Operator> operators() { return operators; }
        public double propositionConfidence() { return propositionConfidence; }

        public String unitKey() {
            return paragraphIndex + ":" + sentenceIndex + ":" + clauseIndex;
        }

        public String label() {
            return safeTitle(left.node) + " × " + safeTitle(right.node);
        }

        public String auditLabel() {
            StringBuilder out = new StringBuilder(label())
                    .append(" [").append(rule.code()).append(']');
            if (relation != SemanticGraph.Relation.GENERIC) {
                out.append(" · ").append(relation.name());
            }
            if (!operators.isEmpty()) out.append(" · ").append(operators);
            return out.toString();
        }
    }

    public static List<Intersection> detect(TopicMap map, SemanticGraph graph) {
        if (map == null || graph == null || graph.isEmpty()) return Collections.emptyList();
        List<Intersection> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (SemanticGraph.Proposition proposition : graph.propositions()) {
            for (Intersection intersection : detectProposition(map, proposition)) {
                String key = intersection.unitKey() + "|" + intersection.left().node().path
                        + "|" + intersection.right().node().path;
                if (dedupe.add(key)) out.add(intersection);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Deterministic crossings for one proposition only. */
    public static List<Intersection> detectProposition(
            TopicMap map,
            SemanticGraph.Proposition proposition
    ) {
        if (proposition == null || safe(proposition.raw()).isEmpty()) return Collections.emptyList();
        List<Anchor> anchors = anchorsFor(map, proposition);
        if (anchors.size() < 2) return Collections.emptyList();

        List<Intersection> out = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            for (int j = i + 1; j < anchors.size(); j++) {
                Anchor b = anchors.get(j);
                if (sameOrHierarchical(a.node, b.node)) continue;
                if (overlaps(a, b)) continue; // one lexical span cannot prove two indices.

                Rule rule = ruleFor(a, b);
                if (rule == null) continue; // co-presence is deliberately not a crossing.

                Anchor left = a;
                Anchor right = b;
                if (safe(left.node.path).compareToIgnoreCase(safe(right.node.path)) > 0) {
                    left = b;
                    right = a;
                }
                out.add(new Intersection(proposition, left, right, rule));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Best independent anchor for every enabled thematic node in one proposition.
     * Returned order is stable by topic path.
     */
    public static List<Anchor> anchorsFor(
            TopicMap map,
            SemanticGraph.Proposition proposition
    ) {
        if (proposition == null || safe(proposition.raw()).isEmpty()) return Collections.emptyList();
        List<TopicNode> active = activeNodes(map);
        if (active.isEmpty()) return Collections.emptyList();

        Map<String, Anchor> byPath = new LinkedHashMap<>();
        for (TopicNode node : active) {
            Anchor anchor = bestAnchor(node, proposition);
            if (anchor != null) byPath.put(node.path, anchor);
        }
        List<Anchor> anchors = new ArrayList<>(byPath.values());
        anchors.sort(Comparator.comparing(a -> safe(a.node.path)));
        return Collections.unmodifiableList(anchors);
    }

    /** Explicit/flexional anchors in arbitrary source text, without semantic-role inference. */
    public static List<Anchor> anchorsForText(TopicMap map, String text) {
        String raw = safe(text);
        if (raw.isEmpty()) return Collections.emptyList();
        List<TopicNode> active = activeNodes(map);
        if (active.isEmpty()) return Collections.emptyList();

        List<Anchor> out = new ArrayList<>();
        for (TopicNode node : active) {
            List<String> terms = node.terms == null || node.terms.isEmpty()
                    ? Collections.singletonList(node.title)
                    : node.terms;
            Anchor best = null;
            for (String term : terms) {
                String clean = safe(term);
                if (clean.isEmpty()) continue;
                Span span = findBestSpan(raw, clean);
                if (span == null) continue;
                Anchor candidate = new Anchor(
                        node, clean, span.surface, span.start, span.end, span.kind,
                        AnchorRole.CONTEXT, "RAW", raw
                );
                if (best == null
                        || (candidate.matchKind == MatchKind.EXACT && best.matchKind != MatchKind.EXACT)
                        || (candidate.matchKind == best.matchKind && candidate.start < best.start)) {
                    best = candidate;
                }
            }
            if (best != null) out.add(best);
        }
        out.sort(Comparator.comparing(a -> safe(a.node.path)));
        return Collections.unmodifiableList(out);
    }

    /**
     * True when two distinct non-hierarchical topic nodes are supported by the
     * exact same source span. Such a unit is retained but must be reviewed manually.
     */
    public static boolean hasSharedSpanAmbiguity(List<Anchor> anchors) {
        if (anchors == null || anchors.size() < 2) return false;
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            if (a == null) continue;
            for (int j = i + 1; j < anchors.size(); j++) {
                Anchor b = anchors.get(j);
                if (b == null || sameOrHierarchical(a.node, b.node)) continue;
                if (a.start == b.start && a.end == b.end) return true;
            }
        }
        return false;
    }

    private static List<TopicNode> activeNodes(TopicMap map) {
        if (map == null || map.nodes == null || map.nodes.isEmpty()) return Collections.emptyList();
        List<TopicNode> active = new ArrayList<>();
        for (TopicNode node : map.nodes) {
            if (node != null && node.enabled && !safe(node.path).isEmpty()) active.add(node);
        }
        active.sort(Comparator.comparing(node -> safe(node.path)));
        return active;
    }

    private static Rule ruleFor(Anchor a, Anchor b) {
        if (a == null || b == null) return null;

        boolean aSubject = a.role == AnchorRole.SUBJECT;
        boolean bSubject = b.role == AnchorRole.SUBJECT;
        if (aSubject ^ bSubject) {
            Anchor other = aSubject ? b : a;
            if (other.role == AnchorRole.OBJECT
                    || other.role == AnchorRole.SLOT
                    || other.role == AnchorRole.PREDICATE) {
                return Rule.SUBJECT_TO_COMPLEMENT;
            }
        }

        if (a.role == b.role
                && !a.fieldKey.isEmpty()
                && a.fieldKey.equals(b.fieldKey)
                && containsRelationalFrame(a.fieldText)) {
            return Rule.EXPLICIT_RELATIONAL_FRAME;
        }
        return null;
    }

    private static Anchor bestAnchor(TopicNode node, SemanticGraph.Proposition proposition) {
        if (node == null || proposition == null) return null;
        List<String> terms = node.terms == null || node.terms.isEmpty()
                ? Collections.singletonList(node.title)
                : node.terms;

        Anchor best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String term : terms) {
            String clean = safe(term);
            if (clean.isEmpty()) continue;
            Span raw = findBestSpan(proposition.raw(), clean);

            Field field = locateField(proposition, clean);
            AnchorRole role = field == null ? AnchorRole.CONTEXT : field.role;
            String fieldKey = field == null ? "RAW" : field.key;
            String fieldText = field == null ? proposition.raw() : field.text;

            if (raw == null && proposition.inheritedSubject()) {
                Span canonical = findBestSpan(proposition.subject(), clean);
                Span coreference = canonical == null ? null : findCoreferenceSpan(proposition.raw());
                if (coreference != null) {
                    raw = new Span(
                            coreference.surface,
                            coreference.start,
                            coreference.end,
                            MatchKind.INHERITED_COREFERENCE
                    );
                    role = AnchorRole.SUBJECT;
                    fieldKey = "SUBJECT:COREFERENCE";
                    fieldText = proposition.subject();
                }
            }
            if (raw == null) continue;

            int score = roleScore(role) * 100;
            if (raw.kind == MatchKind.EXACT) score += 20;
            else if (raw.kind == MatchKind.INHERITED_COREFERENCE) score += 6;
            score += Math.min(18, tokenCount(clean) * 3);
            score -= Math.min(12, raw.start / 80);

            Anchor candidate = new Anchor(
                    node, clean, raw.surface, raw.start, raw.end, raw.kind,
                    role, fieldKey, fieldText
            );
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static Field locateField(SemanticGraph.Proposition proposition, String term) {
        Span subject = findBestSpan(proposition.subject(), term);
        if (subject != null) return new Field(AnchorRole.SUBJECT, "SUBJECT", proposition.subject());

        Span object = findBestSpan(proposition.object(), term);
        if (object != null) return new Field(AnchorRole.OBJECT, "OBJECT", proposition.object());

        for (Map.Entry<SemanticGraph.Slot, String> entry : proposition.slots().entrySet()) {
            if (findBestSpan(entry.getValue(), term) != null) {
                return new Field(AnchorRole.SLOT, "SLOT:" + entry.getKey().name(), entry.getValue());
            }
        }

        Span predicate = findBestSpan(proposition.predicate(), term);
        if (predicate != null) return new Field(AnchorRole.PREDICATE, "PREDICATE", proposition.predicate());

        return null;
    }

    private static boolean containsRelationalFrame(String value) {
        String folded = " " + RomanianLanguagePack.fold(value) + " ";
        return folded.contains(" intre ") || folded.contains(" dintre ");
    }

    private static boolean sameOrHierarchical(TopicNode a, TopicNode b) {
        if (a == null || b == null) return true;
        String left = safe(a.path);
        String right = safe(b.path);
        if (left.isEmpty() || right.isEmpty() || left.equalsIgnoreCase(right)) return true;
        String leftPrefix = left + " > ";
        String rightPrefix = right + " > ";
        return right.regionMatches(true, 0, leftPrefix, 0, leftPrefix.length())
                || left.regionMatches(true, 0, rightPrefix, 0, rightPrefix.length());
    }

    private static boolean overlaps(Anchor a, Anchor b) {
        return a.start < b.end && b.start < a.end;
    }

    private static int roleScore(AnchorRole role) {
        if (role == null) return 0;
        switch (role) {
            case SUBJECT: return 5;
            case OBJECT: return 4;
            case SLOT: return 3;
            case PREDICATE: return 2;
            case CONTEXT:
            default: return 1;
        }
    }

    private static int tokenCount(String value) {
        int count = 0;
        Matcher matcher = TOKEN.matcher(safe(value));
        while (matcher.find()) count++;
        return count;
    }

    private static Span findBestSpan(String text, String phrase) {
        if (safe(text).isEmpty() || safe(phrase).isEmpty()) return null;

        List<LexToken> source = tokenize(text);
        List<LexToken> target = tokenize(phrase);
        if (target.isEmpty() || source.size() < target.size()) return null;

        Span best = null;
        for (int start = 0; start <= source.size() - target.size(); start++) {
            boolean ok = true;
            boolean allExact = true;
            for (int offset = 0; offset < target.size(); offset++) {
                LexToken actual = source.get(start + offset);
                LexToken wanted = target.get(offset);
                if (actual.folded.equals(wanted.folded)) continue;

                allExact = false;
                if (wanted.function || actual.function
                        || wanted.family.isEmpty()
                        || !wanted.family.equals(actual.family)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

            LexToken first = source.get(start);
            LexToken last = source.get(start + target.size() - 1);
            Span candidate = new Span(
                    text.substring(first.start, last.end),
                    first.start,
                    last.end,
                    allExact ? MatchKind.EXACT : MatchKind.INFLECTIONAL_FAMILY
            );
            if (best == null
                    || (candidate.kind == MatchKind.EXACT && best.kind != MatchKind.EXACT)
                    || (candidate.kind == best.kind && candidate.start < best.start)) {
                best = candidate;
            }
        }
        return best;
    }

    private static Span findCoreferenceSpan(String value) {
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String token = matcher.group();
            if (RomanianLanguagePack.isCoreference(token)) {
                return new Span(token, matcher.start(), matcher.end(), MatchKind.INHERITED_COREFERENCE);
            }
        }
        return null;
    }

    private static List<LexToken> tokenize(String value) {
        List<LexToken> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String raw = matcher.group();
            String folded = RomanianLanguagePack.fold(raw);
            boolean function = RomanianLanguagePack.isFunctionWord(folded);
            String family = function ? folded : RomanianLanguagePack.familyKey(raw);
            out.add(new LexToken(matcher.start(), matcher.end(), folded, family, function));
        }
        return out;
    }

    private static String safeTitle(TopicNode node) {
        if (node == null) return "?";
        String title = safe(node.title);
        return title.isEmpty() ? safe(node.path) : title;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Field {
        final AnchorRole role;
        final String key;
        final String text;

        Field(AnchorRole role, String key, String text) {
            this.role = role;
            this.key = key == null ? "" : key;
            this.text = text == null ? "" : text;
        }
    }

    private static final class LexToken {
        final int start;
        final int end;
        final String folded;
        final String family;
        final boolean function;

        LexToken(int start, int end, String folded, String family, boolean function) {
            this.start = start;
            this.end = end;
            this.folded = folded == null ? "" : folded;
            this.family = family == null ? "" : family;
            this.function = function;
        }
    }

    private static final class Span {
        final String surface;
        final int start;
        final int end;
        final MatchKind kind;

        Span(String surface, int start, int end, MatchKind kind) {
            this.surface = surface == null ? "" : surface;
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }
}
