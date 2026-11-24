package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Literal OCR tokens with original UTF-16 offsets. Punctuation is evidence,
 * not whitespace; numeric dates and hyphenated words are indivisible. */
final class LiteralText {
    private static final String ALNUM = "[\\p{L}\\p{M}\\p{N}]";
    private static final Pattern TOKENS = Pattern.compile(
            "\\p{N}+(?:[.,/:–-]\\p{N}+)+(?!"+ALNUM+")|"
            + ALNUM+"+(?:[-'’‐‑\\u00AD]"+ALNUM+"+)*|[^\\s\\p{Z}]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern LETTERS = Pattern.compile("[\\p{L}\\p{M}]+");
    private static final Pattern WORD = Pattern.compile(".*[\\p{L}\\p{N}].*", Pattern.DOTALL);

    static final class Lexeme {
        final String value;
        final int start, end;
        final boolean word;
        Lexeme(String value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
            word = WORD.matcher(value).matches();
        }
    }

    static String canonical(String raw, boolean strip) {
        String value = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFC)
                .replace('ş', 'ș').replace('ţ', 'ț')
                .replace('’', '\'').replace('‐', '-').replace('‑', '-');
        if ("\u00ad".equals(value)) value = "-";
        else value = value.replace("\u00ad", "");
        if (strip) value = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return value;
    }

    static List<Lexeme> lex(String raw, boolean strip) {
        List<Lexeme> out = new ArrayList<>();
        if (raw == null) return out;
        Matcher m = TOKENS.matcher(raw);
        while (m.find()) {
            String value = canonical(m.group(), strip);
            if (!value.isEmpty()) out.add(new Lexeme(value, m.start(), m.end()));
        }
        return out;
    }

    static List<String> values(String raw, boolean strip) {
        List<String> out = new ArrayList<>();
        for (Lexeme l : lex(raw, strip)) out.add(l.value);
        return out;
    }

    static String key(List<String> values, int start, int end) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (out.length() > 0) out.append(' ');
            out.append(values.get(i));
        }
        return out.toString();
    }

    static boolean isAlphabetic(String s) { return LETTERS.matcher(s).matches(); }

    static boolean hasWord(String s) { return WORD.matcher(s).matches(); }

    static boolean whitespaceOnly(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return true;
    }

    /** Fail closed when a recognizer's line text and element text disagree.
     * No invented spacing, skipped characters or guessed coordinates. */
    static int[] align(String raw, List<String> elements) {
        int[] offsets = new int[elements.size()];
        int cursor = 0;
        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            if (element == null || element.isEmpty()) return null;
            int at = raw.indexOf(element, cursor);
            if (at < 0 || !whitespaceOnly(raw, cursor, at)) return null;
            offsets[i] = at;
            cursor = at + element.length();
        }
        return whitespaceOnly(raw, cursor, raw.length()) ? offsets : null;
    }

    static boolean sameColumn(float aLeft, float aTop, float aRight, float aBottom,
                              float bLeft, float bTop, float bRight, float bBottom) {
        float height = Math.max(aBottom - aTop, bBottom - bTop);
        if (height <= 0 || aRight <= aLeft || bRight <= bLeft) return false;
        float overlap = Math.min(aRight, bRight) - Math.max(aLeft, bLeft);
        float gap = bTop - aBottom;
        return bTop > aTop && gap >= -height / 3f && gap <= height * 1.25f
                && overlap >= Math.min(aRight - aLeft, bRight - bLeft) * 0.5f
                && Math.abs(bLeft - aLeft) <= height * 1.6f;
    }

    /** Incomplete terminal word before a visibly attached hyphen. */
    static int terminalFragment(String raw, List<Lexeme> tokens) {
        int n = tokens.size();
        if (n < 2) return -1;
        Lexeme word = tokens.get(n-2), hyphen = tokens.get(n-1);
        if (!word.word || !"-".equals(hyphen.value) || word.end != hyphen.start
                || !whitespaceOnly(raw, hyphen.end, raw.length()) || !isAlphabetic(word.value)) return -1;
        return n-2;
    }

    /** Only whitespace may separate words that a multiword term separates
     * with spaces. Explicit punctuation tokens must also be present. */
    static boolean separated(String raw, Lexeme left, Lexeme right) {
        if (!left.word || !right.word) return true;
        return left.end < right.start && whitespaceOnly(raw, left.end, right.start);
    }
}
