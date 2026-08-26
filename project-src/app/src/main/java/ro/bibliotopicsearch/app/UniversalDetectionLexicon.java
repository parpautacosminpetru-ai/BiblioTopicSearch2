package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Language-facing schema for automatic paragraph detection.
 *
 * The semantic classes are language-independent; the concrete lexical forms below
 * are Romanian (diacritic-insensitive). Structural punctuation is handled by the
 * detector itself. Add another language by supplying equivalent marker lists,
 * without changing the detector or its output schema.
 */
public final class UniversalDetectionLexicon {
    private UniversalDetectionLexicon() {}

    public enum Function {
        INTRODUCTION,
        DEFINITION,
        DESCRIPTION,
        EXPLANATION,
        CAUSE_EFFECT,
        PURPOSE,
        CONDITION,
        EXAMPLE,
        ENUMERATION,
        CLASSIFICATION,
        COMPARISON,
        CONTRAST,
        ARGUMENTATION,
        EVIDENCE,
        PROBLEM,
        SOLUTION,
        SEQUENCE,
        TRANSITION,
        SUMMARY,
        CONCLUSION,
        DEVELOPMENT,
        UNKNOWN
    }

    public enum Slot {
        WHAT,
        WHO,
        WHERE,
        WHEN,
        WHY,
        HOW,
        WHICH,
        QUANTITY,
        CONDITION,
        EFFECT,
        COMPARISON,
        PURPOSE,
        EVIDENCE,
        CLAIM
    }

    public enum Operator {
        NEGATION,
        MODALITY,
        QUANTITY,
        RESTRICTION,
        INCLUSION,
        EXCLUSION,
        TEMPORAL,
        SPATIAL,
        COMPARATIVE,
        COREFERENCE,
        TOPIC_FRAME
    }

    public static final class Marker {
        public final String raw;
        public final String normalized;
        public final double weight;

        Marker(String raw, double weight) {
            this.raw = raw;
            this.normalized = fold(raw);
            this.weight = weight;
        }
    }

    private static final Map<Function, List<Marker>> FUNCTION_MARKERS = new EnumMap<>(Function.class);
    private static final Map<Operator, List<Marker>> OPERATOR_MARKERS = new EnumMap<>(Operator.class);
    private static final Map<Function, List<Slot>> QUERY_SLOTS = new EnumMap<>(Function.class);

    /** Words that should not become standalone subject candidates. */
    public static final Set<String> STOP_WORDS;

    /** Initial frames are context, not automatically the paragraph subject. */
    public static final List<String> FRAME_PREFIXES;

    /** Strong topic-introducing forms; the phrase that follows is a high-value subject candidate. */
    public static final List<String> TOPIC_PREFIXES;

    /** Common finite/predicative cues used only to cut a leading candidate phrase. */
    public static final List<String> PREDICATE_CUES;

    /** Pronouns/demonstratives that usually continue a previous discourse referent. */
    public static final Set<String> COREFERENCE_WORDS;

    static {
        put(Function.INTRODUCTION, 1.8,
                "acest capitol", "această secțiune", "aceasta sectiune", "în cele ce urmează", "in cele ce urmeaza",
                "vom analiza", "vom examina", "ne vom ocupa", "tema este", "subiectul este");

        put(Function.DEFINITION, 3.2,
                "se definește", "se defineste", "este definit", "este definită", "este definita",
                "înseamnă", "inseamna", "reprezintă", "reprezinta", "constituie", "desemnează", "desemneaza",
                "se numește", "se numeste", "prin termenul", "prin conceptul", "se înțelege", "se intelege");

        put(Function.DESCRIPTION, 1.8,
                "se caracterizează", "se caracterizeaza", "caracteristică", "caracteristica", "caracteristici",
                "proprietate", "proprietăți", "proprietati", "prezintă", "prezinta", "este alcătuit", "este alcatuit",
                "este alcătuită", "este alcatuita", "este format din", "este formată din", "este formata din",
                "componentă", "componenta", "componente");

        put(Function.EXPLANATION, 2.7,
                "se explică prin", "se explica prin", "explicația este", "explicatia este", "motivul este",
                "mecanismul", "funcționează prin", "functioneaza prin", "are loc prin", "se produce prin");

        put(Function.CAUSE_EFFECT, 3.0,
                "deoarece", "fiindcă", "fiindca", "întrucât", "intrucat", "din cauza", "datorită", "datorita",
                "ca urmare", "prin urmare", "în consecință", "in consecinta", "de aceea", "drept urmare",
                "conduce la", "duce la", "determină", "determina", "provoacă", "provoaca", "generează", "genereaza",
                "rezultă", "rezulta", "efect", "efecte", "consecință", "consecinta", "consecințe", "consecinte");

        put(Function.PURPOSE, 2.8,
                "pentru a", "în vederea", "in vederea", "cu scopul de", "în scopul", "in scopul",
                "astfel încât", "astfel incat", "obiectivul", "urmărește", "urmareste", "vizează", "vizeaza",
                "destinat să", "destinat sa", "menit să", "menit sa");

        put(Function.CONDITION, 2.8,
                "dacă", "daca", "în cazul în care", "in cazul in care", "cu condiția", "cu conditia",
                "numai dacă", "numai daca", "doar dacă", "doar daca", "atât timp cât", "atat timp cat",
                "în condițiile", "in conditiile");

        put(Function.EXAMPLE, 3.0,
                "de exemplu", "spre exemplu", "de pildă", "de pilda", "bunăoară", "bunaoara",
                "cum ar fi", "printre care", "un exemplu", "exemplifică", "exemplifica", "ilustrează", "ilustreaza");

        put(Function.ENUMERATION, 2.4,
                "în primul rând", "in primul rand", "în al doilea rând", "in al doilea rand",
                "în al treilea rând", "in al treilea rand", "mai întâi", "mai intai", "apoi",
                "următoarele", "urmatoarele", "pot fi enumerate", "se disting", "printre acestea");

        put(Function.CLASSIFICATION, 3.0,
                "se clasifică", "se clasifica", "pot fi clasificate", "pot fi clasificați", "pot fi clasificati",
                "se împart în", "se impart in", "tipuri de", "categorii de", "clase de", "grupe de",
                "se disting două", "se disting doua", "se disting trei");

        put(Function.COMPARISON, 2.8,
                "în comparație cu", "in comparatie cu", "comparativ cu", "similar cu", "asemănător cu", "asemanator cu",
                "la fel ca", "precum", "în mod similar", "in mod similar", "ambele", "atât", "atat");

        put(Function.CONTRAST, 3.0,
                "dar", "însă", "insa", "totuși", "totusi", "în schimb", "in schimb", "dimpotrivă", "dimpotriva",
                "spre deosebire de", "în contrast cu", "in contrast cu", "pe când", "pe cand", "cu toate acestea");

        put(Function.ARGUMENTATION, 2.8,
                "susținem că", "sustinem ca", "se poate susține", "se poate sustine", "argument", "argumente",
                "demonstrează că", "demonstreaza ca", "arată că", "arata ca", "rezultă că", "rezulta ca",
                "considerăm că", "consideram ca", "teza", "ipoteza");

        put(Function.EVIDENCE, 3.0,
                "dovezile", "dovadă", "dovada", "datele arată", "datele arata", "studiul arată", "studiul arata",
                "cercetările arată", "cercetarile arata", "rezultatele indică", "rezultatele indica", "conform datelor",
                "potrivit studiului", "observațiile", "observatiile", "experimentul");

        put(Function.PROBLEM, 2.9,
                "problema este", "dificultatea", "obstacol", "limitare", "limitări", "limitari", "dezavantaj",
                "risc", "riscuri", "provocare", "provocări", "provocari", "nu poate", "nu pot");

        put(Function.SOLUTION, 3.0,
                "soluția", "solutia", "se poate rezolva", "poate fi rezolvat", "poate fi rezolvată", "poate fi rezolvata",
                "remediu", "măsură", "masura", "măsuri", "masuri", "pentru a reduce", "pentru a evita",
                "este necesar", "este necesară", "este necesara");

        put(Function.SEQUENCE, 2.5,
                "mai întâi", "mai intai", "inițial", "initial", "după aceea", "dupa aceea", "ulterior",
                "în continuare", "in continuare", "în cele din urmă", "in cele din urma", "la final",
                "prima etapă", "prima etapa", "a doua etapă", "a doua etapa", "procesul începe", "procesul incepe");

        put(Function.TRANSITION, 3.0,
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la",
                "cu privire la", "în privința", "in privinta", "un alt aspect", "trecând la", "trecand la");

        put(Function.SUMMARY, 2.8,
                "pe scurt", "în rezumat", "in rezumat", "în sinteză", "in sinteza", "în ansamblu", "in ansamblu",
                "per ansamblu", "în esență", "in esenta", "sintetizând", "sintetizand");

        put(Function.CONCLUSION, 3.1,
                "în concluzie", "in concluzie", "așadar", "asadar", "în final", "in final",
                "se poate concluziona", "rezultă în concluzie", "rezulta in concluzie", "în consecință", "in consecinta");

        put(Function.DEVELOPMENT, 1.0,
                "de asemenea", "în plus", "in plus", "totodată", "totodata", "mai mult", "în continuare", "in continuare");

        putOperator(Operator.NEGATION, 2.0,
                "nu", "nici", "niciun", "nicio", "fără", "fara", "absența", "absenta", "lipsa");
        putOperator(Operator.MODALITY, 1.8,
                "poate", "pot", "ar putea", "posibil", "posibilă", "posibila", "probabil", "probabilă", "probabila",
                "trebuie", "necesar", "necesară", "necesara", "obligatoriu", "obligatorie", "este posibil");
        putOperator(Operator.QUANTITY, 1.7,
                "toți", "toti", "toate", "majoritatea", "multe", "mulți", "multi", "puține", "putine", "puțini", "putini",
                "unele", "unii", "jumătate", "jumatate", "procent", "procente", "proporție", "proportie", "număr", "numar");
        putOperator(Operator.RESTRICTION, 2.0,
                "doar", "numai", "exclusiv", "în special", "in special", "în particular", "in particular", "mai ales");
        putOperator(Operator.INCLUSION, 1.5,
                "inclusiv", "de asemenea", "și", "si", "precum și", "precum si", "împreună cu", "impreuna cu");
        putOperator(Operator.EXCLUSION, 2.0,
                "exceptând", "exceptand", "cu excepția", "cu exceptia", "în afară de", "in afara de", "exceptând cazul", "exceptand cazul");
        putOperator(Operator.TEMPORAL, 1.7,
                "înainte", "inainte", "după", "dupa", "ulterior", "anterior", "în timp ce", "in timp ce", "când", "cand",
                "astăzi", "astazi", "ieri", "mâine", "maine", "an", "ani", "secol", "perioadă", "perioada");
        putOperator(Operator.SPATIAL, 1.7,
                "în regiunea", "in regiunea", "în zona", "in zona", "la nivel local", "la nivel global",
                "în europa", "in europa", "în românia", "in romania", "regiune", "zonă", "zona", "local", "global");
        putOperator(Operator.COMPARATIVE, 1.8,
                "mai mult", "mai puțin", "mai putin", "mai puține", "mai putine", "mai puțini", "mai putini",
                "mai precis", "mai precisă", "mai precisa", "mai precise", "mai rapid", "mai rapidă", "mai rapida",
                "mai mare", "mai mari", "mai mic", "mai mică", "mai mica", "mai mici", "decât", "decat",
                "similar", "asemănător", "asemanator", "diferit", "diferită", "diferita", "superior", "inferior");
        putOperator(Operator.COREFERENCE, 1.5,
                "acesta", "aceasta", "aceștia", "acestia", "acestea", "el", "ea", "ei", "ele",
                "acest proces", "acest fenomen", "această metodă", "aceasta metoda", "acest sistem", "respectivul", "respectiva");
        putOperator(Operator.TOPIC_FRAME, 2.2,
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la",
                "cu privire la", "în privința", "in privinta", "în cazul", "in cazul");

        slots(Function.INTRODUCTION, Slot.WHAT, Slot.CLAIM);
        slots(Function.DEFINITION, Slot.WHAT, Slot.WHICH);
        slots(Function.DESCRIPTION, Slot.WHAT, Slot.WHICH, Slot.HOW, Slot.QUANTITY);
        slots(Function.EXPLANATION, Slot.WHY, Slot.HOW, Slot.CONDITION, Slot.EFFECT);
        slots(Function.CAUSE_EFFECT, Slot.WHY, Slot.CONDITION, Slot.HOW, Slot.EFFECT);
        slots(Function.PURPOSE, Slot.PURPOSE, Slot.HOW, Slot.EFFECT);
        slots(Function.CONDITION, Slot.CONDITION, Slot.EFFECT);
        slots(Function.EXAMPLE, Slot.WHICH, Slot.WHAT);
        slots(Function.ENUMERATION, Slot.WHICH, Slot.QUANTITY);
        slots(Function.CLASSIFICATION, Slot.WHICH, Slot.QUANTITY, Slot.COMPARISON);
        slots(Function.COMPARISON, Slot.COMPARISON, Slot.WHAT, Slot.HOW);
        slots(Function.CONTRAST, Slot.COMPARISON, Slot.WHAT, Slot.HOW);
        slots(Function.ARGUMENTATION, Slot.CLAIM, Slot.WHY, Slot.EVIDENCE);
        slots(Function.EVIDENCE, Slot.EVIDENCE, Slot.CLAIM, Slot.WHAT);
        slots(Function.PROBLEM, Slot.WHAT, Slot.WHO, Slot.WHY, Slot.CONDITION, Slot.EFFECT);
        slots(Function.SOLUTION, Slot.WHAT, Slot.HOW, Slot.PURPOSE, Slot.EFFECT);
        slots(Function.SEQUENCE, Slot.WHAT, Slot.WHEN, Slot.HOW, Slot.EFFECT);
        slots(Function.TRANSITION, Slot.WHAT);
        slots(Function.SUMMARY, Slot.WHAT, Slot.CLAIM, Slot.EFFECT);
        slots(Function.CONCLUSION, Slot.CLAIM, Slot.EFFECT);
        slots(Function.DEVELOPMENT, Slot.WHAT, Slot.WHO, Slot.WHERE, Slot.WHEN, Slot.WHY, Slot.HOW);
        slots(Function.UNKNOWN, Slot.WHAT, Slot.WHO, Slot.WHERE, Slot.WHEN, Slot.WHY, Slot.HOW);

        STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "a", "ai", "al", "ale", "acest", "aceasta", "acesta", "acești", "acesti", "aceste",
                "ca", "că", "care", "ce", "cel", "cea", "cei", "cele", "cu", "către", "catre",
                "de", "din", "dintre", "după", "dupa", "este", "sunt", "era", "erau", "fi", "fie",
                "în", "in", "între", "intre", "la", "le", "li", "lui", "lor", "mai", "ne", "o", "pe",
                "pentru", "prin", "sa", "să", "se", "și", "si", "sau", "un", "una", "unei", "unui",
                "the", "of", "and", "or", "to", "on", "for", "with", "by", "from", "as", "is", "are"
        )));

        FRAME_PREFIXES = normalizedList(
                "în anul", "in anul", "în anii", "in anii", "în secolul", "in secolul", "în perioada", "in perioada",
                "în prezent", "in prezent", "în trecut", "in trecut", "în europa", "in europa", "în românia", "in romania",
                "din punct de vedere", "în aceste condiții", "in aceste conditii", "în acest context", "in acest context",
                "la nivel", "în cadrul", "in cadrul"
        );

        TOPIC_PREFIXES = normalizedList(
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la",
                "cu privire la", "în privința", "in privinta", "în legătură cu", "in legatura cu", "privind"
        );

        PREDICATE_CUES = normalizedList(
                "este", "sunt", "reprezintă", "reprezinta", "constituie", "înseamnă", "inseamna",
                "are", "au", "poate", "pot", "produce", "produc", "determină", "determina",
                "provoacă", "provoaca", "generează", "genereaza", "permite", "permit",
                "include", "includ", "cuprinde", "cuprind", "se definește", "se defineste",
                "se caracterizează", "se caracterizeaza", "depinde", "rezultă", "rezulta"
        );

        COREFERENCE_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "acesta", "aceasta", "acestia", "aceștia", "acestea", "acela", "aceea", "aceia", "acelea",
                "el", "ea", "ei", "ele", "respectivul", "respectiva", "respectivii", "respectivele"
        )));
    }

    public static List<Marker> markers(Function function) {
        List<Marker> markers = FUNCTION_MARKERS.get(function);
        return markers == null ? Collections.emptyList() : markers;
    }

    public static List<Marker> markers(Operator operator) {
        List<Marker> markers = OPERATOR_MARKERS.get(operator);
        return markers == null ? Collections.emptyList() : markers;
    }

    public static List<Slot> slotsFor(Function function) {
        List<Slot> slots = QUERY_SLOTS.get(function);
        if (slots != null) return slots;
        return QUERY_SLOTS.get(Function.UNKNOWN);
    }

    public static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}'\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void put(Function function, double weight, String... phrases) {
        List<Marker> markers = new ArrayList<>();
        for (String phrase : phrases) markers.add(new Marker(phrase, weight));
        FUNCTION_MARKERS.put(function, Collections.unmodifiableList(markers));
    }

    private static void putOperator(Operator operator, double weight, String... phrases) {
        List<Marker> markers = new ArrayList<>();
        for (String phrase : phrases) markers.add(new Marker(phrase, weight));
        OPERATOR_MARKERS.put(operator, Collections.unmodifiableList(markers));
    }

    private static void slots(Function function, Slot... slots) {
        QUERY_SLOTS.put(function, Collections.unmodifiableList(Arrays.asList(slots)));
    }

    private static List<String> normalizedList(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) out.add(fold(value));
        return Collections.unmodifiableList(out);
    }
}
