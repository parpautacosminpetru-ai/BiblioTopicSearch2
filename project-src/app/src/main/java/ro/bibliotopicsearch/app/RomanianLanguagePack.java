package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Romanian language layer used by OCR indexing and semantic detection.
 *
 * The pack deliberately separates:
 *  - closed-class grammar (finite lexicons that can be covered comprehensively),
 *  - productive morphology (rules for open-class words),
 *  - conservative lexical-family matching (for inflectional variants),
 *  - broader derivational stemming (only when a caller explicitly wants it).
 *
 * It is not a dictionary of claims and never invents meaning. Unknown open-class
 * words remain unknown lexically but can still be normalized and grouped by form.
 *
 * Romanian stemming rules are a conservative Java reimplementation informed by the
 * public Snowball Romanian algorithm (3-clause BSD); no external runtime is used.
 */
public final class RomanianLanguagePack {
    private RomanianLanguagePack() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:[.,]\\d+)?)");
    private static final Set<Character> VOWELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            'a','ă','â','e','i','î','o','u'
    )));

    public enum PartOfSpeech {
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB,
        PRONOUN,
        DETERMINER,
        ARTICLE,
        PREPOSITION,
        CONJUNCTION,
        NUMERAL,
        PARTICLE,
        AUXILIARY,
        INTERJECTION,
        PROPER_CANDIDATE,
        UNKNOWN
    }

    public static final class Analysis {
        public final String surface;
        public final String normalized;
        public final String folded;
        public final String familyKey;
        public final String derivationalStem;
        public final Set<PartOfSpeech> possiblePartsOfSpeech;
        public final boolean functionWord;

        Analysis(
                String surface,
                String normalized,
                String folded,
                String familyKey,
                String derivationalStem,
                Set<PartOfSpeech> possiblePartsOfSpeech,
                boolean functionWord
        ) {
            this.surface = surface == null ? "" : surface;
            this.normalized = normalized == null ? "" : normalized;
            this.folded = folded == null ? "" : folded;
            this.familyKey = familyKey == null ? "" : familyKey;
            this.derivationalStem = derivationalStem == null ? "" : derivationalStem;
            this.possiblePartsOfSpeech = Collections.unmodifiableSet(
                    possiblePartsOfSpeech == null || possiblePartsOfSpeech.isEmpty()
                            ? EnumSet.of(PartOfSpeech.UNKNOWN)
                            : EnumSet.copyOf(possiblePartsOfSpeech)
            );
            this.functionWord = functionWord;
        }
    }

    private static final Set<String> ARTICLES = set(
            "un","o","niște","niste","unui","unei","unor",
            "al","a","ai","ale","cel","cea","cei","cele","celui","celei","celor",
            "lui"
    );

    private static final Set<String> PERSONAL_PRONOUNS = set(
            "eu","tu","el","ea","noi","voi","ei","ele",
            "mine","tine","sine","dânsul","dansul","dânsa","dansa","dânșii","dansii","dânsele","dansele",
            "mie","ție","tie","lui","ei","nouă","noua","vouă","voua","lor",
            "mă","ma","te","se","ne","vă","va","îl","il","o","îi","ii","le",
            "mi","ți","ti","i","ni","vi","li",
            "m","t","s","l"
    );

    private static final Set<String> DEMONSTRATIVES = set(
            "acest","această","aceasta","acești","acesti","aceste","acesta","aceștia","acestia","acestea",
            "acel","acea","acei","acele","acela","aceea","aceia","acelea",
            "ăsta","asta","ăștia","astia","astea","ăla","ala","aia","ăia","aia","alea",
            "celălalt","celalalt","cealaltă","cealalta","ceilalți","ceilalti","celelalte",
            "același","acelasi","aceeași","aceeasi","aceiași","aceiasi","aceleași","aceleasi",
            "respectiv","respectiva","respectivă","respectivi","respective","respectivul","respectiva","respectivii","respectivele"
    );

    private static final Set<String> RELATIVE_INTERROGATIVE = set(
            "cine","ce","care","cărui","carui","cărei","carei","căror","caror",
            "cât","cat","câtă","cata","câți","cati","câte","cate",
            "unde","când","cand","cum","încotro","incotro","dincotro","deunde"
    );

    private static final Set<String> INDEFINITE_NEGATIVE = set(
            "oricine","orice","oricare","oricât","oricat","oricâtă","oricata","oricâți","oricati","oricâte","oricate",
            "cineva","ceva","careva","câtva","catva","câtăva","catava","câțiva","cativa","câteva","cateva",
            "altcineva","altceva","vreun","vreo","vreunul","vreuna","unul","una",
            "nimeni","nimic","niciun","nicio","niciunul","niciuna",
            "fiecare","fiecarele","oricel","oarecare"
    );

    private static final Set<String> POSSESSIVE_DETERMINERS = set(
            "meu","mea","mei","mele","tău","tau","ta","tăi","tai","tale",
            "său","sau","sa","săi","sai","sale","nostru","noastră","noastra","noștri","nostri","noastre",
            "vostru","voastră","voastra","voștri","vostri","voastre"
    );

    private static final Set<String> PREPOSITIONS = set(
            "a","asupra","contra","către","catre","cu","de","despre","din","dintre","după","dupa",
            "fără","fara","în","in","între","intre","întru","intru","la","lângă","langa","pe","pentru","peste",
            "prin","printre","spre","sub","până","pana","potrivit","conform","contrar","datorită","datorita",
            "grație","gratie","mulțumită","multumita","via","per","versus","vs",
            "înaintea","inaintea","înapoia","inapoia","deasupra","dedesubtul","împotriva","impotriva",
            "împrejurul","imprejurul","înăuntrul","inauntrul","afară","afara"
    );

    private static final Set<String> CONJUNCTIONS = set(
            "și","si","nici","sau","ori","fie","dar","însă","insa","iar","ci","deci","căci","caci",
            "că","ca","dacă","daca","deși","desi","deoarece","fiindcă","fiindca","întrucât","intrucat",
            "încât","incat","precum","cum","când","cand","unde","până","pana","după","dupa","înainte","inainte",
            "așadar","asadar","totuși","totusi","altfel","apoi"
    );

    private static final Set<String> PARTICLES = set(
            "nu","n","nici","mai","chiar","doar","numai","tocmai","oare","cam","prea","foarte","măcar","macar",
            "barem","aproape","circa","vreo","cumva","parcă","parca","iată","iata","uite","na"
    );

    private static final Set<String> AUXILIARIES = set(
            "sunt","ești","esti","este","e","suntem","sunteți","sunteti","eram","erai","era","eram","erați","erati","erau",
            "fui","fuși","fusi","fu","furăm","furam","furăți","furati","fură","fura","fusesem","fuseseși","fusesesi","fusese","fuseserăm","fuseseram","fuseserăți","fuseserati","fuseseră","fusesera",
            "fi","fii","fie","fim","fiți","fiti","fiind","fost","fostă","fosta","foști","fosti","foste",
            "am","ai","are","avem","aveți","aveti","au","aveam","aveai","avea","aveați","aveati","aveau","avut","având","avand",
            "voi","vei","va","vom","veți","veti","vor",
            "aș","as","ai","ar","am","ați","ati"
    );

    private static final Set<String> COMMON_ADVERBS = set(
            "aici","acolo","acasă","acasa","afară","afara","aproape","departe","sus","jos","înainte","inainte","înapoi","inapoi",
            "azi","astăzi","astazi","ieri","mâine","maine","acum","atunci","odată","odata","mereu","întotdeauna","intotdeauna",
            "niciodată","niciodata","uneori","adesea","adeseori","rar","curând","curand","târziu","tarziu","devreme",
            "bine","rău","rau","repede","încet","incet","astfel","altfel","împreună","impreuna","separat",
            "probabil","posibil","desigur","sigur","evident","aparent","practic","teoretic","respectiv",
            "mult","puțin","putin","destul","suficient","extrem","relativ","aproximativ"
    );

    private static final Set<String> INTERJECTIONS = set(
            "ah","aha","vai","of","uf","hei","măi","mai","bre","bravo","ura","iată","iata","uite","na","alo"
    );

    private static final Set<String> CARDINALS = set(
            "zero","unu","unul","una","doi","două","doua","trei","patru","cinci","șase","sase","șapte","sapte","opt","nouă","noua","zece",
            "unsprezece","doisprezece","douăsprezece","douasprezece","treisprezece","paisprezece","cincisprezece","șaisprezece","saisprezece","șaptesprezece","saptesprezece","optsprezece","nouăsprezece","nouasprezece",
            "douăzeci","douazeci","treizeci","patruzeci","cincizeci","șaizeci","saizeci","șaptezeci","saptezeci","optzeci","nouăzeci","nouazeci",
            "sută","suta","sute","mie","mii","milion","milioane","miliard","miliarde"
    );

    private static final Set<String> ORDINAL_CUES = set(
            "primul","prima","primii","primele","întâiul","intaiul","întâia","intaia",
            "doilea","doua","treilea","treia","patrulea","patra","cincilea","cincea","ultimul","ultima","ultimii","ultimele"
    );

    private static final Set<String> NEGATION = set(
            "nu","n","nici","niciun","nicio","nimeni","nimic","nicăieri","nicaieri","niciodată","niciodata","fără","fara"
    );

    private static final Set<String> COREFERENCE = union(
            PERSONAL_PRONOUNS,
            DEMONSTRATIVES,
            set("acestui","acestei","acestor","acelui","acelei","acelor","respectivul","respectiva","respectivii","respectivele")
    );

    private static final Set<String> FUNCTION_WORDS = union(
            ARTICLES, PERSONAL_PRONOUNS, DEMONSTRATIVES, RELATIVE_INTERROGATIVE,
            INDEFINITE_NEGATIVE, POSSESSIVE_DETERMINERS, PREPOSITIONS, CONJUNCTIONS,
            PARTICLES, AUXILIARIES
    );

    private static final Map<String, String> IRREGULAR_FAMILY = irregularFamilies();

    private static final List<String> PREDICATE_CUES = list(
            "este","e","sunt","era","erau","devine","devin","rămâne","ramane","rămân","raman",
            "reprezintă","reprezinta","constituie","înseamnă","inseamna","denumește","denumeste","desemnează","desemneaza",
            "are","au","avea","poate","pot","trebuie","produce","produc","determină","determina","provoacă","provoaca",
            "generează","genereaza","permite","permit","include","includ","cuprinde","cuprind","presupune","presupun",
            "depinde","rezultă","rezulta","apare","apar","devine","devin","crește","creste","cresc","scade","scad",
            "se definește","se defineste","se caracterizează","se caracterizeaza","se numește","se numeste"
    );

    private static final List<String> TOPIC_PREFIXES = list(
            "în ceea ce privește","in ceea ce priveste","cât despre","cat despre","referitor la","referitoare la",
            "cu privire la","în privința","in privinta","în legătură cu","in legatura cu","privind","privitor la",
            "în raport cu","in raport cu","din perspectiva","din punctul de vedere al","în cazul","in cazul"
    );

    private static final List<String> FRAME_PREFIXES = list(
            "în anul","in anul","în anii","in anii","în secolul","in secolul","în perioada","in perioada","în epoca","in epoca",
            "în prezent","in prezent","în trecut","in trecut","anterior","ulterior","după","dupa","înainte de","inainte de",
            "în românia","in romania","în europa","in europa","la nivel","în cadrul","in cadrul","în contextul","in contextul",
            "din punct de vedere","din perspectiva","sub aspect","în aceste condiții","in aceste conditii"
    );

    private static final String[] STEP0_DELETE = sorted(
            "ului","ul"
    );
    private static final String[][] STEP0_REPLACE = new String[][]{
            {"iilor","i"},{"iile","i"},{"ilor","i"},{"iei","i"},{"iua","i"},{"ii","i"},
            {"elor","e"},{"ele","e"},{"ea","e"},{"aua","a"},{"atei","at"},
            {"ației","ați"},{"atiei","ati"},{"ație","ați"},{"atie","ati"},{"ația","ați"},{"atia","ati"}
    };

    private static final String[][] COMBINING = new String[][]{
            {"abilităților","abil"},{"abilitatilor","abil"},{"abilități","abil"},{"abilitati","abil"},{"abilitate","abil"},
            {"ibilitate","ibil"},
            {"ivităților","iv"},{"ivitatilor","iv"},{"ivități","iv"},{"ivitati","iv"},{"ivitate","iv"},
            {"icităților","ic"},{"icitatilor","ic"},{"icități","ic"},{"icitati","ic"},{"icitate","ic"},
            {"icatorilor","ic"},{"icatori","ic"},{"icator","ic"},{"icive","ic"},{"icivi","ic"},{"icivă","ic"},{"iciva","ic"},{"iciv","ic"},
            {"icalelor","ic"},{"icale","ic"},{"icali","ic"},{"icală","ic"},{"icala","ic"},{"ical","ic"},
            {"ațiunilor","at"},{"atiunilor","at"},{"ațiune","at"},{"atiune","at"},{"ătoare","at"},{"atoare","at"},{"ători","at"},{"atori","at"},{"ător","at"},{"ator","at"},{"ative","at"},{"ativi","at"},{"ativă","at"},{"ativa","at"},{"ativ","at"},
            {"ițiunilor","it"},{"itiunilor","it"},{"ițiune","it"},{"itiune","it"},{"itoare","it"},{"itori","it"},{"itor","it"},{"itive","it"},{"itivi","it"},{"itivă","it"},{"itiva","it"},{"itiv","it"}
    };

    private static final String[] STANDARD_SUFFIXES = sorted(
            "abilă","abila","abile","abili","abil","ibilă","ibila","ibile","ibili","ibil",
            "oasă","oasa","oase","oși","osi","os",
            "antă","anta","ante","anți","anti","ant",
            "itate","ități","itati","ităi","itai",
            "atori","ator","ivă","iva","ive","ivi","iv",
            "ică","ica","ice","ici","ic",
            "ată","ata","ate","ați","ati","at",
            "ută","uta","ute","uți","uti","ut",
            "ită","ita","ite","iți","iti","it"
    );

    private static final String[] VERB_SUFFIXES = sorted(
            "aserăm","aseram","aserăți","aserati","aseră","asera","aseși","asesi","asem","ase",
            "iserăm","iseram","iserăți","iserati","iseră","isera","iseși","isesi","isem","ise",
            "âserăm","anseram","âserăți","anserati","âseră","ansera","âseși","ansesi","âsem","ansem","âse","anse",
            "userăm","useram","userăți","userati","useră","usera","useși","usesi","usem","use",
            "seserăm","seseram","seserăți","seserati","seseră","sesera","seseși","sesei","sesem","sese",
            "arăm","aram","arăți","arati","ară","ara","ași","asi",
            "urăm","uram","urăți","urati","ură","ura","uși","usi",
            "irăm","iram","irăți","irati","iră","ira","iși","isi",
            "ârăm","aram","ârăți","arati","âră","ara","âși","asi",
            "ească","easca","ează","eaza","ește","este","ești","esti","eze","ezi","ez",
            "ăște","aste","ăști","asti","ăsc","asc","esc",
            "ind","ând","and","indu","ându","andu",
            "eam","eai","eați","eati","eau","iam","iai","iați","iati","iau",
            "are","ere","ire","âre","im","iți","iti","âm","am","ați","ati","em","eți","eti",
            "seși","sesi","serăm","seram","serăți","serati","seră","sera","sei","se",
            "au","ai","am","ui"
    );

    private static final String[] NOUN_ADJECTIVE_INFLECTIONS = sorted(
            "urilor","iilor","elor","ilor","ului","elor","ească","easca",
            "iile","urile","ele","ile","ii","uri",
            "elor","ilor","ului","ul","a","ă","e","i"
    );

    private static final String[] VERB_SHAPE_SUFFIXES = sorted(
            "ează","eaza","ească","easca","esc","ești","esti","ește","este","ăsc","asc","ăști","asti","ăște","aste",
            "ind","ând","and","are","ere","ire","âre","at","it","ut","ase","ise","use","se"
    );

    private static final String[] ADJECTIVE_SHAPE_SUFFIXES = sorted(
            "abil","ibil","ic","ică","ica","ice","ici","al","ală","ala","ale","ali","iv","ivă","iva","ive","ivi",
            "os","oasă","oasa","oase","oși","osi","ant","antă","anta","ante","anți","anti","ent","entă","enta","ente","enți","enti",
            "tor","toare","tori","ar","ară","ara","are","ari"
    );

    private static final String[] NOUN_DERIVATIONAL_SUFFIXES = sorted(
            "itate","ități","itati","iune","iuni","ție","tie","ții","tii","ism","isme","ist","istă","ista","iști","isti",
            "ment","mente","are","ere","ire","tor","toare","tori","aj","aje","ură","ura","uri","ie","ii"
    );

    public static String normalizeOrthography(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replace('ş', 'ș').replace('Ş', 'Ș')
                .replace('ţ', 'ț').replace('Ţ', 'Ț')
                .replace('’', '\'').replace('`', '\'').replace('´', '\'')
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String fold(String value) {
        String normalized = normalizeOrthography(value);
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}'\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static Analysis analyze(String surface) {
        String normalized = normalizeOrthography(surface);
        String folded = fold(normalized);
        String token = firstToken(folded);
        EnumSet<PartOfSpeech> pos = EnumSet.noneOf(PartOfSpeech.class);

        if (NUMBER.matcher(token).matches() || CARDINALS.contains(token) || ORDINAL_CUES.contains(token)) pos.add(PartOfSpeech.NUMERAL);
        if (ARTICLES.contains(token)) pos.add(PartOfSpeech.ARTICLE);
        if (PERSONAL_PRONOUNS.contains(token) || DEMONSTRATIVES.contains(token) || RELATIVE_INTERROGATIVE.contains(token) || INDEFINITE_NEGATIVE.contains(token)) pos.add(PartOfSpeech.PRONOUN);
        if (DEMONSTRATIVES.contains(token) || POSSESSIVE_DETERMINERS.contains(token) || INDEFINITE_NEGATIVE.contains(token)) pos.add(PartOfSpeech.DETERMINER);
        if (PREPOSITIONS.contains(token)) pos.add(PartOfSpeech.PREPOSITION);
        if (CONJUNCTIONS.contains(token)) pos.add(PartOfSpeech.CONJUNCTION);
        if (PARTICLES.contains(token)) pos.add(PartOfSpeech.PARTICLE);
        if (AUXILIARIES.contains(token)) { pos.add(PartOfSpeech.AUXILIARY); pos.add(PartOfSpeech.VERB); }
        if (COMMON_ADVERBS.contains(token) || token.endsWith("mente")) pos.add(PartOfSpeech.ADVERB);
        if (INTERJECTIONS.contains(token)) pos.add(PartOfSpeech.INTERJECTION);

        if (!token.isEmpty() && !FUNCTION_WORDS.contains(token) && !NUMBER.matcher(token).matches()) {
            if (hasAnySuffix(token, VERB_SHAPE_SUFFIXES)) pos.add(PartOfSpeech.VERB);
            if (hasAnySuffix(token, ADJECTIVE_SHAPE_SUFFIXES)) pos.add(PartOfSpeech.ADJECTIVE);
            if (hasAnySuffix(token, NOUN_DERIVATIONAL_SUFFIXES) || pos.isEmpty()) pos.add(PartOfSpeech.NOUN);
            if (looksProper(surface)) pos.add(PartOfSpeech.PROPER_CANDIDATE);
        }
        if (pos.isEmpty()) pos.add(PartOfSpeech.UNKNOWN);

        return new Analysis(
                surface,
                normalized,
                folded,
                familyKey(token),
                derivationalStem(token),
                pos,
                isFunctionWord(token)
        );
    }

    public static boolean isFunctionWord(String value) {
        String token = firstToken(fold(value));
        return FUNCTION_WORDS.contains(token);
    }

    public static boolean isCoreference(String value) {
        String token = firstToken(fold(value));
        return COREFERENCE.contains(token);
    }

    public static boolean isNegation(String value) {
        String token = firstToken(fold(value));
        return NEGATION.contains(token);
    }

    public static Set<String> subjectStopWords() {
        return FUNCTION_WORDS;
    }

    public static Set<String> coreferenceForms() {
        return COREFERENCE;
    }

    public static List<String> predicateCues() {
        return PREDICATE_CUES;
    }

    public static List<String> topicPrefixes() {
        return TOPIC_PREFIXES;
    }

    public static List<String> framePrefixes() {
        return FRAME_PREFIXES;
    }

    public static String familyKey(String value) {
        String w = firstToken(fold(value));
        if (w.length() <= 2) return w;
        String irregular = IRREGULAR_FAMILY.get(w);
        if (irregular != null) return irregular;
        if (FUNCTION_WORDS.contains(w)) return w;

        String s = inflectionStem(w);
        if (s.length() < 3) return w;
        return s;
    }

    public static String phraseFamilyKey(String value) {
        String folded = fold(value);
        if (folded.isEmpty()) return "";
        Matcher matcher = TOKEN.matcher(folded);
        List<String> out = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (isFunctionWord(token)) out.add(token);
            else out.add(familyKey(token));
        }
        return String.join(" ", out).trim();
    }

    public static boolean sameLexicalFamily(String a, String b) {
        String left = familyKey(a);
        String right = familyKey(b);
        return !left.isEmpty() && left.equals(right);
    }

    public static boolean containsFamilyPhrase(String text, String phrase) {
        String target = phraseFamilyKey(phrase);
        if (target.isEmpty()) return false;
        List<String> targetTokens = tokens(target);
        List<String> textTokens = tokens(phraseFamilyKey(text));
        if (targetTokens.isEmpty() || textTokens.size() < targetTokens.size()) return false;
        for (int i = 0; i <= textTokens.size() - targetTokens.size(); i++) {
            boolean match = true;
            for (int j = 0; j < targetTokens.size(); j++) {
                if (!textTokens.get(i + j).equals(targetTokens.get(j))) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }

    public static boolean containsAnyFamily(String text, String... lemmasOrStems) {
        if (text == null || lemmasOrStems == null || lemmasOrStems.length == 0) return false;
        Set<String> families = new HashSet<>();
        for (String token : tokens(fold(text))) {
            if (!isFunctionWord(token)) families.add(familyKey(token));
        }
        for (String cue : lemmasOrStems) {
            String family = familyKey(cue);
            if (!family.isEmpty() && families.contains(family)) return true;
            String foldedCue = fold(cue);
            if (!foldedCue.isEmpty() && families.contains(foldedCue)) return true;
        }
        return false;
    }

    /** Broader stem for retrieval/domain grouping. Do not use it as an identity key. */
    public static String derivationalStem(String value) {
        String w = familyKey(value);
        if (w.length() < 5 || FUNCTION_WORDS.contains(w)) return w;
        String s = w;

        boolean changed = true;
        int loops = 0;
        while (changed && loops++ < 4) {
            changed = false;
            for (String[] rule : COMBINING) {
                if (s.endsWith(rule[0]) && s.length() - rule[0].length() >= 3) {
                    s = s.substring(0, s.length() - rule[0].length()) + fold(rule[1]);
                    changed = true;
                    break;
                }
            }
        }

        for (String suffix : STANDARD_SUFFIXES) {
            if (s.endsWith(suffix) && s.length() - suffix.length() >= 4) {
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }
        if (s.endsWith("iune") || s.endsWith("iuni")) {
            String base = s.substring(0, s.length() - 4);
            if (base.endsWith("t") || base.endsWith("ț")) s = base;
        } else if (s.endsWith("isme")) {
            s = s.substring(0, s.length() - 4) + "ist";
        } else if (s.endsWith("ism")) {
            s = s.substring(0, s.length() - 3) + "ist";
        }

        if (s.length() < 3) return w;
        return s;
    }

    private static String inflectionStem(String word) {
        String s = normalizeOrthography(word).toLowerCase(Locale.ROOT);
        if (s.length() < 4) return fold(s);

        int r1 = regionAfterVowelConsonant(s, 0);
        boolean step0 = false;
        for (String[] rule : STEP0_REPLACE) {
            String suffix = normalizeOrthography(rule[0]).toLowerCase(Locale.ROOT);
            if (s.endsWith(suffix) && s.length() - suffix.length() >= r1 && s.length() - suffix.length() >= 2) {
                s = s.substring(0, s.length() - suffix.length()) + normalizeOrthography(rule[1]).toLowerCase(Locale.ROOT);
                step0 = true;
                break;
            }
        }
        if (!step0) {
            for (String suffixRaw : STEP0_DELETE) {
                String suffix = normalizeOrthography(suffixRaw).toLowerCase(Locale.ROOT);
                if (s.endsWith(suffix) && s.length() - suffix.length() >= r1 && s.length() - suffix.length() >= 3) {
                    s = s.substring(0, s.length() - suffix.length());
                    break;
                }
            }
        }

        // Conservative noun/adjective inflection pass. We require a substantial
        // remaining stem to avoid classic over-stemming of short Romanian words.
        for (String rawSuffix : NOUN_ADJECTIVE_INFLECTIONS) {
            String suffix = normalizeOrthography(rawSuffix).toLowerCase(Locale.ROOT);
            if (s.endsWith(suffix) && s.length() - suffix.length() >= 4) {
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }

        // Verb forms are normalized only if the remainder is long enough; auxiliaries
        // were already protected by IRREGULAR_FAMILY/FUNCTION_WORDS.
        int rv = rvRegion(s);
        for (String rawSuffix : VERB_SUFFIXES) {
            String suffix = normalizeOrthography(rawSuffix).toLowerCase(Locale.ROOT);
            int start = s.length() - suffix.length();
            if (start >= Math.max(3, rv) && s.endsWith(suffix)) {
                s = s.substring(0, start);
                break;
            }
        }

        String folded = fold(s);
        if (folded.length() < 3) return fold(word);
        return folded;
    }

    private static int regionAfterVowelConsonant(String s, int from) {
        for (int i = Math.max(0, from); i + 1 < s.length(); i++) {
            if (isVowel(s.charAt(i)) && !isVowel(s.charAt(i + 1))) return i + 2;
        }
        return s.length();
    }

    private static int rvRegion(String s) {
        if (s.length() < 3) return s.length();
        boolean v0 = isVowel(s.charAt(0));
        boolean v1 = isVowel(s.charAt(1));
        if (v0 && v1) {
            for (int i = 2; i < s.length(); i++) if (!isVowel(s.charAt(i))) return Math.min(s.length(), i + 1);
            return s.length();
        }
        if (!v0 && !v1) {
            for (int i = 2; i < s.length(); i++) if (isVowel(s.charAt(i))) return Math.min(s.length(), i + 1);
            return s.length();
        }
        return Math.min(s.length(), 3);
    }

    private static boolean isVowel(char c) {
        return VOWELS.contains(Character.toLowerCase(c));
    }

    private static boolean looksProper(String surface) {
        if (surface == null || surface.isEmpty()) return false;
        String n = normalizeOrthography(surface).trim();
        if (n.isEmpty()) return false;
        int first = n.codePointAt(0);
        return Character.isUpperCase(first) && n.length() >= 3;
    }

    private static boolean hasAnySuffix(String token, String[] suffixes) {
        if (token == null) return false;
        for (String suffix : suffixes) if (token.endsWith(fold(suffix)) && token.length() - fold(suffix).length() >= 2) return true;
        return false;
    }

    private static String firstToken(String value) {
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group() : "";
    }

    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    private static Map<String, String> irregularFamilies() {
        Map<String, String> out = new HashMap<>();
        addFamily(out, "fi", "sunt","esti","ești","este","e","suntem","sunteti","sunteți","era","eram","erai","erati","erați","erau","fui","fusi","fuși","fu","furam","furăm","furati","furăți","fura","fură","fusesem","fusese","fusesera","fuseseră","fi","fii","fie","fim","fiti","fiți","fiind","fost","fosta","fostă","fosti","foști","foste");
        addFamily(out, "avea", "am","ai","are","avem","aveti","aveți","au","aveam","aveai","avea","aveati","aveați","aveau","avut","avand","având");
        addFamily(out, "vrea", "vreau","vrei","vrea","vrem","vreti","vreți","vor","voiam","voiai","voia","voiau","vrut");
        addFamily(out, "putea", "pot","poti","poți","poate","putem","puteti","puteți","puteau","putea","putut");
        addFamily(out, "trebui", "trebuie","trebuia","trebuit","trebuind");
        addFamily(out, "face", "fac","faci","face","facem","faceti","faceți","facut","făcut","facand","făcând");
        addFamily(out, "lua", "iau","iei","ia","luam","luati","luați","luat","luand","luând");
        addFamily(out, "da", "dau","dai","da","dam","dăm","dati","dați","dat","dand","dând");
        addFamily(out, "veni", "vin","vii","vine","venim","veniti","veniți","venit","venind");
        addFamily(out, "merge", "merg","mergi","merge","mergem","mergeti","mergeți","mers","mergand","mergând");
        return Collections.unmodifiableMap(out);
    }

    private static void addFamily(Map<String, String> map, String lemma, String... forms) {
        String family = fold(lemma);
        for (String form : forms) map.put(fold(form), family);
    }

    private static Set<String> set(String... values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String folded = fold(value);
            if (!folded.isEmpty()) out.add(folded);
        }
        return Collections.unmodifiableSet(out);
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (sets != null) for (Set<String> set : sets) if (set != null) out.addAll(set);
        return Collections.unmodifiableSet(out);
    }

    private static List<String> list(String... values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String folded = fold(value);
            if (!folded.isEmpty()) out.add(folded);
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    private static String[] sorted(String... values) {
        String[] copy = values.clone();
        Arrays.sort(copy, (a, b) -> Integer.compare(b.length(), a.length()));
        return copy;
    }
}
