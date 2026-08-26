# Marcarea directă SUBIECT / FUNCȚIE

Modul AUTO universal marchează acum direct pe textul OCR:

- `S` = fiecare cuvânt care aparține expresiei detectate ca subiect țintit;
- `F` = fiecare cuvânt din markerul lexical/structural care justifică funcția dominantă/secundară;
- marcarea folosește coordonatele reale ML Kit pentru fiecare `Text.Element`, inclusiv expresii care trec pe mai multe rânduri;
- culoarea și grosimea conturului reflectă încrederea detectorului;
- dacă nu există dovadă lexicală/structurală mapabilă, detectorul nu inventează un fragment `F`;
- panoul AUTO de jos continuă să afișeze subiectul, funcția și scorurile globale.

Flux:

`OCR -> detecție paragraf -> subiect/funcție -> mapare înapoi pe tokenii OCR -> marcaje S/F`

Marcajele sunt independente de `TEXT`, `SEM` și `ȚINTĂ`; este suficient `OCR LIVE`.
