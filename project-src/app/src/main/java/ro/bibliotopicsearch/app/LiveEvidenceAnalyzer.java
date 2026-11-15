package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, deterministic inspection of LIVE OCR. Live evidence is provisional:
 * archival citations must still be verified against the original page image.
 *
 * Occurrence != co-occurrence != same-span thematic overlap. None proves a
 * historical or syntactic relationship. Exact OCR substrings are never rewritten.
 */
public final class LiveEvidenceAnalyzer {
    private LiveEvidenceAnalyzer() {}

    private static final int MAX_PAIRS = 400; // UI preview only; not archival coverage.
    private static final int MAX_CANDIDATES = 100;
    private static final Pattern YEAR =
            Pattern.compile("(?<![\\p{L}\\p{N}])(?:[1-9][0-9]{2,3})(?![\\p{L}\\p{N}])");
    private static final Pattern CENTURY = Pattern.compile(
            "(?iu)(?<!\\p{L})(?:secol(?:ul)?|sec\\.)\\s*(?:al\\s*)?(?:[IVXLCDM]{1,8}|[0-9]{1,2})(?:-lea|-a)?(?!\\p{L})");
    /**
     * Conservative patterns for an alphabetic back-of-book index. A single
     * trailing number without dot leaders is deliberately insufficient.
     * OCR candidates are never inserted into the canonical registry directly.
     */
    private static final Pattern PRINTED_INDEX = Pattern.compile(
            "(?m)^\\s*([\\p{L}][\\p{L}\\p{M}\\p{N} '\\-’]{2,80}?)\\s*(?:" +
                    "\\.{3,}\\s*\\d{1,4}(?:\\s*[,;–-]\\s*\\d{1,4}){0,25}" +
                    "|\\s+\\d{1,4}(?:\\s*[,;]\\s*\\d{1,4}){1,25})\\s*$");

    private static final Pattern EXPLICIT_CONNECTOR = Pattern.compile(
            "(?iu)\\b(?:și|si|dar|iar|sau|deoarece|fiindcă|fiindca|pentru că|pentru ca|deși|desi|întrucât|intrucat|dacă|daca|care|căruia|caruia|cărora|carora|nu|nici|fără|fara|între|intre|dintre)\\b");

    public static final class Spot {
        public final String indexId;
        public final String indexTitle;
        public final String canonicalTerm;
        public final String original;
        public final int block;
        public final int sentence;
        public final int start;
        public final int end;
        public final String quote;
        public final DeterministicIndexIntersectionEngine.MatchKind matchKind;

        Spot(DeterministicMultiIndexMatcher.Occurrence o, int block, int sentence, String quote) {
            this.indexId = o.indexId();
            this.indexTitle = o.title();
            this.canonicalTerm = o.canonicalTerm();
            this.original = o.original();
            this.block = block;
            this.sentence = sentence;
            this.start = o.start();
            this.end = o.end();
            this.quote = quote;
            this.matchKind = o.matchKind();
        }

        public String position() {
            return "bloc " + (block + 1) + ", propoziție " + (sentence + 1)
                    + ", caractere " + start + "–" + end;
        }
    }

    public static final class Group {
        public final String indexId;
        public final String title;
        public final List<Spot> occurrences;

        private Group(String indexId, String title, List<Spot> occurrences) {
            this.indexId = indexId;
            this.title = title;
            this.occurrences = Collections.unmodifiableList(new ArrayList<>(occurrences));
        }

        public int count() { return occurrences.size(); }
    }

    public static final class Pair {
        public final Spot left;
        public final Spot right;
        public final String quote;
        public final String exactBetween;
        public final String visibleConnector;
        public final boolean thematicOverlap;

        private Pair(Spot left, Spot right, String quote, String exactBetween, String visibleConnector,
                     boolean thematicOverlap) {
            this.left = left;
            this.right = right;
            this.quote = quote;
            this.exactBetween = exactBetween;
            this.visibleConnector = visibleConnector;
            this.thematicOverlap = thematicOverlap;
        }

        public String label() {
            return (thematicOverlap ? "SUPRAPUNERE" : "COOCURENȚĂ") + ": "
                    + left.indexTitle + " / " + right.indexTitle;
        }
    }

    public static final class Candidate {
        public final String type;
        public final String original;
        public final String quote;
        public final int block;
        public final int start;
        public final int end;

        private Candidate(String type, String original, String quote, int block, int start, int end) {
            this.type = type;
            this.original = original;
            this.quote = quote;
            this.block = block;
            this.start = start;
            this.end = end;
        }
    }

    public static final class Snapshot {
        public final List<Group> groups;
        public final List<Pair> pairs;
        public final List<Candidate> candidates;
        public final int occurrences;
        public final boolean pairsLimited;
        public final String warning;

        private Snapshot(List<Group> groups, List<Pair> pairs, List<Candidate> candidates,
                         int occurrences, boolean pairsLimited, String warning) {
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.pairs = Collections.unmodifiableList(new ArrayList<>(pairs));
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.occurrences = occurrences;
            this.pairsLimited = pairsLimited;
            this.warning = warning;
        }

        public int overlaps() {
            int count = 0;
            for (Pair pair : pairs) if (pair.thematicOverlap) count++;
            return count;
        }

        public int cooccurrences() { return pairs.size() - overlaps(); }
    }

    private static final class Sentence {
        final int start, end, number;
        Sentence(int start, int end, int number) {
            this.start = start; this.end = end; this.number = number;
        }
    }

    /**
     * Source blocks must be the original TextBlock.getText() values. No
     * paraphrase, stemming of quoted text or inferred syntactic attachments.
     */
    public static Snapshot inspect(
            List<String> rawBlocks,
            DeterministicMultiIndexMatcher.Plan plan
    ) {
        if (rawBlocks == null || rawBlocks.isEmpty()) {
            return new Snapshot(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), 0, false, "Niciun text OCR.");
        }

        Map<String, List<Spot>> grouped = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        List<Pair> pairs = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        boolean limited = false;
        int total = 0;

        for (int blockIndex = 0; blockIndex < rawBlocks.size(); blockIndex++) {
            String raw = rawBlocks.get(blockIndex) == null ? "" : rawBlocks.get(blockIndex);
            if (raw.isEmpty()) continue;
            List<Sentence> sentences = sentences(raw);
            List<DeterministicMultiIndexMatcher.Occurrence> matches =
                    DeterministicMultiIndexMatcher.allOccurrences(plan, raw);
            total += matches.size();
            Map<Integer, List<Spot>> bySentence = new LinkedHashMap<>();

            for (DeterministicMultiIndexMatcher.Occurrence o : matches) {
                Sentence sentence = locate(sentences, o.start(), o.end());
                if (sentence == null) continue;
                String quote = raw.substring(sentence.start, sentence.end);
                Spot spot = new Spot(o, blockIndex, sentence.number, quote);
                grouped.computeIfAbsent(o.indexId(), key -> new ArrayList<>()).add(spot);
                titles.putIfAbsent(o.indexId(), o.title());
                bySentence.computeIfAbsent(sentence.number, ignored -> new ArrayList<>()).add(spot);
            }

            // Only same-sentence, independent indices are paired. If punctuation
            // is uncertain, a result may be missed, but no relationship is invented.
            for (Sentence sentence : sentences) {
                List<Spot> spots = bySentence.get(sentence.number);
                if (spots == null) continue;
                spots.sort(Comparator.comparingInt((Spot s) -> s.start).thenComparing(s -> s.indexId));
                for (int i = 0; i < spots.size(); i++) {
                    for (int j = i + 1; j < spots.size(); j++) {
                        Spot a = spots.get(i);
                        Spot b = spots.get(j);
                        if (a.indexId.equals(b.indexId)) continue; // Repetition, not crossing.
                        if (pairs.size() >= MAX_PAIRS) { limited = true; break; }
                        boolean overlap = a.start < b.end && b.start < a.end;
                        String exactBetween = overlap ? "" : raw.substring(a.end, b.start);
                        Matcher connector = EXPLICIT_CONNECTOR.matcher(exactBetween);
                        String visibleConnector = connector.find() ? connector.group() : "";
                        pairs.add(new Pair(a, b, raw.substring(sentence.start, sentence.end),
                                exactBetween, visibleConnector, overlap));
                    }
                    if (limited) break;
                }
            }

            if (candidates.size() < MAX_CANDIDATES) {
                addCandidates("POSIBIL AN", YEAR, raw, blockIndex, sentences, matches, candidates);
                addCandidates("SECOL EXPLICIT", CENTURY, raw, blockIndex, sentences, matches, candidates);
                addPrintedIndexCandidates(raw, blockIndex, matches, candidates);
            }
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<Spot>> entry : grouped.entrySet()) {
            groups.add(new Group(entry.getKey(), titles.get(entry.getKey()), entry.getValue()));
        }
        groups.sort(Comparator.comparing((Group g) -> g.title == null ? "" : g.title)
                .thenComparing(g -> g.indexId));
        return new Snapshot(groups, pairs, candidates, total, limited,
                "OCR LIVE este orientativ. Verifică imaginea originală înainte de citare.");
    }

    private static void addCandidates(
            String type, Pattern pattern, String raw, int block, List<Sentence> sentences,
            List<DeterministicMultiIndexMatcher.Occurrence> known, List<Candidate> out
    ) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find() && out.size() < MAX_CANDIDATES) {
            boolean alreadyIndexed = false;
            for (DeterministicMultiIndexMatcher.Occurrence hit : known) {
                if (hit.start() <= matcher.start() && hit.end() >= matcher.end()) {
                    alreadyIndexed = true;
                    break;
                }
            }
            if (alreadyIndexed) continue;
            Sentence sentence = locate(sentences, matcher.start(), matcher.end());
            if (sentence != null) {
                out.add(new Candidate(type, matcher.group(),
                        raw.substring(sentence.start, sentence.end), block, matcher.start(), matcher.end()));
            }
        }
    }

    private static void addPrintedIndexCandidates(
            String raw, int blockIndex,
            List<DeterministicMultiIndexMatcher.Occurrence> known,
            List<Candidate> candidates
    ) {
        Matcher m = PRINTED_INDEX.matcher(raw);
        while (m.find() && candidates.size() < MAX_CANDIDATES) {
            String term = m.group(1);
            if (term == null || term.trim().length() < 3) continue;
            int start = m.start(1), end = m.end(1);
            boolean alreadyKnown = false;
            for (DeterministicMultiIndexMatcher.Occurrence hit : known) {
                if (hit.start() <= start && hit.end() >= end) {
                    alreadyKnown = true;
                    break;
                }
            }
            if (!alreadyKnown) {
                candidates.add(new Candidate("INDEX TIPĂRIT • VERIFICĂ",
                        term.trim(), raw.substring(m.start(), m.end()).trim(),
                        blockIndex, start, end));
            }
        }
    }

    private static Sentence locate(List<Sentence> sentences, int start, int end) {
        for (Sentence sentence : sentences) {
            if (start >= sentence.start && end <= sentence.end) return sentence;
        }
        return null;
    }

    private static boolean abbreviates(String raw, int dot) {
        int from = dot - 1;
        while (from >= 0 && Character.isLetter(raw.charAt(from))) from--;
        String word = raw.substring(from + 1, dot).toLowerCase(Locale.ROOT);
        return word.equals("sec") || word.equals("dr") || word.equals("prof")
                || word.equals("etc") || word.equals("nr") || word.equals("vol")
                || word.equals("pag") || word.equals("p");
    }

    private static List<Sentence> sentences(String raw) {
        List<Sentence> out = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch != '.' && ch != '!' && ch != '?') continue;
            if (ch == '.' && abbreviates(raw, i)) continue;
            if (i + 1 < raw.length() && !Character.isWhitespace(raw.charAt(i + 1))) continue;
            int end = i + 1;
            if (end > from) out.add(new Sentence(from, end, out.size()));
            from = end;
            while (from < raw.length() && Character.isWhitespace(raw.charAt(from))) from++;
            i = from - 1;
        }
        if (from < raw.length()) out.add(new Sentence(from, raw.length(), out.size()));
        return out;
    }
}
