package ro.bibliotopicsearch.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-pass deterministic matcher for all enabled thematic indices.
 *
 * Terms are compiled into two token automata:
 * - exact folded tokens;
 * - conservative Romanian inflection-family tokens.
 *
 * A source text is tokenized once and both automata scan it linearly. Semantic
 * synonyms remain user-owned aliases in the topic map.
 */
public final class DeterministicMultiIndexMatcher {
    private DeterministicMultiIndexMatcher() {}

    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    private static volatile String cachedSignature = "";
    private static volatile Plan cachedPlan;

    public static final class Plan {
        private final Automaton exact;
        private final Automaton family;
        private final int termCount;
        private final int activeNodes;

        Plan(Automaton exact, Automaton family, int termCount, int activeNodes) {
            this.exact = exact;
            this.family = family;
            this.termCount = termCount;
            this.activeNodes = activeNodes;
        }

        public int termCount() { return termCount; }
        public int activeNodes() { return activeNodes; }
        public boolean isEmpty() { return termCount == 0; }
    }

    private static final class PatternRecord {
        final TopicNode node;
        final String term;
        final int tokenLength;

        PatternRecord(TopicNode node, String term, int tokenLength) {
            this.node = node;
            this.term = term == null ? "" : term;
            this.tokenLength = Math.max(1, tokenLength);
        }
    }

    private static final class State {
        final Map<String, Integer> next = new HashMap<>();
        final List<PatternRecord> output = new ArrayList<>();
        int fail;
    }

    private static final class Automaton {
        final List<State> states = new ArrayList<>();

        Automaton() {
            states.add(new State());
        }

        void add(List<String> tokens, PatternRecord pattern) {
            if (tokens == null || tokens.isEmpty() || pattern == null) return;
            int state = 0;
            for (String token : tokens) {
                if (token == null || token.isEmpty()) return;
                Integer next = states.get(state).next.get(token);
                if (next == null) {
                    next = states.size();
                    states.get(state).next.put(token, next);
                    states.add(new State());
                }
                state = next;
            }
            states.get(state).output.add(pattern);
        }

        void buildFailures() {
            Deque<Integer> queue = new ArrayDeque<>();
            for (Integer child : states.get(0).next.values()) {
                states.get(child).fail = 0;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                State state = states.get(current);
                for (Map.Entry<String, Integer> edge : state.next.entrySet()) {
                    String token = edge.getKey();
                    int child = edge.getValue();
                    int fallback = state.fail;
                    while (fallback != 0 && !states.get(fallback).next.containsKey(token)) {
                        fallback = states.get(fallback).fail;
                    }
                    Integer linked = states.get(fallback).next.get(token);
                    if (linked != null && linked != child) fallback = linked;
                    states.get(child).fail = fallback;
                    states.get(child).output.addAll(states.get(fallback).output);
                    queue.addLast(child);
                }
            }
        }
    }

    private static final class SourceToken {
        final int start;
        final int end;
        final String folded;
        final String family;

        SourceToken(int start, int end, String folded, String family) {
            this.start = start;
            this.end = end;
            this.folded = folded == null ? "" : folded;
            this.family = family == null ? "" : family;
        }
    }

    private static final class RawMatch {
        final TopicNode node;
        final String term;
        final String surface;
        final int start;
        final int end;
        final DeterministicIndexIntersectionEngine.MatchKind kind;
        final int tokenLength;

        RawMatch(
                TopicNode node,
                String term,
                String surface,
                int start,
                int end,
                DeterministicIndexIntersectionEngine.MatchKind kind,
                int tokenLength
        ) {
            this.node = node;
            this.term = term == null ? "" : term;
            this.surface = surface == null ? "" : surface;
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.tokenLength = tokenLength;
        }
    }

    public static Plan forMap(TopicMap map) {
        String signature = signature(map);
        Plan plan = cachedPlan;
        if (plan != null && signature.equals(cachedSignature)) return plan;
        synchronized (DeterministicMultiIndexMatcher.class) {
            if (cachedPlan != null && signature.equals(cachedSignature)) return cachedPlan;
            cachedPlan = compile(map);
            cachedSignature = signature;
            return cachedPlan;
        }
    }

    public static Plan compile(TopicMap map) {
        Automaton exact = new Automaton();
        Automaton family = new Automaton();
        int terms = 0;
        int nodes = 0;

        if (map != null && map.nodes != null) {
            for (TopicNode node : map.nodes) {
                if (node == null || !node.enabled || safe(node.path).isEmpty()) continue;
                nodes++;
                List<String> values = node.terms == null || node.terms.isEmpty()
                        ? Collections.singletonList(node.title)
                        : node.terms;
                for (String raw : values) {
                    String term = safe(raw);
                    if (term.isEmpty()) continue;

                    List<String> exactTokens = foldedTokens(term);
                    List<String> familyTokens = familyTokens(term);
                    if (exactTokens.isEmpty() || familyTokens.isEmpty()) continue;

                    PatternRecord pattern = new PatternRecord(node, term, exactTokens.size());
                    exact.add(exactTokens, pattern);
                    family.add(familyTokens, pattern);
                    terms++;
                }
            }
        }

        exact.buildFailures();
        family.buildFailures();
        return new Plan(exact, family, terms, nodes);
    }

    public static List<DeterministicIndexIntersectionEngine.Anchor> matchText(
            Plan plan,
            String text
    ) {
        if (plan == null || plan.isEmpty() || safe(text).isEmpty()) return Collections.emptyList();
        List<RawMatch> matches = matchAll(plan, text);
        Map<String, DeterministicIndexIntersectionEngine.Anchor> best = new LinkedHashMap<>();
        Map<String, Integer> scores = new HashMap<>();

        for (RawMatch match : matches) {
            DeterministicIndexIntersectionEngine.Anchor anchor =
                    new DeterministicIndexIntersectionEngine.Anchor(
                            match.node,
                            match.term,
                            match.surface,
                            match.start,
                            match.end,
                            match.kind,
                            DeterministicIndexIntersectionEngine.AnchorRole.CONTEXT,
                            "RAW",
                            text
                    );
            int score = matchScore(anchor, match.tokenLength);
            String key = match.node.path;
            if (!best.containsKey(key) || score > scores.get(key)) {
                best.put(key, anchor);
                scores.put(key, score);
            }
        }
        List<DeterministicIndexIntersectionEngine.Anchor> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparing(a -> safe(a.node().path)));
        return Collections.unmodifiableList(out);
    }

    public static List<DeterministicIndexIntersectionEngine.Anchor> matchProposition(
            Plan plan,
            SemanticGraph.Proposition proposition
    ) {
        if (plan == null || plan.isEmpty() || proposition == null || safe(proposition.raw()).isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, DeterministicIndexIntersectionEngine.Anchor> best = new LinkedHashMap<>();
        Map<String, Integer> scores = new HashMap<>();
        for (RawMatch match : matchAll(plan, proposition.raw())) {
            Field field = fieldFor(proposition, match.term);
            DeterministicIndexIntersectionEngine.Anchor anchor =
                    new DeterministicIndexIntersectionEngine.Anchor(
                            match.node,
                            match.term,
                            match.surface,
                            match.start,
                            match.end,
                            match.kind,
                            field.role,
                            field.key,
                            field.text
                    );
            int score = matchScore(anchor, match.tokenLength);
            String key = match.node.path;
            if (!best.containsKey(key) || score > scores.get(key)) {
                best.put(key, anchor);
                scores.put(key, score);
            }
        }

        // Preserve deterministic discourse continuity only where the parser already
        // resolved an explicit Romanian coreference to a canonical subject.
        if (proposition.inheritedSubject()) {
            Span coreference = firstCoreference(proposition.raw());
            if (coreference != null) {
                for (RawMatch canonical : matchAll(plan, proposition.subject())) {
                    String key = canonical.node.path;
                    if (best.containsKey(key)) continue;
                    DeterministicIndexIntersectionEngine.Anchor anchor =
                            new DeterministicIndexIntersectionEngine.Anchor(
                                    canonical.node,
                                    canonical.term,
                                    coreference.surface,
                                    coreference.start,
                                    coreference.end,
                                    DeterministicIndexIntersectionEngine.MatchKind.INHERITED_COREFERENCE,
                                    DeterministicIndexIntersectionEngine.AnchorRole.SUBJECT,
                                    "SUBJECT:COREFERENCE",
                                    proposition.subject()
                            );
                    best.put(key, anchor);
                    scores.put(key, matchScore(anchor, canonical.tokenLength));
                }
            }
        }

        List<DeterministicIndexIntersectionEngine.Anchor> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparing(a -> safe(a.node().path)));
        return Collections.unmodifiableList(out);
    }

    private static List<RawMatch> matchAll(Plan plan, String text) {
        List<SourceToken> source = sourceTokens(text);
        if (source.isEmpty()) return Collections.emptyList();

        Map<String, RawMatch> dedupe = new LinkedHashMap<>();
        scan(plan.exact, source, text, false, dedupe);
        scan(plan.family, source, text, true, dedupe);

        List<RawMatch> out = new ArrayList<>(dedupe.values());
        out.sort(Comparator
                .comparingInt((RawMatch value) -> value.start)
                .thenComparingInt(value -> value.end)
                .thenComparing(value -> safe(value.node.path)));
        return out;
    }

    private static void scan(
            Automaton automaton,
            List<SourceToken> source,
            String rawText,
            boolean useFamily,
            Map<String, RawMatch> out
    ) {
        int state = 0;
        for (int i = 0; i < source.size(); i++) {
            String token = useFamily ? source.get(i).family : source.get(i).folded;
            while (state != 0 && !automaton.states.get(state).next.containsKey(token)) {
                state = automaton.states.get(state).fail;
            }
            Integer next = automaton.states.get(state).next.get(token);
            state = next == null ? 0 : next;

            if (automaton.states.get(state).output.isEmpty()) continue;
            for (PatternRecord pattern : automaton.states.get(state).output) {
                int startToken = i - pattern.tokenLength + 1;
                if (startToken < 0) continue;
                SourceToken first = source.get(startToken);
                SourceToken last = source.get(i);
                String surface = rawText.substring(first.start, last.end);
                DeterministicIndexIntersectionEngine.MatchKind kind = useFamily
                        ? DeterministicIndexIntersectionEngine.MatchKind.INFLECTIONAL_FAMILY
                        : DeterministicIndexIntersectionEngine.MatchKind.EXACT;
                String key = safe(pattern.node.path) + "|" + first.start + "|" + last.end;
                RawMatch existing = out.get(key);
                if (existing == null
                        || (existing.kind != DeterministicIndexIntersectionEngine.MatchKind.EXACT
                        && kind == DeterministicIndexIntersectionEngine.MatchKind.EXACT)) {
                    out.put(key, new RawMatch(
                            pattern.node,
                            pattern.term,
                            surface,
                            first.start,
                            last.end,
                            kind,
                            pattern.tokenLength
                    ));
                }
            }
        }
    }

    private static Field fieldFor(SemanticGraph.Proposition proposition, String term) {
        if (phraseMatches(proposition.subject(), term)) {
            return new Field(
                    DeterministicIndexIntersectionEngine.AnchorRole.SUBJECT,
                    "SUBJECT",
                    proposition.subject()
            );
        }
        if (phraseMatches(proposition.object(), term)) {
            return new Field(
                    DeterministicIndexIntersectionEngine.AnchorRole.OBJECT,
                    "OBJECT",
                    proposition.object()
            );
        }
        for (Map.Entry<SemanticGraph.Slot, String> entry : proposition.slots().entrySet()) {
            if (phraseMatches(entry.getValue(), term)) {
                return new Field(
                        DeterministicIndexIntersectionEngine.AnchorRole.SLOT,
                        "SLOT:" + entry.getKey().name(),
                        entry.getValue()
                );
            }
        }
        if (phraseMatches(proposition.predicate(), term)) {
            return new Field(
                    DeterministicIndexIntersectionEngine.AnchorRole.PREDICATE,
                    "PREDICATE",
                    proposition.predicate()
            );
        }
        return new Field(
                DeterministicIndexIntersectionEngine.AnchorRole.CONTEXT,
                "RAW",
                proposition.raw()
        );
    }

    private static boolean phraseMatches(String text, String term) {
        if (safe(text).isEmpty() || safe(term).isEmpty()) return false;
        String exactText = RomanianLanguagePack.fold(text);
        String exactTerm = RomanianLanguagePack.fold(term);
        if (!exactText.isEmpty() && !exactTerm.isEmpty()) {
            String padded = " " + exactText + " ";
            if (padded.contains(" " + exactTerm + " ")) return true;
        }
        return RomanianLanguagePack.containsFamilyPhrase(text, term);
    }

    private static int matchScore(
            DeterministicIndexIntersectionEngine.Anchor anchor,
            int tokenLength
    ) {
        int role;
        switch (anchor.role()) {
            case SUBJECT: role = 5; break;
            case OBJECT: role = 4; break;
            case SLOT: role = 3; break;
            case PREDICATE: role = 2; break;
            case CONTEXT:
            default: role = 1; break;
        }
        int kind = anchor.matchKind() == DeterministicIndexIntersectionEngine.MatchKind.EXACT
                ? 20
                : anchor.matchKind() == DeterministicIndexIntersectionEngine.MatchKind.INHERITED_COREFERENCE
                ? 6 : 10;
        return role * 100 + kind + Math.min(18, Math.max(1, tokenLength) * 3)
                - Math.min(12, anchor.start() / 80);
    }

    private static List<SourceToken> sourceTokens(String value) {
        List<SourceToken> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String raw = matcher.group();
            String folded = RomanianLanguagePack.fold(raw);
            boolean function = RomanianLanguagePack.isFunctionWord(folded);
            String family = function ? folded : RomanianLanguagePack.familyKey(raw);
            out.add(new SourceToken(matcher.start(), matcher.end(), folded, family));
        }
        return out;
    }

    private static List<String> foldedTokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String folded = RomanianLanguagePack.fold(matcher.group());
            if (!folded.isEmpty()) out.add(folded);
        }
        return out;
    }

    private static List<String> familyTokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String raw = matcher.group();
            String folded = RomanianLanguagePack.fold(raw);
            String family = RomanianLanguagePack.isFunctionWord(folded)
                    ? folded
                    : RomanianLanguagePack.familyKey(raw);
            if (!family.isEmpty()) out.add(family);
        }
        return out;
    }

    private static Span firstCoreference(String raw) {
        Matcher matcher = TOKEN.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            if (RomanianLanguagePack.isCoreference(matcher.group())) {
                return new Span(matcher.group(), matcher.start(), matcher.end());
            }
        }
        return null;
    }

    private static String signature(TopicMap map) {
        if (map == null || map.nodes == null) return "";
        StringBuilder out = new StringBuilder();
        for (TopicNode node : map.nodes) {
            if (node == null) continue;
            out.append(node.path).append('\u0002')
                    .append(node.title).append('\u0002')
                    .append(node.enabled ? '1' : '0').append('\u0003');
            if (node.terms != null) {
                for (String term : node.terms) out.append(term).append('\u0004');
            }
            out.append('\u0005');
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Field {
        final DeterministicIndexIntersectionEngine.AnchorRole role;
        final String key;
        final String text;

        Field(DeterministicIndexIntersectionEngine.AnchorRole role, String key, String text) {
            this.role = role;
            this.key = key == null ? "" : key;
            this.text = text == null ? "" : text;
        }
    }

    private static final class Span {
        final String surface;
        final int start;
        final int end;

        Span(String surface, int start, int end) {
            this.surface = surface == null ? "" : surface;
            this.start = start;
            this.end = end;
        }
    }
}
