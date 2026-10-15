package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic OCR-page stability gate.
 *
 * It does not infer historical meaning. It only answers whether consecutive OCR
 * frames are sufficiently similar to be treated as the same stable page and whether
 * that stable page has already been captured.
 */
public final class PageStabilityTracker {

    private static final int MIN_TOKENS = 14;
    private static final int REQUIRED_OBSERVATIONS = 3;
    private static final long REQUIRED_STABLE_MS = 420L;
    private static final double SAME_PAGE_KNOWN = 0.72;
    private static final double SAME_PAGE_UNKNOWN = 0.88;
    private static final double ALREADY_CAPTURED_UNKNOWN = 0.91;

    public static final class Observation {
        public final String page;
        public final int tokenCount;
        public final int observations;
        public final long stableForMs;
        public final double similarity;
        public final boolean sufficient;
        public final boolean stable;
        public final boolean alreadyCaptured;
        public final boolean shouldCapture;

        Observation(
                String page,
                int tokenCount,
                int observations,
                long stableForMs,
                double similarity,
                boolean sufficient,
                boolean stable,
                boolean alreadyCaptured
        ) {
            this.page = safe(page);
            this.tokenCount = tokenCount;
            this.observations = observations;
            this.stableForMs = Math.max(0L, stableForMs);
            this.similarity = clamp01(similarity);
            this.sufficient = sufficient;
            this.stable = stable;
            this.alreadyCaptured = alreadyCaptured;
            this.shouldCapture = sufficient && stable && !alreadyCaptured;
        }

        public String compact() {
            if (!sufficient) return "text insuficient";
            if (!stable) return "stabilizare " + observations + "/" + REQUIRED_OBSERVATIONS;
            if (alreadyCaptured) return "pagină deja capturată";
            return "pagină stabilă";
        }
    }

    private String candidatePage = "";
    private List<String> candidateTokens = Collections.emptyList();
    private long candidateSince;
    private int observations;

    private String capturedPage = "";
    private List<String> capturedTokens = Collections.emptyList();

    public synchronized Observation observe(String text, String page, long now) {
        List<String> tokens = tokens(text);
        String cleanPage = safe(page);
        if (tokens.size() < MIN_TOKENS) {
            resetCandidate();
            return new Observation(cleanPage, tokens.size(), 0, 0L, 0.0, false, false, false);
        }

        double similarity = candidateTokens.isEmpty() ? 0.0 : similarity(candidateTokens, tokens);
        boolean sameCandidate;
        if (candidateTokens.isEmpty()) {
            sameCandidate = false;
        } else if (!cleanPage.isEmpty() && !candidatePage.isEmpty()) {
            sameCandidate = cleanPage.equals(candidatePage) && similarity >= SAME_PAGE_KNOWN;
        } else {
            sameCandidate = similarity >= SAME_PAGE_UNKNOWN;
        }

        if (!sameCandidate) {
            candidatePage = cleanPage;
            candidateTokens = tokens;
            candidateSince = now;
            observations = 1;
            similarity = 1.0;
        } else {
            observations++;
            // Keep the richer OCR reading as the representative candidate.
            if (tokens.size() > candidateTokens.size()) candidateTokens = tokens;
            if (candidatePage.isEmpty() && !cleanPage.isEmpty()) candidatePage = cleanPage;
        }

        long stableFor = Math.max(0L, now - candidateSince);
        boolean stable = observations >= REQUIRED_OBSERVATIONS && stableFor >= REQUIRED_STABLE_MS;
        boolean captured = isAlreadyCaptured(candidatePage, candidateTokens);
        return new Observation(
                candidatePage,
                candidateTokens.size(),
                observations,
                stableFor,
                similarity,
                true,
                stable,
                captured
        );
    }

    public synchronized void markCaptured(Observation observation) {
        if (observation == null || !observation.stable) return;
        capturedPage = observation.page;
        capturedTokens = new ArrayList<>(candidateTokens);
    }

    /** Suppress re-capture of a page after the historian explicitly closes it. */
    public synchronized void markPageClosed(String page) {
        String clean = safe(page);
        if (!clean.isEmpty()) capturedPage = clean;
        if (!candidateTokens.isEmpty()) capturedTokens = new ArrayList<>(candidateTokens);
    }

    public synchronized void resetForNewSource() {
        resetCandidate();
        capturedPage = "";
        capturedTokens = Collections.emptyList();
    }

    private boolean isAlreadyCaptured(String page, List<String> tokens) {
        if (!page.isEmpty() && !capturedPage.isEmpty()) return page.equals(capturedPage);
        if (capturedTokens.isEmpty()) return false;
        return similarity(capturedTokens, tokens) >= ALREADY_CAPTURED_UNKNOWN;
    }

    private void resetCandidate() {
        candidatePage = "";
        candidateTokens = Collections.emptyList();
        candidateSince = 0L;
        observations = 0;
    }

    static double similarity(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0.0;
        Set<String> a = shingles(left);
        Set<String> b = shingles(right);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        for (String value : a) if (b.contains(value)) intersection++;
        int union = a.size() + b.size() - intersection;
        return union <= 0 ? 0.0 : intersection / (double) union;
    }

    private static Set<String> shingles(List<String> tokens) {
        Set<String> out = new HashSet<>();
        if (tokens.size() == 1) {
            out.add(tokens.get(0));
            return out;
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            out.add(tokens.get(i) + "\u0001" + tokens.get(i + 1));
        }
        return out;
    }

    private static List<String> tokens(String text) {
        String folded = RomanianLanguagePack.fold(text);
        if (folded.isEmpty()) return Collections.emptyList();
        String[] pieces = folded.split("\\s+");
        List<String> out = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (!piece.isEmpty()) out.add(piece);
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
