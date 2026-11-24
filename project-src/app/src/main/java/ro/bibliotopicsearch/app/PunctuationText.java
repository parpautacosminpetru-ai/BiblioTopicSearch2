package ro.bibliotopicsearch.app;

/** The punctuation channel is independent of word tokenization. It never
 * splits numbers or hyphenated words in the ordinary word-search index. */
final class PunctuationText {
    private PunctuationText() {}

    static boolean isSign(int cp) {
        switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
            case Character.MATH_SYMBOL:
            case Character.CURRENCY_SYMBOL:
            case Character.MODIFIER_SYMBOL:
            case Character.OTHER_SYMBOL:
                return true;
            default: return false;
        }
    }

    static boolean isPattern(String value) {
        if (value == null || value.isEmpty()) return false;
        boolean found = false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (isSign(cp)) found = true;
            else if (cp != ' ' && cp != '\t' && cp != 0x00A0 && cp != 0x202F) return false;
            i += Character.charCount(cp);
        }
        return found && isSign(value.codePointAt(0)) && isSign(value.codePointBefore(value.length()));
    }

    /** Preserve spacing in adjacent punctuation. A literal ?! never
     * matches ? !; … and ... remain different. Word spacing remains as
     * in the word matcher. */
    static boolean sameSignGroups(String expected, String actual) {
        return signGroups(expected).equals(signGroups(actual));
    }

    private static java.util.List<String> signGroups(String value) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (!isSign(cp)) { i += Character.charCount(cp); continue; }
            int start = i;
            do {
                i += Character.charCount(cp);
                if (i >= value.length()) break;
                cp = value.codePointAt(i);
            } while (isSign(cp));
            out.add(value.substring(start,i));
        }
        return out;
    }
}
