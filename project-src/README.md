# Biblio Topic Search 1.1

Biblio Topic Search este o aplicație Android offline pentru cercetare rapidă în cărți și alte surse fizice, folosind OCR live pe cameră. Aplicația nu decide semantic ce este relevant: utilizatorul definește termenii, nodurile și hărțile de cercetare, iar interpretarea contextului rămâne la utilizator.

## Interfața 1.1

- Camera rămâne ecranul principal.
- Buton mare `OCR LIVE / OCR ÎN PAUZĂ`: camera poate rămâne deschisă chiar dacă detecția este oprită temporar.
- Stare OCR vizibilă prin indicator colorat și statistici live.
- Harta activă și numărul de noduri/termeni sunt afișate sus; atingerea titlului deschide biblioteca de teme.
- Biblioteca locală permite mai multe hărți și foldere. Pentru subfoldere se pot folosi căi precum `Medievală/Reforma`.
- Hărțile pot fi activate rapid, duplicate, mutate/redenumite și șterse.
- Editorul hărții este vizual: arbore ierarhic, indentare pe nivel, culori permanente, termeni tip „chip”, pliere/expandare și activare/dezactivare rapidă prin apăsare lungă.
- Editorul brut cu `# / ## / ###` rămâne disponibil sub `EDITOR TEXT AVANSAT` pentru import și editări masive.
- Overlay-ul camerei păstrează toate highlight-urile, dar evită aglomerarea etichetelor: etichetele care nu încap devin badge-uri compacte în loc să se suprapună.

## OCR și căutare

- OCR live local pe dispozitiv cu CameraX + ML Kit Text Recognition.
- Camera folosește `STRATEGY_KEEP_ONLY_LATEST`, astfel încât cadrele vechi să nu blocheze preview-ul.
- Planul de căutare este compilat o singură dată când harta/setările se schimbă.
- Caută simultan termenii din toate nodurile active.
- Harta nu conține categorii prestabilite.
- Dacă un nod nu are termeni expliciți, titlul nodului este folosit temporar ca termen.
- Potriviri: Exact / Începe cu / Conține.
- Opțional: ignorare diacritice și comparație pe primele N caractere.
- Reglajul `Țintire` controlează stabilizarea grafică a etichetelor, nu precizia ML Kit.
- Highlight-urile vechi sunt eliminate automat dacă nu mai există un rezultat OCR proaspăt.

## Hărți și noduri

- `+ NOD PRINCIPAL`, `+ SUBNOD`, `+ TERMEN`.
- Oricâte niveluri de ierarhie.
- Fiecare nod are culoare, simbol și stare activă proprie.
- `DOAR` activează temporar numai nodul ales.
- Activare/dezactivare pe nivel.
- Import din TXT, MD sau DOCX și export TXT.

Formatul avansat:

```text
# Nod principal
termen | expresie
## Subnod
alt termen
### Nivel următor
termen specific
```

## Dicționar local

CSV opțional:

```text
term,definition,synonyms,antonyms,source
```

Dicționarul și hărțile rămân local pe telefon.

## Confidențialitate

Aplicația nu cere permisiunea `INTERNET`. Camera nu este înregistrată și nimic nu se salvează automat. Un cadru este salvat numai dacă utilizatorul îl îngheață și apasă explicit `SALVEAZĂ`.

## Build

Proiectul folosește:

- Java 17
- Android API 36
- Android Gradle Plugin 8.13.2
- CameraX 1.6.1
- ML Kit Text Recognition bundled 16.0.1
- minSdk 23
- orientare portret

Dacă repository-ul tău conține arhiva `BiblioTopicSearch_FINAL.zip`, păstrează workflow-ul GitHub pe care îl folosești deja: acesta dezarhivează proiectul într-un folder `project` și rulează `gradle --no-daemon -p project :app:assembleDebug`.

## 1.1.1 — control vizual live
- comenzile camerei au fost mutate sus, departe de bara de navigație Android;
- panou NODURI pentru a controla în timp real ce noduri sunt afișate, fără a opri căutarea lor;
- legendă compactă cu nod, forma OCR detectată și numărul de apariții;
- etichetele plutitoare sunt opționale și pot fi pornite/oprite direct pe cameră;
- mod de potrivire „Toate simultan (Exact + Începe cu + Conține)”.


## 1.6.0 — cartografiere istorică exhaustivă, deterministă

Aplicația separă acum două moduri de lucru:

- **OCR LIVE** = orientare rapidă și previzualizare; poate eșantiona cadre pentru a păstra latența mică.
- **CAPTURĂ EXACTĂ** = cale explicită, ne-eșantionată, pentru cercetare. Fiecare paragraf OCR din cadrul acceptat intră în registrul sursei, inclusiv dacă nu corespunde niciunui indice.

Fluxul de cercetare este controlat de utilizator și nu generează sinteză istorică:

`SURSĂ → PAGINĂ → PARAGRAF → CLAUZĂ → INDICI → + / × → ARII → MATRICE MULTISURSĂ`

### Registrul exhaustivității

Pentru fiecare sursă se poate declara intervalul de pagini care trebuie parcurs. O pagină devine `COMPLETĂ` numai prin acțiunea explicită **ÎNCHEIE PAGINA ✓**. Aplicația nu declară o sursă exhaustivă numai pentru că a văzut câteva cadre.

Fiecare unitate este contabilizată ca:

- `MAPPED` — există cel puțin un indice tematic explicit;
- `NO_INDEX` — unitatea a fost procesată, dar nu conține un indice configurat;
- `AMBIGUOUS` — aceeași dovadă lexicală ar susține mai multe noduri independente;
- `UNPARSED` — textul OCR a fost păstrat în registru, dar parserul determinist nu a produs o clauză utilizabilă.

Unitățile excepționale pot fi revizuite uman. Exhaustivitatea cere atât închiderea tuturor paginilor din domeniul declarat, cât și absența excepțiilor nerevizuite.

### Indici și încrucișări

Potrivirea tematică acceptă forme românești din aceeași familie flexionară conservatoare. Sinonimele, echivalențele istorice și conceptele rămân definite de utilizator; aplicația nu inventează sinonimie.

- `A + B` = co-prezență în aceeași unitate fără relație structural demonstrată;
- `A × B [R1/R2/...]` = încrucișare deterministă cu două ancore independente și regulă auditabilă;
- părinte/subnod = ierarhie, nu încrucișare.

### Arii și matrice multisursă

Index Core poate reconstrui intervalele de pagini în care fiecare indice este activ și poate afișa o matrice `temă × sursă`. Astfel cercetătorul poate vedea rapid acoperirea, suprapunerile și golurile bibliografice fără ca aplicația să formuleze în locul lui concluzii istorice.

Fișa de cartografiere poate fi exportată ca text și conține proveniența, intervalele tematice, încrucișările deterministe și co-prezențele.

### Fără interpretare generativă

Nu este folosit niciun LLM și niciun serviciu extern de interpretare semantică. După OCR, clasificarea și cartografierea sunt algoritmice, locale și auditabile. ML Kit este folosit local numai pentru recunoașterea optică a textului.
