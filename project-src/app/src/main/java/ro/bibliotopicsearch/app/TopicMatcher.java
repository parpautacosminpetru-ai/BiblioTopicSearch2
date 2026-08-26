package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TopicMatcher {
    private TopicMatcher() {}

    /**
     * Semantic sidecar used by the existing live OCR path. It intentionally has a
     * single worker and a busy gate: camera frames may arrive faster than semantic
     * detection, and low latency is more useful than building an unbounded queue.
     */
    private static final ExecutorService PARAGRAPH_DETECTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "biblio-subject-function");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    private static final AtomicBoolean PARAGRAPH_DETECTOR_BUSY = new AtomicBoolean(false);
    private static volatile List<UniversalParagraphDetector.Detection> latestParagraphDetections =
            Collections.emptyList();

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
        private final Map<String, TopicNode> punctuationNodes;
        private final List<String> punctuationMarks;

        private SearchPlan(
                boolean stripDiacritics,
                int compareChars,
                AppPrefs.MatchMode mode,
                Map<Integer, List<CompiledTerm>> termsByTokenCount,
                Map<Integer, Map<Character, List<CompiledTerm>>> termsByFirstChar,
                List<Integer> tokenCounts,
                int termCount,
                Map<String, TopicNode> punctuationNodes,
                List<String> punctuationMarks
        ) {
            this.stripDiacritics = stripDiacritics;
            this.compareChars = compareChars;
            this.mode = mode;
            this.termsByTokenCount = termsByTokenCount;
            this.termsByFirstChar = termsByFirstChar;
            this.tokenCounts = tokenCounts;
            this.termCount = termCount;
            this.punctuationNodes = punctuationNodes;
            this.punctuationMarks = punctuationMarks;
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
        Map<String, TopicNode> punctuationNodes = new HashMap<>();
        List<TopicNode> punctuationStyleNodes = new ArrayList<>();

        if (map != null) {
            // Built-in maps assign their visual defaults on every load. Reapply the
            // user's persisted TEXTUAL / SEMANTIC colors after those defaults exist.
            BuiltInColorStore.apply(context, map);

            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;

                List<String> punctuationMarks = PunctuationSupport.marksForNode(node);
                if (!punctuationMarks.isEmpty()) {
                    punctuationStyleNodes.add(node);
                    for (String mark : punctuationMarks) {
                        String raw = mark == null ? "" : mark.trim();
                        if (raw.isEmpty()) continue;
                        if (!punctuationNodes.containsKey(raw)) {
                            punctuationNodes.put(raw, node);
                            termCount++;
                        }
                    }
                }

                List<String> searchTerms = node.terms.isEmpty()
                        ? Collections.singletonList(node.title)
                        : node.terms;

                for (String rawTerm : searchTerms) {
                    if (!punctuationMarks.isEmpty()
                            && (node.terms.isEmpty() || PunctuationSupport.isPunctuationTerm(rawTerm))) {
                        continue;
                    }

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

        PunctuationSupport.ensureDistinctColors(punctuationStyleNodes);

        List<Integer> tokenCounts = new ArrayList<>(counts);
        Collections.sort(tokenCounts);

        List<String> punctuationMarks = new ArrayList<>(punctuationNodes.keySet());
        punctuationMarks.sort(
                Comparator.comparingInt(String::length)
                        .reversed()
                        .thenComparing(Comparator.naturalOrder())
        );

        return new SearchPlan(
                stripDiacritics,
                compareChars,
                mode,
                byCount,
                byFirstChar,
                tokenCounts,
                termCount,
                punctuationNodes,
                punctuationMarks
        );
    }

    /** Compatibilitate pentru apelurile vechi. MainActivity folosește planul compilat. */
    public static List<MatchHit> find(Context context, Text text, TopicMap map) {
        return find(text, compile(context, map));
    }

    /**
     * Existing public live-search entry point. Subject/function detection is now
     * automatically scheduled in parallel, so MainActivity does not need to be
     * rewritten to activate the new detector.
     */
    public static List<MatchHit> find(Text text, SearchPlan plan) {
        scheduleParagraphDetection(text);
        return findLexicalOnly(text, plan);
    }

    /**
     * Lexical/punctuation branch without scheduling the semantic sidecar. Package
     * visible so ParallelTextDetectionEngine can combine both branches without
     * executing subject/function detection twice.
     */
    static List<MatchHit> findLexicalOnly(Text text, SearchPlan plan) {
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

                // Structural punctuation scan on raw OCR tokens, including custom maps
                // and multi-character punctuation sequences.
                if (!plan.punctuationNodes.isEmpty()) {
                    for (int i = 0; i < size; i++) {
                        if (boxes[i] == null || originalElements[i] == null) continue;
                        addPunctuationHits(
                                hits,
                                dedupe,
                                boxes[i],
                                originalElements[i],
                                plan.punctuationNodes,
                                plan.punctuationMarks,
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

    /** Latest automatic detections from the live OCR sidecar, in TextBlock order. */
    public static List<UniversalParagraphDetector.Detection> latestParagraphDetections() {
        return latestParagraphDetections;
    }

    /** Convenience accessor for overlays/debug panels that only need one candidate. */
    public static UniversalParagraphDetector.Detection strongestLatestParagraph() {
        UniversalParagraphDetector.Detection best = null;
        double bestScore = -1.0;
        for (UniversalParagraphDetector.Detection detection : latestParagraphDetections) {
            double score = detection.subjectConfidence() * 0.55
                    + detection.functionConfidence() * 0.45;
            if (score > bestScore) {
                bestScore = score;
                best = detection;
            }
        }
        return best;
    }

    private static void scheduleParagraphDetection(Text text) {
        if (text == null || text.getTextBlocks() == null || text.getTextBlocks().isEmpty()) {
            latestParagraphDetections = Collections.emptyList();
            return;
        }
        if (!PARAGRAPH_DETECTOR_BUSY.compareAndSet(false, true)) {
            return;
        }

        final List<String> blocks = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            if (block == null) continue;
            String value = block.getText();
            if (value != null && !value.trim().isEmpty()) blocks.add(value.trim());
        }

        if (blocks.isEmpty()) {
            PARAGRAPH_DETECTOR_BUSY.set(false);
            latestParagraphDetections = Collections.emptyList();
            return;
        }

        PARAGRAPH_DETECTOR.execute(() -> {
            try {
                List<UniversalParagraphDetector.Detection> detections = new ArrayList<>(blocks.size());
                for (int i = 0; i < blocks.size(); i++) {
                    detections.add(UniversalParagraphDetector.detect(blocks.get(i), i));
                }
                latestParagraphDetections = Collections.unmodifiableList(detections);
            } catch (RuntimeException ignored) {
                // Live OCR must never fail because the semantic sidecar encountered
                // an unexpected input. Preserve the last valid detections instead.
            } finally {
                PARAGRAPH_DETECTOR_BUSY.set(false);
            }
        });
    }

    private static void addPunctuationHits(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String rawToken,
            Map<String, TopicNode> punctuationNodes,
            List<String> punctuationMarks,
            long timestamp
    ) {
        if (rawToken == null || rawToken.isEmpty() || punctuationMarks.isEmpty()) return;

        boolean[] occupied = new boolean[rawToken.length()];

        // Longest marks are scanned first, so "..." masks its dots and a custom
        // sequence like "?!" can win over its component characters when defined.
        for (String mark : punctuationMarks) {
            if (mark == null || mark.isEmpty()) continue;
            TopicNode node = punctuationNodes.get(mark);
            if (node == null) continue;

            int from = 0;
            while (from <= rawToken.length() - mark.length()) {
                int index = rawToken.indexOf(mark, from);
                if (index < 0) break;

                int end = index + mark.length();
                boolean free = true;
                for (int i = index; i < end; i++) {
                    if (occupied[i]) {
                        free = false;
                        break;
                    }
                }

                if (free) {
                    addHit(
                            hits,
                            dedupe,
                            new RectF(box),
                            mark,
                            mark,
                            node,
                            timestamp
                    );
                    for (int i = index; i < end; i++) occupied[i] = true;
                }

                from = index + Math.max(1, mark.length());
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
