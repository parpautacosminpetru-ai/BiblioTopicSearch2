# Detecție universală paralelă — subiect + funcție

Acest strat adaugă un detector offline, fără model extern, pentru a transforma textul în unități interogabile rapid:

`PARAGRAF → SUBIECT ȚINTIT → FUNCȚIE → OPERATORI → SLOTURI DE INTEROGARE`

## Ce detectează

### Subiect țintit

Detectorul combină:

- topic explicit (`în ceea ce privește`, `cât despre`, `referitor la` etc.);
- eliminarea cadrelor inițiale temporale/spațiale;
- grupul dinaintea unui predicat puternic;
- persistența între propoziții;
- reluările pronominale/demonstrative;
- recurența lexicală și distribuția pe propoziții;
- bonus pentru poziția inițială și pentru expresii nominale suficient de specifice.

Ieșirea păstrează expresia țintită când există, de exemplu `efectele economice ale inflației`, în loc să reducă automat la `inflație`.

### Funcția dominantă a paragrafului

Schema conține:

- INTRODUCTION
- DEFINITION
- DESCRIPTION
- EXPLANATION
- CAUSE_EFFECT
- PURPOSE
- CONDITION
- EXAMPLE
- ENUMERATION
- CLASSIFICATION
- COMPARISON
- CONTRAST
- ARGUMENTATION
- EVIDENCE
- PROBLEM
- SOLUTION
- SEQUENCE
- TRANSITION
- SUMMARY
- CONCLUSION
- DEVELOPMENT
- UNKNOWN

Detectorul păstrează și o funcție secundară atunci când două funcții au suport lexical comparabil.

### Operatori care nu trebuie pierduți

- NEGATION
- MODALITY
- QUANTITY
- RESTRICTION
- INCLUSION
- EXCLUSION
- TEMPORAL
- SPATIAL
- COMPARATIVE
- COREFERENCE
- TOPIC_FRAME

Acești operatori împiedică reducerea greșită a unor afirmații precum `X poate produce Y` la `X produce Y` sau `X nu produce Y` la `X produce Y`.

### Sloturi de interogare

Funcția activează automat numai întrebările relevante din schema extinsă:

- WHAT
- WHO
- WHERE
- WHEN
- WHY
- HOW
- WHICH
- QUANTITY
- CONDITION
- EFFECT
- COMPARISON
- PURPOSE
- EVIDENCE
- CLAIM

Exemplu: `CAUSE_EFFECT` activează prioritar `WHY + CONDITION + HOW + EFFECT`, iar `DEFINITION` activează `WHAT + WHICH`.

## Activare automată în OCR live

Fluxul existent al aplicației este deja legat la detector. `MainActivity` continuă să apeleze `TopicMatcher.find(...)`, iar acel apel:

1. pornește imediat detecția `SUBIECT + FUNCȚIE` pe un worker separat;
2. continuă matching-ul lexical/punctuațional pe fluxul existent;
3. nu pune cadrele OCR în coadă dacă detectorul semantic este încă ocupat — cadrul intermediar este omis pentru a păstra latența mică;
4. publică ultima detecție completă prin `TopicMatcher.latestParagraphDetections()`.

Pentru un singur candidat dominant se poate folosi:

```java
UniversalParagraphDetector.Detection detection = TopicMatcher.strongestLatestParagraph();
```

Astfel, detecția semantică este activă fără rescrierea analyzerului live și fără să blocheze highlight-urile existente.

## Execuție paralelă explicită

`ParallelTextDetectionEngine` rămâne disponibil când apelantul dorește un rezultat sincron combinat. El rulează concomitent:

1. matching lexical/punctuațional prin ramura `TopicMatcher.findLexicalOnly(...)`;
2. detecția automată de subiect + funcție pe fiecare `ML Kit TextBlock`.

Ramura lexicală nu pornește încă o dată sidecar-ul semantic, deci nu există detecție dublă.

Pool-ul este fix și reutilizabil; nu se creează fire noi la fiecare cadru OCR.

Pentru text lipit/importat, `detectText(...)` separă paragrafele după liniile goale și le procesează concurent, păstrând ordinea originală.

## Folosire cu OCR ML Kit

Pentru fluxul live existent nu este necesar cod suplimentar. Dacă este nevoie de rezultat sincron combinat:

```java
private final ParallelTextDetectionEngine detector = new ParallelTextDetectionEngine();

ParallelTextDetectionEngine.CombinedResult result = detector.detect(text, searchPlan);

List<MatchHit> hits = result.lexicalHits();
List<UniversalParagraphDetector.Detection> paragraphs = result.paragraphs();

for (UniversalParagraphDetector.Detection p : paragraphs) {
    String subject = p.subject();
    UniversalDetectionLexicon.Function function = p.function();
    List<UniversalDetectionLexicon.Slot> questions = p.querySlots();
}

// în onDestroy()/close:
detector.close();
```

## Folosire cu text arbitrar

```java
ParallelTextDetectionEngine detector = new ParallelTextDetectionEngine();
try {
    List<UniversalParagraphDetector.Detection> result = detector.detectText(textIntegral);
    for (UniversalParagraphDetector.Detection p : result) {
        System.out.println(p.compactLabel());
    }
} finally {
    detector.close();
}
```

## Principiu de siguranță semantică

Detectorul nu completează informația care nu există în text. Când dovezile lexicale/structurale sunt slabe:

- `subjectConfidence` rămâne mic;
- `functionConfidence` rămâne mic;
- funcția cade pe `DEVELOPMENT` sau `UNKNOWN`.

Aplicația poate folosi pragurile de încredere pentru a decide dacă afișează automat rezultatul sau cere validare vizuală.

## Limbă și universalitate

Ontologia (`Function`, `Operator`, `Slot`) și motorul sunt reutilizabile pentru orice limbă. Pachetul lexical inclus în această versiune este românesc și tolerant la diacritice. Pentru altă limbă se adaugă un pachet echivalent de forme lexicale fără a schimba algoritmul.

„Orice complexitate” înseamnă că motorul nu presupune o lungime fixă a paragrafului și poate procesa multe paragrafe în paralel. Nu înseamnă acuratețe semantică absolută: fără parser sintactic/coreference neural, anumite texte eliptice, literare sau intenționat ambigue vor avea încredere scăzută.
