package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A single index for all maps. Every emitted hit is tied to a current
 * literal OCR span, or to an explicitly enabled, labelled reconstruction. */
public final class TopicMatcher {
    private TopicMatcher() {}

    static final class CompiledTerm {
        final String raw, normalized;
        final int tokenCount;
        final TopicNode node;
        CompiledTerm(String raw, String normalized, int tokenCount, TopicNode node) {
            this.raw = raw; this.normalized = normalized; this.tokenCount = tokenCount; this.node = node;
        }
    }

    interface WindowConsumer { void match(int start, int end, List<CompiledTerm> terms, MatchHit.Kind kind); }

    public static final class SearchPlan {
        private final boolean stripDiacritics, repairEnabled, joinEnabled;
        private final AppPrefs.MatchMode mode;
        private final Map<Integer, TermIndex<CompiledTerm>> exact;
        private final TermIndex<CompiledTerm> approximate;
        private final int maxTokens, termCount;

        private SearchPlan(boolean strip, AppPrefs.MatchMode mode, boolean repair, boolean join,
                           Map<Integer, TermIndex<CompiledTerm>> exact, TermIndex<CompiledTerm> approximate,
                           int maxTokens, int termCount) {
            stripDiacritics = strip; this.mode = mode; repairEnabled = repair; joinEnabled = join;
            this.exact = exact; this.approximate = approximate;
            this.maxTokens = maxTokens; this.termCount = termCount;
        }
        public int termCount() { return termCount; }

        List<CompiledTerm> exactCandidates(int count, String value) {
            TermIndex<CompiledTerm> index = exact.get(count);
            return index == null ? Collections.emptyList() : index.exact(value);
        }

        List<CompiledTerm> candidates(int count, String value) {
            List<CompiledTerm> literal = exactCandidates(count, value);
            // Partial matching is opt-in, alphabetic, single-word only.
            if (mode == AppPrefs.MatchMode.EXACT || count != 1 || !LiteralText.isAlphabetic(value))
                return literal;
            List<CompiledTerm> other = mode == AppPrefs.MatchMode.PREFIX
                    ? approximate.prefix(value) : approximate.contains(value);
            if (other.isEmpty()) return literal;
            Set<CompiledTerm> both = new LinkedHashSet<>(literal);
            both.addAll(other);
            return new ArrayList<>(both);
        }

        List<CompiledTerm> repairedCandidates(String token) {
            if (!repairEnabled || !candidates(1, token).isEmpty()) return Collections.emptyList();
            List<CompiledTerm> found = Collections.emptyList();
            for (String variant : OcrRepair.digitVariants(token)) {
                List<CompiledTerm> candidates = exactCandidates(1, variant);
                if (candidates.isEmpty()) continue;
                if (!found.isEmpty()) return Collections.emptyList();
                found = candidates;
            }
            return found;
        }

        /** End is exclusive. Barriers exclude incomplete fragments and unsafe
         * gaps; only actual contiguous token windows reach the index. */
        void scan(List<String> values, boolean[] barriers, boolean[] breaks,
                  int boundary, int joined, String alternative, WindowConsumer consumer) {
            if (termCount == 0) return;
            int pivot = joined >= 0 ? joined : boundary;
            int first = pivot < 0 ? 0 : Math.max(0, pivot-maxTokens+1);
            int limit = joined >= 0 ? joined+1 : boundary >= 0 ? boundary : values.size();
            for (int start = first; start < limit; start++) {
                if (Thread.currentThread().isInterrupted()) return;
                StringBuilder phrase = new StringBuilder();
                for (int i = start; i < values.size() && i-start < maxTokens; i++) {
                    if (barriers[i] || (i > start && breaks[i])) break;
                    if (phrase.length() > 0) phrase.append(' ');
                    phrase.append(values.get(i));
                    if (pivot >= 0 && i < pivot) continue;
                    int count = i-start+1;
                    if (!exact.containsKey(count)) continue;
                    String value = phrase.toString();
                    List<CompiledTerm> found = joined >= 0 ? exactCandidates(count, value) : candidates(count, value);
                    MatchHit.Kind kind = joined >= 0 ? MatchHit.Kind.LINE_JOIN : MatchHit.Kind.LITERAL;
                    if (joined >= 0 && !found.isEmpty() && alternative != null) {
                        StringBuilder alt = new StringBuilder();
                        for (int j = start; j <= i; j++) {
                            if (alt.length() > 0) alt.append(' ');
                            alt.append(j == joined ? alternative : values.get(j));
                        }
                        if (!exactCandidates(count, alt.toString()).isEmpty()) continue; // ambiguous
                    }
                    if (found.isEmpty() && joined < 0 && boundary < 0 && count == 1) {
                        found = repairedCandidates(value);
                        if (!found.isEmpty()) kind = MatchHit.Kind.OCR_REPAIR;
                    }
                    if (!found.isEmpty()) consumer.match(start, i+1, found, kind);
                }
            }
        }
    }

    public static SearchPlan compile(Context context, TopicMap map) {
        return compile(map, AppPrefs.ignoreDiacritics(context), AppPrefs.compareChars(context),
                AppPrefs.getMatchMode(context), AppPrefs.ocrRepair(context),
                AppPrefs.joinHyphenation(context), AppPrefs.searchNodeTitles(context));
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode) {
        return compile(map, strip, chars, mode, false, false, false);
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode, boolean repair) {
        return compile(map, strip, chars, mode, repair, false, false);
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode,
                              boolean repair, boolean join, boolean titles) {
        Map<Integer, TermIndex<CompiledTerm>> exact = new HashMap<>();
        TermIndex<CompiledTerm> approximate = new TermIndex<>();
        int max = 0, count = 0;
        if (map != null) for (TopicNode node : map.nodes) {
            if (!node.enabled) continue;
            List<String> terms = node.terms.isEmpty() && titles ? Collections.singletonList(node.title) : node.terms;
            Set<String> seen = new HashSet<>();
            for (String raw : terms) {
                List<String> tokens = LiteralText.values(raw, strip);
                if (tokens.isEmpty()) continue;
                String key = LiteralText.key(tokens, 0, tokens.size());
                if (!seen.add(key)) continue;
                CompiledTerm term = new CompiledTerm(raw, key, tokens.size(), node);
                exact.computeIfAbsent(tokens.size(), unused -> new TermIndex<>()).add(key, term);
                if (mode != AppPrefs.MatchMode.EXACT && tokens.size() == 1 &&
                        key.length() >= 3 && LiteralText.isAlphabetic(key)) {
                    String part = chars > 0 ? key.substring(0, Math.min(key.length(), Math.max(3, chars))) : key;
                    approximate.add(part, term);
                }
                max = Math.max(max, tokens.size());
                count++;
            }
        }
        return new SearchPlan(strip, mode, repair, join, exact, approximate, max, count);
    }

    public static List<MatchHit> find(Context context, Text text, TopicMap map) {
        return find(text, compile(context, map));
    }

    private static final class ElementSpan {
        final Text.Element element;
        final int start, end;
        ElementSpan(Text.Element element, int start) {
            this.element = element; this.start = start; end = start + element.getText().length();
        }

        RectF geometry(int from, int to) {
            Rect box = element.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0) return null;
            if (from == start && to == end) return new RectF(box);
            String raw = element.getText();
            int a = from - start, b = to - start;
            // Symbol coordinates are used only when their text can be aligned
            // exactly to the element. Never estimate a position by character count.
            List<Text.Symbol> symbols = element.getSymbols();
            if (symbols != null && !symbols.isEmpty()) {
                List<String> values = new ArrayList<>(symbols.size());
                for (Text.Symbol symbol : symbols) values.add(symbol.getText());
                int[] offsets = LiteralText.align(raw, values);
                if (offsets != null) {
                    RectF union = null;
                    boolean valid = true;
                    int coveredStart = Integer.MAX_VALUE, coveredEnd = -1;
                    for (int i = 0; i < symbols.size(); i++) {
                        int x = offsets[i], y = x + values.get(i).length();
                        if (y <= a || x >= b) continue;
                        Rect symbolBox = symbols.get(i).getBoundingBox();
                        if (x < a || y > b || symbolBox == null || symbolBox.width() <= 0 || symbolBox.height() <= 0) {
                            valid = false; break;
                        }
                        coveredStart = Math.min(coveredStart, x);
                        coveredEnd = Math.max(coveredEnd, y);
                        if (union == null) union = new RectF(symbolBox);
                        else union.union(new RectF(symbolBox));
                    }
                    if (valid && union != null && coveredStart == a && coveredEnd == b) {
                        // Reject symbol bounds in a different coordinate system.
                        RectF padded = new RectF(box);
                        padded.inset(-box.height()*0.25f, -box.height()*0.25f);
                        if (padded.contains(union)) return union;
                    }
                }
            }
            // A whole word with only surrounding punctuation may use its
            // element box. Never highlight a neighbouring word as a fallback.
            if (LiteralText.hasWord(raw.substring(a,b)) &&
                    !LiteralText.hasWord(raw.substring(0,a)) && !LiteralText.hasWord(raw.substring(b)))
                return new RectF(box);
            return null;
        }
    }

    private static final class LineData {
        final String raw;
        final Rect bounds;
        final List<LiteralText.Lexeme> lexemes;
        final List<ElementSpan> elements = new ArrayList<>();
        final List<Token> tokens = new ArrayList<>();
        int fragment = -1;

        LineData(Text.Line line, boolean strip) {
            raw = line.getText();
            bounds = line.getBoundingBox();
            lexemes = LiteralText.lex(raw, strip);
            List<Text.Element> source = line.getElements();
            List<String> values = new ArrayList<>(source.size());
            for (Text.Element e : source) values.add(e.getText());
            int[] offsets = LiteralText.align(raw, values);
            if (offsets == null) return;
            for (int i = 0; i < source.size(); i++) elements.add(new ElementSpan(source.get(i), offsets[i]));
            for (int i = 0; i < lexemes.size(); i++) {
                LiteralText.Lexeme l = lexemes.get(i);
                Token token = new Token(l.value, this, l.start, l.end);
                if (i > 0 && !LiteralText.separated(raw, lexemes.get(i-1), l)) token.breakBefore = true;
                tokens.add(token);
            }
            fragment = LiteralText.terminalFragment(raw, lexemes);
            if (fragment >= 0) {
                tokens.get(fragment).barrier = true;
                tokens.get(fragment+1).barrier = true;
            }
        }

        /** Each OCR element gets its own box so a phrase and a nested
         * single-word match can share exactly the correct coloured mark. */
        List<RectF> geometry(int from, int to) {
            List<RectF> out = new ArrayList<>();
            for (ElementSpan e : elements) {
                if (e.end <= from) continue;
                if (e.start >= to) break;
                RectF box = e.geometry(Math.max(from,e.start), Math.min(to,e.end));
                if (box == null) return null;
                if (!out.isEmpty()) {
                    RectF prev = out.get(out.size()-1);
                    float height = Math.max(prev.height(), box.height());
                    if (box.left-prev.right > height*2.5f ||
                            Math.abs(box.centerY()-prev.centerY()) > height*1.25f) return null;
                }
                out.add(box);
            }
            return out.isEmpty() ? null : out;
        }
    }

    private static final class Token {
        final String value;
        final LineData line;
        final int start, end;
        boolean barrier, breakBefore;
        Token left, right;
        Token(String value, LineData line, int start, int end) {
            this.value = value; this.line = line; this.start = start; this.end = end;
        }
    }

    private static boolean adjacent(LineData a, LineData b) {
        if (a == null || b == null || a.tokens.isEmpty() || b.tokens.isEmpty() ||
                a.bounds == null || b.bounds == null) return false;
        Rect x = a.bounds, y = b.bounds;
        return LiteralText.sameColumn(x.left,x.top,x.right,x.bottom,y.left,y.top,y.right,y.bottom);
    }

    public static List<MatchHit> find(Text text, SearchPlan plan) {
        List<MatchHit> hits = new ArrayList<>();
        if (text == null || plan == null || plan.termCount <= 0) return hits;
        Set<String> dedupe = new HashSet<>();
        long now = android.os.SystemClock.elapsedRealtime();
        for (Text.TextBlock block : text.getTextBlocks()) {
            List<LineData> lines = new ArrayList<>();
            for (Text.Line line : block.getLines()) {
                if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
                lines.add(new LineData(line, plan.stripDiacritics));
            }
            for (int i = 1; i < lines.size(); i++) {
                LineData a = lines.get(i-1), b = lines.get(i);
                if (adjacent(a,b) && a.fragment >= 0 && LiteralText.isAlphabetic(b.tokens.get(0).value))
                    b.tokens.get(0).barrier = true;
            }
            for (int i = 0; i < lines.size(); i++) {
                LineData current = lines.get(i);
                scan(current.tokens, -1,-1,null,plan,hits,dedupe,now);
                if (i == 0) continue;
                LineData before = lines.get(i-1);
                if (!adjacent(before,current)) continue;
                if (before.fragment >= 0) {
                    if (plan.joinEnabled) scanJoined(before,current,plan,hits,dedupe,now);
                } else {
                    List<Token> both = new ArrayList<>(before.tokens);
                    both.addAll(current.tokens);
                    scan(both,before.tokens.size(),-1,null,plan,hits,dedupe,now);
                }
            }
        }
        return hits;
    }

    private static void scanJoined(LineData a, LineData b, SearchPlan plan,
                                   List<MatchHit> hits, Set<String> dedupe, long now) {
        Token left = a.tokens.get(a.fragment), hyphen = a.tokens.get(a.fragment+1), right = b.tokens.get(0);
        List<String> variants = OcrRepair.joinedVariants(left.value+"-",right.value);
        if (variants.size() != 2) return;
        for (int i = 0; i < variants.size(); i++) {
            List<Token> both = new ArrayList<>(a.tokens.subList(0,a.fragment));
            Token merged = new Token(variants.get(i),a,left.start,hyphen.end);
            merged.left = new Token(left.value+"-",a,left.start,hyphen.end);
            merged.right = right;
            merged.breakBefore = left.breakBefore;
            int at = both.size();
            both.add(merged);
            both.addAll(b.tokens.subList(1,b.tokens.size()));
            scan(both,-1,at,variants.get(1-i),plan,hits,dedupe,now);
        }
    }

    private static void scan(List<Token> tokens, int boundary, int joined, String alternative,
                             SearchPlan plan, List<MatchHit> hits, Set<String> dedupe, long now) {
        List<String> values = new ArrayList<>(tokens.size());
        boolean[] barriers = new boolean[tokens.size()], breaks = new boolean[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            values.add(t.value);
            barriers[i] = t.barrier;
            breaks[i] = t.breakBefore;
        }
        plan.scan(values,barriers,breaks,boundary,joined,alternative,
                (start,end,terms,kind) -> emit(tokens,start,end,terms,kind,hits,dedupe,now));
    }

    private static void emit(List<Token> tokens, int start, int end, List<CompiledTerm> terms, MatchHit.Kind kind,
                             List<MatchHit> hits, Set<String> dedupe, long now) {
        List<Token> evidence = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Token t = tokens.get(i);
            if (t.left == null) evidence.add(t);
            else { evidence.add(t.left); evidence.add(t.right); }
        }
        List<RectF> geometry = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < evidence.size();) {
            Token first = evidence.get(i);
            int j = i+1;
            while (j < evidence.size() && evidence.get(j).line == first.line) j++;
            int to = evidence.get(j-1).end;
            List<RectF> boxes = first.line.geometry(first.start,to);
            if (boxes == null) return;
            geometry.addAll(boxes);
            if (raw.length() > 0) raw.append('\n');
            raw.append(first.line.raw,first.start,to);
            i = j;
        }
        String original = raw.toString();
        for (CompiledTerm term : terms) {
            StringBuilder key = new StringBuilder(term.node.identityKey()).append('|').append(term.raw);
            for (RectF rect : geometry) key.append('|').append(rect.left).append(':').append(rect.top)
                    .append(':').append(rect.right).append(':').append(rect.bottom);
            if (dedupe.add(key.toString())) hits.add(new MatchHit(geometry,original,term.raw,term.node,now,kind,false));
        }
    }

    /** Compatibility helper used for page anchors and not for matching. */
    public static String normalize(String value, boolean strip) {
        StringBuilder result = new StringBuilder();
        for (LiteralText.Lexeme token : LiteralText.lex(value,strip)) if (token.word) {
            if (result.length() > 0) result.append(' ');
            result.append(token.value);
        }
        return result.toString();
    }
    static List<String> tokenize(String original, boolean strip) {
        List<String> out = new ArrayList<>();
        for (LiteralText.Lexeme t : LiteralText.lex(original,strip)) if (t.word) out.add(t.value);
        return out;
    }

    public static boolean matches(String actual, String term, AppPrefs.MatchMode mode, int compareChars) {
        if (actual == null || term == null) return false;
        if (actual.equals(term)) return true;
        if (mode == AppPrefs.MatchMode.EXACT || !LiteralText.isAlphabetic(actual) ||
                !LiteralText.isAlphabetic(term) || term.length() < 3) return false;
        String t = compareChars > 0 ? term.substring(0,Math.min(term.length(),Math.max(3,compareChars))) : term;
        return mode == AppPrefs.MatchMode.PREFIX ? actual.startsWith(t) : actual.contains(t);
    }
}
