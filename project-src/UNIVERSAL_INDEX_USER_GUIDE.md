# BiblioTopicSearch Universal — utilizare metodologică

Această variantă extinde **BiblioTopicSearch 2.3 Beta 5** cu un registru canonic offline, inbox OCR și motor rapid de căutare. Cele șapte hărți tematice incluse rămân disponibile. Fără interpretare generativă.

## Flux de cercetare

1. Din ecranul de pornire: **INDEX UNIVERSAL • IMPORT / INBOX**.
2. Importă un fișier CSV UTF-8 cu coloanele: id, label, category, aliases, source. Separator CSV: virgulă, punct și virgulă sau TAB. În câmpul aliases separă variantele cu |. Folosește ID-uri calificate cu sursa (de exemplu GND:123456 sau LOCAL:ARMATA). Nu se unesc automat omonime cu ID-uri diferite.
3. Opțional, generează anii 1–2100 și secolele I–XXI; anii foarte scurți cer formulări explicite, deoarece numerele de pagină sunt ambigue.
4. Pornește **DETECTORUL**. Hărțile și indicii canonici sunt compilați împreună pe executor separat; căutarea exactă folosește un matcher multipattern.
5. Apasă **INDICI** pentru a consulta separat **OCURENȚE**, **COOCURENȚE**, **SUPRAPUNERI** și **CANDIDAȚI DIN OCR**. Citatele provin textual din OCR, iar conectorii dintre indici sunt afișați literal.
6. Pentru o pagină cu index tipărit precum „Armată ....... 14, 19, 24”, parserul poate propune **INDEX TIPĂRIT • VERIFICĂ**. Atinge candidatul → **TRIMITE ÎN INBOX**. În **INDEX UNIVERSAL → INBOX**, aprobă cu ID canonic sau respinge.
7. Exportă registrul canonic ca CSV. Acest CSV NU este backup complet pentru originale, cartografiere ori tematicile existente.

## Reguli metodologice

- Ocurență: fragment OCR detectat; toate repetițiile reale sunt contabilizate.
- Duplicat: aceeași formă introdusă de mai multe ori pentru un ID; compilată o singură dată. Două variante imbricate ale aceleiași ocurențe canonice nu trebuie să o dubleze.
- Suprapunere: mai multe ID-uri pe aceeași zonă OCR; ele rămân independente.
- Coocurență: doi indici în aceeași propoziție. Nu demonstrează automat relație sintactică ori istorică.
- Conector explicit: fragment lexical vizibil, nu inferență asupra raportului dintre termenii vecini.
- OCR LIVE este orientativ; citatele trebuie verificate pe fotografia originală înainte de utilizare academică.
- Registrul, tematicile și procesarea OCR funcționează offline.

## Limite

- Importul CSV este pe linii; nu acceptă linii noi în interiorul unui câmp citat. Cel mult 200.000 rânduri per fișier.
- Analiza frazei nu este un parser gramatical universal. Relațiile ambigue nu sunt inventate.
- GND, Wikidata, RAMEAU și alte tezaure nu sunt preinstalate: utilizatorul trebuie să importe date obținute legal și convertite în formatul CSV.
- Este un pachet Android nou în varianta debug, cu identificator de instalare distinct de Beta 5, pentru a nu suprascrie cercetarea existentă. Nu migrează automat datele private între aplicații.
- Compilarea și testele automatizate nu înlocuiesc verificarea pe telefon și semnarea cu cheia release originală.
