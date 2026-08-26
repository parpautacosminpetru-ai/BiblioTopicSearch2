package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Language-facing schema for automatic paragraph detection.
 *
 * Semantic classes are language-independent. Romanian lexical realization is now
 * delegated to RomanianLanguagePack for orthography, closed classes, morphology and
 * productive inflection, while this class keeps the discourse ontology/markers.
 */
public final class UniversalDetectionLexicon {
    private UniversalDetectionLexicon() {}

    public enum Function {
        INTRODUCTION, DEFINITION, DESCRIPTION, EXPLANATION, CAUSE_EFFECT, PURPOSE,
        CONDITION, EXAMPLE, ENUMERATION, CLASSIFICATION, COMPARISON, CONTRAST,
        ARGUMENTATION, EVIDENCE, PROBLEM, SOLUTION, SEQUENCE, TRANSITION,
        SUMMARY, CONCLUSION, DEVELOPMENT, UNKNOWN
    }

    public enum Slot {
        WHAT, WHO, WHERE, WHEN, WHY, HOW, WHICH, QUANTITY, CONDITION, EFFECT,
        COMPARISON, PURPOSE, EVIDENCE, CLAIM
    }

    public enum Operator {
        NEGATION, MODALITY, QUANTITY, RESTRICTION, INCLUSION, EXCLUSION,
        TEMPORAL, SPATIAL, COMPARATIVE, COREFERENCE, TOPIC_FRAME
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

    public static final Set<String> STOP_WORDS;
    public static final List<String> FRAME_PREFIXES;
    public static final List<String> TOPIC_PREFIXES;
    public static final List<String> PREDICATE_CUES;
    public static final Set<String> COREFERENCE_WORDS;

    static {
        put(Function.INTRODUCTION, 1.8,
                "acest capitol", "această secțiune", "aceasta sectiune", "în cele ce urmează", "in cele ce urmeaza",
                "vom analiza", "vom examina", "ne vom ocupa", "tema este", "subiectul este",
                "lucrarea de față", "lucrarea de fata", "în continuare analizăm", "in continuare analizam");

        put(Function.DEFINITION, 3.2,
                "se definește", "se defineste", "este definit", "este definită", "este definita",
                "înseamnă", "inseamna", "reprezintă", "reprezinta", "constituie", "desemnează", "desemneaza",
                "se numește", "se numeste", "prin termenul", "prin conceptul", "se înțelege", "se intelege",
                "poartă denumirea", "poarta denumirea", "este denumit", "este denumită", "este denumita");

        put(Function.DESCRIPTION, 1.8,
                "se caracterizează", "se caracterizeaza", "caracteristică", "caracteristica", "caracteristici",
                "proprietate", "proprietăți", "proprietati", "prezintă", "prezinta", "este alcătuit", "este alcatuit",
                "este alcătuită", "este alcatuita", "este format din", "este formată din", "este formata din",
                "componentă", "componenta", "componente", "cuprinde", "include", "are următoarele", "are urmatoarele");

        put(Function.EXPLANATION, 2.7,
                "se explică prin", "se explica prin", "explicația este", "explicatia este", "motivul este",
                "mecanismul", "funcționează prin", "functioneaza prin", "are loc prin", "se produce prin",
                "se datorează", "se datoreaza", "poate fi explicat prin", "poate fi explicată prin", "poate fi explicata prin");

        put(Function.CAUSE_EFFECT, 3.0,
                "deoarece", "fiindcă", "fiindca", "întrucât", "intrucat", "din cauza", "datorită", "datorita",
                "ca urmare", "prin urmare", "în consecință", "in consecinta", "de aceea", "drept urmare",
                "conduce la", "duce la", "determină", "determina", "provoacă", "provoaca", "generează", "genereaza",
                "rezultă", "rezulta", "efect", "efecte", "consecință", "consecinta", "consecințe", "consecinte",
                "cauzează", "cauzeaza", "favorizează", "favorizeaza", "produce", "producând", "producand");

        put(Function.PURPOSE, 2.8,
                "pentru a", "în vederea", "in vederea", "cu scopul de", "în scopul", "in scopul",
                "astfel încât", "astfel incat", "obiectivul", "urmărește", "urmareste", "vizează", "vizeaza",
                "destinat să", "destinat sa", "menit să", "menit sa", "are drept scop", "are ca scop", "în vederea realizării", "in vederea realizarii");

        put(Function.CONDITION, 2.8,
                "dacă", "daca", "în cazul în care", "in cazul in care", "cu condiția", "cu conditia",
                "numai dacă", "numai daca", "doar dacă", "doar daca", "atât timp cât", "atat timp cat",
                "în condițiile", "in conditiile", "cu condiția ca", "cu conditia ca", "presupunând că", "presupunand ca");

        put(Function.EXAMPLE, 3.0,
                "de exemplu", "spre exemplu", "de pildă", "de pilda", "bunăoară", "bunaoara",
                "cum ar fi", "printre care", "un exemplu", "exemplifică", "exemplifica", "ilustrează", "ilustreaza",
                "precum", "între altele", "intre altele");

        put(Function.ENUMERATION, 2.4,
                "în primul rând", "in primul rand", "în al doilea rând", "in al doilea rand",
                "în al treilea rând", "in al treilea rand", "mai întâi", "mai intai", "apoi",
                "următoarele", "urmatoarele", "pot fi enumerate", "se disting", "printre acestea",
                "în primul rând", "în al doilea rând", "în sfârșit", "in sfarsit");

        put(Function.CLASSIFICATION, 3.0,
                "se clasifică", "se clasifica", "pot fi clasificate", "pot fi clasificați", "pot fi clasificati",
                "se împart în", "se impart in", "tipuri de", "categorii de", "clase de", "grupe de",
                "se disting două", "se disting doua", "se disting trei", "se subdivid", "se grupează", "se grupeaza");

        put(Function.COMPARISON, 2.8,
                "în comparație cu", "in comparatie cu", "comparativ cu", "similar cu", "asemănător cu", "asemanator cu",
                "la fel ca", "precum", "în mod similar", "in mod similar", "ambele", "atât", "atat",
                "față de", "fata de", "raportat la");

        put(Function.CONTRAST, 3.0,
                "dar", "însă", "insa", "totuși", "totusi", "în schimb", "in schimb", "dimpotrivă", "dimpotriva",
                "spre deosebire de", "în contrast cu", "in contrast cu", "pe când", "pe cand", "cu toate acestea",
                "în opoziție cu", "in opozitie cu", "contrar", "în vreme ce", "in vreme ce");

        put(Function.ARGUMENTATION, 2.8,
                "susținem că", "sustinem ca", "se poate susține", "se poate sustine", "argument", "argumente",
                "demonstrează că", "demonstreaza ca", "arată că", "arata ca", "rezultă că", "rezulta ca",
                "considerăm că", "consideram ca", "teza", "ipoteza", "se argumentează", "se argumenteaza", "în sprijinul", "in sprijinul");

        put(Function.EVIDENCE, 3.0,
                "dovezile", "dovadă", "dovada", "datele arată", "datele arata", "studiul arată", "studiul arata",
                "cercetările arată", "cercetarile arata", "rezultatele indică", "rezultatele indica", "conform datelor",
                "potrivit studiului", "observațiile", "observatiile", "experimentul", "sursele arată", "sursele arata",
                "documentele indică", "documentele indica", "potrivit datelor", "conform studiului");

        put(Function.PROBLEM, 2.9,
                "problema este", "dificultatea", "obstacol", "limitare", "limitări", "limitari", "dezavantaj",
                "risc", "riscuri", "provocare", "provocări", "provocari", "nu poate", "nu pot",
                "impediment", "deficiență", "deficienta", "neajuns");

        put(Function.SOLUTION, 3.0,
                "soluția", "solutia", "se poate rezolva", "poate fi rezolvat", "poate fi rezolvată", "poate fi rezolvata",
                "remediu", "măsură", "masura", "măsuri", "masuri", "pentru a reduce", "pentru a evita",
                "este necesar", "este necesară", "este necesara", "se recomandă", "se recomanda", "se impune", "poate fi remediat");

        put(Function.SEQUENCE, 2.5,
                "mai întâi", "mai intai", "inițial", "initial", "după aceea", "dupa aceea", "ulterior",
                "în continuare", "in continuare", "în cele din urmă", "in cele din urma", "la final",
                "prima etapă", "prima etapa", "a doua etapă", "a doua etapa", "procesul începe", "procesul incepe",
                "înainte de", "inainte de", "după care", "dupa care", "într-o primă etapă", "intr-o prima etapa");

        put(Function.TRANSITION, 3.0,
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la",
                "cu privire la", "în privința", "in privinta", "un alt aspect", "trecând la", "trecand la",
                "în legătură cu", "in legatura cu", "privitor la", "în raport cu", "in raport cu");

        put(Function.SUMMARY, 2.8,
                "pe scurt", "în rezumat", "in rezumat", "în sinteză", "in sinteza", "în ansamblu", "in ansamblu",
                "per ansamblu", "în esență", "in esenta", "sintetizând", "sintetizand", "rezumând", "rezumand");

        put(Function.CONCLUSION, 3.1,
                "în concluzie", "in concluzie", "așadar", "asadar", "în final", "in final",
                "se poate concluziona", "rezultă în concluzie", "rezulta in concluzie", "în consecință", "in consecinta",
                "de aici rezultă", "de aici rezulta", "în încheiere", "in incheiere");

        put(Function.DEVELOPMENT, 1.0,
                "de asemenea", "în plus", "in plus", "totodată", "totodata", "mai mult", "în continuare", "in continuare",
                "pe de altă parte", "pe de alta parte", "în același timp", "in acelasi timp");

        putOperator(Operator.NEGATION, 2.0,
                "nu", "nici", "niciun", "nicio", "fără", "fara", "absența", "absenta", "lipsa", "nimeni", "nimic", "niciodată", "niciodata");
        putOperator(Operator.MODALITY, 1.8,
                "poate", "pot", "ar putea", "posibil", "posibilă", "posibila", "probabil", "probabilă", "probabila",
                "trebuie", "necesar", "necesară", "necesara", "obligatoriu", "obligatorie", "este posibil", "ar trebui", "este probabil");
        putOperator(Operator.QUANTITY, 1.7,
                "toți", "toti", "toate", "majoritatea", "multe", "mulți", "multi", "puține", "putine", "puțini", "putini",
                "unele", "unii", "jumătate", "jumatate", "procent", "procente", "proporție", "proportie", "număr", "numar",
                "fiecare", "oricare", "niciun", "minimum", "maximum", "aproximativ");
        putOperator(Operator.RESTRICTION, 2.0,
                "doar", "numai", "exclusiv", "în special", "in special", "în particular", "in particular", "mai ales", "cel puțin", "cel putin", "cel mult");
        putOperator(Operator.INCLUSION, 1.5,
                "inclusiv", "de asemenea", "și", "si", "precum și", "precum si", "împreună cu", "impreuna cu", "alături de", "alaturi de");
        putOperator(Operator.EXCLUSION, 2.0,
                "exceptând", "exceptand", "cu excepția", "cu exceptia", "în afară de", "in afara de", "exceptând cazul", "exceptand cazul", "mai puțin", "mai putin");
        putOperator(Operator.TEMPORAL, 1.7,
                "înainte", "inainte", "după", "dupa", "ulterior", "anterior", "în timp ce", "in timp ce", "când", "cand",
                "astăzi", "astazi", "ieri", "mâine", "maine", "an", "ani", "secol", "perioadă", "perioada", "epocă", "epoca", "interval");
        putOperator(Operator.SPATIAL, 1.7,
                "în regiunea", "in regiunea", "în zona", "in zona", "la nivel local", "la nivel global",
                "în europa", "in europa", "în românia", "in romania", "regiune", "zonă", "zona", "local", "global", "teritoriu", "spațiu", "spatiu");
        putOperator(Operator.COMPARATIVE, 1.8,
                "mai mult", "mai puțin", "mai putin", "mai puține", "mai putine", "mai puțini", "mai putini",
                "mai precis", "mai precisă", "mai precisa", "mai precise", "mai rapid", "mai rapidă", "mai rapida",
                "mai mare", "mai mari", "mai mic", "mai mică", "mai mica", "mai mici", "decât", "decat",
                "similar", "asemănător", "asemanator", "diferit", "diferită", "diferita", "superior", "inferior", "egal", "echivalent");
        putOperator(Operator.COREFERENCE, 1.5,
                "acesta", "aceasta", "aceștia", "acestia", "acestea", "el", "ea", "ei", "ele",
                "acest proces", "acest fenomen", "această metodă", "aceasta metoda", "acest sistem", "respectivul", "respectiva", "cel dintâi", "cel din urmă");
        putOperator(Operator.TOPIC_FRAME, 2.2,
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la",
                "cu privire la", "în privința", "in privinta", "în cazul", "in cazul", "din perspectiva", "sub aspectul");

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

        LinkedHashSet<String> stops = new LinkedHashSet<>(RomanianLanguagePack.subjectStopWords());
        stops.addAll(Arrays.asList("the","of","and","or","to","on","for","with","by","from","as","is","are"));
        STOP_WORDS = Collections.unmodifiableSet(stops);

        FRAME_PREFIXES = mergeLists(RomanianLanguagePack.framePrefixes(), normalizedList(
                "în aceste condiții", "in aceste conditii", "în acest context", "in acest context", "la nivel", "în cadrul", "in cadrul"
        ));
        TOPIC_PREFIXES = mergeLists(RomanianLanguagePack.topicPrefixes(), normalizedList(
                "în ceea ce privește", "in ceea ce priveste", "cât despre", "cat despre", "referitor la", "cu privire la", "privind"
        ));
        PREDICATE_CUES = mergeLists(RomanianLanguagePack.predicateCues(), normalizedList(
                "reprezintă", "reprezinta", "constituie", "înseamnă", "inseamna", "se definește", "se defineste", "depinde", "rezultă", "rezulta"
        ));

        LinkedHashSet<String> refs = new LinkedHashSet<>(RomanianLanguagePack.coreferenceForms());
        refs.addAll(Arrays.asList("respectivul","respectiva","respectivii","respectivele"));
        COREFERENCE_WORDS = Collections.unmodifiableSet(refs);
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
        return slots != null ? slots : QUERY_SLOTS.get(Function.UNKNOWN);
    }

    public static String fold(String value) {
        return RomanianLanguagePack.fold(value);
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

    private static List<String> mergeLists(List<String> first, List<String> second) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return Collections.unmodifiableList(new ArrayList<>(out));
    }
}
