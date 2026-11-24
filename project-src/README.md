# 1.1.5 — Detecție literală verificabilă

- Modul implicit este Exact: termen integral, fără trunchiere, fără potriviri de prefix sau subșir. Cuvintele cu diacritice diferite nu sunt confundate. Titlurile nodurilor fără termeni, corectarea OCR și reunirea la cratimă se activează numai explicit din Setări.
- Setările de potrivire și stabilizare folosesc chei noi pentru a nu reactiva automat valorile permisive din versiunile anterioare. Hărțile, culorile, selecțiile, dicționarul și preferințele de afișare rămân neschimbate.
- Punctuația dintre cuvinte este păstrată în index; numerele compuse și cuvintele cu cratimă nu sunt fragmentate pentru a crea potriviri false. Expresiile traversează rândurile numai în același bloc/coloană și cu spațiere plauzibilă.
- Textul OCR și elementele sale sunt aliniate după offseturi reale. Marcajele folosesc dreptunghiuri pe element/simbol; un fragment care nu poate fi localizat fără a acoperi alt cuvânt este respins. Suprapunerile tematice păstrează identitatea și culoarea fiecărei hărți.
- Rezultatele expiră la cel mult 900 ms de la captură; nu se mai rețin marcaje absente din ultimul OCR. Modificarea zoomului ori transformării camerei invalidează cadrele vechi.
- Reconstrucțiile opționale se bazează pe un termen integral existent, elimină alternativele ambigue și necesită confirmare pe cadre stabile. Textul brut rămâne accesibil prin atingerea marcajului.
- Teste de regresie: stat/statuie, art/partid, date numerice, punctuație, diacritice, hărți suprapuse, titluri implicite, fragmente la cratimă, delimitarea coloanelor, alinierea textului și expirarea cadrelor. Motorul ML Kit poate continua să citească greșit caractere; măsurarea preciziei reale necesită pagini și dispozitive reprezentative.

# 1.1.4 — Culoare proprie pentru fiecare hartă

- O singură culoare RGB pentru fiecare profil, aleasă în Bibliotecă (pătratul colorat), în dialogul de creare sau în editorul hărții.
- Culoarea se salvează local după ID-ul stabil al hărții, se păstrează la redenumire/mutare și se copiază la duplicare. Nu depinde de selecția OCR ori de nod.
- Toate nodurile, marcajele, legenda și etichetele folosesc culoarea profilului. Potrivirile suprapuse exact păstrează câte o bandă pe hartă.
- La prima citire a hărților vechi, prima culoare de nod personalizată explicit devine culoarea hărții; dacă nu există, se folosește o culoare implicită. Preferințele vechi nu sunt șterse.
- Paletă, RGB și HEX cu contrast automat al textului. Utilizatorul poate alege inclusiv aceeași culoare pentru două hărți; identitatea și detecția rămân separate.

# 1.1.3 — OCR adaptiv și detecție stabilă

- OCR local ML Kit cu admitere controlată a cadrelor și pauze adaptate latenței și temperaturii raportate de Android (API 29+).
- Rezoluție preferată 1600×1200, mod economic 960×720; revenire doar după performanță stabilă, cu interval minim de 20 s. CameraX alege cea mai apropiată rezoluție suportată.
- Compararea ancorelor OCR din cadre succesive: schimbarea paginii sau mișcarea camerei întrerupe stabilizarea și păstrarea marcajelor.
- Corectări prudente ale unei singure cifre confundate cu o literă, doar pentru un termen integral existent în hărțile selectate și confirmare în două cadre; opțiunea se poate opri din Setări.
- Reunirea cuvintelor despărțite vizibil la sfârșitul rândului, cu potrivire exactă și geometrie separată pentru ambele rânduri. Textul OCR brut nu este modificat.
- Recuperare după erori OCR repetate cu pauză progresivă. Potrivirile incerte nu sunt inventate; motorul OCR de bază rămâne ML Kit.
- Teste unitare pentru reguli, hărți multiple, controlul ritmului, continuitatea paginii și confirmarea temporală. Calitatea imaginii, latența și consumul trebuie măsurate pe telefon fizic; nu se promite o acuratețe absolută.

# BiblioTopicSearch 1.1.2 — MultiMap OCR (August 11 baseline)

This branch starts from the original 2026-08-11 archive at commit
`e139cc98818f8f8e3716ee202d2d987dc237d89b`. The original ZIP is retained
unchanged. The editable and buildable project is in `project-src/`.

## Changes

- Select any number of thematic map profiles from the camera header or library.
  All profiles are scanned by default, independently of the currently edited
  profile. Disabled nodes stay disabled; custom colours and symbols are preserved.
- One local ML Kit OCR stream, one shared term index and independent bounded
  workers for matching, plan compilation and UI delivery. New frames replace
  pending older frames; generation checks discard results after pause, map change
  or freeze. No network OCR or fabricated text.
- Prefer 1280×960 analysis resolution with device fallback; tap-to-focus and
  pinch-to-zoom on unmarked preview. Romanian diacritics and punctuation variants
  are normalized for matching. Adjacent lines in the same OCR block may match
  a phrase, with separate highlight rectangles.
- Coincident map matches retain profile identity and remain individually
  accessible. Compact map counts and optional labels; remove redundant prose
  from scanner, library, editor and settings.
- The existing preferences, local dictionary, map import/export and manual
  freeze/save behavior are retained. Scan selection uses a new preference key.

## Build and tests

```sh
gradle --no-daemon -p project-src :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds the debug APK from extracted, modified sources, not the
historical ZIP. It uploads the APK and SHA-256 file only after unit tests and build
succeed. Package ID is unchanged; a debug APK can update an installed app only
if its signing certificate is compatible. Export maps before changing APKs;
do not uninstall the old application merely to try this build.

Unit tests verify profile identity, matching rules, Romanian normalization,
large index construction and bounded latest-frame scheduling. Real-device
camera performance, OCR accuracy and stability still require physical testing:
try two maps containing the same term, then 10+ maps; pause, freeze, resume and
change map selections while scanning; test small print, dim light and page
changes. Check that no obsolete overlays reappear and that both map matches
remain accessible. Compare frame latency and recognition accuracy on the same
phone and printed pages against the original version.

## Original README

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
