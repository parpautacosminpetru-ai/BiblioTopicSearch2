package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, dependency-free scorer for research relevance and explicit answer spans.
 *
 * The engine deliberately rewards explicit lexical/structural evidence. It does not
 * infer an answer that is absent from the text. A question such as "de ce...?" must
 * find causal evidence in the candidate span (or a strongly classified causal
 * paragraph) before the span can cross the answer threshold.
 */
public final class ResearchSemanticEngine {
    private ResearchSemanticEngine() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?;\\n]+(?:[.!?;]|$)");
    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20)\\d{2}\\b");

    public enum Intent {
        TOPIC,
        DEFINITION,
        WHY,
        HOW,
        EFFECT,
        CONDITION,
        WHEN,
        WHERE,
        WHO,
        COMPARISON,
        PURPOSE,
        EVIDENCE,
        PROBLEM,
        SOLUTION
    }

    public static final class Profile {
        private final String rawQuery;
        private final String displayQuery;
        private final Intent intent;
        private final boolean explicitQuestion;
        private final Set<String> directTerms;
        private final Set<String> aliasTerms;

        private Profile(
                String rawQuery,
                String displayQuery,
                Intent intent,
                boolean explicitQuestion,
                Set<String> directTerms,
                Set<String> aliasTerms
        ) {
            this.rawQuery = rawQuery == null ? "" : rawQuery.trim();
            this.displayQuery = displayQuery == null ? "" : displayQuery.trim();
            this.intent = intent == null ? Intent.TOPIC : intent;
            this.explicitQuestion = explicitQuestion;
            this.directTerms = Collections.unmodifiableSet(new LinkedHashSet<>(directTerms));
            this.aliasTerms = Collections.unmodifiableSet(new LinkedHashSet<>(aliasTerms));
        }

        public String rawQuery() { return rawQuery; }
        public String displayQuery() { return displayQuery; }
        public Intent intent() { return intent; }
        public boolean explicitQuestion() { return explicitQuestion; }
        public Set<String> directTerms() { return directTerms; }
        public Set<String> aliasTerms() { return aliasTerms; }
        public boolean enabled() { return !directTerms.isEmpty() || !aliasTerms.isEmpty(); }
    }

    public static final class Answer {
        private final int paragraphIndex;
        private final String segment;
        private final double score;
        private final double directCoverage;
        private final double relationEvidence;
        private final Intent intent;
        private final List<String> matchedTerms;

        private Answer(
                int paragraphIndex,
                String segment,
                double score,
                double directCoverage,
                double relationEvidence,
                Intent intent,
                List<String> matchedTerms
        ) {
            this.paragraphIndex = paragraphIndex;
            this.segment = segment == null ? "" : segment.trim();
            this.score = clamp01(score);
            this.directCoverage = clamp01(directCoverage);
            this.relationEvidence = clamp01(relationEvidence);
            this.intent = intent == null ? Intent.TOPIC : intent;
            this.matchedTerms = Collections.unmodifiableList(new ArrayList<>(matchedTerms));
        }

        public int paragraphIndex() { return paragraphIndex; }
        public String segment() { return segment; }
        public double score() { return score; }
        public double directCoverage() { return directCoverage; }
        public double relationEvidence() { return relationEvidence; }
        public Intent intent() { return intent; }
        public List<String> matchedTerms() { return matchedTerms; }
    }

    private static final class Candidate {
        final int paragraphIndex;
        final String text;
        final UniversalParagraphDetector.Detection detection;
        double score;
        double directCoverage;
        double relationEvidence;
        final List<String> matchedTerms = new ArrayList<>();

        Candidate(int paragraphIndex, String text, UniversalParagraphDetector.Detection detection) {
            this.paragraphIndex = paragraphIndex;
            this.text = text;
            this.detection = detection;
        }
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "ai", "al", "ale", "aici", "acest", "aceasta", "acesta", "acele", "aceea",
            "ca", "care", "cat", "cât", "ce", "cel", "cea", "cei", "cele", "cine", "cu",
            "cum", "cand", "când", "catre", "către", "de", "despre", "din", "dintre", "dupa", "după",
            "este", "sunt", "era", "erau", "fi", "fie", "in", "în", "intre", "între", "la",
            "mai", "o", "pe", "pentru", "prin", "privind", "sa", "să", "se", "si", "și", "sau",
            "un", "una", "unei", "unui", "unde", "the", "of", "and", "or", "to", "for", "with",
            "what", "why", "how", "where", "when", "who", "which", "is", "are"
    ));

    public static Profile compile(String query, TopicMap themeMap) {
        String raw = query == null ? "" : query.trim();
        String normalized = fold(raw);
        Intent intent = detectIntent(normalized);
        boolean question = raw.contains("?") || intent != Intent.TOPIC;

        LinkedHashSet<String> direct = contentTerms(raw);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();

        if (themeMap != null) {
            if (direct.isEmpty()) {
                for (TopicNode node : themeMap.nodes) {
                    if (!eligibleThemeNode(node)) continue;
                    aliases.addAll(contentTerms(node.title));
                    for (String term : node.terms) aliases.addAll(contentTerms(term));
                }
            } else {
                for (TopicNode node : themeMap.nodes) {
                    if (!eligibleThemeNode(node)) continue;
                    LinkedHashSet<String> nodeTerms = new LinkedHashSet<>();
                    nodeTerms.addAll(contentTerms(node.title));
                    nodeTerms.addAll(contentTerms(node.path));
                    for (String term : node.terms) nodeTerms.addAll(contentTerms(term));
                    if (anyConceptMatch(direct, nodeTerms)) aliases.addAll(nodeTerms);
                }
            }
        }

        // Never let alias expansion reduce the weight of the literal research query.
        aliases.removeAll(direct);
        String display = raw;
        if (display.isEmpty() && themeMap != null) display = themeMap.name;
        return new Profile(raw, display, intent, question, direct, aliases);
    }

    public static Answer findBest(Profile profile, List<UniversalParagraphDetector.Detection> detections) {
        if (profile == null || !profile.enabled() || detections == null || detections.isEmpty()) return null;

        Candidate best = null;
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null || detection.paragraph() == null || detection.paragraph().trim().isEmpty()) continue;
            for (String segment : candidateSegments(detection.paragraph())) {
                Candidate candidate = score(profile, detection, segment);
                if (!passesExplicitGate(profile, candidate)) continue;
                if (best == null || better(candidate, best)) best = candidate;
            }
        }

        if (best == null) return null;
        double threshold = profile.explicitQuestion ? 0.54 : 0.44;
        if (best.score < threshold) return null;
        return new Answer(
                best.paragraphIndex,
                best.text,
                best.score,
                best.directCoverage,
                best.relationEvidence,
                profile.intent,
                best.matchedTerms
        );
    }

    private static Candidate score(
            Profile profile,
            UniversalParagraphDetector.Detection detection,
            String segment
    ) {
        Candidate out = new Candidate(detection.paragraphIndex(), segment, detection);
        LinkedHashSet<String> tokens = contentTerms(segment);
        if (tokens.isEmpty()) return out;

        int directMatches = 0;
        for (String term : profile.directTerms) {
            if (containsConcept(tokens, term)) {
                directMatches++;
                out.matchedTerms.add(term);
            }
        }
        out.directCoverage = profile.directTerms.isEmpty()
                ? 0.0
                : directMatches / (double) profile.directTerms.size();

        int aliasMatches = 0;
        for (String alias : profile.aliasTerms) {
            if (containsConcept(tokens, alias)) aliasMatches++;
        }
        double aliasSignal = profile.aliasTerms.isEmpty()
                ? 0.0
                : Math.min(1.0, aliasMatches / 3.0);

        double subjectAlignment = 0.0;
        if (detection.subject() != null && !detection.subject().isEmpty()) {
            LinkedHashSet<String> subjectTerms = contentTerms(detection.subject());
            if (anyConceptMatch(profile.directTerms, subjectTerms)) subjectAlignment = detection.subjectConfidence();
            else if (anyConceptMatch(profile.aliasTerms, subjectTerms)) subjectAlignment = detection.subjectConfidence() * 0.65;
        }

        out.relationEvidence = relationEvidence(profile.intent, segment);
        double functionAlignment = functionAlignment(profile.intent, detection);
        double informativeness = informativeness(tokens, profile);
        double compactness = compactness(segment);

        if (profile.explicitQuestion) {
            out.score = 0.47 * out.directCoverage
                    + 0.11 * aliasSignal
                    + 0.20 * out.relationEvidence
                    + 0.10 * functionAlignment
                    + 0.07 * subjectAlignment
                    + 0.03 * informativeness
                    + 0.02 * compactness;
        } else {
            double lexicalCore = profile.directTerms.isEmpty() ? aliasSignal : out.directCoverage;
            out.score = 0.62 * lexicalCore
                    + 0.13 * aliasSignal
                    + 0.12 * subjectAlignment
                    + 0.08 * informativeness
                    + 0.05 * compactness;
        }
        out.score = clamp01(out.score);
        return out;
    }

    private static boolean passesExplicitGate(Profile profile, Candidate c) {
        if (c == null || c.text == null || c.text.trim().isEmpty()) return false;
        boolean conceptPresent = c.directCoverage > 0.0 || (!profile.aliasTerms.isEmpty() && anyConceptMatch(profile.aliasTerms, contentTerms(c.text)));
        if (!conceptPresent) return false;
        if (!profile.explicitQuestion) return true;

        // A literal question needs at least some lexical anchoring to its target.
        if (!profile.directTerms.isEmpty() && c.directCoverage < (profile.directTerms.size() <= 2 ? 0.5 : 0.34)) return false;

        switch (profile.intent) {
            case WHY:
            case EFFECT:
            case CONDITION:
            case COMPARISON:
            case PURPOSE:
            case DEFINITION:
            case EVIDENCE:
            case SOLUTION:
            case PROBLEM:
                return c.relationEvidence >= 0.40;
            case HOW:
                return c.relationEvidence >= 0.28 || functionAlignment(profile.intent, c.detection) >= 0.70;
            case WHEN:
            case WHERE:
            case WHO:
            case TOPIC:
            default:
                return true;
        }
    }

    private static boolean better(Candidate a, Candidate b) {
        if (a.score > b.score + 0.025) return true;
        if (b.score > a.score + 0.025) return false;
        // When two candidates are almost equivalent, prefer the shorter explicit span.
        return tokenCount(a.text) < tokenCount(b.text);
    }

    private static List<String> candidateSegments(String paragraph) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher matcher = SENTENCE.matcher(paragraph == null ? "" : paragraph);
        while (matcher.find()) {
            String sentence = cleanSegment(matcher.group());
            if (tokenCount(sentence) >= 3) out.add(sentence);
            String[] clauses = sentence.split("(?<=,)|(?<=:)|(?<=—)|(?<=–)");
            for (String clause : clauses) {
                String clean = cleanSegment(clause);
                if (tokenCount(clean) >= 3) out.add(clean);
            }
        }
        if (out.isEmpty() && paragraph != null && !paragraph.trim().isEmpty()) out.add(paragraph.trim());
        return new ArrayList<>(out);
    }

    private static String cleanSegment(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("^[,;:—–\\s]+|[,;:—–\\s]+$", "").trim();
    }

    private static double relationEvidence(Intent intent, String segment) {
        String text = " " + fold(segment) + " ";
        switch (intent) {
            case WHY:
                return cueScore(text,
                        " deoarece ", " fiindca ", " intrucat ", " pentru ca ", " din cauza ",
                        " datorita ", " motiv ", " cauza ", " explica ", " determinat de ");
            case EFFECT:
                return cueScore(text,
                        " conduce la ", " duce la ", " determina ", " provoaca ", " genereaza ",
                        " efect ", " consecinta ", " prin urmare ", " in consecinta ", " rezulta ");
            case HOW:
                return cueScore(text,
                        " prin ", " astfel ", " mecanism ", " proces ", " etapa ", " consta in ",
                        " se realizeaza ", " functioneaza ", " are loc ");
            case CONDITION:
                return cueScore(text,
                        " daca ", " cu conditia ", " in cazul in care ", " numai daca ", " conditie ");
            case DEFINITION:
                return cueScore(text,
                        " este ", " sunt ", " reprezinta ", " inseamna ", " se defineste ", " desemneaza ");
            case COMPARISON:
                return cueScore(text,
                        " comparativ cu ", " in comparatie cu ", " similar ", " asemanator ", " spre deosebire de ",
                        " diferit ", " mai mult ", " mai putin ", " decat ");
            case PURPOSE:
                return cueScore(text,
                        " pentru a ", " in vederea ", " cu scopul ", " obiectiv ", " vizeaza ", " urmareste ");
            case EVIDENCE:
                return cueScore(text,
                        " datele arata ", " studiul arata ", " rezultatele indica ", " dovezi ", " dovada ",
                        " conform datelor ", " experiment ");
            case PROBLEM:
                return cueScore(text,
                        " problema ", " dificultate ", " limitare ", " risc ", " obstacol ", " provocare ");
            case SOLUTION:
                return cueScore(text,
                        " solutia ", " se poate rezolva ", " masura ", " remediu ", " este necesar ",
                        " pentru a reduce ", " pentru a evita ");
            case WHEN:
                if (YEAR.matcher(text).find()) return 1.0;
                return cueScore(text,
                        " in anul ", " in perioada ", " in secolul ", " ulterior ", " anterior ",
                        " dupa ", " inainte ", " cand ");
            case WHERE:
                return cueScore(text,
                        " in romania ", " in europa ", " regiune ", " zona ", " local ", " global ");
            case WHO:
                return 0.45; // lexical anchoring does the main work without a local NER model.
            case TOPIC:
            default:
                return 1.0;
        }
    }

    private static double cueScore(String text, String... cues) {
        int hits = 0;
        for (String cue : cues) if (text.contains(cue)) hits++;
        if (hits == 0) return 0.0;
        if (hits == 1) return 0.72;
        if (hits == 2) return 0.88;
        return 1.0;
    }

    private static double functionAlignment(Intent intent, UniversalParagraphDetector.Detection d) {
        if (d == null) return 0.0;
        UniversalDetectionLexicon.Function primary = d.function();
        UniversalDetectionLexicon.Function secondary = d.secondaryFunction();
        double p = functionMatch(intent, primary) ? d.functionConfidence() : 0.0;
        double s = functionMatch(intent, secondary) ? d.functionConfidence() * 0.72 : 0.0;
        return Math.max(p, s);
    }

    private static boolean functionMatch(Intent intent, UniversalDetectionLexicon.Function f) {
        if (f == null) return false;
        switch (intent) {
            case WHY: return f == UniversalDetectionLexicon.Function.CAUSE_EFFECT || f == UniversalDetectionLexicon.Function.EXPLANATION;
            case EFFECT: return f == UniversalDetectionLexicon.Function.CAUSE_EFFECT || f == UniversalDetectionLexicon.Function.CONCLUSION;
            case HOW: return f == UniversalDetectionLexicon.Function.EXPLANATION || f == UniversalDetectionLexicon.Function.SEQUENCE || f == UniversalDetectionLexicon.Function.DESCRIPTION;
            case CONDITION: return f == UniversalDetectionLexicon.Function.CONDITION;
            case DEFINITION: return f == UniversalDetectionLexicon.Function.DEFINITION;
            case COMPARISON: return f == UniversalDetectionLexicon.Function.COMPARISON || f == UniversalDetectionLexicon.Function.CONTRAST;
            case PURPOSE: return f == UniversalDetectionLexicon.Function.PURPOSE;
            case EVIDENCE: return f == UniversalDetectionLexicon.Function.EVIDENCE || f == UniversalDetectionLexicon.Function.ARGUMENTATION;
            case PROBLEM: return f == UniversalDetectionLexicon.Function.PROBLEM;
            case SOLUTION: return f == UniversalDetectionLexicon.Function.SOLUTION;
            default: return false;
        }
    }

    private static Intent detectIntent(String q) {
        String text = " " + (q == null ? "" : q) + " ";
        if (text.contains(" de ce ") || text.contains(" cauz") || text.contains(" motiv")) return Intent.WHY;
        if (text.contains(" ce efect") || text.contains(" efectele ") || text.contains(" consecint") || text.contains(" impact")) return Intent.EFFECT;
        if (text.contains(" cum ") || text.contains(" mecanism") || text.contains(" in ce mod ")) return Intent.HOW;
        if (text.contains(" ce este ") || text.contains(" ce inseamna ") || text.contains(" definit")) return Intent.DEFINITION;
        if (text.contains(" in ce condit") || text.contains(" daca ") || text.contains(" conditii ")) return Intent.CONDITION;
        if (text.contains(" cand ") || text.contains(" in ce perioada ") || text.contains(" in ce an ")) return Intent.WHEN;
        if (text.contains(" unde ") || text.contains(" in ce loc ") || text.contains(" in ce zona ")) return Intent.WHERE;
        if (text.contains(" cine ") || text.contains(" care persoane ") || text.contains(" ce actor")) return Intent.WHO;
        if (text.contains(" compar") || text.contains(" diferent") || text.contains(" aseman")) return Intent.COMPARISON;
        if (text.contains(" scop") || text.contains(" pentru ce ") || text.contains(" cu ce scop ")) return Intent.PURPOSE;
        if (text.contains(" dovez") || text.contains(" ce date ") || text.contains(" evidenta ")) return Intent.EVIDENCE;
        if (text.contains(" problema") || text.contains(" dificultat") || text.contains(" risc")) return Intent.PROBLEM;
        if (text.contains(" soluti") || text.contains(" cum se rezolva ") || text.contains(" remedi")) return Intent.SOLUTION;
        return Intent.TOPIC;
    }

    private static boolean eligibleThemeNode(TopicNode node) {
        if (node == null || !node.enabled || node.path == null) return false;
        String path = fold(node.path);
        return !(path.equals("textual") || path.startsWith("textual >")
                || path.equals("semantic") || path.startsWith("semantic >"));
    }

    private static LinkedHashSet<String> contentTerms(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(fold(value));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static boolean anyConceptMatch(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        for (String x : a) for (String y : b) if (conceptMatch(x, y)) return true;
        return false;
    }

    private static boolean containsConcept(Set<String> haystack, String needle) {
        for (String value : haystack) if (conceptMatch(value, needle)) return true;
        return false;
    }

    private static boolean conceptMatch(String a, String b) {
        if (a == null || b == null) return false;
        String x = fold(a);
        String y = fold(b);
        if (x.equals(y)) return true;
        int min = Math.min(x.length(), y.length());
        if (min < 5) return false;
        int prefix = min >= 8 ? 6 : 5;
        return x.regionMatches(0, y, 0, prefix);
    }

    private static double informativeness(Set<String> tokens, Profile profile) {
        int extra = 0;
        for (String token : tokens) {
            if (!containsConcept(profile.directTerms, token) && !containsConcept(profile.aliasTerms, token)) extra++;
        }
        return Math.min(1.0, extra / 5.0);
    }

    private static double compactness(String segment) {
        int n = tokenCount(segment);
        if (n <= 8) return 1.0;
        if (n <= 18) return 0.8;
        if (n <= 30) return 0.55;
        return 0.30;
    }

    private static int tokenCount(String value) {
        int count = 0;
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) count++;
        return count;
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
