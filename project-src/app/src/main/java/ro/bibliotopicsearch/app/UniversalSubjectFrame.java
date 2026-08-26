package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compositional, language-facing subject representation for semantic cartography.
 *
 * Instead of enumerating every possible domain subject, the frame decomposes an
 * explicit subject span into a reusable head plus orthogonal semantic axes. One
 * subject can therefore live on several axes at the same time (for example:
 * EFFECT + ECONOMIC + POPULATION + LOCATION + TIME).
 */
public final class UniversalSubjectFrame {
    private UniversalSubjectFrame() {}

    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20)\\d{2}\\b");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");

    public enum OntologyType {
        ENTITY,
        PERSON,
        GROUP,
        ORGANIZATION,
        OBJECT,
        PLACE,
        TIME,
        EVENT,
        PROCESS,
        PHENOMENON,
        STATE,
        SYSTEM,
        STRUCTURE,
        PROPERTY,
        MEASURE,
        RELATION,
        CLASS,
        RULE,
        METHOD,
        CLAIM,
        UNKNOWN
    }

    public enum Axis {
        RELATION,
        DOMAIN,
        TARGET,
        AGENT,
        PATIENT,
        LOCATION,
        TIME,
        POPULATION,
        CONDITION,
        PURPOSE,
        MECHANISM,
        CAUSE,
        EFFECT,
        COMPARISON,
        QUANTITY,
        EVIDENCE,
        PROBLEM,
        SOLUTION,
        METHOD,
        RISK,
        SCOPE
    }

    public static final class Frame {
        private final String explicitSpan;
        private final String head;
        private final OntologyType type;
        private final Map<Axis, List<String>> axes;
        private final Set<SemanticGraph.Operator> operators;
        private final List<String> parentConcepts;
        private final double confidence;

        Frame(
                String explicitSpan,
                String head,
                OntologyType type,
                Map<Axis, List<String>> axes,
                Set<SemanticGraph.Operator> operators,
                List<String> parentConcepts,
                double confidence
        ) {
            this.explicitSpan = safe(explicitSpan);
            this.head = safe(head);
            this.type = type == null ? OntologyType.UNKNOWN : type;
            EnumMap<Axis, List<String>> copy = new EnumMap<>(Axis.class);
            if (axes != null) {
                for (Map.Entry<Axis, List<String>> entry : axes.entrySet()) {
                    LinkedHashSet<String> unique = new LinkedHashSet<>();
                    if (entry.getValue() != null) {
                        for (String value : entry.getValue()) {
                            String clean = safe(value);
                            if (!clean.isEmpty()) unique.add(clean);
                        }
                    }
                    if (!unique.isEmpty()) copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(unique)));
                }
            }
            this.axes = Collections.unmodifiableMap(copy);
            EnumSet<SemanticGraph.Operator> opCopy = operators == null || operators.isEmpty()
                    ? EnumSet.noneOf(SemanticGraph.Operator.class)
                    : EnumSet.copyOf(operators);
            this.operators = Collections.unmodifiableSet(opCopy);
            this.parentConcepts = Collections.unmodifiableList(new ArrayList<>(
                    parentConcepts == null ? Collections.emptyList() : parentConcepts
            ));
            this.confidence = clamp01(confidence);
        }

        public String explicitSpan() { return explicitSpan; }
        public String head() { return head; }
        public OntologyType type() { return type; }
        public Map<Axis, List<String>> axes() { return axes; }
        public List<String> axis(Axis axis) { return axes.getOrDefault(axis, Collections.emptyList()); }
        public boolean hasAxis(Axis axis) { return axes.containsKey(axis) && !axes.get(axis).isEmpty(); }
        public Set<SemanticGraph.Operator> operators() { return operators; }
        public List<String> parentConcepts() { return parentConcepts; }
        public double confidence() { return confidence; }

        public String compactLabel() {
            StringBuilder out = new StringBuilder(head.isEmpty() ? explicitSpan : head);
            int count = 0;
            for (Axis axis : axes.keySet()) {
                if (count++ >= 3) break;
                out.append(" · ").append(axis.name());
            }
            return out.toString();
        }
    }

    public static Frame from(
            UniversalParagraphDetector.Detection detection,
            List<SemanticGraph.Proposition> propositions
    ) {
        if (detection == null) return empty();
        String subject = safe(detection.subject());
        if (subject.isEmpty()) subject = firstExplicitSubject(propositions);
        if (subject.isEmpty()) subject = safe(detection.paragraph());

        EnumMap<Axis, List<String>> axes = new EnumMap<>(Axis.class);
        EnumSet<SemanticGraph.Operator> operators = EnumSet.noneOf(SemanticGraph.Operator.class);

        addTextualAxes(subject, axes);
        addFunctionAxes(detection.function(), subject, axes);

        if (propositions != null) {
            for (SemanticGraph.Proposition proposition : propositions) {
                if (proposition == null) continue;
                operators.addAll(proposition.operators());
                addPropositionAxes(proposition, axes);
            }
        }

        String head = deriveHead(subject, axes);
        OntologyType type = detectType(subject, head, detection, propositions);
        List<String> parents = buildParents(subject, head, axes);

        double confidence = 0.44 + detection.subjectConfidence() * 0.32;
        if (type != OntologyType.UNKNOWN) confidence += 0.08;
        confidence += Math.min(0.12, axes.size() * 0.018);
        if (!head.isEmpty()) confidence += 0.04;

        return new Frame(subject, head, type, axes, operators, parents, confidence);
    }

    public static Frame from(UniversalParagraphDetector.Detection detection, SemanticGraph graph) {
        List<SemanticGraph.Proposition> propositions = graph == null || detection == null
                ? Collections.emptyList()
                : graph.propositionsForParagraph(detection.paragraphIndex());
        return from(detection, propositions);
    }

    private static Frame empty() {
        return new Frame("", "", OntologyType.UNKNOWN, Collections.emptyMap(),
                Collections.emptySet(), Collections.emptyList(), 0.0);
    }

    private static void addTextualAxes(String subject, EnumMap<Axis, List<String>> axes) {
        String folded = " " + fold(subject) + " ";

        putIfCue(axes, Axis.RELATION, subject, folded,
                " efect ", " efecte ", " consecinta ", " consecinte ", " impact ",
                " cauza ", " cauze ", " relatie ", " legatura ", " influenta ");
        putIfCue(axes, Axis.DOMAIN, subject, folded,
                " economic ", " economica ", " economice ", " financiar ", " financiara ",
                " social ", " sociala ", " sociale ", " politic ", " politica ",
                " juridic ", " juridica ", " medical ", " medicala ", " biologic ",
                " tehnologic ", " educational ", " ecologic ", " cultural ");
        putIfCue(axes, Axis.POPULATION, subject, folded,
                " populatie ", " populatia ", " gospodarii ", " gospodariilor ", " pacienti ",
                " copii ", " adulti ", " femei ", " barbati ", " angajati ", " consumatori ",
                " studenti ", " elevi ", " utilizatori ");
        putIfCue(axes, Axis.TARGET, subject, folded,
                " asupra ", " pentru ", " catre ", " fata de ");
        putIfCue(axes, Axis.CONDITION, subject, folded,
                " in conditii ", " conditia ", " daca ", " in cazul ");
        putIfCue(axes, Axis.PURPOSE, subject, folded,
                " scop ", " obiectiv ", " pentru a ", " in vederea ");
        putIfCue(axes, Axis.COMPARISON, subject, folded,
                " comparativ ", " comparatie ", " fata de ", " versus ", " decat ");
        putIfCue(axes, Axis.QUANTITY, subject, folded,
                " procent ", " rata ", " nivel ", " valoare ", " cantitate ", " numar ");
        putIfCue(axes, Axis.METHOD, subject, folded,
                " metoda ", " metodologie ", " tehnica ", " procedura ", " abordare ");
        putIfCue(axes, Axis.RISK, subject, folded,
                " risc ", " riscuri ", " pericol ", " vulnerabilitate ");
        putIfCue(axes, Axis.PROBLEM, subject, folded,
                " problema ", " dificultate ", " obstacol ", " limitare ");
        putIfCue(axes, Axis.SOLUTION, subject, folded,
                " solutie ", " interventie ", " remediu ", " masura ");

        Matcher year = YEAR.matcher(subject);
        if (year.find()) add(axes, Axis.TIME, year.group());
        String location = extractLocation(subject);
        if (!location.isEmpty()) add(axes, Axis.LOCATION, location);
    }

    private static void addFunctionAxes(
            UniversalDetectionLexicon.Function function,
            String subject,
            EnumMap<Axis, List<String>> axes
    ) {
        if (function == null) return;
        switch (function) {
            case CAUSE_EFFECT:
                add(axes, Axis.CAUSE, subject);
                add(axes, Axis.EFFECT, subject);
                break;
            case PURPOSE:
                add(axes, Axis.PURPOSE, subject);
                break;
            case CONDITION:
                add(axes, Axis.CONDITION, subject);
                break;
            case COMPARISON:
            case CONTRAST:
                add(axes, Axis.COMPARISON, subject);
                break;
            case EVIDENCE:
            case ARGUMENTATION:
                add(axes, Axis.EVIDENCE, subject);
                break;
            case PROBLEM:
                add(axes, Axis.PROBLEM, subject);
                break;
            case SOLUTION:
                add(axes, Axis.SOLUTION, subject);
                break;
            case SEQUENCE:
            case EXPLANATION:
                add(axes, Axis.MECHANISM, subject);
                break;
            default:
                break;
        }
    }

    private static void addPropositionAxes(
            SemanticGraph.Proposition proposition,
            EnumMap<Axis, List<String>> axes
    ) {
        String raw = proposition.raw();
        switch (proposition.relation()) {
            case CAUSE:
                add(axes, Axis.CAUSE, valueOr(raw, proposition.slot(SemanticGraph.Slot.WHY)));
                break;
            case EFFECT:
                add(axes, Axis.EFFECT, valueOr(raw, proposition.slot(SemanticGraph.Slot.EFFECT)));
                break;
            case CONDITION:
                add(axes, Axis.CONDITION, valueOr(raw, proposition.slot(SemanticGraph.Slot.CONDITION)));
                break;
            case PURPOSE:
                add(axes, Axis.PURPOSE, valueOr(raw, proposition.slot(SemanticGraph.Slot.PURPOSE)));
                break;
            case COMPARISON:
                add(axes, Axis.COMPARISON, valueOr(raw, proposition.slot(SemanticGraph.Slot.COMPARISON)));
                break;
            case EVIDENCE:
                add(axes, Axis.EVIDENCE, valueOr(raw, proposition.slot(SemanticGraph.Slot.EVIDENCE)));
                break;
            case MECHANISM:
            case SEQUENCE:
                add(axes, Axis.MECHANISM, valueOr(raw, proposition.slot(SemanticGraph.Slot.HOW)));
                break;
            case PROBLEM:
                add(axes, Axis.PROBLEM, raw);
                break;
            case SOLUTION:
                add(axes, Axis.SOLUTION, raw);
                break;
            default:
                break;
        }

        addIfPresent(axes, Axis.LOCATION, proposition.slot(SemanticGraph.Slot.WHERE));
        addIfPresent(axes, Axis.TIME, proposition.slot(SemanticGraph.Slot.WHEN));
        addIfPresent(axes, Axis.QUANTITY, proposition.slot(SemanticGraph.Slot.QUANTITY));
        addIfPresent(axes, Axis.TARGET, proposition.object());
        addIfPresent(axes, Axis.AGENT, proposition.subject());
    }

    private static OntologyType detectType(
            String subject,
            String head,
            UniversalParagraphDetector.Detection detection,
            List<SemanticGraph.Proposition> propositions
    ) {
        String folded = " " + fold(subject) + " ";
        if (containsAny(folded, " persoana ", " autor ", " cercetator ", " pacient ", " individ ")) return OntologyType.PERSON;
        if (containsAny(folded, " grup ", " populatie ", " gospodarii ", " comunitate ", " echipa ")) return OntologyType.GROUP;
        if (containsAny(folded, " companie ", " institutie ", " organizatie ", " universitate ", " guvern ")) return OntologyType.ORGANIZATION;
        if (containsAny(folded, " sistem ", " retea ", " ecosistem ")) return OntologyType.SYSTEM;
        if (containsAny(folded, " structura ", " arhitectura ", " componenta ")) return OntologyType.STRUCTURE;
        if (containsAny(folded, " proces ", " mecanism ", " procedura ", " evolutie ")) return OntologyType.PROCESS;
        if (containsAny(folded, " fenomen ", " inflatie ", " schimbare ", " efect ", " impact ")) return OntologyType.PHENOMENON;
        if (containsAny(folded, " stare ", " situatie ", " conditie ")) return OntologyType.STATE;
        if (containsAny(folded, " eveniment ", " criza ", " razboi ", " alegeri ")) return OntologyType.EVENT;
        if (containsAny(folded, " metoda ", " tehnica ", " procedura ", " abordare ")) return OntologyType.METHOD;
        if (containsAny(folded, " rata ", " indice ", " nivel ", " valoare ", " scor ", " procent ")) return OntologyType.MEASURE;
        if (containsAny(folded, " relatie ", " legatura ", " asociere ", " corelatie ")) return OntologyType.RELATION;
        if (containsAny(folded, " tipuri ", " clasa ", " categorie ", " clasificare ")) return OntologyType.CLASS;
        if (containsAny(folded, " regula ", " principiu ", " lege ", " norma ")) return OntologyType.RULE;
        if (containsAny(folded, " loc ", " regiune ", " tara ", " oras ", " romania ")) return OntologyType.PLACE;
        if (YEAR.matcher(subject).find()) return OntologyType.TIME;
        if (detection != null && detection.function() == UniversalDetectionLexicon.Function.ARGUMENTATION) return OntologyType.CLAIM;
        if (propositions != null) {
            for (SemanticGraph.Proposition proposition : propositions) {
                if (proposition.relation() == SemanticGraph.Relation.MECHANISM) return OntologyType.PROCESS;
            }
        }
        if (!head.isEmpty()) return OntologyType.ENTITY;
        return OntologyType.UNKNOWN;
    }

    private static String deriveHead(String subject, Map<Axis, List<String>> axes) {
        List<String> tokens = tokens(subject);
        if (tokens.isEmpty()) return "";
        String folded = fold(subject);

        String[] relationStarts = {
                "efectele ", "efectul ", "cauzele ", "cauza ", "impactul ", "rolul ",
                "relatia dintre ", "legatura dintre ", "mecanismul ", "procesul ",
                "problema ", "solutia ", "riscurile ", "riscul ", "metoda ", "metodele "
        };
        for (String prefix : relationStarts) {
            String p = fold(prefix);
            if (folded.startsWith(p)) {
                String rest = subject.substring(Math.min(subject.length(), approximatePrefixEnd(subject, prefix))).trim();
                rest = rest.replaceFirst("^(?i)(ale|al|a|ai|asupra|dintre|privind|pentru)\\s+", "").trim();
                if (!rest.isEmpty()) return trimContextTail(rest);
            }
        }
        return trimContextTail(subject);
    }

    private static String trimContextTail(String value) {
        String out = safe(value);
        String folded = fold(out);
        String[] cues = {" asupra ", " in romania", " în românia", " dupa ", " după ", " in perioada ", " în perioada ", " pentru "};
        int best = out.length();
        for (String cue : cues) {
            int i = folded.indexOf(fold(cue));
            if (i > 0) best = Math.min(best, i);
        }
        return safe(out.substring(0, Math.min(best, out.length())));
    }

    private static List<String> buildParents(String subject, String head, Map<Axis, List<String>> axes) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!head.isEmpty()) out.add(head);
        String folded = fold(subject);
        if (folded.contains("efect")) out.add("efectele " + head);
        if (folded.contains("cauz")) out.add("cauzele " + head);
        if (axes.containsKey(Axis.DOMAIN) && !head.isEmpty()) out.add("dimensiunea de domeniu a " + head);
        if (axes.containsKey(Axis.TARGET) && !head.isEmpty()) out.add(head + " asupra țintei");
        return new ArrayList<>(out);
    }

    private static String extractLocation(String raw) {
        Matcher matcher = Pattern.compile(
                "\\b(?:în|in|la|din)\\s+([\\p{L}][\\p{L}\\-]*(?:\\s+[\\p{L}][\\p{L}\\-]*){0,3})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String candidate = safe(matcher.group());
            String folded = " " + fold(candidate) + " ";
            if (containsAny(folded, " in cazul ", " in vederea ", " in perioada ", " in anul ")) continue;
            return candidate;
        }
        return "";
    }

    private static void putIfCue(
            EnumMap<Axis, List<String>> axes,
            Axis axis,
            String value,
            String folded,
            String... cues
    ) {
        if (containsAny(folded, cues)) add(axes, axis, value);
    }

    private static void addIfPresent(EnumMap<Axis, List<String>> axes, Axis axis, String value) {
        if (!safe(value).isEmpty()) add(axes, axis, value);
    }

    private static void add(EnumMap<Axis, List<String>> axes, Axis axis, String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return;
        axes.computeIfAbsent(axis, ignored -> new ArrayList<>()).add(clean);
    }

    private static boolean containsAny(String value, String... cues) {
        for (String cue : cues) if (value.contains(cue)) return true;
        return false;
    }

    private static int approximatePrefixEnd(String raw, String prefix) {
        List<String> rawTokens = tokens(raw);
        List<String> prefixTokens = tokens(prefix);
        if (prefixTokens.isEmpty() || rawTokens.isEmpty()) return 0;
        Matcher matcher = TOKEN.matcher(raw);
        int seen = 0;
        while (matcher.find()) {
            seen++;
            if (seen >= prefixTokens.size()) return matcher.end();
        }
        return 0;
    }

    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    private static String firstExplicitSubject(List<SemanticGraph.Proposition> propositions) {
        if (propositions == null) return "";
        for (SemanticGraph.Proposition proposition : propositions) {
            if (proposition != null && !proposition.subject().isEmpty()) return proposition.subject();
        }
        return "";
    }

    private static String valueOr(String fallback, String preferred) {
        return safe(preferred).isEmpty() ? safe(fallback) : safe(preferred);
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
