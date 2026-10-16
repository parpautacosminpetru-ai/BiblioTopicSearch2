# Cartografiere istorică exhaustivă — contract de metodă

BiblioTopicSearch nu trebuie să înlocuiască lectura critică și sinteza istoricului. Scopul său este să elimine timpul mort dintre **sursă** și **fișa de lucru**, păstrând trasabilitatea completă.

## Invariantul central

> Nimic omis fără stare. Nimic legat fără dovadă. Nimic sintetizat automat.

Aplicația poate accelera metoda istorică numai dacă viteza nu ascunde pierderi. De aceea există două căi diferite:

- **OCR LIVE**: orientare rapidă. Cadrele pot fi eșantionate pentru latență mică.
- **CAPTURĂ EXACTĂ**: operație explicită de cercetare. Toate paragrafele OCR din cadrul acceptat sunt contabilizate; acest flux este singurul care contribuie la registrul exhaustivității.

## Unitatea de proveniență

Schema persistentă este:

`SURSĂ → OUTLINE → PAGINĂ → PARAGRAF → PROPOZIȚIE/CLAUZĂ → INDICE/INDICI`

Pentru fiecare unitate se păstrează identitatea stabilă, pagina, poziția, funcția/relația deterministă, starea de cartografiere și dovezile tematice. Indexul tematic păstrează amprenta textului și dovezile explicite, însă din 1.11 originalul PDF/JPEG și textul OCR poziționat sunt arhivate în straturi separate. Originalul nu este rescris; istoricul poate verifica pasajele direct în facsimil.

## Stări exhaustive

Fiecare unitate ajunge într-o stare explicită:

- **MAPPED** — unul sau mai mulți indici controlați sunt ancorați explicit;
- **NO_INDEX** — unitatea a fost parcursă, dar nu servește vocabularului tematic curent;
- **AMBIGUOUS** — aceeași dovadă ar activa mai multe noduri independente;
- **UNPARSED** — OCR-ul a fost contabilizat, dar analiza deterministă nu a produs o structură suficientă.

AMBIGUOUS și UNPARSED intră în coada de audit uman. O sursă nu este declarată exhaustivă cât timp există excepții nerevizuite.

## Exhaustivitate verificabilă

Utilizatorul declară intervalul fizic al sursei, de exemplu `15–342`. O pagină este completă numai prin acțiunea explicită **ÎNCHEIE PAGINA ✓**.

Prin urmare:

`EXHAUSTIV = toate paginile declarate închise + zero excepții nerevizuite`

Aplicația nu deduce singură că o carte a fost parcursă integral.

## Vocabular controlat

Nodurile tematice și sinonimele istorice aparțin cercetătorului. Motorul automatizează numai variația lingvistică sigură:

- formă exactă;
- familie flexionară românească conservatoare.

Nu se folosește derivare semantică largă pentru a declara identitatea unui indice. `basileus` nu devine sinonim pentru `împărat` decât dacă utilizatorul îl introduce ca alias.

## Relații între indici

- `A + B` = co-prezență în aceeași unitate, fără dovadă structurală de relație;
- `A × B [R...]` = două ancore independente + regulă structurală auditabilă;
- `A > B` = ierarhie tematică, nu încrucișare.

Orice `×` trebuie să poată răspunde la întrebarea: **care au fost cele două ancore și ce regulă a autorizat legătura?**

## Viteza metodologică

Cartografierea exactă produce direct:

1. **arii tematice** — unde începe și unde se termină acoperirea unui indice;
2. **coada de extracție** — lista exactă de pagini/paragrafe/clauze la care cercetătorul trebuie să revină pentru o temă;
3. **matricea temă × sursă** — densități și goluri bibliografice;
4. **corpus transversal** — toate locațiile unei teme, grupate pe surse, pentru etapa de sinteză;
5. **fișa exportabilă a sursei** — proveniență, arii, ×, + și starea exhaustivității.

Aceasta permite trei treceri metodologice distincte:

`CARTOGRAFIERE → EXTRACȚIE ȚINTITĂ → SINTEZĂ UMANĂ`

Prima trecere este paralelă pentru toate temele. A doua nu mai caută informația, ci revine direct la locațiile cartografiate. A treia lucrează transversal peste surse.

## Ce nu face aplicația

- nu scrie sinteza istorică;
- nu decide ce „a vrut să spună” autorul;
- nu inventează sinonime;
- nu transformă probabilitatea într-o relație;
- nu ascunde unitățile pe care nu le-a putut clasifica;
- nu declară exhaustivitate fără domeniu și confirmare umană.

ML Kit este folosit local pentru OCR. După OCR, cartografierea de cercetare este deterministă și locală; nu există LLM sau serviciu extern de interpretare.
