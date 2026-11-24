package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TopicMatcher {
    private TopicMatcher() {}

    static final class CompiledTerm {
        final String raw;
        final String normalized;
        final int tokenCount;
        final TopicNode node;

        CompiledTerm(String raw, String normalized, int tokenCount, TopicNode node) {
            this.raw = raw;
            this.normalized = normalized;
            this.tokenCount = tokenCount;
            this.node = node;
        }
    }

    /**
     * Planul de căutare este construit o singură dată când harta/setările se schimbă.
     * Astfel termenii nu mai sunt normalizați și reconstruiți pentru fiecare cadru OCR.
     */
    public static final class SearchPlan {
        private final boolean stripDiacritics;
        private final int compareChars;
        private final AppPrefs.MatchMode mode;
        private final Map<Integer, TermIndex<CompiledTerm>> indexes;
        private final List<Integer> tokenCounts;
        private final int termCount;
        private final boolean repairEnabled;

        private SearchPlan(boolean stripDiacritics, int compareChars, AppPrefs.MatchMode mode,
                           Map<Integer, TermIndex<CompiledTerm>> indexes, List<Integer> tokenCounts, int termCount, boolean repairEnabled) {
            this.stripDiacritics = stripDiacritics;
            this.compareChars = compareChars;
            this.mode = mode;
            this.indexes = indexes;
            this.tokenCounts = tokenCounts;
            this.termCount = termCount;
            this.repairEnabled = repairEnabled;
        }

        public int termCount() { return termCount; }

        List<CompiledTerm> candidates(int tokenCount, String actual) {
            TermIndex<CompiledTerm> index = indexes.get(tokenCount);
            if (index == null || actual == null || actual.isEmpty()) return Collections.emptyList();
            switch (mode) {
                case EXACT: return index.exact(shorten(actual, compareChars));
                case PREFIX: return index.prefix(actual);
                default: return index.contains(actual);
            }
        }

        List<CompiledTerm> exactCandidates(int count, String value) {
            TermIndex<CompiledTerm> index = indexes.get(count);
            if (index == null || value.isEmpty()) return Collections.emptyList();
            List<CompiledTerm> exact = new ArrayList<>();
            for (CompiledTerm term : index.exact(shorten(value, compareChars))) {
                if (term.normalized.equals(value)) exact.add(term);
            }
            return exact;
        }

        List<CompiledTerm> repairedCandidates(String token) {
            if (!repairEnabled || !candidates(1, token).isEmpty()) return Collections.emptyList();
            List<CompiledTerm> found = Collections.emptyList();
            for (String variant : OcrRepair.digitVariants(token)) {
                List<CompiledTerm> exact = exactCandidates(1, variant);
                if (exact.isEmpty()) continue;
                if (!found.isEmpty()) return Collections.emptyList(); // ambiguous spelling
                found = exact; // preserve all maps sharing this one spelling
            }
            return found;
        }
    }

    private static String shorten(String term, int length) {
        return length > 0 && term.length() > length ? term.substring(0, length) : term;
    }

    public static SearchPlan compile(Context context, TopicMap map) {
        return compile(map, AppPrefs.ignoreDiacritics(context), AppPrefs.compareChars(context),
                AppPrefs.getMatchMode(context), AppPrefs.ocrRepair(context));
    }

    static SearchPlan compile(TopicMap map, boolean stripDiacritics, int compareChars, AppPrefs.MatchMode mode) {
        return compile(map, stripDiacritics, compareChars, mode, true);
    }

    static SearchPlan compile(TopicMap map, boolean stripDiacritics, int compareChars, AppPrefs.MatchMode mode, boolean repairEnabled) {
        Map<Integer, TermIndex<CompiledTerm>> indexes = new HashMap<>();
        int termCount = 0;
        if (map != null) {
            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;
                Set<String> seen = new HashSet<>();
                List<String> searchTerms = node.terms.isEmpty()
                        ? Collections.singletonList(node.title) : node.terms;
                for (String raw : searchTerms) {
                    String normalized = normalize(raw, stripDiacritics);
                    if (normalized.isEmpty()) continue;
                    int count = countTokens(normalized);
                    String key = shorten(normalized, compareChars);
                    if (!seen.add(count + ":" + normalized)) continue;
                    CompiledTerm term = new CompiledTerm(raw, normalized, count, node);
                    indexes.computeIfAbsent(count, ignored -> new TermIndex<>()).add(key, term);
                    termCount++;
                }
            }
        }
        List<Integer> counts = new ArrayList<>(indexes.keySet());
        Collections.sort(counts);
        return new SearchPlan(stripDiacritics, compareChars, mode, indexes, counts, termCount, repairEnabled);
    }

    /** Compatibilitate pentru apelurile vechi. MainActivity folosește planul compilat. */
    public static List<MatchHit> find(Context context, Text text, TopicMap map) {
        return find(text, compile(context, map));
    }

    private static final class Token {
        final String value, original;
        final RectF box;
        final int element, line;
        final Token left, right;
        Token(String value, String original, RectF box, int element, int line) {
            this(value, original, box, element, line, null, null);
        }
        Token(String value, String original, RectF box, int element, int line, Token left, Token right) {
            this.value = value;
            this.original = original;
            this.box = box;
            this.element = element;
            this.line = line;
            this.left = left;
            this.right = right;
        }
    }

    static List<String> tokenize(String original, boolean stripDiacritics) {
        String value = normalize(original, stripDiacritics);
        if (value.isEmpty()) return Collections.emptyList();
        return java.util.Arrays.asList(value.split(" "));
    }

    public static List<MatchHit> find(Text text, SearchPlan plan) {
        List<MatchHit> hits = new ArrayList<>();
        if (text == null || plan == null || plan.termCount <= 0) return hits;
        long now = System.currentTimeMillis();
        Set<String> dedupe = new HashSet<>();
        int elementId = 0, lineId = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            List<Token> previous = Collections.emptyList();
            Text.Line previousLine = null;
            for (Text.Line line : block.getLines()) {
                if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
                List<Token> tokens = new ArrayList<>();
                for (Text.Element element : line.getElements()) {
                    String original = element.getText();
                    Rect box = element.getBoundingBox();
                    RectF geometry = box == null ? null : new RectF(box);
                    int id = elementId++;
                    for (String token : tokenize(original, plan.stripDiacritics)) {
                        tokens.add(new Token(token, original, geometry, id, lineId));
                    }
                }
                scan(tokens, -1, -1, plan, hits, dedupe, now);
                if (!previous.isEmpty() && !tokens.isEmpty() && adjacent(previousLine, line)) {
                    String end = previousLine.getText().trim();
                    if (hasBreakHyphen(end)) {
                        scanJoined(previous, tokens, plan, hits, dedupe, now);
                    } else if (!end.endsWith(".") && !end.endsWith("!") && !end.endsWith("?") &&
                            !end.endsWith(":") && !end.endsWith(";")) {
                        List<Token> both = new ArrayList<>(previous.size() + tokens.size());
                        both.addAll(previous);
                        both.addAll(tokens);
                        scan(both, previous.size(), -1, plan, hits, dedupe, now);
                    }
                }
                previous = tokens;
                previousLine = line;
                lineId++;
            }
        }
        return hits;
    }

    private static boolean hasBreakHyphen(String text) {
        return text.endsWith("-") || text.endsWith("‐") || text.endsWith("‑") || text.endsWith("\u00ad");
    }

    private static boolean adjacent(Text.Line previous, Text.Line next) {
        if (previous == null || next == null) return false;
        Rect a = previous.getBoundingBox(), b = next.getBoundingBox();
        if (a == null || b == null || a.height() <= 0 || b.height() <= 0) return false;
        int height = Math.max(a.height(), b.height());
        int gap = b.top - a.bottom;
        int overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        return gap >= -height / 3 && gap <= height * 1.5f &&
                overlap > 0 && b.centerY() > a.centerY();
    }

    private static void scanJoined(List<Token> previous, List<Token> next, SearchPlan plan,
                                   List<MatchHit> hits, Set<String> dedupe, long now) {
        Token left = previous.get(previous.size() - 1), right = next.get(0);
        // Reconstruct only a visibly hyphenated word, not a standalone dash.
        if (left.box == null || right.box == null || !hasBreakHyphen(left.original.trim())) return;
        String value = left.value;
        if (!value.endsWith("-") && left.original.endsWith("\u00ad")) value += "-";
        for (String joined : OcrRepair.joinedVariants(value, right.value)) {
            List<Token> both = new ArrayList<>(previous.size() + next.size() - 1);
            both.addAll(previous.subList(0, previous.size() - 1));
            int at = both.size();
            both.add(new Token(joined, "", left.box, left.element, left.line, left, right));
            both.addAll(next.subList(1, next.size()));
            scan(both, -1, at, plan, hits, dedupe, now);
        }
    }

    /** A repaired line break must occur inside the window; a standard cross-line
     * window must span the boundary. Only exact full terms justify reconstruction. */
    private static void scan(List<Token> tokens, int boundary, int joinedIndex, SearchPlan plan,
                             List<MatchHit> hits, Set<String> dedupe, long now) {
        int max = plan.tokenCounts.get(plan.tokenCounts.size() - 1);
        int pivot = joinedIndex >= 0 ? joinedIndex : boundary;
        int first = pivot < 0 ? 0 : Math.max(0, pivot - max + 1);
        int limit = joinedIndex >= 0 ? joinedIndex + 1 : boundary >= 0 ? boundary : tokens.size();
        for (int start = first; start < limit; start++) {
            if (Thread.currentThread().isInterrupted()) return;
            StringBuilder phrase = new StringBuilder();
            for (int end = start; end < tokens.size() && end - start < max; end++) {
                Token token = tokens.get(end);
                if (token.box == null) break;
                if (phrase.length() > 0) phrase.append(' ');
                phrase.append(token.value);
                int count = end - start + 1;
                if ((pivot >= 0 && end < pivot) || !plan.indexes.containsKey(count)) continue;
                String actual = phrase.toString();
                MatchHit.Kind kind = joinedIndex >= 0 ? MatchHit.Kind.LINE_JOIN : MatchHit.Kind.LITERAL;
                List<CompiledTerm> found = joinedIndex >= 0 ? plan.exactCandidates(count, actual) : plan.candidates(count, actual);
                if (found.isEmpty() && joinedIndex < 0 && boundary < 0 && count == 1) {
                    found = plan.repairedCandidates(actual);
                    if (!found.isEmpty()) kind = MatchHit.Kind.OCR_REPAIR;
                }
                if (found.isEmpty()) continue;
                List<Token> evidence = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    Token part = tokens.get(i);
                    if (part.left != null) {
                        evidence.add(part.left);
                        evidence.add(part.right);
                    } else evidence.add(part);
                }
                List<RectF> segments = new ArrayList<>();
                StringBuilder original = new StringBuilder();
                int lastLine = -1, lastElement = -1;
                for (Token part : evidence) {
                    if (part.line != lastLine) segments.add(new RectF(part.box));
                    else segments.get(segments.size() - 1).union(part.box);
                    if (part.element != lastElement) {
                        if (original.length() > 0) original.append(part.line == lastLine ? ' ' : '\n');
                        original.append(part.original);
                    }
                    lastLine = part.line;
                    lastElement = part.element;
                }
                for (CompiledTerm term : found) addHit(hits, dedupe, segments, original.toString(), term, now, kind);
            }
        }
    }

    private static int countTokens(String normalized) {
        int count = 0;
        boolean inToken = false;
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isWhitespace(normalized.charAt(i))) {
                inToken = false;
            } else if (!inToken) {
                count++;
                inToken = true;
            }
        }
        return Math.max(1, count);
    }

    private static void addHit(List<MatchHit> hits, Set<String> dedupe,
                               List<RectF> segments, String original, CompiledTerm term, long timestamp, MatchHit.Kind kind) {
        StringBuilder key = new StringBuilder(term.node.identityKey()).append('|').append(term.raw);
        for (RectF box : segments) key.append('|').append(box.left).append(':').append(box.top)
                .append(':').append(box.right).append(':').append(box.bottom);
        if (dedupe.add(key.toString())) {
            hits.add(new MatchHit(segments, original, term.raw, term.node, timestamp, kind, false));
        }
    }

    public static boolean matches(
            String actual,
            String term,
            AppPrefs.MatchMode mode,
            int compareChars
    ) {
        if (actual == null || term == null) return false;
        String a = actual.trim();
        String t = term.trim();
        if (a.isEmpty() || t.isEmpty()) return false;

        if (compareChars > 0) {
            int tLen = Math.min(compareChars, t.length());
            t = t.substring(0, tLen);

            if (mode == AppPrefs.MatchMode.EXACT) {
                int aLen = Math.min(compareChars, a.length());
                a = a.substring(0, aLen);
            }
        }

        switch (mode) {
            case EXACT:
                return a.equals(t);
            case CONTAINS:
                return a.contains(t);
            case FLEXIBLE:
                return a.equals(t) || a.startsWith(t) || a.contains(t);
            case PREFIX:
            default:
                return a.startsWith(t);
        }
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final java.util.regex.Pattern SEPARATORS = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}\\s'-]+");
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("\\s+");

    public static String normalize(String value, boolean stripDiacritics) {
        if (value == null) return "";
        String out = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFC)
                .replace('ş', 'ș').replace('ţ', 'ț')
                .replace('’', '\'').replace('–', '-').replace('‑', '-').replace('‐', '-');
        if (stripDiacritics) {
            out = MARKS.matcher(Normalizer.normalize(out, Normalizer.Form.NFD)).replaceAll("");
        }
        return SPACES.matcher(SEPARATORS.matcher(out).replaceAll(" ")).replaceAll(" ").trim();
    }
}
