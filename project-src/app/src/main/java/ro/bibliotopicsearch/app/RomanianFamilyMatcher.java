package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds Romanian lexical-family matches while preserving the exact OCR surface. */
public final class RomanianFamilyMatcher {
    private RomanianFamilyMatcher() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    private static final class Key {
        final String narrow;
        final String broad;
        final boolean function;
        Key(String narrow, String broad, boolean function) {
            this.narrow = narrow; this.broad = broad; this.function = function;
        }
        boolean matches(Key other) {
            if (other == null) return false;
            if (function || other.function) return function == other.function && narrow.equals(other.narrow);
            if (!narrow.isEmpty() && narrow.equals(other.narrow)) return true;
            return broad.length() >= 4 && broad.equals(other.broad);
        }
    }

    private static final class SpanToken {
        final int start;
        final int end;
        final Key key;
        SpanToken(int start, int end, Key key) { this.start = start; this.end = end; this.key = key; }
    }

    public static String findSurface(String text, String phrase) {
        if (text == null || phrase == null || phrase.trim().isEmpty()) return "";
        List<Key> target = familyTokens(phrase);
        if (target.isEmpty()) return "";

        String normalized = RomanianLanguagePack.normalizeOrthography(text);
        List<SpanToken> source = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            source.add(new SpanToken(matcher.start(), matcher.end(), key(matcher.group())));
        }
        if (source.size() < target.size()) return "";

        for (int i = 0; i <= source.size() - target.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < target.size(); j++) {
                if (!source.get(i + j).key.matches(target.get(j))) { ok = false; break; }
            }
            if (ok) return normalized.substring(source.get(i).start, source.get(i + target.size() - 1).end);
        }
        return "";
    }

    private static List<Key> familyTokens(String value) {
        List<Key> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(RomanianLanguagePack.normalizeOrthography(value));
        while (matcher.find()) out.add(key(matcher.group()));
        return out;
    }

    private static Key key(String token) {
        String folded = RomanianLanguagePack.fold(token);
        boolean function = RomanianLanguagePack.isFunctionWord(folded);
        if (function) return new Key(folded, folded, true);
        return new Key(RomanianMorphology.familyKey(token), RomanianMorphology.broadKey(token), false);
    }
}
