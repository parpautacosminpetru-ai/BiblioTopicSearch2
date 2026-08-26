package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic index detector. It indexes explicit forms without generating claims. */
public final class LivingIndexEngine {
    private LivingIndexEngine() {}

    private static final Pattern DATE = Pattern.compile("\\b(?:0?[1-9]|[12]\\d|3[01])[./-](?:0?[1-9]|1[0-2])[./-](?:1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b|\\b(?:1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b");
    private static final Pattern PROPER = Pattern.compile("(?<![\\p{L}])([A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,}(?:\\s+(?:de|din|von|van|da|del|al|a|the)?\\s*[A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,}){0,3})");
    private static final Pattern MULTI_PROPER = Pattern.compile("^[A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,}(?:\\s+(?:de|din|von|van|da|del|al|a|the)?\\s*[A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,})+$");
    private static final Pattern EVENT_PHRASE = Pattern.compile("(?i)\\b(reforma|revoluția|revolutia|războiul|razboiul|tratatul|alegerile|bătălia|batalia|criza|revolta|unirea|independența|independenta|conferința|conferinta|conciliul|sinodul)\\s+([\\p{L}\\p{N}'’\\-]+(?:\\s+[\\p{L}\\p{N}'’\\-]+){0,4})");
    private static final Pattern LAW_PHRASE = Pattern.compile("(?i)\\b(legea|decretul|ordonanța|ordonanta|constituția|constitutia|regulamentul|directiva)\\s+([\\p{L}\\p{N}'’\\-]+(?:\\s+[\\p{L}\\p{N}'’\\-]+){0,4})");
    private static final Pattern WORK_PHRASE = Pattern.compile("(?i)\\b(lucrarea|cartea|romanul|tratatul|opera)\\s+[„\"']?([A-ZĂÂÎȘȚ][^,.;:!?]{2,70})");
    private static final Pattern PLACE_CUE = Pattern.compile("(?i)\\b(?:în|in|din|la|spre|către|catre)\\s+([A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,}(?:\\s+[A-ZĂÂÎȘȚ][\\p{L}'’\\-]{2,}){0,2})");
    private static final Pattern ORG_CUE = Pattern.compile("(?i)\\b((?:Universitatea|Biserica|Partidul|Consiliul|Comisia|Organizația|Organizatia|Academia|Institutul|Ministerul|Guvernul)\\s+[A-ZĂÂÎȘȚ][^,.;:!?]{1,70})");

    private static final Set<String> BAD_SINGLE_NAMES = new HashSet<>(Arrays.asList(
            "Acest", "Aceasta", "Aceste", "Acel", "Aceea", "După", "Dupa", "În", "In",
            "Din", "Prin", "Pentru", "Astfel", "Totuși", "Totusi", "Deoarece", "Dacă", "Daca",
            "Mai", "Un", "Una", "O", "Este", "Sunt", "Prima", "Primul", "Concluzie",
            "Introducere", "Exemplu", "Figura", "Tabelul", "Capitolul", "Secțiunea", "Sectiunea"
    ));

    public static final class Candidate {
        private final String surface;
        private final LivingIndexStore.Category category;
        private final double confidence;
        private final int paragraphIndex;
        private final String knownId;
        private final boolean validated;
        private final String contextCode;
        private final List<String> axes;

        Candidate(String surface, LivingIndexStore.Category category, double confidence, int paragraphIndex,
                  String knownId, boolean validated, String contextCode, List<String> axes) {
            this.surface = safe(surface);
            this.category = category == null ? LivingIndexStore.Category.INBOX : category;
            this.confidence = clamp01(confidence);
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.knownId = safe(knownId);
            this.validated = validated;
            this.contextCode = safe(contextCode);
            this.axes = Collections.unmodifiableList(new ArrayList<>(axes == null ? Collections.emptyList() : axes));
        }

        public String surface() { return surface; }
        public LivingIndexStore.Category category() { return category; }
        public double confidence() { return confidence; }
        public int paragraphIndex() { return paragraphIndex; }
        public String knownId() { return knownId; }
        public boolean validated() { return validated; }
        public String contextCode() { return contextCode; }
        public List<String> axes() { return axes; }
        public boolean isInboxCandidate() { return !validated && category == LivingIndexStore.Category.INBOX; }
    }

    public static List<Candidate> detect(List<UniversalParagraphDetector.Detection> detections,
                                         SemanticGraph graph,
                                         ParagraphCartography.Map cartography,
                                         LivingIndexStore.State known) {
        if (detections == null || detections.isEmpty()) return Collections.emptyList();
        LivingIndexStore.State state = known == null ? new LivingIndexStore.State() : known;
        Map<String, Candidate> out = new LinkedHashMap<>();

        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null || safe(detection.paragraph()).isEmpty()) continue;
            int p = detection.paragraphIndex();
            List<SemanticGraph.Proposition> propositions = graph == null
                    ? Collections.emptyList() : graph.propositionsForParagraph(p);
            UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, propositions);
            ParagraphCartography.Node node = cartography == null ? null : cartography.nodeForParagraph(p);
            List<String> axes = axisNames(frame);
            String context = contextCode(detection, frame, node);
            String paragraph = detection.paragraph();

            for (LivingIndexStore.Entry entry : state.validated()) {
                String matched = matchedAlias(paragraph, entry);
                if (!matched.isEmpty()) {
                    add(out, new Candidate(matched, entry.category(), 0.98, p, entry.id(), true, context, axes));
                }
            }

            String head = frame.head().isEmpty() ? detection.subject() : frame.head();
            if (!safe(head).isEmpty() && tokenCount(head) <= 10) {
                LivingIndexStore.Category category = categoryFor(frame.type());
                boolean safeAuto = true;
                if (category == LivingIndexStore.Category.INBOX) category = LivingIndexStore.Category.CONCEPT;

                // A multi-token capitalized name with no validated identity is not
                // auto-learned as a generic CONCEPT merely because it is the subject.
                if (category == LivingIndexStore.Category.CONCEPT
                        && state.findCanonical(head) == null
                        && MULTI_PROPER.matcher(head.trim()).matches()) {
                    category = LivingIndexStore.Category.INBOX;
                    safeAuto = false;
                }
                add(out, knownOr(state, head, category, 0.83 + detection.subjectConfidence() * 0.12,
                        p, context, axes, safeAuto));
            }

            findDates(paragraph, p, context, axes, state, out);
            findPattern(paragraph, EVENT_PHRASE, LivingIndexStore.Category.EVENT, p, context, axes, state, out, 0.86);
            findPattern(paragraph, LAW_PHRASE, LivingIndexStore.Category.LAW, p, context, axes, state, out, 0.88);
            findPattern(paragraph, WORK_PHRASE, LivingIndexStore.Category.WORK, p, context, axes, state, out, 0.76);
            findPattern(paragraph, ORG_CUE, LivingIndexStore.Category.ORGANIZATION, p, context, axes, state, out, 0.87);
            findPattern(paragraph, PLACE_CUE, LivingIndexStore.Category.PLACE, p, context, axes, state, out, 0.75);
            findProperNames(paragraph, p, context, axes, state, out);
        }
        return Collections.unmodifiableList(new ArrayList<>(out.values()));
    }

    private static void findDates(String paragraph, int p, String context, List<String> axes,
                                  LivingIndexStore.State state, Map<String, Candidate> out) {
        Matcher matcher = DATE.matcher(paragraph);
        while (matcher.find()) add(out, knownOr(state, matcher.group(), LivingIndexStore.Category.DATE, 0.99, p, context, axes, true));
    }

    private static void findPattern(String paragraph, Pattern pattern, LivingIndexStore.Category category,
                                    int p, String context, List<String> axes, LivingIndexStore.State state,
                                    Map<String, Candidate> out, double confidence) {
        Matcher matcher = pattern.matcher(paragraph);
        while (matcher.find()) {
            String value = matcher.group();
            if (matcher.groupCount() >= 1 && category == LivingIndexStore.Category.PLACE) value = matcher.group(1);
            add(out, knownOr(state, cleanTail(value), category, confidence, p, context, axes, true));
        }
    }

    private static void findProperNames(String paragraph, int p, String context, List<String> axes,
                                        LivingIndexStore.State state, Map<String, Candidate> out) {
        Matcher matcher = PROPER.matcher(paragraph);
        while (matcher.find()) {
            String value = cleanTail(matcher.group(1));
            if (value.length() < 4 || value.length() > 80) continue;
            String first = value.split("\\s+")[0];
            if (BAD_SINGLE_NAMES.contains(first) && tokenCount(value) == 1) continue;
            if (value.matches("[A-ZĂÂÎȘȚ][a-zăâîșț]+") && matcher.start() == 0 && BAD_SINGLE_NAMES.contains(value)) continue;

            LivingIndexStore.Entry known = state.findCanonical(value);
            if (known != null) {
                add(out, new Candidate(value, known.category(), 0.97, p, known.id(), known.validated(), context, axes));
            } else {
                add(out, new Candidate(value, LivingIndexStore.Category.INBOX, 0.61, p, "", false, context, axes));
            }
        }
    }

    private static Candidate knownOr(LivingIndexStore.State state, String value,
                                     LivingIndexStore.Category suggested, double confidence, int p,
                                     String context, List<String> axes, boolean safeAutoCategory) {
        LivingIndexStore.Entry known = state.findCanonical(value);
        if (known != null) {
            return new Candidate(value, known.category(), Math.max(confidence, 0.95), p,
                    known.id(), known.validated(), context, axes);
        }
        LivingIndexStore.Category category = safeAutoCategory && suggested != null
                ? suggested : LivingIndexStore.Category.INBOX;
        boolean validated = safeAutoCategory && category != LivingIndexStore.Category.INBOX;
        return new Candidate(value, category, confidence, p, "", validated, context, axes);
    }

    private static LivingIndexStore.Category categoryFor(UniversalSubjectFrame.OntologyType type) {
        if (type == null) return LivingIndexStore.Category.INBOX;
        switch (type) {
            case PERSON: return LivingIndexStore.Category.PERSON;
            case PLACE: return LivingIndexStore.Category.PLACE;
            case ORGANIZATION: return LivingIndexStore.Category.ORGANIZATION;
            case EVENT: return LivingIndexStore.Category.EVENT;
            case TIME: return LivingIndexStore.Category.DATE;
            case METHOD: return LivingIndexStore.Category.METHOD;
            case CLAIM:
            case RELATION:
            case PROCESS:
            case PHENOMENON:
            case STATE:
            case SYSTEM:
            case STRUCTURE:
            case PROPERTY:
            case MEASURE:
            case CLASS:
            case RULE:
            case ENTITY:
            case GROUP:
            case OBJECT:
                return LivingIndexStore.Category.CONCEPT;
            case UNKNOWN:
            default: return LivingIndexStore.Category.INBOX;
        }
    }

    private static String contextCode(UniversalParagraphDetector.Detection detection,
                                      UniversalSubjectFrame.Frame frame,
                                      ParagraphCartography.Node node) {
        StringBuilder out = new StringBuilder();
        out.append("F:").append(detection.function().name());
        if (node != null) out.append("|L:").append(node.depth()).append(':').append(node.link().name());
        if (frame != null && frame.type() != null) out.append("|T:").append(frame.type().name());
        if (frame != null && !frame.axes().isEmpty()) {
            out.append("|A:");
            int n = 0;
            for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
                if (n++ > 0) out.append('+');
                out.append(axis.name());
                if (n >= 7) break;
            }
        }
        return out.toString();
    }

    private static List<String> axisNames(UniversalSubjectFrame.Frame frame) {
        if (frame == null || frame.axes().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) out.add(axis.name());
        return out;
    }

    private static String matchedAlias(String paragraph, LivingIndexStore.Entry entry) {
        String foldedParagraph = " " + fold(paragraph) + " ";
        String best = "";
        for (String alias : entry.aliases()) {
            String f = fold(alias);
            if (!f.isEmpty() && foldedParagraph.contains(" " + f + " ") && alias.length() > best.length()) best = alias;
        }
        return best;
    }

    private static void add(Map<String, Candidate> out, Candidate candidate) {
        if (candidate == null || candidate.surface().isEmpty()) return;
        String key = candidate.paragraphIndex() + "|" + fold(candidate.surface());
        Candidate existing = out.get(key);
        if (existing == null) { out.put(key, candidate); return; }

        int incomingPriority = priority(candidate);
        int existingPriority = priority(existing);
        if (incomingPriority > existingPriority
                || (incomingPriority == existingPriority && candidate.confidence() > existing.confidence())) {
            out.put(key, candidate);
        }
    }

    private static int priority(Candidate candidate) {
        if (candidate.validated() && !candidate.knownId().isEmpty()) return 6;
        if (candidate.category() != LivingIndexStore.Category.CONCEPT
                && candidate.category() != LivingIndexStore.Category.TERM
                && candidate.category() != LivingIndexStore.Category.INBOX) return 5;
        if (candidate.category() == LivingIndexStore.Category.INBOX && !candidate.validated()) return 4;
        if (candidate.category() == LivingIndexStore.Category.CONCEPT) return 3;
        return 2;
    }

    private static String cleanTail(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s,.;:!?]+$", "").replaceAll("\\s+", " ").trim();
    }

    private static int tokenCount(String value) {
        String clean = safe(value);
        return clean.isEmpty() ? 0 : clean.split("\\s+").length;
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
