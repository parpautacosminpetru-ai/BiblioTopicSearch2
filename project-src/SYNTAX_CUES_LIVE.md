# Marcarea operatorilor gramaticali — OCR LIVE (fără AI)

Modul LUPĂ marchează automat, la zoom PROPOZIȚIE și SEGMENT, expresii
gramaticale găsite literal în textul OCR.

Familiile inițiale: cauză, scop, condiție, timp, negație, modalitate,
concesie, contrast, comparație, consecință, atribuire, mod, restricție,
excepție, cantitate, spațiu și referință pronominală.

- Sintaxa este semnalată prin indicii lexicale, nu prin afirmații noi.
- Markerii sunt ancorați în coordonatele cuvintelor OCR din imagine.
- Regula de potrivire este deterministă și publicată în
  SyntaxCueDetector.java. Nu există apeluri AI sau deducție generativă.
- Sunt recunoscute întâi expresiile mai lungi: "chiar dacă" este
  concesie, nu simplu "dacă"; "după cum spune" atribuire, nu "după".
- "după" poate fi ambiguu în limba naturală: markerul temporal este
  doar un indiciu de control, nu dovedește o relație.
- "pentru a" este indiciu pentru scop, nu pentru cauză.
- O evidențiere exactă pe imagine nu demonstrează că au fost recuperate
  toate dependențele sintactice sau toate relațiile istorice.
- Nu se modifică originalul fotografic și nu se generează sinteze.

## Afișaj

La zoom SURSĂ și SECȚIUNE, suprafața rămâne curată.
La PARAGRAF se păstrează funcția dominantă a paragrafului.
La PROPOZIȚIE și SEGMENT, stratul de funcție prezintă operatorii locali,
iar subiectul și încrucișările R1/R2 rămân disponibile.
Nicio co-prezență de termeni nu devine automat o relație verificată.

## Testare

SyntaxCueDetectorTest verifică negarea, cauza, scopul, timpul,
concesia, atribuirea, repetările și coordonatele de token.
Rulați Gradle :app:testDebugUnitTest.
