package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic lexical/grammatical cues for live OCR.
 *
 * A cue is an EXACT TEXT SPAN, not a verified historical relation. For example,
 * "după" is marked as a possible temporal cue but cannot prove causation.
 * No language model, external lookup, or guessed subject/object is used here.
 */
public final class SyntaxCueDetector {
    private SyntaxCueDetector() {}

    private static final Pattern WORD = Pattern.compile(
            "[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*"
    );

    public enum Kind {
        CAUSE("CAUZĂ"),
        PURPOSE("SCOP"),
        CONDITION("CONDIȚIE"),
        TIME("TIMP"),
        NEGATION("NEGAȚIE"),
        MODALITY("MODALITATE"),
        CONCESSION("CONCESIE"),
        CONTRAST("CONTRAST"),
        COMPARISON("COMPARAȚIE"),
        CONSEQUENCE("CONSECINȚĂ"),
        ATTRIBUTION("ATRIBUIRE"),
        MANNER("MOD"),
        RESTRICTION("RESTRICȚIE"),
        EXCEPTION("EXCEPȚIE"),
        QUANTITY("CANTITATE"),
        LOCATION("SPAȚIU"),
        COREFERENCE("REFERINȚĂ");

        private final String label;
        Kind(String label) { this.label = label; }
        public String label() { return label; }
    }

    public static final class Cue {
        private final Kind kind;
        private final int startToken;
        private final int endToken;
        private final String evidence;

        Cue(Kind kind, int startToken, int endToken, String evidence) {
            this.kind = kind;
            this.startToken = startToken;
            this.endToken = endToken;
            this.evidence = evidence;
        }

        public Kind kind() { return kind; }
        public int startToken() { return startToken; }
        /** Exclusive word index. */
        public int endToken() { return endToken; }
        public String evidence() { return evidence; }
    }

    private static final class Rule {
        final Kind kind;
        final List<String> words;
        final String expression;

        Rule(Kind kind, String phrase) {
            this.kind = kind;
            this.expression = phrase;
            this.words = Collections.unmodifiableList(tokenize(phrase));
        }
    }

    private static final List<Rule> RULES = buildRules();

    /** Returns normalized words in original reading order, preserving repetitions. */
    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = fold(matcher.group());
            if (!word.isEmpty()) out.add(word);
        }
        return out;
    }

    /** Case/diacritic-insensitive comparison, not stemming or synonym inference. */
    private static String fold(String word) {
        if (word == null) return "";
        String normalized = Normalizer.normalize(
                word.toLowerCase(Locale.ROOT)
                        .replace('ş', 'ș').replace('ţ', 'ț'),
                Normalizer.Form.NFD
        );
        return normalized.replaceAll("\\p{M}+", "");
    }

    public static List<Cue> detectText(String source) {
        return detectWords(tokenize(source));
    }

    /**
     * One linear pass. Longest anchored expression wins so that "chiar dacă"
     * is not reduced to plain "dacă", or "după cum spune" to "după".
     * Unrecognized grammar is not silently claimed to have been indexed.
     */
    public static List<Cue> detectWords(List<String> sourceWords) {
        if (sourceWords == null || sourceWords.isEmpty()) return Collections.emptyList();
        List<String> words = new ArrayList<>(sourceWords.size());
        for (String raw : sourceWords) words.add(fold(raw));

        List<Cue> out = new ArrayList<>();
        int index = 0;
        while (index < words.size()) {
            Rule matched = null;
            for (Rule candidate : RULES) {
                if (candidate.words.size() > words.size() - index) continue;
                boolean fits = true;
                for (int offset = 0; offset < candidate.words.size(); offset++) {
                    if (!candidate.words.get(offset).equals(words.get(index + offset))) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    matched = candidate;
                    break;
                }
            }
            if (matched == null) {
                index++;
            } else {
                out.add(new Cue(matched.kind, index,
                        index + matched.words.size(), matched.expression));
                index += matched.words.size();
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();
        add(rules, Kind.ATTRIBUTION,
                "după cum afirmă", "după cum spune", "după cum arată",
                "potrivit autorului", "potrivit sursei", "potrivit lui",
                "conform", "așa cum susține", "așa cum afirmă");
        add(rules, Kind.CAUSE,
                "din cauza", "pe motiv că", "pentru că", "dat fiind că",
                "deoarece", "fiindcă", "întrucât", "datorită");
        add(rules, Kind.PURPOSE,
                "cu scopul de a", "în vederea", "pentru a", "ca să");
        add(rules, Kind.CONDITION,
                "în cazul în care", "cu condiția ca", "cu condiția să", "dacă");
        add(rules, Kind.CONCESSION,
                "chiar dacă", "cu toate că", "deși", "deşi", "deși totuși");
        add(rules, Kind.CONSEQUENCE,
                "în consecință", "drept urmare", "prin urmare",
                "astfel încât", "ca rezultat");
        add(rules, Kind.TIME,
                "înainte de", "după ce", "în timp ce", "în vreme ce",
                "atunci când", "până când", "de atunci", "ulterior",
                "anterior", "după", "înainte");
        add(rules, Kind.NEGATION,
                "nu", "nici", "niciun", "nicio", "fără", "niciodată", "nimeni", "nimic");
        add(rules, Kind.MODALITY,
                "s-ar putea", "ar putea", "ar trebui", "este posibil",
                "este probabil", "probabil", "poate", "trebuie", "posibil");
        add(rules, Kind.CONTRAST,
                "pe de altă parte", "în schimb", "însă", "dar", "totuși", "pe când");
        add(rules, Kind.COMPARISON,
                "în comparație cu", "spre deosebire de", "la fel ca",
                "mai mult decât", "mai puțin decât", "decât", "similar");
        add(rules, Kind.MANNER,
                "prin intermediul", "în acest mod", "în felul acesta",
                "în felul în care");
        add(rules, Kind.RESTRICTION,
                "cel puțin", "cel mult", "în special", "mai ales",
                "exclusiv", "numai", "doar");
        add(rules, Kind.EXCEPTION,
                "cu excepția", "în afară de", "exceptând");
        add(rules, Kind.QUANTITY,
                "majoritatea", "aproximativ", "fiecare", "toate", "toți",
                "mulți", "puțini", "puține");
        add(rules, Kind.LOCATION,
                "pe teritoriul", "în regiunea", "în zona", "în orașul",
                "în localitatea", "la nord de", "la sud de", "la est de", "la vest de");
        add(rules, Kind.COREFERENCE,
                "aceștia", "acestea", "acesta", "aceasta", "respectivul", "respectiva",
                "el", "ea", "ei", "ele");
        // Longest expression first; stable ordering preserves explicitly chosen ties.
        rules.sort(Comparator.comparingInt((Rule r) -> r.words.size()).reversed()
                .thenComparing(Comparator.comparingInt((Rule r) -> r.expression.length()).reversed()));
        return Collections.unmodifiableList(rules);
    }

    private static void add(List<Rule> rules, Kind kind, String... expressions) {
        for (String value : Arrays.asList(expressions)) {
            Rule r = new Rule(kind, value);
            if (!r.words.isEmpty()) rules.add(r);
        }
    }
}
