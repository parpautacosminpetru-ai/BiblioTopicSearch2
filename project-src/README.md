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

Aplicația nu cere permisiunea `INTERNET`. În fluxurile noi **IMPORT PDF** și **SCANARE ORIGINALĂ**, fișierul PDF sau fotografia JPEG originală sunt păstrate **integral, local, fără modificarea octeților**, separat de OCR și de cartografiere, pentru verificarea dovezilor. Fiecare original are amprentă SHA-256; nu este suprascris în aceeași sursă/pagină. OCR-ul și pozițiile sale sunt derivate și stocate separat. Fluxul vechi **LUPĂ LIVE** rămâne orientativ și nu poate încheia o pagină fără facsimil păstrat. Exportul originalului este explicit. Fișierele private pot ocupa spațiu considerabil; nu dezinstala fără backup.

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


## 1.7.0 — viteză de trecere unică

Această versiune reduce interacțiunea manuală din cartografiere fără a relaxa controlul metodologic.

### AUTO CAPTURĂ deterministă

LUPĂ poate urmări stabilitatea paginii OCR. O pagină este eligibilă pentru captură automată numai dacă:

- există suficient text OCR;
- mai multe cadre consecutive descriu aceeași pagină;
- stabilitatea durează un interval minim;
- pagina nu a fost deja capturată;
- există un număr de pagină detectat sau introdus explicit.

AUTO CAPTURĂ execută numai **CAPTURĂ EXACTĂ**. Nu declară pagina completă. Actul **ÎNCHEIE ✓** rămâne explicit la cercetător, astfel încât viteza nu poate falsifica exhaustivitatea.

### Matching tematic într-o singură trecere

Toți termenii tuturor nodurilor active sunt compilați într-un matcher multi-pattern pe tokeni. Sunt folosite două automate:

- forma lexicală exactă normalizată;
- familia flexionară românească conservatoare.

Textul sursei este tokenizat o singură dată, iar toate temele sunt căutate simultan. Sinonimele istorice rămân exclusiv aliasuri definite de utilizator.

Același plan tematic compilat este reutilizat pe întreg cadrul de CAPTURĂ EXACTĂ pentru:

- paragrafe;
- propoziții/clauze;
- încrucișări deterministe;
- continuitate prin coreferență explicită.

Prin urmare, creșterea numărului de teme nu mai implică o recitire separată a textului pentru fiecare indice.

### Principiu de viteză

`ÎNTOARCE PAGINA → STABILIZARE → AUTO CAPTURĂ → ÎNCHEIE ✓ → URMĂTOAREA PAGINĂ`

Interpretarea și sinteza istorică rămân umane.


## 1.8.0 — Atelierul istoricului

Peste cartografierea exhaustivă există acum un strat explicit de argumentație controlată de cercetător:

- **Critica sursei** — fișe pentru gen, datare, proveniență, transmitere, public, proximitate, perspectivă, autenticitate și ediție.
- **Afirmații de cercetare** — formulate numai de utilizator, cu stare metodologică aleasă manual.
- **Rețea de dovezi** — orice unitate cartografiată poate fi legată de o afirmație ca `SUSȚINE`, `CONTRAZICE`, `NUANȚEAZĂ`, `EXEMPLIFICĂ` sau `CONTEXTUALIZEAZĂ`.
- **Proveniență reversibilă** — fiecare legătură păstrează ID-ul unității și adresa sursă/pagină/paragraf/propoziție/clauză.
- **Jurnal de decizii** — modificările metodologice rămân auditabile.
- **Export dosar reproductibil** — harta tematică, critica surselor, afirmațiile, dovezile și jurnalul pot fi exportate împreună.

Aplicația nu deduce adevărul unei afirmații din numărul de dovezi și nu scrie argumentația în locul istoricului.


## 1.9.0 — trusă de arhivă digitală

### Import PDF offline

O sursă PDF poate fi parcursă în lot fără server și fără permisiune INTERNET:

`PDF → randare locală a paginii → OCR local → CAPTURĂ EXACTĂ → index persistent`

Utilizatorul stabilește explicit maparea dintre paginile PDF și paginile tipărite. Pagina este marcată completă numai dacă OCR-ul produce material explicit; paginile ilizibile rămân deschise pentru control uman. Opțional, intervalul importat poate defini domeniul exhaustivității.

Randările temporare ale paginilor sunt reciclate după OCR și nu sunt păstrate; din versiunea 1.11 originalul PDF este însă păstrat separat, integral și neschimbat.

### Cronologie și locuri navigabile

Unitățile cartografiate păstrează fațete deterministe:

- `YEAR` pentru ani expliciți;
- `TIME` pentru expresii temporale detectate;
- `PLACE` pentru expresii spațiale detectate.

Index Core oferă navigare `CRONOLOGIE / TIMP / LOCURI` și revine la pagina, paragraful și clauza exactă.

### Relații între surse

Atelierul Istoric poate păstra relații declarate de cercetător între surse:

`SAME_WORK`, `EDITION_OF`, `TRANSLATION_OF`, `MANUSCRIPT_OF`, `COPY_OF`, `DERIVED_FROM`, `CITES`, `RESPONDS_TO`, `CONTEMPORARY_WITH`.

Aceste relații nu sunt inferate automat și apar în dosarul reproductibil.

### Mod de arhivă cu pedală / telecomandă

În LUPĂ, modul **PEDALĂ / TASTE** permite controlul fără atingerea ecranului:

- `PageUp` sau `Vol−` → CAPTURĂ EXACTĂ;
- `PageDown` sau `Vol+` → ÎNCHEIE PAGINA.

O pedală Bluetooth sau o telecomandă configurată ca PageUp/PageDown poate deveni astfel control fizic de lucru în bibliotecă/arhivă.


## 1.10.0 — flux simplificat pentru cercetător

Aplicația pornește acum într-un ecran principal orientat după pașii reali ai cercetării, nu după modulele tehnice:

`TEME → SURSĂ → CARTOGRAFIERE → REZULTATE → ATELIER`

Ecranul principal arată imediat:
- harta tematică activă și numărul de noduri/termeni;
- sursa activă;
- starea acoperirii;
- următorul pas recomandat.

Butonul principal **CONTINUĂ** duce automat la primul pas lipsă:
- fără hartă → biblioteca de teme;
- fără sursă definită → editorul sursei;
- hartă + sursă pregătite → LUPĂ.

LUPĂ păstrează comenzile de cercetare frecvente vizibile, iar comenzile tehnice mai rare (zoom semantic, index viu, culori, OCR manual, lumină) sunt ascunse implicit sub **AVANSAT**.

Scopul este ca utilizarea zilnică să poată fi făcută fără memorarea structurii interne a aplicației.


## 1.10.1 — reparare import PDF pe dispozitive reale

Importul PDF a fost întărit pentru furnizori Android diferiți și documente mari:

- fișierul ales este copiat temporar în cache-ul local înainte de `PdfRenderer`, astfel încât importul nu mai depinde de un descriptor seekable oferit de Google Drive / Files / alte document providers;
- interfața PDF este scrollabilă pe ecrane mici;
- randarea paginii are buget de memorie și nu mai creează bitmap-uri necontrolat de mari;
- dacă primul OCR nu citește pagina, există o a doua randare cu rezoluție mai mare;
- o eroare pe o singură pagină nu mai oprește întregul PDF;
- paginile nereușite rămân neînchise în registrul exhaustivității;
- mesajele disting mai clar PDF protejat, lipsă spațiu și alte erori;
- titlul sursei este cerut înainte de import, pentru proveniență corectă.

PDF-ul temporar și bitmap-urile paginilor sunt șterse/eliberate după utilizare. Din versiunea 1.11 o copie octet-cu-octet a PDF-ului original rămâne arhivată local, separat de OCR.


## Căutare deterministă prin întrebare — funcționalitate propusă

În pagina de pornire apare bara **ÎNTREABĂ TOATE SURSELE ARHIVATE**. Scrie o întrebare precisă, de exemplu \`De ce a crescut inflația?\` sau \`Când a fost publicată lucrarea?\`, apoi apasă **CAUTĂ**.

Interogarea:
1. Extrage determinist termenii-țintă și tipul explicit al întrebării (de ce, când, unde, cine, cum etc.) din \`ResearchSemanticEngine\`. Nu folosește LLM sau server.
2. Caută **în toate** sursele locale pentru care s-a arhivat un PDF/JPEG și există OCR (nu doar în sursa activă sau harta tematică). Folosește paragrafele OCR stocate în \`idx_ocr_blocks\`; pentru arhivele vechi fără blocuri reunește liniile OCR ale paginii.
3. Folosește **OCR numai intern** pentru localizarea indiciilor directe și a pasajelor conexe. În interfața de rezultate afișează **imagini decupate din PDF/JPEG-ul original**, nu transcrierea OCR sau răspunsuri inventate.
4. Afișează titlul sursei, pagina și dacă pagina a fost verificată manual. Atingerea unei imagini deschide **întreaga pagină originală**, derulată spre linia corespunzătoare. Marcajul galben este desenat doar în memorie, fără modificarea originalului, iar transcrierea OCR rămâne ascunsă în acest flux.
5. Păstrează căutarea lexicală veche numai în sursa curentă ca opțiune distinctă în ecranul originalului.

**Limite:** analiza depinde de OCR, de lexicul și relațiile deterministe recunoscute. O formulare cu sinonime neconfigurate ori cu răspuns distribuit în surse fără o legătură textuală explicită poate să nu producă un indiciu direct. Faptul că nu s-a găsit un rezultat nu certifică absența din document. \`INDICIU DIRECT\` nu înlocuiește confirmarea umană a sensului și a paginii originale. O pagină fără facsimil arhivat nu apare ca dovadă verificabilă în acest flux.
