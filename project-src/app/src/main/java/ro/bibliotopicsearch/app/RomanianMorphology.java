package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative Romanian morphology.
 * Narrow familyKey groups ordinary inflection/agreement; broadKey additionally
 * normalizes productive verb/derivational forms and is never an identity key.
 */
public final class RomanianMorphology {
    private RomanianMorphology() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    private static final String[] VERB_BROAD_SUFFIXES = {
            "eaza","easca","este","esti","esc","aste","asti","asc",
            "indu","andu","ind","and",
            "asera","isera","usera","sesera","ase","ise","use","sese",
            "aram","urăm","uram","iram","ati","eti","iti",
            "at","it","ut"
    };

    public static String familyKey(String value) {
        String w = first(RomanianLanguagePack.fold(value));
        if (w.isEmpty() || RomanianLanguagePack.isFunctionWord(w) || w.matches("\\d+")) return w;

        // Productive finite/non-finite verb endings stay distinct at identity level.
        // Irregular lexical normalization remains available only through broadKey.
        if (looksProductiveVerb(w)) return w;

        String narrow = nominalAdjectivalInflection(w);
        return narrow.length() >= 3 ? narrow : w;
    }

    public static String broadKey(String value) {
        String w = first(RomanianLanguagePack.fold(value));
        if (w.isEmpty() || RomanianLanguagePack.isFunctionWord(w) || w.matches("\\d+")) return w;

        String verb = verbStem(w);
        if (!verb.equals(w) && verb.length() >= 4) return verb;

        String irregularOrSnowball = RomanianLanguagePack.derivationalStem(value);
        if (irregularOrSnowball.length() >= 4 && irregularOrSnowball.length() <= w.length()) return irregularOrSnowball;

        String narrow = familyKey(w);
        return narrow.length() >= 3 ? narrow : w;
    }

    public static boolean sameFamily(String a, String b) {
        String left = familyKey(a), right = familyKey(b);
        if (!left.isEmpty() && left.equals(right)) return true;
        String broadLeft = broadKey(a), broadRight = broadKey(b);
        return broadLeft.length() >= 4 && broadLeft.equals(broadRight);
    }

    public static String phraseFamilyKey(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(RomanianLanguagePack.normalizeOrthography(value));
        while (matcher.find()) {
            String token = matcher.group();
            String folded = RomanianLanguagePack.fold(token);
            out.add(RomanianLanguagePack.isFunctionWord(folded) ? folded : familyKey(token));
        }
        return String.join(" ", out).trim();
    }

    public static boolean containsAnyFamily(String text, String... cues) {
        Set<String> narrow = new HashSet<>();
        Set<String> broad = new HashSet<>();
        Matcher matcher = TOKEN.matcher(RomanianLanguagePack.normalizeOrthography(text));
        while (matcher.find()) {
            String token = matcher.group();
            String folded = RomanianLanguagePack.fold(token);
            if (RomanianLanguagePack.isFunctionWord(folded)) continue;
            narrow.add(familyKey(token));
            broad.add(broadKey(token));
        }
        for (String cue : cues) {
            if (cue == null || cue.trim().isEmpty()) continue;
            if (narrow.contains(familyKey(cue)) || broad.contains(broadKey(cue))) return true;
        }
        return false;
    }

    private static String nominalAdjectivalInflection(String w) {
        String s = w;

        // Long Romanian definite/genitive/dative endings first.
        s = remove(s, 4, "iilor", "urilor", "elor", "ilor", "ului");
        s = remove(s, 4, "iile", "urile");
        if (!s.equals(w)) return s;

        if (s.endsWith("ei") && s.length() >= 6) return s.substring(0, s.length() - 2);
        if (s.endsWith("ii") && s.length() >= 6) return s.substring(0, s.length() - 2);
        if (s.endsWith("le") && s.length() >= 7) return s.substring(0, s.length() - 2);

        // Productive gender/number agreement. Length guard protects short lexemes
        // such as mare/carte/țară from aggressive chopping.
        if (s.length() >= 6 && (s.endsWith("a") || s.endsWith("e") || s.endsWith("i"))) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean looksProductiveVerb(String w) {
        for (String suffix : VERB_BROAD_SUFFIXES) {
            if (w.endsWith(suffix) && w.length() - suffix.length() >= 4) return true;
        }
        return false;
    }

    private static String verbStem(String w) {
        // High-information suffixes first. This intentionally produces a retrieval
        // root rather than a dictionary lemma: analizează/analizând/analizat -> analiz.
        String[] suffixes = {
                "eaza","easca","este","esti","esc","aste","asti","asc",
                "indu","andu","ind","and",
                "asera","isera","usera","sesera","ase","ise","use","sese",
                "aseram","iseram","useram","seseram",
                "aram","uram","iram",
                "at","it","ut"
        };
        for (String suffix : suffixes) {
            if (w.endsWith(suffix) && w.length() - suffix.length() >= 4) {
                return w.substring(0, w.length() - suffix.length());
            }
        }
        return w;
    }

    private static String remove(String value, int min, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() - suffix.length() >= min) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String first(String value) {
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group() : "";
    }
}
