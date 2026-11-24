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

        private SearchPlan(boolean stripDiacritics, int compareChars, AppPrefs.MatchMode mode,
                           Map<Integer, TermIndex<CompiledTerm>> indexes, List<Integer> tokenCounts, int termCount) {
            this.stripDiacritics = stripDiacritics;
            this.compareChars = compareChars;
            this.mode = mode;
            this.indexes = indexes;
            this.tokenCounts = tokenCounts;
            this.termCount = termCount;
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
    }

    private static String shorten(String term, int length) {
        return length > 0 && term.length() > length ? term.substring(0, length) : term;
    }

    public static SearchPlan compile(Context context, TopicMap map) {
        return compile(map, AppPrefs.ignoreDiacritics(context), AppPrefs.compareChars(context),
                AppPrefs.getMatchMode(context));
    }

    static SearchPlan compile(TopicMap map, boolean stripDiacritics, int compareChars, AppPrefs.MatchMode mode) {
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
                    if (!seen.add(count + ":" + key)) continue;
                    CompiledTerm term = new CompiledTerm(raw, normalized, count, node);
                    indexes.computeIfAbsent(count, ignored -> new TermIndex<>()).add(key, term);
                    termCount++;
                }
            }
        }
        List<Integer> counts = new ArrayList<>(indexes.keySet());
        Collections.sort(counts);
        return new SearchPlan(stripDiacritics, compareChars, mode, indexes, counts, termCount);
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

                // Fiecare cuvânt OCR este normalizat o singură dată pe cadru.
                // În versiunea veche era normalizat din nou pentru fiecare termen din hartă.
                for (int i = 0; i < size; i++) {
                    Text.Element element = elements.get(i);
                    originalElements[i] = element.getText();
                    normalizedElements[i] = normalize(element.getText(), plan.stripDiacritics);
                    Rect box = element.getBoundingBox();
                    boxes[i] = box == null ? null : new RectF(box);
                }

                // Termeni de un singur cuvânt.
                if (plan.indexes.containsKey(1)) {
                    for (int i = 0; i < size; i++) {
                        if (boxes[i] == null) continue;
                        String actual = normalizedElements[i];
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

                // Expresiile cu mai multe cuvinte sunt construite o singură dată
                // pentru fiecare fereastră, apoi comparate cu termenii de aceeași lungime.
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
        String key = node.identityKey() + "|" + searchTerm + "|" + cx + "|" + cy;
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
