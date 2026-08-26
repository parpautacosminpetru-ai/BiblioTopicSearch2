package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline research relevance engine.
 *
 * Semantic Engine v2 aligns the research query with clause-level propositions,
 * relation types and operator scope. Every emitted answer remains a literal span
 * from the source text; the engine never synthesizes missing content.
 */
public final class ResearchSemanticEngine {
    private ResearchSemanticEngine() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20)\\d{2}\\b");

    public enum Intent {
        TOPIC,
        DEFINITION,
        WHY,
        HOW,
        EFFECT,
        CONDITION,
        WHEN,
        WHERE,
        WHO,
        COMPARISON,
        PURPOSE,
        EVIDENCE,
        PROBLEM,
        SOLUTION
    }

    public static final class Profile {
        private final String rawQuery;
        private final String displayQuery;
        private final Intent intent;
        private final Set<Intent> intents;
        private final boolean explicitQuestion;
        private final boolean asksNegation;
        private final boolean asksModality;
        private final Set<String> directTerms;
        private final Set<String> aliasTerms;

        private Profile(
                String rawQuery,
                String displayQuery,
                Intent intent,
                Set<Intent> intents,
                boolean explicitQuestion,
                boolean asksNegation,
                boolean asksModality,
                Set<String> directTerms,
                Set<String> aliasTerms
        ) {
            this.rawQuery = safe(rawQuery);
            this.displayQuery = safe(displayQuery);
            this.intent = intent == null ? Intent.TOPIC : intent;
            EnumSet<Intent> intentCopy = intents == null || intents.isEmpty()
                    ? EnumSet.of(this.intent)
                    : EnumSet.copyOf(intents);
            this.intents = Collections.unmodifiableSet(intentCopy);
            this.explicitQuestion = explicitQuestion;
            this.asksNegation = asksNegation;
            this.asksModality = asksModality;
            this.directTerms = Collections.unmodifiableSet(new LinkedHashSet<>(directTerms));
            this.aliasTerms = Collections.unmodifiableSet(new LinkedHashSet<>(aliasTerms));
        }

        public String rawQuery() { return rawQuery; }
        public String displayQuery() { return displayQuery; }
        public Intent intent() { return intent; }
        public Set<Intent> intents() { return intents; }
        public boolean explicitQuestion() { return explicitQuestion; }
        public boolean asksNegation() { return asksNegation; }
        public boolean asksModality() { return asksModality; }
        public Set<String> directTerms() { return directTerms; }
        public Set<String> aliasTerms() { return aliasTerms; }
        public boolean enabled() { return !directTerms.isEmpty() || !aliasTerms.isEmpty(); }
    }

    public static final class Answer {
        private final int paragraphIndex;
        private final String segment;
        private final double score;
        private final double directCoverage;
        private final double relationEvidence;
        private final Intent intent;
        private final List<String> matchedTerms;
        private final Map<SemanticGraph.Slot, String> slots;
        private final Set<SemanticGraph.Operator> operators;
        private final SemanticGraph.Relation relation;
        private final String subject;
        private final String predicate;
        private final String object;

        private Answer(
                int paragraphIndex,
                String segment,
                double score,
                double directCoverage,
                double relationEvidence,
                Intent intent,
                List<String> matchedTerms,
                Map<SemanticGraph.Slot, String> slots,
                Set<SemanticGraph.Operator> operators,
                SemanticGraph.Relation relation,
                String subject,
                String predicate,
                String object
        ) {
            this.paragraphIndex = paragraphIndex;
            this.segment = safe(segment);
            this.score = clamp01(score);
            this.directCoverage = clamp01(directCoverage);
            this.relationEvidence = clamp01(relationEvidence);
            this.intent = intent == null ? Intent.TOPIC : intent;
            this.matchedTerms = Collections.unmodifiableList(new ArrayList<>(matchedTerms));

            EnumMap<SemanticGraph.Slot, String> slotCopy = new EnumMap<>(SemanticGraph.Slot.class);
            if (slots != null) slotCopy.putAll(slots);
            this.slots = Collections.unmodifiableMap(slotCopy);

            EnumSet<SemanticGraph.Operator> operatorCopy = operators == null || operators.isEmpty()
                    ? EnumSet.noneOf(SemanticGraph.Operator.class)
                    : EnumSet.copyOf(operators);
            this.operators = Collections.unmodifiableSet(operatorCopy);
            this.relation = relation == null ? SemanticGraph.Relation.GENERIC : relation;
            this.subject = safe(subject);
            this.predicate = safe(predicate);
            this.object = safe(object);
        }

        public int paragraphIndex() { return paragraphIndex; }
        public String segment() { return segment; }
        public double score() { return score; }
        public double directCoverage() { return directCoverage; }
        public double relationEvidence() { return relationEvidence; }
        public Intent intent() { return intent; }
        public List<String> matchedTerms() { return matchedTerms; }
        public Map<SemanticGraph.Slot, String> slots() { return slots; }
        public String slot(SemanticGraph.Slot slot) { return slots.getOrDefault(slot, ""); }
        public Set<SemanticGraph.Operator> operators() { return operators; }
        public SemanticGraph.Relation relation() { return relation; }
        public String subject() { return subject; }
        public String predicate() { return predicate; }
        public String object() { return object; }
        public boolean negated() { return operators.contains(SemanticGraph.Operator.NEGATION); }
        public boolean modal() {
            return operators.contains(SemanticGraph.Operator.POSSIBILITY)
                    || operators.contains(SemanticGraph.Operator.OBLIGATION);
        }
    }

    private static final class Candidate {
        final SemanticGraph.Proposition proposition;
        final UniversalParagraphDetector.Detection detection;
        final Intent matchedIntent;
        final List<String> matchedTerms = new ArrayList<>();
        double directCoverage;
        double aliasSignal;
        double relationEvidence;
        double subjectAlignment;
        double operatorAlignment;
        double score;

        Candidate(
                SemanticGraph.Proposition proposition,
                UniversalParagraphDetector.Detection detection,
                Intent matchedIntent
        ) {
            this.proposition = proposition;
            this.detection = detection;
            this.matchedIntent = matchedIntent;
        }
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "ai", "al", "ale", "aici", "acest", "aceasta", "acesta", "acele", "aceea",
            "ca", "care", "cat", "cât", "ce", "cel", "cea", "cei", "cele", "cine", "cu",
            "cum", "cand", "când", "catre", "către", "de", "despre", "din", "dintre", "dupa", "după",
            "este", "sunt", "era", "erau", "fi", "fie", "in", "în", "intre", "între", "la",
            "mai", "o", "pe", "pentru", "prin", "privind", "sa", "să", "se", "si", "și", "sau",
            "un", "una", "unei", "unui", "unde", "the", "of", "and", "or", "to", "for", "with",
            "what", "why", "how", "where", "when", "who", "which", "is", "are"
    ));

    public static Profile compile(String query, TopicMap themeMap) {
        String raw = safe(query);
        String folded = fold(raw);
        LinkedHashSet<Intent> detectedIntents = detectIntents(folded);
        Intent primary = detectedIntents.isEmpty() ? Intent.TOPIC : detectedIntents.iterator().next();
        if (detectedIntents.isEmpty()) detectedIntents.add(Intent.TOPIC);

        boolean explicit = raw.contains("?") || primary != Intent.TOPIC || detectedIntents.size() > 1;
        boolean asksNegation = containsWord(folded, "nu") || folded.contains("fara");
        boolean asksModality = containsAny(" " + folded + " ",
                " poate ", " pot ", " posibil ", " probabil ", " trebuie ");

        LinkedHashSet<String> direct = contentTerms(raw);
        for (Intent intent : detectedIntents) removeIntentControlTerms(direct, intent);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();

        if (themeMap != null) {
            if (direct.isEmpty()) {
                for (TopicNode node : themeMap.nodes) {
                    if (!eligibleThemeNode(node)) continue;
                    aliases.addAll(contentTerms(node.title));
                    for (String term : node.terms) aliases.addAll(contentTerms(term));
                }
            } else {
                for (TopicNode node : themeMap.nodes) {
                    if (!eligibleThemeNode(node)) continue;
                    LinkedHashSet<String> nodeTerms = new LinkedHashSet<>();
                    nodeTerms.addAll(contentTerms(node.title));
                    nodeTerms.addAll(contentTerms(node.path));
                    for (String term : node.terms) nodeTerms.addAll(contentTerms(term));
                    if (anyConceptMatch(direct, nodeTerms)) aliases.addAll(nodeTerms);
                }
            }
        }

        for (Intent intent : detectedIntents) removeIntentControlTerms(aliases, intent);
        aliases.removeAll(direct);

        String display = raw;
        if (display.isEmpty() && themeMap != null) display = themeMap.name;
        return new Profile(
                raw,
                display,
                primary,
                detectedIntents,
                explicit,
                asksNegation,
                asksModality,
                direct,
                aliases
        );
    }

    public static Answer findBest(Profile profile, List<UniversalParagraphDetector.Detection> detections) {
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        return findBest(profile, detections, graph);
    }

    public static Answer findBest(
            Profile profile,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph
    ) {
        List<Answer> all = findAll(profile, detections, graph, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<Answer> findAll(
            Profile profile,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            int maxResults
    ) {
        if (profile == null || !profile.enabled() || detections == null || detections.isEmpty()) {
            return Collections.emptyList();
        }
        if (graph == null || graph.isEmpty()) graph = SemanticGraphBuilder.build(detections);
        if (graph.isEmpty()) return Collections.emptyList();

        Map<Integer, UniversalParagraphDetector.Detection> byParagraph = new java.util.HashMap<>();
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection != null) byParagraph.put(detection.paragraphIndex(), detection);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (SemanticGraph.Proposition proposition : graph.propositions()) {
            UniversalParagraphDetector.Detection detection = byParagraph.get(proposition.paragraphIndex());
            Intent bestIntent = bestIntentFor(profile, proposition, detection);
            Candidate candidate = score(profile, proposition, detection, bestIntent, graph);
            if (passesExplicitGate(profile, candidate)) candidates.add(candidate);
        }

        candidates.sort(Comparator
                .comparingDouble((Candidate c) -> c.score).reversed()
                .thenComparingInt(c -> tokenCount(c.proposition.raw())));

        List<Answer> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int limit = Math.max(1, maxResults);
        for (Candidate candidate : candidates) {
            double threshold = threshold(profile, candidate.matchedIntent);
            if (candidate.score < threshold) continue;
            String key = candidate.proposition.paragraphIndex() + "|" + fold(candidate.proposition.raw());
            if (!dedupe.add(key)) continue;
            out.add(toAnswer(candidate));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static Candidate score(
            Profile profile,
            SemanticGraph.Proposition proposition,
            UniversalParagraphDetector.Detection detection,
            Intent matchedIntent,
            SemanticGraph graph
    ) {
        Candidate out = new Candidate(proposition, detection, matchedIntent);
        Set<String> semanticTerms = propositionTerms(proposition, detection, graph);

        int directMatches = 0;
        for (String term : profile.directTerms) {
            if (containsConcept(semanticTerms, term)) {
                directMatches++;
                out.matchedTerms.add(term);
            }
        }
        out.directCoverage = profile.directTerms.isEmpty()
                ? 0.0
                : directMatches / (double) profile.directTerms.size();

        int aliasMatches = 0;
        for (String alias : profile.aliasTerms) {
            if (containsConcept(semanticTerms, alias)) aliasMatches++;
        }
        out.aliasSignal = aliasSignal(profile, aliasMatches);
        out.relationEvidence = relationEvidence(matchedIntent, proposition, detection);
        out.subjectAlignment = subjectAlignment(profile, proposition, detection);
        out.operatorAlignment = operatorAlignment(profile, proposition);

        double informativeness = informativeness(contentTerms(proposition.raw()), profile);
        double compactness = compactness(proposition.raw());
        double propositionConfidence = proposition.confidence();

        if (profile.explicitQuestion || matchedIntent != Intent.TOPIC) {
            out.score = 0.34 * lexicalCore(profile, out)
                    + 0.29 * out.relationEvidence
                    + 0.12 * out.subjectAlignment
                    + 0.08 * out.operatorAlignment
                    + 0.08 * propositionConfidence
                    + 0.05 * informativeness
                    + 0.04 * compactness;
        } else {
            out.score = 0.66 * lexicalCore(profile, out)
                    + 0.12 * out.subjectAlignment
                    + 0.10 * propositionConfidence
                    + 0.07 * informativeness
                    + 0.05 * compactness;
        }

        // A proposition that only inherits a discourse subject is useful but slightly
        // weaker than a locally anchored proposition.
        if (proposition.inheritedSubject()) out.score -= 0.025;
        out.score = clamp01(out.score);
        return out;
    }

    private static double lexicalCore(Profile profile, Candidate candidate) {
        if (profile.directTerms.isEmpty()) return candidate.aliasSignal;
        if (profile.aliasTerms.isEmpty()) return candidate.directCoverage;
        return clamp01(candidate.directCoverage * 0.84 + candidate.aliasSignal * 0.16);
    }

    private static Intent bestIntentFor(
            Profile profile,
            SemanticGraph.Proposition proposition,
            UniversalParagraphDetector.Detection detection
    ) {
        Intent best = profile.intent;
        double bestScore = -1.0;
        for (Intent intent : profile.intents) {
            double score = relationEvidence(intent, proposition, detection);
            if (score > bestScore) {
                bestScore = score;
                best = intent;
            }
        }
        return best;
    }

    private static boolean passesExplicitGate(Profile profile, Candidate candidate) {
        if (candidate == null || candidate.proposition == null || candidate.proposition.raw().isEmpty()) return false;

        boolean conceptPresent = candidate.directCoverage > 0.0 || candidate.aliasSignal > 0.0;
        if (!conceptPresent) return false;

        if (!profile.directTerms.isEmpty()) {
            double minimumCoverage = profile.directTerms.size() <= 2 ? 0.50 : 0.34;
            if (candidate.directCoverage < minimumCoverage) return false;
        }

        if (candidate.matchedIntent == Intent.TOPIC) return true;

        switch (candidate.matchedIntent) {
            case WHY:
            case EFFECT:
            case CONDITION:
            case COMPARISON:
            case PURPOSE:
            case DEFINITION:
            case EVIDENCE:
            case SOLUTION:
            case PROBLEM:
                return candidate.relationEvidence >= 0.62;
            case HOW:
                return candidate.relationEvidence >= 0.52;
            case WHEN:
                return !candidate.proposition.slot(SemanticGraph.Slot.WHEN).isEmpty()
                        || candidate.relationEvidence >= 0.62;
            case WHERE:
                return !candidate.proposition.slot(SemanticGraph.Slot.WHERE).isEmpty()
                        || candidate.relationEvidence >= 0.62;
            case WHO:
                return !candidate.proposition.subject().isEmpty();
            default:
                return true;
        }
    }

    private static double threshold(Profile profile, Intent intent) {
        if (intent == Intent.TOPIC && !profile.explicitQuestion) return 0.43;
        if (intent == Intent.WHO || intent == Intent.WHERE || intent == Intent.WHEN) return 0.49;
        return 0.52;
    }

    private static Answer toAnswer(Candidate candidate) {
        SemanticGraph.Proposition proposition = candidate.proposition;
        return new Answer(
                proposition.paragraphIndex(),
                proposition.raw(),
                candidate.score,
                candidate.directCoverage,
                candidate.relationEvidence,
                candidate.matchedIntent,
                candidate.matchedTerms,
                proposition.slots(),
                proposition.operators(),
                proposition.relation(),
                proposition.subject(),
                proposition.predicate(),
                proposition.object()
        );
    }

    private static Set<String> propositionTerms(
            SemanticGraph.Proposition proposition,
            UniversalParagraphDetector.Detection detection,
            SemanticGraph graph
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(contentTerms(proposition.raw()));
        out.addAll(contentTerms(proposition.subject()));
        out.addAll(contentTerms(proposition.predicate()));
        out.addAll(contentTerms(proposition.object()));
        if (detection != null) out.addAll(contentTerms(detection.subject()));

        // Relation-bearing subordinate clauses can omit the event they explain.
        // Pull lexical anchors from the immediately preceding clause in the same
        // sentence, without merging its operators into the current proposition.
        if (proposition.relation() == SemanticGraph.Relation.CAUSE
                || proposition.relation() == SemanticGraph.Relation.CONDITION
                || proposition.relation() == SemanticGraph.Relation.PURPOSE) {
            SemanticGraph.Proposition previous = previousSibling(proposition, graph);
            if (previous != null) {
                out.addAll(contentTerms(previous.raw()));
                out.addAll(contentTerms(previous.subject()));
                out.addAll(contentTerms(previous.predicate()));
                out.addAll(contentTerms(previous.object()));
            }
        }
        return out;
    }

    private static SemanticGraph.Proposition previousSibling(
            SemanticGraph.Proposition proposition,
            SemanticGraph graph
    ) {
        if (graph == null || proposition == null || proposition.clauseIndex() <= 0) return null;
        for (SemanticGraph.Proposition candidate : graph.propositions()) {
            if (candidate.paragraphIndex() == proposition.paragraphIndex()
                    && candidate.sentenceIndex() == proposition.sentenceIndex()
                    && candidate.clauseIndex() == proposition.clauseIndex() - 1) {
                return candidate;
            }
        }
        return null;
    }

    private static double relationEvidence(
            Intent intent,
            SemanticGraph.Proposition proposition,
            UniversalParagraphDetector.Detection detection
    ) {
        if (intent == null || proposition == null) return 0.0;
        SemanticGraph.Relation relation = proposition.relation();
        switch (intent) {
            case TOPIC:
                return 1.0;
            case WHY:
                if (relation == SemanticGraph.Relation.CAUSE) return 1.0;
                if (relation == SemanticGraph.Relation.EFFECT) return 0.84;
                if (relation == SemanticGraph.Relation.MECHANISM) return 0.58;
                break;
            case EFFECT:
                if (relation == SemanticGraph.Relation.EFFECT) return 1.0;
                if (relation == SemanticGraph.Relation.CAUSE) return 0.68;
                break;
            case HOW:
                if (relation == SemanticGraph.Relation.MECHANISM) return 1.0;
                if (relation == SemanticGraph.Relation.SEQUENCE) return 0.92;
                if (relation == SemanticGraph.Relation.ATTRIBUTE) return 0.54;
                break;
            case CONDITION:
                if (relation == SemanticGraph.Relation.CONDITION) return 1.0;
                break;
            case DEFINITION:
                if (relation == SemanticGraph.Relation.DEFINITION) return 1.0;
                break;
            case COMPARISON:
                if (relation == SemanticGraph.Relation.COMPARISON) return 1.0;
                break;
            case PURPOSE:
                if (relation == SemanticGraph.Relation.PURPOSE) return 1.0;
                break;
            case EVIDENCE:
                if (relation == SemanticGraph.Relation.EVIDENCE) return 1.0;
                break;
            case PROBLEM:
                if (relation == SemanticGraph.Relation.PROBLEM) return 1.0;
                break;
            case SOLUTION:
                if (relation == SemanticGraph.Relation.SOLUTION) return 1.0;
                break;
            case WHEN:
                if (!proposition.slot(SemanticGraph.Slot.WHEN).isEmpty()) return 1.0;
                break;
            case WHERE:
                if (!proposition.slot(SemanticGraph.Slot.WHERE).isEmpty()) return 1.0;
                break;
            case WHO:
                if (!proposition.subject().isEmpty()) return proposition.inheritedSubject() ? 0.72 : 0.92;
                break;
            default:
                break;
        }

        // Paragraph classification is a weaker fallback than the local proposition.
        return detectionFunctionCompatibility(intent, detection) * 0.55;
    }

    private static double detectionFunctionCompatibility(
            Intent intent,
            UniversalParagraphDetector.Detection detection
    ) {
        if (detection == null) return 0.0;
        UniversalDetectionLexicon.Function primary = detection.function();
        UniversalDetectionLexicon.Function secondary = detection.secondaryFunction();
        double p = functionMatch(intent, primary) ? detection.functionConfidence() : 0.0;
        double s = functionMatch(intent, secondary) ? detection.functionConfidence() * 0.72 : 0.0;
        return Math.max(p, s);
    }

    private static boolean functionMatch(Intent intent, UniversalDetectionLexicon.Function function) {
        if (function == null) return false;
        switch (intent) {
            case WHY:
                return function == UniversalDetectionLexicon.Function.CAUSE_EFFECT
                        || function == UniversalDetectionLexicon.Function.EXPLANATION;
            case EFFECT:
                return function == UniversalDetectionLexicon.Function.CAUSE_EFFECT
                        || function == UniversalDetectionLexicon.Function.CONCLUSION;
            case HOW:
                return function == UniversalDetectionLexicon.Function.EXPLANATION
                        || function == UniversalDetectionLexicon.Function.SEQUENCE
                        || function == UniversalDetectionLexicon.Function.DESCRIPTION;
            case CONDITION:
                return function == UniversalDetectionLexicon.Function.CONDITION;
            case DEFINITION:
                return function == UniversalDetectionLexicon.Function.DEFINITION;
            case COMPARISON:
                return function == UniversalDetectionLexicon.Function.COMPARISON
                        || function == UniversalDetectionLexicon.Function.CONTRAST;
            case PURPOSE:
                return function == UniversalDetectionLexicon.Function.PURPOSE;
            case EVIDENCE:
                return function == UniversalDetectionLexicon.Function.EVIDENCE
                        || function == UniversalDetectionLexicon.Function.ARGUMENTATION;
            case PROBLEM:
                return function == UniversalDetectionLexicon.Function.PROBLEM;
            case SOLUTION:
                return function == UniversalDetectionLexicon.Function.SOLUTION;
            default:
                return false;
        }
    }

    private static double subjectAlignment(
            Profile profile,
            SemanticGraph.Proposition proposition,
            UniversalParagraphDetector.Detection detection
    ) {
        Set<String> subjectTerms = contentTerms(proposition.subject());
        if (anyConceptMatch(profile.directTerms, subjectTerms)) {
            return proposition.inheritedSubject() ? 0.78 : 1.0;
        }
        if (anyConceptMatch(profile.aliasTerms, subjectTerms)) {
            return proposition.inheritedSubject() ? 0.58 : 0.78;
        }
        if (detection != null) {
            Set<String> paragraphSubject = contentTerms(detection.subject());
            if (anyConceptMatch(profile.directTerms, paragraphSubject)) return 0.68;
            if (anyConceptMatch(profile.aliasTerms, paragraphSubject)) return 0.48;
        }
        return 0.0;
    }

    private static double operatorAlignment(Profile profile, SemanticGraph.Proposition proposition) {
        double score = 0.72;
        if (profile.asksNegation) {
            score = proposition.operators().contains(SemanticGraph.Operator.NEGATION) ? 1.0 : 0.15;
        }
        if (profile.asksModality) {
            boolean modal = proposition.operators().contains(SemanticGraph.Operator.POSSIBILITY)
                    || proposition.operators().contains(SemanticGraph.Operator.OBLIGATION);
            score = Math.min(score, modal ? 1.0 : 0.25);
        }
        return score;
    }

    private static double aliasSignal(Profile profile, int aliasMatches) {
        if (profile.aliasTerms.isEmpty() || aliasMatches <= 0) return 0.0;
        if (profile.directTerms.isEmpty()) {
            return Math.min(1.0, 0.84 + Math.max(0, aliasMatches - 1) * 0.08);
        }
        return Math.min(1.0, aliasMatches / 3.0);
    }

    private static LinkedHashSet<Intent> detectIntents(String query) {
        String text = " " + safe(query) + " ";
        LinkedHashSet<Intent> out = new LinkedHashSet<>();

        if (containsAny(text, " de ce ", " cauza ", " cauze ", " cauzele ", " motiv ", " motive ")) {
            out.add(Intent.WHY);
        }
        if (containsAny(text, " ce efect ", " efectul ", " efecte ", " efectele ", " consecinta ",
                " consecinte ", " impact ", " impactul ")) {
            out.add(Intent.EFFECT);
        }
        if (containsAny(text, " ce este ", " ce inseamna ", " definitie ", " defineste ")) {
            out.add(Intent.DEFINITION);
        }
        if (containsAny(text, " in ce conditie ", " in ce conditii ", " daca ", " conditii ")) {
            out.add(Intent.CONDITION);
        }
        if (containsAny(text, " comparatie ", " compara ", " comparativ ", " diferente ", " diferenta ",
                " asemanari ", " asemanare ")) {
            out.add(Intent.COMPARISON);
        }
        if (containsAny(text, " scop ", " pentru ce ", " cu ce scop ", " obiectiv ")) {
            out.add(Intent.PURPOSE);
        }
        if (containsAny(text, " dovezi ", " dovada ", " ce date ", " evidenta ", " studii ")) {
            out.add(Intent.EVIDENCE);
        }
        if (containsAny(text, " problema ", " probleme ", " dificultati ", " risc ", " riscuri ")) {
            out.add(Intent.PROBLEM);
        }
        if (containsAny(text, " solutie ", " solutii ", " cum se rezolva ", " remediu ")) {
            out.add(Intent.SOLUTION);
        }
        if (containsAny(text, " cand ", " in ce perioada ", " in ce an ")) out.add(Intent.WHEN);
        if (containsAny(text, " unde ", " in ce loc ", " in ce zona ")) out.add(Intent.WHERE);
        if (containsAny(text, " cine ", " care persoane ", " ce actor ", " ce actori ")) out.add(Intent.WHO);
        if (containsAny(text, " cum ", " mecanism ", " in ce mod ", " prin ce ")) out.add(Intent.HOW);

        return out;
    }

    private static void removeIntentControlTerms(Set<String> terms, Intent intent) {
        if (terms == null || terms.isEmpty() || intent == null) return;
        List<String> remove = new ArrayList<>();
        for (String term : terms) if (isIntentControlTerm(intent, fold(term))) remove.add(term);
        terms.removeAll(remove);
    }

    private static boolean isIntentControlTerm(Intent intent, String term) {
        switch (intent) {
            case WHY: return startsAny(term, "cauz", "motiv");
            case EFFECT: return startsAny(term, "efect", "consec", "impact");
            case HOW: return startsAny(term, "mecan", "mod", "proces");
            case DEFINITION: return startsAny(term, "defin", "insemn");
            case CONDITION: return startsAny(term, "condit");
            case COMPARISON: return startsAny(term, "compar", "difer", "aseman");
            case PURPOSE: return startsAny(term, "scop", "obiectiv");
            case EVIDENCE: return startsAny(term, "dove", "evid", "date", "studi");
            case PROBLEM: return startsAny(term, "problem", "dificult", "risc");
            case SOLUTION: return startsAny(term, "solut", "remedi");
            default: return false;
        }
    }

    private static boolean eligibleThemeNode(TopicNode node) {
        if (node == null || !node.enabled || node.path == null) return false;
        String path = fold(node.path);
        return !(path.equals("textual") || path.startsWith("textual >")
                || path.equals("semantic") || path.startsWith("semantic >"));
    }

    private static LinkedHashSet<String> contentTerms(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(fold(value));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static boolean anyConceptMatch(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        for (String left : a) {
            for (String right : b) {
                if (conceptMatch(left, right)) return true;
            }
        }
        return false;
    }

    private static boolean containsConcept(Set<String> haystack, String needle) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String value : haystack) if (conceptMatch(value, needle)) return true;
        return false;
    }

    private static boolean conceptMatch(String a, String b) {
        if (a == null || b == null) return false;
        String left = fold(a);
        String right = fold(b);
        if (left.equals(right)) return true;
        int min = Math.min(left.length(), right.length());
        if (min < 5) return false;
        int prefix = min >= 8 ? 6 : 5;
        return left.regionMatches(0, right, 0, prefix);
    }

    private static double informativeness(Set<String> tokens, Profile profile) {
        int extra = 0;
        for (String token : tokens) {
            if (!containsConcept(profile.directTerms, token)
                    && !containsConcept(profile.aliasTerms, token)) extra++;
        }
        return Math.min(1.0, extra / 5.0);
    }

    private static double compactness(String segment) {
        int count = tokenCount(segment);
        if (count <= 8) return 1.0;
        if (count <= 18) return 0.82;
        if (count <= 30) return 0.58;
        return 0.32;
    }

    private static int tokenCount(String value) {
        int count = 0;
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) count++;
        return count;
    }

    private static boolean containsWord(String text, String word) {
        return (" " + safe(text) + " ").contains(" " + safe(word) + " ");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static boolean startsAny(String value, String... prefixes) {
        if (value == null) return false;
        for (String prefix : prefixes) if (value.startsWith(prefix)) return true;
        return false;
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
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
