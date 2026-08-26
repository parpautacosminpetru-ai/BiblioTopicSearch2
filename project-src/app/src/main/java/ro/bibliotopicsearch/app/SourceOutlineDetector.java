package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects physical/editorial source structure without storing page images or body text. */
public final class SourceOutlineDetector {
    private SourceOutlineDetector() {}

    private static final Pattern PART = Pattern.compile("(?iu)^(?:partea|parte|part)\\s+([IVXLCDM]+|\\d+)\\b(?:\\s*[-–—:.]?\\s*(.*))?$");
    private static final Pattern CHAPTER = Pattern.compile("(?iu)^(?:capitolul|capitol|chapter)\\s+([IVXLCDM]+|\\d+)\\b(?:\\s*[-–—:.]?\\s*(.*))?$");
    private static final Pattern SECTION = Pattern.compile("(?iu)^(?:secțiunea|sectiunea|secțiune|sectiune|section)\\s+([IVXLCDM]+|\\d+(?:\\.\\d+)*)\\b(?:\\s*[-–—:.]?\\s*(.*))?$");
    private static final Pattern SUBSECTION = Pattern.compile("(?iu)^(?:subsecțiunea|subsectiunea|subsecțiune|subsectiune|subsection)\\s+([IVXLCDM]+|\\d+(?:\\.\\d+)*)\\b(?:\\s*[-–—:.]?\\s*(.*))?$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+){1,5})[.)]?\\s+(.{2,100})$");
    private static final Pattern ROMAN_ONLY = Pattern.compile("^[IVXLCDM]{1,10}[.)]\\s+(.{2,100})$");

    public static final class Heading {
        public final int paragraphIndex;
        public final String kind;
        public final int depth;
        public final String title;
        public final double confidence;

        Heading(int paragraphIndex, String kind, int depth, String title, double confidence) {
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.kind = kind == null ? "HEADING" : kind;
            this.depth = Math.max(0, depth);
            this.title = title == null ? "" : title.trim();
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    public static List<Heading> detect(List<UniversalParagraphDetector.Detection> detections) {
        if (detections == null || detections.isEmpty()) return Collections.emptyList();
        List<Heading> out = new ArrayList<>();
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            Heading heading = detectOne(detection.paragraph(), detection.paragraphIndex());
            if (heading != null) out.add(heading);
        }
        return Collections.unmodifiableList(out);
    }

    public static Heading detectOne(String raw, int paragraphIndex) {
        String value = normalizeLine(raw);
        if (value.isEmpty() || value.length() > 140 || tokenCount(value) > 18) return null;

        Matcher m = PART.matcher(value);
        if (m.matches()) return new Heading(paragraphIndex, "PART", 0, title(value, m), 0.99);
        m = CHAPTER.matcher(value);
        if (m.matches()) return new Heading(paragraphIndex, "CHAPTER", 1, title(value, m), 0.99);
        m = SECTION.matcher(value);
        if (m.matches()) return new Heading(paragraphIndex, "SECTION", 2, title(value, m), 0.97);
        m = SUBSECTION.matcher(value);
        if (m.matches()) return new Heading(paragraphIndex, "SUBSECTION", 3, title(value, m), 0.97);

        m = NUMBERED.matcher(value);
        if (m.matches()) {
            String number = m.group(1);
            int depth = Math.min(6, 1 + count(number, '.'));
            return new Heading(paragraphIndex, "NUMBERED", depth, value, 0.93);
        }
        m = ROMAN_ONLY.matcher(value);
        if (m.matches()) return new Heading(paragraphIndex, "ROMAN_HEADING", 2, value, 0.83);

        // Conservative typographic fallback: short all-uppercase lines only.
        if (value.length() >= 4 && value.length() <= 90 && letters(value) >= 4
                && uppercaseRatio(value) >= 0.92 && !value.endsWith(".") && !value.endsWith(";")) {
            return new Heading(paragraphIndex, "TITLE", 2, value, 0.76);
        }
        return null;
    }

    private static String title(String full, Matcher matcher) {
        if (matcher.groupCount() >= 2) {
            String tail = matcher.group(2);
            if (tail != null && !tail.trim().isEmpty()) return full;
        }
        return full;
    }

    private static String normalizeLine(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int tokenCount(String value) {
        return value.isEmpty() ? 0 : value.split("\\s+").length;
    }

    private static int count(String value, char c) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == c) n++;
        return n;
    }

    private static int letters(String value) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) if (Character.isLetter(value.charAt(i))) n++;
        return n;
    }

    private static double uppercaseRatio(String value) {
        int letters = 0, upper = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if (Character.isUpperCase(c)) upper++;
        }
        return letters == 0 ? 0.0 : upper / (double) letters;
    }
}