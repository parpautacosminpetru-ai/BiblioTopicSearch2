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

    private static final String PUNCTUATION = ".,;:?!…—–()[]„”«»\"";

    private static final class CompiledTerm {
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
     * Punctuația este păstrată separat și inspectată pe textul OCR brut, înainte de normalizare.
     */
    public static final class SearchPlan {
        private final boolean stripDiacritics;
        private final int compareChars;
        private final AppPrefs.MatchMode mode;
        private final Map<Integer, List<CompiledTerm>> termsByTokenCount;
        private final Map<Integer, Map<Character, List<CompiledTerm>>> termsByFirstChar;
        private final List<Integer> tokenCounts;
        private final int termCount;
        private final TopicNode punctuationNode;

        private SearchPlan(
                boolean stripDiacritics,
                int compareChars,
                AppPrefs.MatchMode mode,
                Map<Integer, List<CompiledTerm>> termsByTokenCount,
                Map<Integer, Map<Character, List<CompiledTerm>>> termsByFirstChar,
                List<Integer> tokenCounts,
                int termCount,
                TopicNode punctuationNode
        ) {
            this.stripDiacritics = stripDiacritics;
            this.compareChars = compareChars;
            this.mode = mode;
            this.termsByTokenCount = termsByTokenCount;
            this.termsByFirstChar = termsByFirstChar;
            this.tokenCounts = tokenCounts;
            this.termCount = termCount;
            this.punctuationNode = punctuationNode;
        }

        public int termCount() {
            return termCount;
        }

        private List<CompiledTerm> candidates(int tokenCount, String actual) {
            List<CompiledTerm> all = termsByTokenCount.get(tokenCount);
            if (all == null || all.isEmpty() || actual == null || actual.isEmpty()) {
                return Collections.emptyList();
            }

            if (mode != AppPrefs.MatchMode.CONTAINS && mode != AppPrefs.MatchMode.FLEXIBLE) {
                Map<Character, List<CompiledTerm>> byChar = termsByFirstChar.get(tokenCount);
                if (byChar == null) return Collections.emptyList();
                List<CompiledTerm> candidates = byChar.get(actual.charAt(0));
                return candidates == null ? Collections.emptyList() : candidates;
            }

            return all;
        }
    }

    public static SearchPlan compile(Context context, TopicMap map) {
        boolean stripDiacritics = AppPrefs.ignoreDiacritics(context);
        int compareChars = AppPrefs.compareChars(context);
        AppPrefs.MatchMode mode = AppPrefs.getMatchMode(context);

        Map<Integer, List<CompiledTerm>> byCount = new HashMap<>();
        Map<Integer, Map<Character, List<CompiledTerm>>> byFirstChar = new HashMap<>();
        Set<Integer> counts = new HashSet<>();
        int termCount = 0;
        TopicNode punctuationNode = null;

        if (map != null) {
            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;

                // Special built-in structural detector. It must never pass through normalize(),
                // because normalize intentionally removes punctuation for lexical search.
                if (BuiltInMaps.isPunctuationNode(node)) {
                    punctuationNode = node;
                    termCount++;
                    continue;
                }

                List<String> searchTerms = node.terms.isEmpty()
                        ? Collections.singletonList(node.title)
                        : node.terms;

                for (String rawTerm : searchTerms) {
                    String normalized = normalize(rawTerm, stripDiacritics);
                    if (normalized.isEmpty()) continue;

                    int tokenCount = countTokens(normalized);
                    CompiledTerm term = new CompiledTerm(rawTerm, normalized, tokenCount, node);
                    byCount.computeIfAbsent(tokenCount, ignored -> new ArrayList<>()).add(term);
                    byFirstChar
                            .computeIfAbsent(tokenCount, ignored -> new HashMap<>())
                            .computeIfAbsent(normalized.charAt(0), ignored -> new ArrayList<>())
                            .add(term);
                    counts.add(tokenCount);
                    termCount++;
                }
            }
        }

        List<Integer> tokenCounts = new ArrayList<>(counts);
        Collections.sort(tokenCounts);
        return new SearchPlan(
                stripDiacritics,
                compareChars,
                mode,
                byCount,
                byFirstChar,
                tokenCounts,
                termCount,
                punctuationNode
        );
    }

    /** Compatibilitate pentru apelurile vechi. MainActivity folosește planul compilat. */
    public static List<MatchHit> find(Context context, Text text, TopicMap map) {
        return find(text, compile(context, map));
    }

    public static List<MatchHit> find(Text text, SearchPlan plan) {
        List<MatchHit> hits = new ArrayList<>();
        if (text == null || plan == null || plan.termCount <= 0) return hits;

        long now = System.currentTimeMillis();
        Set<String> dedupe = new HashSet<>();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                List<Text.Element> elements = line.getElements();
                if (elements == null || elements.isEmpty()) continue;

                int size = elements.size();
                String[] normalizedElements = new String[size];
                String[] originalElements = new String[size];
                RectF[] boxes = new RectF[size];

                for (int i = 0; i < size; i++) {
                    Text.Element element = elements.get(i);
                    originalElements[i] = element.getText();
                    normalizedElements[i] = normalize(element.getText(), plan.stripDiacritics);
                    Rect box = element.getBoundingBox();
                    boxes[i] = box == null ? null : new RectF(box);
                }

                // Structural punctuation scan on the raw OCR token, before punctuation is stripped.
                if (plan.punctuationNode != null) {
                    for (int i = 0; i < size; i++) {
                        if (boxes[i] == null || originalElements[i] == null) continue;
                        addPunctuationHits(
                                hits,
                                dedupe,
                                boxes[i],
                                originalElements[i],
                                plan.punctuationNode,
                                now
                        );
                    }
                }

                if (plan.termsByTokenCount.containsKey(1)) {
                    for (int i = 0; i < size; i++) {
                        if (boxes[i] == null) continue;
                        String actual = normalizedElements[i];
                        if (actual == null || actual.isEmpty()) continue;
                        for (CompiledTerm term : plan.candidates(1, actual)) {
                            if (matches(actual, term.normalized, plan.mode, plan.compareChars)) {
                                addHit(
                                        hits,
                                        dedupe,
                                        new RectF(boxes[i]),
                                        originalElements[i],
                                        term.raw,
                                        term.node,
                                        now
                                );
                            }
                        }
                    }
                }

                for (int tokenCount : plan.tokenCounts) {
                    if (tokenCount <= 1 || tokenCount > size) continue;

                    for (int start = 0; start + tokenCount <= size; start++) {
                        StringBuilder phrase = new StringBuilder();
                        StringBuilder original = new StringBuilder();
                        RectF union = null;
                        boolean valid = true;

                        for (int i = start; i < start + tokenCount; i++) {
                            if (boxes[i] == null) {
                                valid = false;
                                break;
                            }
                            if (phrase.length() > 0) {
                                phrase.append(' ');
                                original.append(' ');
                            }
                            phrase.append(normalizedElements[i]);
                            original.append(originalElements[i]);
                            if (union == null) union = new RectF(boxes[i]);
                            else union.union(boxes[i]);
                        }

                        if (!valid || union == null) continue;
                        String actualPhrase = phrase.toString().trim();
                        if (actualPhrase.isEmpty()) continue;

                        for (CompiledTerm term : plan.candidates(tokenCount, actualPhrase)) {
                            if (matches(actualPhrase, term.normalized, plan.mode, plan.compareChars)) {
                                addHit(
                                        hits,
                                        dedupe,
                                        union,
                                        original.toString(),
                                        term.raw,
                                        term.node,
                                        now
                                );
                            }
                        }
                    }
                }
            }
        }

        return hits;
    }

    private static void addPunctuationHits(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String rawToken,
            TopicNode node,
            long timestamp
    ) {
        if (rawToken.contains("...")) {
            addHit(hits, dedupe, new RectF(box), "...", "...", node, timestamp);
        }
        if (rawToken.indexOf('…') >= 0) {
            addHit(hits, dedupe, new RectF(box), "…", "…", node, timestamp);
        }

        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < rawToken.length(); i++) {
            char c = rawToken.charAt(i);
            if (c == '.' && rawToken.contains("...")) continue;
            if (PUNCTUATION.indexOf(c) < 0 || !seen.add(c)) continue;
            String mark = String.valueOf(c);
            addHit(hits, dedupe, new RectF(box), mark, mark, node, timestamp);
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

    private static void addHit(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String original,
            String searchTerm,
            TopicNode node,
            long timestamp
    ) {
        int cx = Math.round(box.centerX() / 4f);
        int cy = Math.round(box.centerY() / 4f);
        String key = node.path + "|" + searchTerm + "|" + cx + "|" + cy;
        if (dedupe.add(key)) {
            hits.add(new MatchHit(box, original, searchTerm, node, timestamp));
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

    public static String normalize(String value, boolean stripDiacritics) {
        if (value == null) return "";
        String out = value.toLowerCase(Locale.ROOT).trim();

        if (stripDiacritics) {
            out = Normalizer.normalize(out, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
        }

        out = out
                .replaceAll("[^\\p{L}\\p{N}\\s'-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return out;
    }
}
