package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic clause-level semantic parser for Romanian informative text.
 *
 * It is intentionally conservative: it does not invent omitted arguments. The
 * builder resolves simple anaphora, identifies relation-bearing clauses and binds
 * logical operators to the clause in which they are explicitly present.
 */
public final class SemanticGraphBuilder {
    private SemanticGraphBuilder() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\\n+");
    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20)\\d{2}\\b");

    /** All values are folded because matching also uses folded OCR text. */
    private static final Set<String> COREFERENCE_START = new HashSet<>(Arrays.asList(
            "acesta", "aceasta", "acestia", "acestea", "el", "ea", "ei", "ele",
            "acest", "acel", "acea", "acei", "acele",
            "fenomenul", "procesul", "mecanismul", "metoda", "situatia", "rezultatul"
    ));

    private static final Set<String> PREDICATE_WORDS = new HashSet<>(Arrays.asList(
            "este", "sunt", "era", "erau", "reprezinta", "inseamna",
            "constituie", "devine", "ramane", "are", "au", "poate", "pot", "trebuie",
            "creste", "scade", "reduce", "mareste", "produce", "produc",
            "determina", "provoaca", "genereaza", "conduce", "duce", "permite", "transforma",
            "explica", "arata", "indica", "sugereaza", "functioneaza", "consta",
            "apare", "apar", "include", "cuprinde", "contine", "depinde", "favorizeaza"
    ));

    private static final String[] STRONG_SPLIT_CUES = {
            "deoarece", "fiindcă", "fiindca", "întrucât", "intrucat", "din cauza", "datorită", "datorita",
            "prin urmare", "în consecință", "in consecinta", "de aceea", "drept urmare",
            "dacă", "daca", "în cazul în care", "in cazul in care", "cu condiția", "cu conditia",
            "pentru a", "în vederea", "in vederea", "cu scopul de",
            "comparativ cu", "în comparație cu", "in comparatie cu", "spre deosebire de",
            "conform datelor", "potrivit studiului", "datele arată", "datele arata"
    };

    public static SemanticGraph build(List<UniversalParagraphDetector.Detection> detections) {
        if (detections == null || detections.isEmpty()) {
            return new SemanticGraph(Collections.emptyList(), "");
        }

        List<SemanticGraph.Proposition> propositions = new ArrayList<>();
        String discourseSubject = "";

        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null || detection.paragraph() == null || detection.paragraph().trim().isEmpty()) continue;
            ParagraphResult result = parseParagraph(detection, discourseSubject);
            propositions.addAll(result.propositions);
            if (!result.lastSubject.isEmpty()) discourseSubject = result.lastSubject;
        }

        return new SemanticGraph(propositions, discourseSubject);
    }

    public static SemanticGraph build(UniversalParagraphDetector.Detection detection) {
        if (detection == null) return new SemanticGraph(Collections.emptyList(), "");
        return build(Collections.singletonList(detection));
    }

    private static ParagraphResult parseParagraph(
            UniversalParagraphDetector.Detection detection,
            String priorSubject
    ) {
        List<SemanticGraph.Proposition> out = new ArrayList<>();

        String detectedSubject = clean(detection.subject());
        boolean detectedSubjectIsAnaphoric = isCoreferenceStart(
                fold(detectedSubject), firstToken(detectedSubject)
        );
        String currentSubject;
        if (detectedSubjectIsAnaphoric && !clean(priorSubject).isEmpty()) {
            currentSubject = clean(priorSubject);
        } else if (!detectedSubject.isEmpty()) {
            currentSubject = detectedSubject;
        } else {
            currentSubject = clean(priorSubject);
        }

        String[] sentences = SENTENCE_SPLIT.split(detection.paragraph().trim());
        int sentenceIndex = 0;
        for (String sentence : sentences) {
            String cleanSentence = clean(sentence);
            if (cleanSentence.isEmpty()) continue;

            List<String> clauses = splitClauses(cleanSentence);
            int clauseIndex = 0;
            for (String clause : clauses) {
                String cleanClause = cleanClause(clause);
                if (tokenCount(cleanClause) < 2) continue;

                LocalFrame frame = parseClause(
                        cleanClause,
                        detection,
                        currentSubject,
                        sentenceIndex,
                        clauseIndex
                );
                out.add(frame.proposition);
                if (!frame.resolvedSubject.isEmpty()) currentSubject = frame.resolvedSubject;
                clauseIndex++;
            }
            sentenceIndex++;
        }

        return new ParagraphResult(out, currentSubject);
    }

    private static LocalFrame parseClause(
            String clause,
            UniversalParagraphDetector.Detection detection,
            String currentSubject,
            int sentenceIndex,
            int clauseIndex
    ) {
        String folded = fold(clause);
        List<String> clauseTokens = tokens(clause);
        String first = clauseTokens.isEmpty() ? "" : fold(clauseTokens.get(0));
        boolean inherited = isCoreferenceStart(folded, first);

        PredicateFrame predicate = extractPredicateFrame(clause, detection.subject());
        String subject = clean(predicate.subject);
        String detectionSubject = clean(detection.subject());
        boolean detectionSubjectIsAnaphoric = isCoreferenceStart(
                fold(detectionSubject), firstToken(detectionSubject)
        );

        if (inherited && !clean(currentSubject).isEmpty()) {
            subject = clean(currentSubject);
        } else if (subject.isEmpty() && !detectionSubject.isEmpty() && !detectionSubjectIsAnaphoric) {
            subject = detectionSubject;
        } else if (subject.isEmpty()) {
            subject = clean(currentSubject);
            inherited = !subject.isEmpty();
        }

        SemanticGraph.Relation relation = detectRelation(folded);
        EnumSet<SemanticGraph.Operator> operators = detectOperators(folded);
        EnumMap<SemanticGraph.Slot, String> slots = detectSlots(
                clause, folded, relation, predicate, operators
        );
        if (!subject.isEmpty()) slots.put(SemanticGraph.Slot.WHO, subject);

        double confidence = 0.46;
        if (!subject.isEmpty()) confidence += 0.13;
        if (!predicate.predicate.isEmpty()) confidence += 0.13;
        if (relation != SemanticGraph.Relation.GENERIC) confidence += 0.16;
        if (inherited) confidence -= 0.05;
        if (!slots.isEmpty()) confidence += Math.min(0.10, slots.size() * 0.025);

        SemanticGraph.Proposition proposition = new SemanticGraph.Proposition(
                detection.paragraphIndex(),
                sentenceIndex,
                clauseIndex,
                clause,
                subject,
                predicate.predicate,
                predicate.object,
                relation,
                operators,
                slots,
                clamp01(confidence),
                inherited
        );
        return new LocalFrame(proposition, subject);
    }

    private static List<String> splitClauses(String sentence) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        List<String> stage = new ArrayList<>();
        stage.add(sentence);

        for (String cue : STRONG_SPLIT_CUES) {
            List<String> next = new ArrayList<>();
            for (String part : stage) next.addAll(splitBeforeCue(part, cue));
            stage = next;
        }

        for (String part : stage) {
            String[] punctuation = part.split("(?<=,)|(?<=:)|(?<=—)|(?<=–)");
            for (String value : punctuation) {
                String clean = cleanClause(value);
                if (!clean.isEmpty()) parts.add(clean);
            }
        }

        if (parts.isEmpty()) parts.add(sentence);
        return new ArrayList<>(parts);
    }

    private static List<String> splitBeforeCue(String value, String cue) {
        int originalIndex = findCueStart(value, cue);
        if (originalIndex <= 0 || originalIndex >= value.length()) {
            return Collections.singletonList(value);
        }

        List<String> out = new ArrayList<>();
        String left = cleanClause(value.substring(0, originalIndex));
        String right = cleanClause(value.substring(originalIndex));
        if (!left.isEmpty()) out.add(left);
        if (!right.isEmpty()) out.add(right);
        return out.isEmpty() ? Collections.singletonList(value) : out;
    }

    /** Finds a folded cue through token spans so diacritics never shift source indices. */
    private static int findCueStart(String value, String cue) {
        List<TokenSpan> source = tokenSpans(value);
        List<String> wanted = tokens(cue);
        if (source.isEmpty() || wanted.isEmpty() || wanted.size() > source.size()) return -1;

        for (int start = 0; start + wanted.size() <= source.size(); start++) {
            boolean same = true;
            for (int offset = 0; offset < wanted.size(); offset++) {
                if (!fold(source.get(start + offset).token).equals(fold(wanted.get(offset)))) {
                    same = false;
                    break;
                }
            }
            if (same) return source.get(start).start;
        }
        return -1;
    }

    private static PredicateFrame extractPredicateFrame(String clause, String paragraphSubject) {
        List<TokenSpan> spans = tokenSpans(clause);
        if (spans.isEmpty()) return new PredicateFrame("", "", "");

        int predicateIndex = findPredicateIndex(spans);
        if (predicateIndex < 0) {
            String subject = paragraphSubject != null && containsFolded(clause, paragraphSubject)
                    ? paragraphSubject
                    : "";
            return new PredicateFrame(subject, "", clause);
        }

        TokenSpan predicate = spans.get(predicateIndex);
        String subject = clean(clause.substring(0, predicate.start));
        subject = trimLeadingConnectors(subject);
        if (isCoreferenceStart(fold(subject), firstToken(subject))) subject = "";

        String predicateText = predicate.token;
        int objectStart = predicate.end;
        if ((fold(predicateText).equals("poate") || fold(predicateText).equals("pot")
                || fold(predicateText).equals("trebuie")) && predicateIndex + 1 < spans.size()) {
            TokenSpan next = spans.get(predicateIndex + 1);
            predicateText = clean(clause.substring(predicate.start, next.end));
            objectStart = next.end;
        }

        String object = objectStart < clause.length() ? clean(clause.substring(objectStart)) : "";
        return new PredicateFrame(subject, predicateText, object);
    }

    private static int findPredicateIndex(List<TokenSpan> spans) {
        for (int i = 0; i < spans.size(); i++) {
            String token = fold(spans.get(i).token);
            if (PREDICATE_WORDS.contains(token)) return i;
            if (looksLikeRomanianVerb(token, i)) return i;
        }
        return -1;
    }

    private static boolean looksLikeRomanianVerb(String token, int position) {
        if (token == null || token.length() < 5 || position == 0) return false;
        return token.endsWith("eaza")
                || token.endsWith("izeaza")
                || token.endsWith("ifica")
                || token.endsWith("esc")
                || token.endsWith("ind")
                || token.endsWith("and");
    }

    /** Relation is local to the clause; paragraph function is not promoted here. */
    private static SemanticGraph.Relation detectRelation(String folded) {
        String text = " " + folded + " ";
        if (containsAny(text, " deoarece ", " fiindca ", " intrucat ", " din cauza ", " datorita ")) {
            return SemanticGraph.Relation.CAUSE;
        }
        if (containsAny(text, " prin urmare ", " in consecinta ", " de aceea ", " drept urmare ",
                " conduce la ", " duce la ", " determina ", " provoaca ", " genereaza ", " rezulta ")) {
            return SemanticGraph.Relation.EFFECT;
        }
        if (containsAny(text, " daca ", " in cazul in care ", " cu conditia ", " numai daca ")) {
            return SemanticGraph.Relation.CONDITION;
        }
        if (containsAny(text, " pentru a ", " in vederea ", " cu scopul de ", " obiectiv ", " vizeaza ")) {
            return SemanticGraph.Relation.PURPOSE;
        }
        if (containsAny(text, " comparativ cu ", " in comparatie cu ", " spre deosebire de ",
                " similar cu ", " asemanator cu ", " decat ")) {
            return SemanticGraph.Relation.COMPARISON;
        }
        if (containsAny(text, " conform datelor ", " potrivit studiului ", " datele arata ",
                " rezultatele indica ", " dovezile ", " experimentul ")) {
            return SemanticGraph.Relation.EVIDENCE;
        }
        if (containsAny(text, " problema ", " dificultate ", " limitare ", " risc ", " obstacol ")) {
            return SemanticGraph.Relation.PROBLEM;
        }
        if (containsAny(text, " solutia ", " se poate rezolva ", " remediu ", " masura ", " este necesar ")) {
            return SemanticGraph.Relation.SOLUTION;
        }
        if (containsAny(text, " prin ", " mecanism ", " proces ", " etapa ", " functioneaza ", " consta in ")) {
            return SemanticGraph.Relation.MECHANISM;
        }
        if (containsAny(text, " este ", " sunt ", " reprezinta ", " inseamna ", " se defineste ", " desemneaza ")) {
            return SemanticGraph.Relation.DEFINITION;
        }
        return SemanticGraph.Relation.GENERIC;
    }

    private static EnumSet<SemanticGraph.Operator> detectOperators(String folded) {
        String text = " " + folded + " ";
        EnumSet<SemanticGraph.Operator> out = EnumSet.noneOf(SemanticGraph.Operator.class);
        if (containsAny(text, " nu ", " nici ", " niciun ", " nicio ", " fara ", " lipsa ")) {
            out.add(SemanticGraph.Operator.NEGATION);
        }
        if (containsAny(text, " poate ", " pot ", " ar putea ", " posibil ", " probabil ")) {
            out.add(SemanticGraph.Operator.POSSIBILITY);
        }
        if (containsAny(text, " trebuie ", " necesar ", " necesara ", " obligatoriu ")) {
            out.add(SemanticGraph.Operator.OBLIGATION);
        }
        if (containsAny(text, " toate ", " toti ", " majoritatea ", " unele ", " unii ", " multe ", " putine ", " procent ")) {
            out.add(SemanticGraph.Operator.QUANTIFICATION);
        }
        if (containsAny(text, " doar ", " numai ", " exclusiv ", " in special ", " mai ales ")) {
            out.add(SemanticGraph.Operator.RESTRICTION);
        }
        if (containsAny(text, " exceptand ", " cu exceptia ", " in afara de ")) {
            out.add(SemanticGraph.Operator.EXCEPTION);
        }
        if (containsAny(text, " conform ", " potrivit ", " sustine ca ", " afirma ca ", " arata ca ")) {
            out.add(SemanticGraph.Operator.ATTRIBUTION);
        }
        return out;
    }

    private static EnumMap<SemanticGraph.Slot, String> detectSlots(
            String raw,
            String folded,
            SemanticGraph.Relation relation,
            PredicateFrame predicate,
            Set<SemanticGraph.Operator> operators
    ) {
        EnumMap<SemanticGraph.Slot, String> slots = new EnumMap<>(SemanticGraph.Slot.class);
        if (!predicate.subject.isEmpty()) slots.put(SemanticGraph.Slot.WHO, predicate.subject);
        if (!predicate.object.isEmpty()) slots.put(SemanticGraph.Slot.WHAT, predicate.object);

        Matcher year = YEAR.matcher(folded);
        if (year.find()) slots.put(SemanticGraph.Slot.WHEN, year.group());

        String where = extractLocation(raw);
        if (!where.isEmpty()) slots.put(SemanticGraph.Slot.WHERE, where);

        switch (relation) {
            case CAUSE:
                slots.put(SemanticGraph.Slot.WHY, raw);
                break;
            case EFFECT:
                slots.put(SemanticGraph.Slot.EFFECT, predicate.object.isEmpty() ? raw : predicate.object);
                break;
            case CONDITION:
                slots.put(SemanticGraph.Slot.CONDITION, raw);
                break;
            case PURPOSE:
                slots.put(SemanticGraph.Slot.PURPOSE, raw);
                break;
            case COMPARISON:
                slots.put(SemanticGraph.Slot.COMPARISON, raw);
                break;
            case EVIDENCE:
                slots.put(SemanticGraph.Slot.EVIDENCE, raw);
                break;
            case MECHANISM:
            case SEQUENCE:
                slots.put(SemanticGraph.Slot.HOW, raw);
                break;
            default:
                break;
        }

        if (operators.contains(SemanticGraph.Operator.QUANTIFICATION)) {
            String quantity = extractQuantity(raw);
            if (!quantity.isEmpty()) slots.put(SemanticGraph.Slot.QUANTITY, quantity);
        }
        return slots;
    }

    private static String extractLocation(String raw) {
        Matcher matcher = Pattern.compile(
                "\\b(?:în|in|la|din)\\s+([\\p{L}][\\p{L}\\-]*(?:\\s+[\\p{L}][\\p{L}\\-]*){0,3})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(raw);
        while (matcher.find()) {
            String candidate = clean(matcher.group());
            String normalized = fold(candidate);
            if (containsAny(" " + normalized + " ",
                    " in cazul ", " in vederea ", " in consecinta ", " in timp ",
                    " in anul ", " in anii ", " in perioada ", " in secolul ",
                    " in ultimii ", " in ultimele ", " in prezent ", " in trecut ", " in viitor ")) {
                continue;
            }
            if (tokenCount(candidate) <= 5) return candidate;
        }
        return "";
    }

    private static String extractQuantity(String raw) {
        Matcher matcher = Pattern.compile(
                "\\b(?:toate|toți|toti|majoritatea|unele|unii|multe|puține|putine|\\d+(?:[.,]\\d+)?%?)\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(raw);
        return matcher.find() ? matcher.group() : "";
    }

    private static boolean isCoreferenceStart(String foldedClause, String firstToken) {
        if (COREFERENCE_START.contains(firstToken)) return true;
        return startsAny(foldedClause,
                "acest proces", "acest fenomen", "aceasta situatie",
                "acest mecanism", "aceasta metoda", "acest rezultat", "acest efect");
    }

    private static String trimLeadingConnectors(String value) {
        String out = clean(value);
        String folded = fold(out);
        String[] prefixes = {
                "deoarece ", "fiindca ", "intrucat ", "datorita ", "prin urmare ", "in consecinta ",
                "daca ", "pentru a ", "comparativ cu ", "conform datelor "
        };
        for (String prefix : prefixes) {
            if (folded.startsWith(prefix)) {
                int words = tokenCount(prefix);
                List<TokenSpan> spans = tokenSpans(out);
                if (spans.size() >= words) {
                    int cut = spans.get(words - 1).end;
                    return clean(out.substring(cut));
                }
            }
        }
        return out;
    }

    private static boolean containsFolded(String raw, String value) {
        if (raw == null || value == null || value.trim().isEmpty()) return false;
        return (" " + fold(raw) + " ").contains(" " + fold(value) + " ");
    }

    private static String firstToken(String value) {
        List<String> values = tokens(value);
        return values.isEmpty() ? "" : fold(values.get(0));
    }

    private static boolean containsAny(String text, String... cues) {
        for (String cue : cues) if (text.contains(cue)) return true;
        return false;
    }

    private static boolean startsAny(String text, String... prefixes) {
        if (text == null) return false;
        for (String prefix : prefixes) if (text.startsWith(fold(prefix))) return true;
        return false;
    }

    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    private static List<TokenSpan> tokenSpans(String value) {
        List<TokenSpan> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) out.add(new TokenSpan(matcher.group(), matcher.start(), matcher.end()));
        return out;
    }

    private static int tokenCount(String value) {
        int count = 0;
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) count++;
        return count;
    }

    private static String cleanClause(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("^[,;:—–\\s]+|[,;:—–\\s]+$", "").trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class TokenSpan {
        final String token;
        final int start;
        final int end;

        TokenSpan(String token, int start, int end) {
            this.token = token;
            this.start = start;
            this.end = end;
        }
    }

    private static final class PredicateFrame {
        final String subject;
        final String predicate;
        final String object;

        PredicateFrame(String subject, String predicate, String object) {
            this.subject = clean(subject);
            this.predicate = clean(predicate);
            this.object = clean(object);
        }
    }

    private static final class LocalFrame {
        final SemanticGraph.Proposition proposition;
        final String resolvedSubject;

        LocalFrame(SemanticGraph.Proposition proposition, String resolvedSubject) {
            this.proposition = proposition;
            this.resolvedSubject = clean(resolvedSubject);
        }
    }

    private static final class ParagraphResult {
        final List<SemanticGraph.Proposition> propositions;
        final String lastSubject;

        ParagraphResult(List<SemanticGraph.Proposition> propositions, String lastSubject) {
            this.propositions = propositions;
            this.lastSubject = clean(lastSubject);
        }
    }
}
