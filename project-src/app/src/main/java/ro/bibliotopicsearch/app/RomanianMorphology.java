package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative morphology utilities layered over RomanianLanguagePack. */
public final class RomanianMorphology {
    private RomanianMorphology() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    public static String familyKey(String value) {
        String w = first(RomanianLanguagePack.fold(value));
        if (w.isEmpty() || RomanianLanguagePack.isFunctionWord(w) || w.matches("\\d+")) return w;

        String base = RomanianLanguagePack.familyKey(w);
        String ro = romanianInflection(w);
        if (ro.length() >= 3 && ro.length() < base.length()) return ro;
        return base;
    }

    public static String broadKey(String value) {
        String family = familyKey(value);
        if (family.length() < 4 || RomanianLanguagePack.isFunctionWord(family)) return family;
        String stem = RomanianLanguagePack.derivationalStem(value);
        if (stem.length() >= 4 && stem.length() <= family.length()) return stem;
        return family;
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

    private static String romanianInflection(String w) {
        String s = w;
        // Genitive/dative and definite plural endings.
        s = remove(s, 4, "iilor", "urilor", "elor", "ilor", "ului");
        s = remove(s, 4, "iile", "urile");
        s = remove(s, 4, "elor", "ilor");

        if (s.endsWith("ei") && s.length() >= 6) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("ii") && s.length() >= 6) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("le") && s.length() >= 6) s = s.substring(0, s.length() - 2);

        // Productive adjective/noun agreement endings. Keep at least four letters.
        if (s.length() >= 6) {
            if (s.endsWith("e") || s.endsWith("i") || s.endsWith("a")) s = s.substring(0, s.length() - 1);
        }

        // Verb endings that are distinctive enough to be safely normalized.
        String[] verb = {
                "ează","eaza","ească","easca","ește","este","ești","esti","esc",
                "ăște","aste","ăști","asti","ăsc","asc","ind","ând","and","indu","andu",
                "aseră","asera","iseră","isera","useră","usera","seseră","sesera",
                "are","ere","ire","âre"
        };
        for (String suffix : verb) {
            String f = RomanianLanguagePack.fold(suffix);
            if (s.endsWith(f) && s.length() - f.length() >= 4) {
                s = s.substring(0, s.length() - f.length());
                break;
            }
        }
        return s.length() >= 3 ? s : w;
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
