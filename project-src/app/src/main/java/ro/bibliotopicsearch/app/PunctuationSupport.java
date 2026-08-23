package ro.bibliotopicsearch.app;

import android.graphics.Color;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared punctuation support for built-in and user-defined topic maps.
 *
 * Custom nodes do not need to live under TEXTUAL > PUNCTUAȚIE. A node becomes
 * punctuation-aware when it contains at least one punctuation-only term, or
 * when it has no explicit terms and its title clearly names a punctuation type.
 */
public final class PunctuationSupport {
    private PunctuationSupport() {}

    private static final class Family {
        final String[] aliases;
        final String[] marks;

        Family(String[] aliases, String[] marks) {
            this.aliases = aliases;
            this.marks = marks;
        }
    }

    private static final Family[] FAMILIES = new Family[] {
            new Family(
                    new String[]{"punct finalizare", "punct final", "period", "full stop", "dot"},
                    new String[]{".", "．", "。"}
            ),
            new Family(
                    new String[]{"virgula", "comma"},
                    new String[]{",", "，", "、"}
            ),
            new Family(
                    new String[]{"punct si virgula", "semicolon"},
                    new String[]{";", "；"}
            ),
            new Family(
                    new String[]{"doua puncte", "colon"},
                    new String[]{":", "："}
            ),
            new Family(
                    new String[]{"intrebare", "semn de intrebare", "question", "question mark"},
                    new String[]{"?", "？", "⁇", "¿"}
            ),
            new Family(
                    new String[]{"exclamare", "semn de exclamare", "exclamation", "exclamation mark"},
                    new String[]{"!", "！", "‼", "¡"}
            ),
            new Family(
                    new String[]{"suspensie", "puncte de suspensie", "elipsa", "ellipsis"},
                    new String[]{"...", "…", "⋯", ".."}
            ),
            new Family(
                    new String[]{"linie de pauza", "linie pauza", "linie insertie", "dash", "em dash", "en dash"},
                    new String[]{"—", "–", "―", "‒", "−"}
            ),
            new Family(
                    new String[]{"cratima", "hyphen"},
                    new String[]{"-", "‐", "‑", "﹣", "－"}
            ),
            new Family(
                    new String[]{"paranteze", "paranteze rotunde", "parentheses", "round brackets"},
                    new String[]{"(", ")", "（", "）"}
            ),
            new Family(
                    new String[]{"paranteze drepte", "square brackets", "brackets"},
                    new String[]{"[", "]", "［", "］", "【", "】", "〔", "〕"}
            ),
            new Family(
                    new String[]{"acolade", "braces", "curly brackets"},
                    new String[]{"{", "}", "｛", "｝"}
            ),
            new Family(
                    new String[]{"ghilimele", "citate", "quotes", "quotation marks", "double quotes"},
                    new String[]{"\"", "„", "”", "“", "«", "»", "〝", "〞", "＂"}
            ),
            new Family(
                    new String[]{"apostrof", "apostrophe", "single quote", "single quotes"},
                    new String[]{"'", "’", "‘", "‚", "‛", "ʼ", "＇", "‹", "›"}
            ),
            new Family(
                    new String[]{"bara oblica", "slash", "forward slash"},
                    new String[]{"/", "⁄", "／"}
            ),
            new Family(
                    new String[]{"bara inversa", "backslash"},
                    new String[]{"\\", "＼"}
            ),
            new Family(
                    new String[]{"punct median", "marcator", "bullet", "middle dot"},
                    new String[]{"·", "•", "‧", "∙"}
            ),
            new Family(
                    new String[]{"bara verticala", "pipe", "vertical bar"},
                    new String[]{"|", "¦", "｜"}
            ),
            new Family(
                    new String[]{"underscore", "linie jos", "subliniere"},
                    new String[]{"_", "＿"}
            )
    };

    /**
     * Returns all punctuation marks represented by a node, including Unicode
     * variants from the same punctuation family.
     */
    public static List<String> marksForNode(TopicNode node) {
        LinkedHashSet<String> marks = new LinkedHashSet<>();
        if (node == null) return new ArrayList<>(marks);

        LinkedHashSet<Family> matchedFamilies = new LinkedHashSet<>();
        for (String term : node.terms) {
            String raw = term == null ? "" : term.trim();
            if (!isPunctuationTerm(raw)) continue;

            marks.add(raw);
            Family family = familyForMark(raw);
            if (family != null) matchedFamilies.add(family);
        }

        String normalizedTitle = normalizeWords(node.title);
        boolean mayInferFromTitle = !marks.isEmpty() || node.terms.isEmpty();
        if (mayInferFromTitle) {
            for (Family family : FAMILIES) {
                if (titleMatchesFamily(normalizedTitle, family)) {
                    matchedFamilies.add(family);
                }
            }
        }

        for (Family family : matchedFamilies) {
            for (String mark : family.marks) {
                marks.add(mark);
            }
        }

        return new ArrayList<>(marks);
    }

    public static boolean isPunctuationTerm(String value) {
        if (value == null) return false;
        String raw = value.trim();
        if (raw.isEmpty()) return false;

        boolean hasVisibleMark = false;
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) continue;

            int type = Character.getType(codePoint);
            boolean punctuation = type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || type == Character.FINAL_QUOTE_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION;
            boolean symbol = type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL
                    || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL;

            if (!punctuation && !symbol) return false;
            hasVisibleMark = true;
        }

        return hasVisibleMark;
    }

    /**
     * Keeps existing colors whenever they are already unique. If a custom or
     * merged punctuation node reuses a color, only the collision is reassigned.
     */
    public static void ensureDistinctColors(List<TopicNode> punctuationNodes) {
        if (punctuationNodes == null || punctuationNodes.isEmpty()) return;

        Set<Integer> used = new HashSet<>();
        int generatedIndex = 0;

        for (TopicNode node : punctuationNodes) {
            if (node == null) continue;
            if (used.add(node.color)) continue;

            int candidate;
            do {
                candidate = generatedColor(generatedIndex++);
            } while (used.contains(candidate));

            node.color = candidate;
            used.add(candidate);
        }
    }

    private static int generatedColor(int index) {
        float hue = (17f + index * 137.50776f) % 360f;
        float saturation = 0.64f + (index % 3) * 0.08f;
        float value = 0.88f - (index % 2) * 0.07f;
        return Color.HSVToColor(new float[]{hue, saturation, value});
    }

    private static Family familyForMark(String mark) {
        for (Family family : FAMILIES) {
            for (String candidate : family.marks) {
                if (candidate.equals(mark)) return family;
            }
        }
        return null;
    }

    private static boolean titleMatchesFamily(String normalizedTitle, Family family) {
        if (normalizedTitle.isEmpty()) return false;
        String paddedTitle = " " + normalizedTitle + " ";

        for (String alias : family.aliases) {
            String normalizedAlias = normalizeWords(alias);
            if (normalizedAlias.isEmpty()) continue;
            if (normalizedTitle.equals(normalizedAlias)
                    || paddedTitle.contains(" " + normalizedAlias + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeWords(String value) {
        if (value == null) return "";
        String out = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return out;
    }
}
