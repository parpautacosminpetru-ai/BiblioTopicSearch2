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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live OCR topic matcher with a lightweight, fully offline semantic layer.
 *
 * Direct topic terms keep their original behavior. During compilation we also
 * expand every mapped term with synonyms from DictionaryStore. A synonym hit is
 * emitted against the original topic term and carries a semantic relevance score
 * so OverlayView can render it as a stronger/weaker "echo" without changing the
 * camera/OCR pipeline.
 *
 * This is intentionally local: no network request, remote LLM, or camera upload.
 */
public final class TopicMatcher {
    private TopicMatcher() {}

    private static final float DIRECT_RELEVANCE = 1.00f;
    private static final float SYNONYM_RELEVANCE = 0.88f;
    private static final int MAX_SYNONYMS_PER_TERM = 24;

    private static final class CompiledTerm {
        /** The concept term displayed to the user. */
        final String raw;
        /** The actual surface form searched in OCR text. */
        final String surface;
        final String normalized;
        final int tokenCount;
        final TopicNode node;
        final boolean semantic;
        final float baseRelevance;
        final String semanticCategory;

        CompiledTerm(
                String raw,
                String surface,
                String normalized,
                int tokenCount,
                TopicNode node,
                boolean semantic,
                float baseRelevance,
                String semanticCategory
        ) {
            this.raw = raw;
            this.surface = surface;
            this.normalized = normalized;
            this.tokenCount = tokenCount;
            this.node = node;
            this.semantic = semantic;
            this.baseRelevance = baseRelevance;
            this.semanticCategory = semanticCategory;
        }
    }

    /**
     * Planul de căutare este construit o singură dată când harta/setările se schimbă.
     * Include termenii direcți și extensiile semantice locale (sinonime).
     */
    public static final class SearchPlan {
        private final boolean stripDiacritics;
        private final int compareChars;
        private final AppPrefs.MatchMode mode;
        private final Map<Integer, List<CompiledTerm>> termsByTokenCount;
        private final Map<Integer, Map<Character, List<CompiledTerm>>> termsByFirstChar;
        private final List<Integer> tokenCounts;
        private final int termCount;
        private final int semanticVariantCount;

        private SearchPlan(
                boolean stripDiacritics,
                int compareChars,
                AppPrefs.MatchMode mode,
                Map<Integer, List<CompiledTerm>> termsByTokenCount,
                Map<Integer, Map<Character, List<CompiledTerm>>> termsByFirstChar,
                List<Integer> tokenCounts,
                int termCount,
                int semanticVariantCount
        ) {
            this.stripDiacritics = stripDiacritics;
            this.compareChars = compareChars;
            this.mode = mode;
            this.termsByTokenCount = termsByTokenCount;
            this.termsByFirstChar = termsByFirstChar;
            this.tokenCounts = tokenCounts;
            this.termCount = termCount;
            this.semanticVariantCount = semanticVariantCount;
        }

        public int termCount() {
            return termCount;
        }

        public int semanticVariantCount() {
            return semanticVariantCount;
        }

        private List<CompiledTerm> candidates(int tokenCount, String actual) {
            List<CompiledTerm> all = termsByTokenCount.get(tokenCount);
            if (all == null || all.isEmpty() || actual == null || actual.isEmpty()) {
                return Collections.emptyList();
            }

            // Pentru EXACT/PREFIX primul caracter trebuie să coincidă. Reducem mult
            // numărul de comparații când harta conține sute/mii de termeni și sinonime.
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
        Set<String> compiledKeys = new HashSet<>();
        Map<String, DictionaryStore.Entry> dictionaryCache = new HashMap<>();
        Set<String> dictionaryMisses = new HashSet<>();
        int termCount = 0;
        int semanticVariantCount = 0;

        DictionaryStore dictionary = new DictionaryStore(context.getApplicationContext());
        try {
            if (map != null) {
                for (TopicNode node : map.nodes) {
                    if (!node.enabled) continue;

                    List<String> searchTerms = node.terms.isEmpty()
                            ? Collections.singletonList(node.title)
                            : node.terms;

                    for (String rawTerm : searchTerms) {
                        String normalized = normalize(rawTerm, stripDiacritics);
                        if (normalized.isEmpty()) continue;
                        termCount++;

                        if (addVariant(
                                byCount, byFirstChar, counts, compiledKeys,
                                rawTerm, rawTerm, normalized, node,
                                false, DIRECT_RELEVANCE, "DIRECT"
                        )) {
                            // direct term added
                        }

                        DictionaryStore.Entry entry = cachedLookup(
                                dictionary, rawTerm, dictionaryCache, dictionaryMisses
                        );
                        if (entry == null || entry.synonyms == null || entry.synonyms.trim().isEmpty()) {
                            continue;
                        }

                        int addedForTerm = 0;
                        for (String synonym : splitSemanticValues(entry.synonyms)) {
                            if (addedForTerm >= MAX_SYNONYMS_PER_TERM) break;
                            String normalizedSynonym = normalize(synonym, stripDiacritics);
                            if (normalizedSynonym.isEmpty() || normalizedSynonym.equals(normalized)) continue;

                            if (addVariant(
                                    byCount, byFirstChar, counts, compiledKeys,
                                    rawTerm, synonym, normalizedSynonym, node,
                                    true, SYNONYM_RELEVANCE, "SINONIM"
                            )) {
                                semanticVariantCount++;
                                addedForTerm++;
                            }
                        }
                    }
                }
            }
        } finally {
            dictionary.close();
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
                semanticVariantCount
        );
    }

    private static DictionaryStore.Entry cachedLookup(
            DictionaryStore store,
            String term,
            Map<String, DictionaryStore.Entry> cache,
            Set<String> misses
    ) {
        String key = normalize(term, true);
        if (key.isEmpty() || misses.contains(key)) return null;
        DictionaryStore.Entry cached = cache.get(key);
        if (cached != null) return cached;

        DictionaryStore.Entry entry = store.lookup(term);
        if (entry == null) misses.add(key);
        else cache.put(key, entry);
        return entry;
    }

    private static boolean addVariant(
            Map<Integer, List<CompiledTerm>> byCount,
            Map<Integer, Map<Character, List<CompiledTerm>>> byFirstChar,
            Set<Integer> counts,
            Set<String> compiledKeys,
            String raw,
            String surface,
            String normalized,
            TopicNode node,
            boolean semantic,
            float relevance,
            String category
    ) {
        int tokenCount = countTokens(normalized);
        String key = node.path + '|' + normalize(raw, true) + '|' + normalized;
        if (!compiledKeys.add(key)) return false;

        CompiledTerm term = new CompiledTerm(
                raw,
                surface,
                normalized,
                tokenCount,
                node,
                semantic,
                relevance,
                category
        );
        byCount.computeIfAbsent(tokenCount, ignored -> new ArrayList<>()).add(term);
        byFirstChar
                .computeIfAbsent(tokenCount, ignored -> new HashMap<>())
                .computeIfAbsent(normalized.charAt(0), ignored -> new ArrayList<>())
                .add(term);
        counts.add(tokenCount);
        return true;
    }

    private static List<String> splitSemanticValues(String values) {
        if (values == null || values.trim().isEmpty()) return Collections.emptyList();
        Set<String> unique = new LinkedHashSet<>();
        for (String piece : values.split("[,;|\\n]+")) {
            String clean = piece == null ? "" : piece.trim();
            if (!clean.isEmpty()) unique.add(clean);
        }
        return new ArrayList<>(unique);
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
                for (int i = 0; i < size; i++) {
                    Text.Element element = elements.get(i);
                    originalElements[i] = element.getText();
                    normalizedElements[i] = normalize(element.getText(), plan.stripDiacritics);
                    Rect box = element.getBoundingBox();
                    boxes[i] = box == null ? null : new RectF(box);
                }

                if (plan.termsByTokenCount.containsKey(1)) {
                    for (int i = 0; i < size; i++) {
                        if (boxes[i] == null) continue;
                        String actual = normalizedElements[i];
                        if (actual == null || actual.isEmpty()) continue;

                        for (CompiledTerm term : plan.candidates(1, actual)) {
                            float surfaceQuality = matchQuality(
                                    actual, term.normalized, plan.mode, plan.compareChars
                            );
                            if (surfaceQuality > 0f) {
                                addHit(
                                        hits,
                                        dedupe,
                                        new RectF(boxes[i]),
                                        originalElements[i],
                                        term,
                                        now,
                                        surfaceQuality
                                );
                            }
                        }
                    }
                }

                // Expresiile cu mai multe cuvinte sunt construite o singură dată
                // pentru fiecare fereastră, apoi comparate cu variantele de aceeași lungime.
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
                            float surfaceQuality = matchQuality(
                                    actualPhrase, term.normalized, plan.mode, plan.compareChars
                            );
                            if (surfaceQuality > 0f) {
                                addHit(
                                        hits,
                                        dedupe,
                                        union,
                                        original.toString(),
                                        term,
                                        now,
                                        surfaceQuality
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
            CompiledTerm term,
            long timestamp,
            float surfaceQuality
    ) {
        int cx = Math.round(box.centerX() / 4f);
        int cy = Math.round(box.centerY() / 4f);
        String key = term.node.path + "|" + term.raw + "|" + cx + "|" + cy;
        if (!dedupe.add(key)) return;

        float relevance = clamp01(term.baseRelevance * surfaceQuality);
        hits.add(new MatchHit(
                box,
                original,
                term.raw,
                term.node,
                timestamp,
                term.semantic,
                relevance,
                term.semanticCategory
        ));
    }

    public static boolean matches(
            String actual,
            String term,
            AppPrefs.MatchMode mode,
            int compareChars
    ) {
        return matchQuality(actual, term, mode, compareChars) > 0f;
    }

    private static float matchQuality(
            String actual,
            String term,
            AppPrefs.MatchMode mode,
            int compareChars
    ) {
        if (actual == null || term == null) return 0f;
        String a = actual.trim();
        String t = term.trim();
        if (a.isEmpty() || t.isEmpty()) return 0f;

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
                return a.equals(t) ? 1.00f : 0f;
            case CONTAINS:
                if (!a.contains(t)) return 0f;
                return a.equals(t) ? 1.00f : 0.91f;
            case FLEXIBLE:
                if (a.equals(t)) return 1.00f;
                if (a.startsWith(t)) return 0.96f;
                if (a.contains(t)) return 0.90f;
                return 0f;
            case PREFIX:
            default:
                if (!a.startsWith(t)) return 0f;
                return a.equals(t) ? 1.00f : 0.95f;
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

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
