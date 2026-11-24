package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deliberately narrow repairs: only one digit inside a mostly alphabetic
 * token, not numbers/dates, not general edit-distance or guessed words. */
final class OcrRepair {
    private OcrRepair() {}

    static List<String> digitVariants(String token) {
        if (token == null || token.length() < 5 || token.length() > 48) return Collections.emptyList();
        int pos = -1;
        String alternatives = "";
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c)) continue;
            if (pos >= 0 || i == 0 || i == token.length() - 1) return Collections.emptyList();
            switch (c) {
                case '0': alternatives = "o"; break;
                case '1': alternatives = "il"; break;
                case '5': alternatives = "s"; break;
                default: return Collections.emptyList();
            }
            pos = i;
        }
        if (pos < 0) return Collections.emptyList();
        List<String> out = new ArrayList<>(alternatives.length());
        for (int i = 0; i < alternatives.length(); i++) {
            out.add(token.substring(0, pos) + alternatives.charAt(i) + token.substring(pos + 1));
        }
        return out;
    }

    static List<String> joinedVariants(String left, String right) {
        if (left == null || right == null || left.length() < 2 || right.length() < 2 ||
                !left.endsWith("-")) return Collections.emptyList();
        String first = left.substring(0, left.length() - 1);
        if (!letters(first) || !letters(right)) return Collections.emptyList();
        List<String> out = new ArrayList<>(2);
        out.add(first + right);
        out.add(first + "-" + right);
        return out;
    }

    private static boolean letters(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isLetter(s.charAt(i))) return false;
        return true;
    }
}
