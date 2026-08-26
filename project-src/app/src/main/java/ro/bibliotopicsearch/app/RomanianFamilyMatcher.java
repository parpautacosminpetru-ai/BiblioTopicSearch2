package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds inflected lexical-family matches while preserving the exact source surface. */
public final class RomanianFamilyMatcher {
    private RomanianFamilyMatcher() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    private static final class SpanToken {
        final int start;
        final int end;
        final String family;
        SpanToken(int start, int end, String family) {
            this.start = start; this.end = end; this.family = family;
        }
    }

    public static String findSurface(String text, String phrase) {
        if (text == null || phrase == null || phrase.trim().isEmpty()) return "";
        List<String> target = familyTokens(phrase);
        if (target.isEmpty()) return "";

        String normalized = RomanianLanguagePack.normalizeOrthography(text);
        List<SpanToken> source = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            String folded = RomanianLanguagePack.fold(token);
            String family = RomanianLanguagePack.isFunctionWord(folded)
                    ? folded : RomanianLanguagePack.familyKey(token);
            source.add(new SpanToken(matcher.start(), matcher.end(), family));
        }
        if (source.size() < target.size()) return "";

        for (int i = 0; i <= source.size() - target.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < target.size(); j++) {
                if (!source.get(i + j).family.equals(target.get(j))) { ok = false; break; }
            }
            if (ok) {
                return normalized.substring(source.get(i).start, source.get(i + target.size() - 1).end);
            }
        }
        return "";
    }

    private static List<String> familyTokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(RomanianLanguagePack.normalizeOrthography(value));
        while (matcher.find()) {
            String token = matcher.group();
            String folded = RomanianLanguagePack.fold(token);
            out.add(RomanianLanguagePack.isFunctionWord(folded)
                    ? folded : RomanianLanguagePack.familyKey(token));
        }
        return out;
    }
}
