package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, dependency-free paragraph detector.
 *
 * It intentionally performs detection, not semantic invention: when lexical or
 * structural evidence is weak, confidence stays low and the function falls back
 * to DEVELOPMENT/UNKNOWN rather than fabricating a precise interpretation.
 */
public final class UniversalParagraphDetector {
    private UniversalParagraphDetector() {}

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\\n+");
    private static final int MAX_SUBJECT_WORDS = 10;

    public static final class Detection {
        private final int paragraphIndex;
        private final String paragraph;
        private final String subject;
        private final UniversalDetectionLexicon.Function function;
        private final UniversalDetectionLexicon.Function secondaryFunction;
        private final double subjectConfidence;
        private final double functionConfidence;
        private final List<UniversalDetectionLexicon.Slot> querySlots;
        private final Set<UniversalDetectionLexicon.Operator> operators;
        private final List<String> matchedMarkers;

        Detection(
                int paragraphIndex,
                String paragraph,
                String subject,
                UniversalDetectionLexicon.Function function,
                UniversalDetectionLexicon.Function secondaryFunction,
                double subjectConfidence,
                double functionConfidence,
                List<UniversalDetectionLexicon.Slot> querySlots,
                Set<UniversalDetectionLexicon.Operator> operators,
                List<String> matchedMarkers
        ) {
            this.paragraphIndex = paragraphIndex;
            this.paragraph = paragraph;
            this.subject = subject;
            this.function = function;
            this.secondaryFunction = secondaryFunction;
            this.subjectConfidence = subjectConfidence;
            this.functionConfidence = functionConfidence;
            this.querySlots = Collections.unmodifiableList(new ArrayList<>(querySlots));
            this.operators = Collections.unmodifiableSet(EnumSet.copyOf(operators));
            this.matchedMarkers = Collections.unmodifiableList(new ArrayList<>(matchedMarkers));
        }

        public int paragraphIndex() { return paragraphIndex; }
        public String paragraph() { return paragraph; }
        public String subject() { return subject; }
        public UniversalDetectionLexicon.Function function() { return function; }
        public UniversalDetectionLexicon.Function secondaryFunction() { return secondaryFunction; }
        public double subjectConfidence() { return subjectConfidence; }
        public double functionConfidence() { return functionConfidence; }
        public List<UniversalDetectionLexicon.Slot> querySlots() { return querySlots; }
        public Set<UniversalDetectionLexicon.Operator> operators() { return operators; }
        public List<String> matchedMarkers() { return matchedMarkers; }

        public String compactLabel() {
            String safeSubject = subject == null || subject.isEmpty() ? "?" : subject;
            return safeSubject + " • " + function.name();
        }
    }

    private static final class FunctionResult {
        final UniversalDetectionLexicon.Function primary;
        final UniversalDetectionLexicon.Function secondary;
        final double confidence;
        final List<String> markers;

        FunctionResult(
                UniversalDetectionLexicon.Function primary,
                UniversalDetectionLexicon.Function secondary,
                double confidence,
                List<String> markers
        ) {
            this.primary = primary;
            this.secondary = secondary;
            this.confidence = confidence;
            this.markers = markers;
        }
    }

    private static final class SubjectResult {
        final String value;
        final double confidence;

        SubjectResult(String value, double confidence) {
            this.value = value;
            this.confidence = confidence;
        }
    }

    private static final class Candidate {
        String surface;
        double score;
        int firstSentence = Integer.MAX_VALUE;
        final Set<Integer> sentenceIds = new HashSet<>();

        Candidate(String surface) {
            this.surface = surface;
        }
    }

    public static Detection detect(String paragraph) {
        return detect(paragraph, 0);
    }

    public static Detection detect(String paragraph, int paragraphIndex) {
        String raw = paragraph == null ? "" : paragraph.trim();
        if (raw.isEmpty()) {
            return new Detection(
                    paragraphIndex,
                    "",
                    "",
                    UniversalDetectionLexicon.Function.UNKNOWN,
                    UniversalDetectionLexicon.Function.UNKNOWN,
                    0.0,
                    0.0,
                    UniversalDetectionLexicon.slotsFor(UniversalDetectionLexicon.Function.UNKNOWN),
                    EnumSet.noneOf(UniversalDetectionLexicon.Operator.class),
                    Collections.emptyList()
            );
        }

        List<String> sentences = splitSentences(raw);
        FunctionResult function = detectFunction(raw, sentences);
        SubjectResult subject = detectSubject(raw, sentences);
        Set<UniversalDetectionLexicon.Operator> operators = detectOperators(raw);

        return new Detection(
                paragraphIndex,
                raw,
                subject.value,
                function.primary,
                function.secondary,
                subject.confidence,
                function.confidence,
                UniversalDetectionLexicon.slotsFor(function.primary),
                operators,
                function.markers
        );
    }

    /** Split ordinary pasted text into paragraphs. OCR callers should prefer ML Kit TextBlocks. */
    public static List<String> splitParagraphs(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n+");
        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            String value = block.replaceAll("[ \\t]+", " ").trim();
            if (!value.isEmpty()) out.add(value);
        }
        if (out.isEmpty() && !normalized.trim().isEmpty()) out.add(normalized.trim());
        return out;
    }

    private static FunctionResult detectFunction(String raw, List<String> sentences) {
        String folded = pad(UniversalDetectionLexicon.fold(raw));
        Map<UniversalDetectionLexicon.Function, Double> scores =
                new EnumMap<>(UniversalDetectionLexicon.Function.class);
        List<String> matched = new ArrayList<>();

        for (UniversalDetectionLexicon.Function function : UniversalDetectionLexicon.Function.values()) {
            if (function == UniversalDetectionLexicon.Function.UNKNOWN) continue;
            double score = 0.0;
            for (UniversalDetectionLexicon.Marker marker : UniversalDetectionLexicon.markers(function)) {
                int count = countPhrase(folded, marker.normalized);
                if (count <= 0) continue;
                double local = marker.weight * count;
                int first = folded.indexOf(" " + marker.normalized + " ");
                if (first >= 0 && first < Math.min(100, folded.length() / 3 + 1)) local += 0.45;
                if (marker.normalized.indexOf(' ') >= 0) local += 0.25;
                score += local;
                matched.add(function.name() + ":" + marker.raw);
            }
            scores.put(function, score);
        }

        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            add(scores, UniversalDetectionLexicon.Function.DEFINITION, 0.45);
            add(scores, UniversalDetectionLexicon.Function.ENUMERATION, 0.45);
            add(scores, UniversalDetectionLexicon.Function.EXPLANATION, 0.25);
        }
        if (trimmed.endsWith("?") || trimmed.startsWith("Cum ") || trimmed.startsWith("De ce ")) {
            add(scores, UniversalDetectionLexicon.Function.PROBLEM, 1.3);
            add(scores, UniversalDetectionLexicon.Function.INTRODUCTION, 0.5);
        }
        if (containsEarlyCopula(sentences)) {
            add(scores, UniversalDetectionLexicon.Function.DEFINITION, 0.75);
        }
        if (containsListShape(trimmed)) {
            add(scores, UniversalDetectionLexicon.Function.ENUMERATION, 0.85);
        }

        List<Map.Entry<UniversalDetectionLexicon.Function, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Map.Entry.<UniversalDetectionLexicon.Function, Double>comparingByValue().reversed());

        double topScore = ranked.isEmpty() ? 0.0 : ranked.get(0).getValue();
        double secondScore = ranked.size() < 2 ? 0.0 : ranked.get(1).getValue();
        UniversalDetectionLexicon.Function primary;
        UniversalDetectionLexicon.Function secondary;

        if (topScore < 0.8) {
            primary = sentences.size() > 1
                    ? UniversalDetectionLexicon.Function.DEVELOPMENT
                    : UniversalDetectionLexicon.Function.UNKNOWN;
            secondary = UniversalDetectionLexicon.Function.UNKNOWN;
        } else {
            primary = ranked.get(0).getKey();
            secondary = secondScore >= Math.max(1.0, topScore * 0.55)
                    ? ranked.get(1).getKey()
                    : UniversalDetectionLexicon.Function.UNKNOWN;
        }

        double confidence = clamp01(topScore / (topScore + secondScore + 1.25));
        if (primary == UniversalDetectionLexicon.Function.DEVELOPMENT && topScore < 0.8) confidence = 0.35;
        if (primary == UniversalDetectionLexicon.Function.UNKNOWN) confidence = Math.min(confidence, 0.25);

        return new FunctionResult(primary, secondary, confidence, dedupe(matched));
    }

    private static SubjectResult detectSubject(String raw, List<String> sentences) {
        String explicit = extractExplicitTopic(raw);
        if (!explicit.isEmpty()) return new SubjectResult(explicit, 0.94);

        Map<String, Candidate> candidates = new HashMap<>();
        String mostRecentKey = null;

        for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
            String sentence = stripInitialFrame(sentences.get(sentenceIndex));
            if (sentence.isEmpty()) continue;

            String firstWord = firstWord(sentence);
            if (UniversalDetectionLexicon.COREFERENCE_WORDS.contains(UniversalDetectionLexicon.fold(firstWord))) {
                if (mostRecentKey != null) {
                    Candidate previous = candidates.get(mostRecentKey);
                    if (previous != null) previous.score += 1.8;
                }
                continue;
            }

            String leading = extractLeadingSubjectPhrase(sentence);
            if (!leading.isEmpty()) {
                String key = subjectKey(leading);
                if (!key.isEmpty()) {
                    Candidate candidate = candidates.computeIfAbsent(key, ignored -> new Candidate(leading));
                    if (leading.length() > candidate.surface.length()) candidate.surface = leading;
                    candidate.score += sentenceIndex == 0 ? 4.2 : 2.4;
                    candidate.firstSentence = Math.min(candidate.firstSentence, sentenceIndex);
                    candidate.sentenceIds.add(sentenceIndex);
                    mostRecentKey = key;
                }
            }
        }

        addTokenFallbackCandidates(sentences, candidates);

        if (candidates.isEmpty()) return new SubjectResult("", 0.0);

        for (Candidate candidate : candidates.values()) {
            candidate.score += Math.max(0, candidate.sentenceIds.size() - 1) * 2.2;
            if (candidate.firstSentence == 0) candidate.score += 1.0;
            int words = wordCount(candidate.surface);
            if (words >= 2 && words <= 6) candidate.score += 0.45;
            if (words > MAX_SUBJECT_WORDS) candidate.score -= (words - MAX_SUBJECT_WORDS) * 0.5;
        }

        List<Candidate> ranked = new ArrayList<>(candidates.values());
        ranked.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        Candidate top = ranked.get(0);
        double second = ranked.size() > 1 ? ranked.get(1).score : 0.0;
        double confidence = clamp01(top.score / (top.score + second + 1.5));

        return new SubjectResult(cleanSubjectCandidate(top.surface), confidence);
    }

    private static Set<UniversalDetectionLexicon.Operator> detectOperators(String raw) {
        String folded = pad(UniversalDetectionLexicon.fold(raw));
        EnumSet<UniversalDetectionLexicon.Operator> result =
                EnumSet.noneOf(UniversalDetectionLexicon.Operator.class);

        for (UniversalDetectionLexicon.Operator operator : UniversalDetectionLexicon.Operator.values()) {
            for (UniversalDetectionLexicon.Marker marker : UniversalDetectionLexicon.markers(operator)) {
                if (containsPhrase(folded, marker.normalized)) {
                    result.add(operator);
                    break;
                }
            }
        }
        return result;
    }

    private static String extractExplicitTopic(String raw) {
        String loose = foldPreserveSpacing(raw);
        int bestStart = Integer.MAX_VALUE;
        String bestPrefix = null;

        for (String prefix : UniversalDetectionLexicon.TOPIC_PREFIXES) {
            int index = loose.indexOf(prefix);
            if (index >= 0 && index < bestStart) {
                bestStart = index;
                bestPrefix = prefix;
            }
        }
        if (bestPrefix == null) return "";

        int start = Math.min(raw.length(), bestStart + bestPrefix.length());
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) start++;
        int end = raw.length();
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ',' || c == ';' || c == ':' || c == '.' || c == '!' || c == '?') {
                end = i;
                break;
            }
        }
        return cleanSubjectCandidate(limitWords(raw.substring(start, end), MAX_SUBJECT_WORDS));
    }

    private static String stripInitialFrame(String sentence) {
        String value = sentence.trim();
        if (value.isEmpty()) return value;
        String folded = foldPreserveSpacing(value);
        int comma = value.indexOf(',');
        if (comma > 0 && comma < 100) {
            String prefix = folded.substring(0, Math.min(comma, folded.length())).trim();
            for (String frame : UniversalDetectionLexicon.FRAME_PREFIXES) {
                if (prefix.startsWith(frame)) return value.substring(comma + 1).trim();
            }
            if (looksLikeTemporalOrSpatialFrame(prefix)) return value.substring(comma + 1).trim();
        }
        return value;
    }

    private static boolean looksLikeTemporalOrSpatialFrame(String prefix) {
        if (prefix.matches("^(in|la|din) (anul |anii |secolul |perioada )?\\d{3,4}.*")) return true;
        if (prefix.matches("^(in|la|din) [\\p{L}\\- ]{2,30}$") && wordCount(prefix) <= 5) return true;
        return false;
    }

    private static String extractLeadingSubjectPhrase(String sentence) {
        String value = sentence.trim();
        if (value.isEmpty()) return "";
        String folded = " " + foldPreserveSpacing(value) + " ";
        int best = Integer.MAX_VALUE;

        for (String cue : UniversalDetectionLexicon.PREDICATE_CUES) {
            int index = folded.indexOf(cue);
            if (index > 1 && index < best) best = index;
        }
        if (best == Integer.MAX_VALUE) return "";

        int cut = Math.max(0, best - 1);
        String candidate = value.substring(0, Math.min(cut, value.length())).trim();
        int comma = candidate.lastIndexOf(',');
        if (comma >= 0) candidate = candidate.substring(comma + 1).trim();
        candidate = limitWords(candidate, MAX_SUBJECT_WORDS);
        return cleanSubjectCandidate(candidate);
    }

    private static void addTokenFallbackCandidates(List<String> sentences, Map<String, Candidate> candidates) {
        Map<String, Integer> frequency = new HashMap<>();
        Map<String, Set<Integer>> spread = new HashMap<>();
        Map<String, String> surfaces = new HashMap<>();

        for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
            Matcher matcher = WORD_PATTERN.matcher(sentences.get(sentenceIndex));
            while (matcher.find()) {
                String surface = matcher.group();
                String key = UniversalDetectionLexicon.fold(surface);
                if (!isContentToken(key)) continue;
                frequency.put(key, frequency.getOrDefault(key, 0) + 1);
                spread.computeIfAbsent(key, ignored -> new HashSet<>()).add(sentenceIndex);
                surfaces.putIfAbsent(key, surface);
            }
        }

        for (Map.Entry<String, Integer> entry : frequency.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();
            int sentenceCount = spread.get(key).size();
            double score = count * 0.75 + sentenceCount * 1.35;
            if (spread.get(key).contains(0)) score += 0.55;
            Candidate candidate = candidates.computeIfAbsent(key, ignored -> new Candidate(surfaces.get(key)));
            candidate.score += score;
            candidate.sentenceIds.addAll(spread.get(key));
            if (spread.get(key).contains(0)) candidate.firstSentence = 0;
        }
    }

    private static boolean isContentToken(String key) {
        if (key == null || key.length() < 3) return false;
        if (key.matches("\\d+")) return false;
        if (UniversalDetectionLexicon.STOP_WORDS.contains(key)) return false;
        if (UniversalDetectionLexicon.COREFERENCE_WORDS.contains(key)) return false;
        return true;
    }

    private static boolean containsEarlyCopula(List<String> sentences) {
        if (sentences.isEmpty()) return false;
        String first = " " + UniversalDetectionLexicon.fold(sentences.get(0)) + " ";
        return first.indexOf(" este ") > 0 && first.indexOf(" este ") < 90
                || first.indexOf(" reprezinta ") > 0 && first.indexOf(" reprezinta ") < 90
                || first.indexOf(" inseamna ") > 0 && first.indexOf(" inseamna ") < 90;
    }

    private static boolean containsListShape(String raw) {
        return raw.matches("(?s).*(^|\\s)(1[.)]|2[.)]|3[.)]|[a-cA-C][.)])\\s+.*")
                || raw.contains("; ") && raw.indexOf(';') != raw.lastIndexOf(';');
    }

    private static int countPhrase(String paddedFolded, String phrase) {
        if (phrase == null || phrase.isEmpty()) return 0;
        String needle = " " + phrase + " ";
        int count = 0;
        int from = 0;
        while (from <= paddedFolded.length() - needle.length()) {
            int index = paddedFolded.indexOf(needle, from);
            if (index < 0) break;
            count++;
            from = index + needle.length();
        }
        return count;
    }

    private static boolean containsPhrase(String paddedFolded, String phrase) {
        if (phrase == null || phrase.isEmpty()) return false;
        return paddedFolded.contains(" " + phrase + " ");
    }

    private static List<String> splitSentences(String raw) {
        String[] chunks = SENTENCE_SPLIT.split(raw);
        List<String> out = new ArrayList<>();
        for (String chunk : chunks) {
            String value = chunk.trim();
            if (!value.isEmpty()) out.add(value);
        }
        if (out.isEmpty()) out.add(raw.trim());
        return out;
    }

    private static String firstWord(String value) {
        Matcher matcher = WORD_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private static String subjectKey(String surface) {
        String folded = UniversalDetectionLexicon.fold(surface)
                .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")
                .trim();
        return folded;
    }

    private static String cleanSubjectCandidate(String value) {
        if (value == null) return "";
        String out = value.trim()
                .replaceAll("^[,;:–—\\-\\s]+", "")
                .replaceAll("[,;:–—\\-\\s]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (out.isEmpty()) return out;

        List<String> words = words(out);
        while (!words.isEmpty()
                && UniversalDetectionLexicon.STOP_WORDS.contains(UniversalDetectionLexicon.fold(words.get(0)))) {
            words.remove(0);
        }
        if (words.isEmpty()) return "";
        return String.join(" ", words);
    }

    private static String limitWords(String value, int maxWords) {
        List<String> words = words(value);
        if (words.size() <= maxWords) return value.trim();
        return String.join(" ", words.subList(0, maxWords));
    }

    private static List<String> words(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    private static int wordCount(String value) {
        int count = 0;
        Matcher matcher = WORD_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) count++;
        return count;
    }

    private static String foldPreserveSpacing(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');
    }

    private static String pad(String value) {
        return " " + (value == null ? "" : value.trim()) + " ";
    }

    private static void add(
            Map<UniversalDetectionLexicon.Function, Double> scores,
            UniversalDetectionLexicon.Function function,
            double value
    ) {
        scores.put(function, scores.getOrDefault(function, 0.0) + value);
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
