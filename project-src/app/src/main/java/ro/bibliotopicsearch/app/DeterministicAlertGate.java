package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * No AI and no per-frame vibration: only explicit, reproducible lexical rules.
 * OCR highlighting is independent of this gate.
 *
 * !term = priority, ?term / ~term = contextual only, -term = visual only.
 */
public final class DeterministicAlertGate {
    public enum Mode { EXPLORARE, ECHILIBRAT, STRICT }

    public static final class Evidence {
        public final String profileId;
        /** A distinct seminar/exam subject within a selected discipline. */
        public final String topicId;
        public final String term;
        public final String observed;

        public Evidence(String profileId, String term, String observed) {
            this(profileId, "", term, observed);
        }

        public Evidence(String profileId, String topicId, String term, String observed) {
            this.profileId = profileId == null ? "" : profileId;
            this.topicId = topicId == null ? "" : topicId;
            this.term = term == null ? "" : term;
            this.observed = observed == null ? "" : observed;
        }
    }

    private static final class State {
        boolean armed = true;
        int misses;
        long lastAlert = Long.MIN_VALUE / 4;
        long lastSeen;
    }

    private static final long GLOBAL_COOLDOWN_MS = 950;
    private static final long REARM_COOLDOWN_MS = 2200;
    private static final long RETENTION_MS = 60000;
    private final Map<String, State> states = new HashMap<>();
    private long lastPulse = Long.MIN_VALUE / 4;

    public static String searchableTerm(String term) {
        if (term == null) return "";
        String value = term.trim();
        if (value.length() > 1 && isHint(value.charAt(0))) {
            String remainder = value.substring(1).trim();
            if (!remainder.isEmpty() && Character.isLetterOrDigit(remainder.codePointAt(0))) {
                return remainder;
            }
        }
        return value;
    }

    private static boolean isHint(char value) {
        return value == '!' || value == '?' || value == '~' || value == '-';
    }

    private static char hint(String term) {
        String value = term == null ? "" : term.trim();
        return !value.isEmpty() && !value.equals(searchableTerm(value))
                ? value.charAt(0) : '\0';
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String raw = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return raw.replaceAll("\\s+", " ");
    }

    private static int wordCount(String value) {
        if (value.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == ' ') count++;
        return count;
    }

    public void reset() {
        states.clear();
        lastPulse = Long.MIN_VALUE / 4;
    }

    /** Pass elapsedRealtime(), never wall-clock time. Returns at most one pulse. */
    public boolean shouldAlert(List<Evidence> observations, Mode mode, long elapsedMs) {
        if (mode == null) mode = Mode.ECHILIBRAT;
        Set<String> qualified = new TreeSet<>();
        Map<String, Set<String>> contextual = new TreeMap<>();
        for (Evidence e : observations == null ? Collections.<Evidence>emptyList() : observations) {
            if (e == null) continue;
            String term = normalized(searchableTerm(e.term));
            if (term.isEmpty() || !term.equals(normalized(e.observed))) continue;
            char hint = hint(e.term);
            if (hint == '-') continue;
            // Distinct themes are independent: two clues in different seminar
            // subjects must never accidentally make a contextual cluster.
            String profile = e.profileId.isEmpty() ? "default" : e.profileId;
            String topic = e.topicId.isEmpty() ? profile : profile + "|" + e.topicId;
            int words = wordCount(term);
            // A bare year is useful visually but is not reliable evidence for
            // a whole historical subject, even when marked ! by a user.
            if (term.matches("[0-9]+")) continue;

            if (hint == '!') {
                qualified.add("priority:" + topic + ":" + term);
            } else if (hint != '?' && hint != '~'
                    && ((mode == Mode.EXPLORARE && term.length() >= 5)
                    || (mode == Mode.ECHILIBRAT && words >= 2)
                    || (mode == Mode.STRICT && words >= 3))) {
                qualified.add("phrase:" + topic + ":" + term);
            }
            if (words >= 2 || term.length() >= 6) {
                contextual.computeIfAbsent(topic, ignored -> new TreeSet<>()).add(term);
            }
        }
        int minimum = mode == Mode.STRICT ? 3 : 2;
        for (Map.Entry<String, Set<String>> entry : contextual.entrySet()) {
            if (entry.getValue().size() >= minimum) {
                qualified.add("cluster:" + entry.getKey());
            }
        }

        for (Map.Entry<String, State> entry : new ArrayList<>(states.entrySet())) {
            if (qualified.contains(entry.getKey())) continue;
            State s = entry.getValue();
            s.misses++;
            if (s.misses >= 2 && elapsedMs - s.lastAlert >= REARM_COOLDOWN_MS) s.armed = true;
            if (elapsedMs - s.lastSeen > RETENTION_MS) states.remove(entry.getKey());
        }

        boolean pulse = false;
        for (String signal : qualified) {
            State s = states.computeIfAbsent(signal, ignored -> new State());
            s.lastSeen = elapsedMs;
            s.misses = 0;
            if (s.armed && elapsedMs - lastPulse >= GLOBAL_COOLDOWN_MS
                    && elapsedMs - s.lastAlert >= REARM_COOLDOWN_MS) {
                pulse = true;
            }
        }
        if (pulse) {
            lastPulse = elapsedMs;
            for (String signal : qualified) {
                State s = states.get(signal);
                s.armed = false;
                s.lastAlert = elapsedMs;
            }
        }
        return pulse;
    }
}
