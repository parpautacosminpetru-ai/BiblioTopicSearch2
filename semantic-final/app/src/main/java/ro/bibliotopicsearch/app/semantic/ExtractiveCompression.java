package ro.bibliotopicsearch.app.semantic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces only extractive labels: every label is a contiguous phrase copied from evidence.
 * No generated interpretation is introduced here.
 */
final class ExtractiveCompression {
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "a", "ai", "al", "ale", "am", "ar", "are", "au", "ca", "că", "ce", "cu", "cum",
            "de", "din", "doar", "e", "era", "este", "fi", "fie", "fost", "iar", "în", "la",
            "mai", "nu", "o", "pe", "pentru", "prin", "sau", "se", "și", "sunt", "un", "una",
            "unei", "unui", "va", "vor", "the", "and", "of", "to", "in", "is", "are"
    ));

    private ExtractiveCompression() {}

    static List<String> labels(String evidence, String query, int limit) {
        if (evidence == null || evidence.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        String[] clauses = evidence.split("(?<=[.!?;:])\\s+|\\s*[,;:]\\s*");
        List<Candidate> candidates = new ArrayList<>();
        Set<String> queryTokens = tokens(SemanticCatalog.normalize(query));

        int clauseIndex = 0;
        for (String clause : clauses) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) continue;
            List<String> words = words(trimmed);
            if (words.isEmpty()) continue;
            int maxWindow = Math.min(6, words.size());
            int minWindow = Math.min(2, maxWindow);
            for (int width = minWindow; width <= maxWindow; width++) {
                for (int start = 0; start + width <= words.size(); start++) {
                    List<String> window = words.subList(start, start + width);
                    int content = 0;
                    int queryOverlap = 0;
                    for (String word : window) {
                        String norm = SemanticCatalog.normalize(word);
                        if (!STOP.contains(norm) && norm.length() > 2) content++;
                        if (queryTokens.contains(norm)) queryOverlap++;
                    }
                    if (content == 0) continue;
                    float score = content * 1.0f + queryOverlap * 2.4f - width * 0.08f - clauseIndex * 0.03f;
                    String phrase = join(window);
                    candidates.add(new Candidate(phrase, score));
                }
            }
            clauseIndex++;
        }

        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.phrase.length() < 3) continue;
            String normalized = SemanticCatalog.normalize(candidate.phrase);
            boolean redundant = false;
            for (String existing : selected) {
                String ex = SemanticCatalog.normalize(existing);
                if (ex.contains(normalized) || normalized.contains(ex)) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) selected.add(candidate.phrase);
            if (selected.size() >= limit) break;
        }
        return new ArrayList<>(selected);
    }

    private static Set<String> tokens(String normalized) {
        if (normalized == null || normalized.isEmpty()) return Collections.emptySet();
        return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
    }

    private static List<String> words(String clause) {
        String cleaned = clause.replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}]+$", "").trim();
        if (cleaned.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(Arrays.asList(cleaned.split("\\s+")));
    }

    private static String join(List<String> words) {
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(word.replaceAll("^[\\p{Punct}]+|[\\p{Punct}]+$", ""));
        }
        return out.toString().trim();
    }

    private static final class Candidate {
        final String phrase;
        final float score;

        Candidate(String phrase, float score) {
            this.phrase = phrase;
            this.score = score;
        }
    }
}
