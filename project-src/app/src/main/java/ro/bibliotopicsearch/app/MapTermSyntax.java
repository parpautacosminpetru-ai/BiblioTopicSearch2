package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;

/** Preserve the existing # headings and | separators, but let a user
 * enter the literal characters through the normal Add term dialog. */
final class MapTermSyntax {
    private MapTermSyntax() {}

    static String encode(String term) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (c == '\\' || c == '|' || (i == 0 &&
                    (c == '#' || ((c == '-' || c == '•') && term.length() > 1 && term.charAt(1) == ' '))))
                out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    static List<String> decode(String line) {
        List<String> out = new ArrayList<>();
        if (line.startsWith("- ") || line.startsWith("• ")) line = line.substring(2);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i+1 < line.length()) {
                char next = line.charAt(i+1);
                if (next == '\\' || next == '|' || (value.toString().trim().isEmpty() &&
                        (next == '#' || next == '-' || next == '•'))) {
                    value.append(next); i++; continue;
                }
            }
            if (c == '|') { add(out,value); value.setLength(0); }
            else value.append(c);
        }
        add(out,value);
        return out;
    }
    private static void add(List<String> out,StringBuilder value) {
        String term=value.toString().trim();
        if(!term.isEmpty()&&!out.contains(term))out.add(term);
    }
}
