# Biblio Paleografie 1.4.2 — verificare Android

- Ecran de pornire independent de cameră, hărți și transliterare. Acces clar la cameră, galerie, modele, text și Biblio.
- OCR-ul camerei așteaptă fluxul STREAMING înainte de analiză. La eșec/timeout afișează o explicație; galeria rămâne accesibilă chiar fără permisiune de cameră. Fixează imaginea este independent de rezultatul OCR (cadru din preview, nu fotografie originală certificată).
- OCR latin pe pagină afișează direct transcrierea editabilă; căutarea tematică rămâne în meniul OCR istoric. Pentru imaginea aleasă din galerie se încearcă păstrarea accesului URI și restaurarea textului la recreare. Cadrele din cameră rămân temporare.
- APK-ul folosește ID nou ro.bibliotopicsearch.paleography.universal pentru a nu suprascrie versiunile deja instalate. Cheia debug este reutilizată prin cache CI, când cache-ul există; nu este o cheie de producție. Un release profesional necesită cheie de semnare stabilă administrată de proprietar. Nu publica chei private și nu dezinstala aplicațiile vechi înainte de backup.
- Pipeline-ul publică artefactul numai după trecerea testelor JVM, verificarea APK-ului și executarea testelor instrumentate pe emulator Android API 35. Testele includ pornire, inițializarea celor 12 modele, OCR nativ pe imagini sintetice latină/chirilică și catalog vizual. Aceste probe nu validează performanța camerei fizice sau precizia pe toate manuscrisele.

# Instalare separată 1.4.1

Această ramură produce un APK cu ID Android `ro.bibliotopicsearch.paleography.v141`. Se poate instala alături de 1.4.0, inclusiv atunci când certificatele debug diferă. Datele aplicației vechi rămân acolo; hărțile și sistemele de reguli se pot muta prin export/import. Modelele OCR standard sunt deja incluse. Nicio dezinstalare necesară.

# Biblio Paleografie 1.4.1 — remediere pornire fără sistem

- OCR și transliterarea sunt independente. Apăsarea scurtă Paleo pornește camera OCR universală; apăsarea lungă deschide atelierul text/reguli. Din atelier, CAMERA · OCR și IMAGINE · SEMNE · OCR sunt disponibile fără sistem de transliterare.
- Original — fără substituții este modul implicit. Verifică textul păstrează integral caracterele și raportează lipsa regulilor; nu pretinde transliterare. Suprapunerea latină cu reguli rămâne opțională.
- Camera poate porni direct cu ML Kit Latin inclus. Alege scrierea selectează modelele Tesseract. Importă model OCR funcționează chiar din cameră; nu necesită o fotografie prealabilă. Pachetul CI include modele oficiale tessdata_fast 4.1.0 pentru română, engleză, rusă, greacă modernă și veche, arabă, ebraică, hindi, chineză simplificată/tradițională, japoneză, coreeană. Acestea sunt extrase local numai la utilizare; modelele proprii rămân separate. Nu se descarcă nimic pe telefon.
- Modelele incluse sunt din https://github.com/tesseract-ocr/tessdata_fast/releases/tag/4.1.0 (licență inclusă în APK). Nu garantează citirea manuscriselor sau a tuturor scrierilor istorice. Catalogul de semne și importul de modele proprii rămân disponibile.
- Test JVM pentru modul original; test Android compilat pentru butoanele de pornire și text fără sistem. Testarea pe telefon rămâne necesară. ID Android neschimbat; instalarea peste APK-ul anterior necesită certificat de semnare compatibil. Nu dezinstala aplicația veche fără backup.
- Versiunea 1.4.1 este un build debug de testare, nu o certificare a preciziei paleografice.

# Biblio Paleografie 1.4.0 — sisteme de scriere extensibile

- Modele Tesseract importabile pentru scrieri alfabetice, silabice, logografice și mixte; se pot combina maximum trei modele locale. Moduri explicite de așezare: pagină, text vertical, bloc, rând, cuvânt, semn, text dispersat. Pentru orice scriere fără model disponibil, este necesar un model antrenat compatibil sau catalogul vizual.
- În meniul camerei: **OCR universal pe cameră**. Selectează modele deja importate, apoi Pornește. Un singur cadru redus este analizat la un moment dat; următorul este programat după rezultat. Textul aparține ultimului cadru procesat, nu imaginii în mișcare. Verifică cadrul exact sau trimite textul candidat în transliterare. Motorul este refolosit pe worker, fără rețea. Overlay-ul geometric latin original rămâne separat.
- **Pagină / OCR istoric → Învață semnul selectat**: încadrează un singur semn, cu margine, prin glisare în modul Selectează zonă / semn, și introdu un nume de sistem și un caracter Unicode sau un identificator propriu (de exemplu [S001]). Adaugă mai multe variante. **Identifică semnul selectat** compară siluete normalizate cu exemplele din același catalog, afișează candidați și cere confirmarea umană înainte de adăugarea în text. Distanța este euristică, nu probabilitate. Nicio descifrare automată a unei scrieri necunoscute, nicio segmentare automată universală. Gestiunea catalogului permite eliminarea exemplelor greșite per etichetă. Maximum 2048 exemple locale; catalogul nu are încă export/import.
- Modelele de referință: https://github.com/tesseract-ocr/tessdata_fast/tree/main/script și https://github.com/tesseract-ocr/tessdata_best/tree/main/script. Nu sunt preinstalate. Un model de scriere modernă nu garantează recunoașterea manuscriselor, a formelor istorice sau a simbolurilor necodificate.
- Testele noi verifică selectarea modelelor, similitudinea la deplasare și inversarea contrastului, respingerea imaginilor goale și ambiguitatea. Testarea pe Android și evaluarea pe surse autentice rămân obligatorii. Versiunea este un candidat de testare, nu produs certificat.

# Biblio Paleografie 1.3.0 — candidat de verificare

- APK separat (`ro.bibliotopicsearch.paleography`): se instalează alături de Biblio 1.11.1 și de Paleografie 1.2.1. Datele și sistemele vechi nu sunt migrate automat; exportă/importă hărțile și sistemele. Sunetele atașate se transferă separat. Backup Android automat dezactivat în această variantă.
- În cameră, deschide **Pagină / OCR aprofundat**, alege fotografia sau cadrul, ajustează colțurile și contrastul. **OCR istoric** acceptă modele Tesseract `.traineddata` locale, de exemplu `Cyrillic.traineddata`, `rus.traineddata`, `ell.traineddata` sau un model paleografic propriu, dacă este compatibil. Modelele nu sunt incluse și nu sunt descărcate de aplicație. Folosește fișiere din surse de încredere: https://github.com/tesseract-ocr/tessdata_best/tree/main/script sau https://github.com/tesseract-ocr/tessdata_fast.
- OCR-ul Tesseract este de pagină, nu live; textul candidat se editează lângă imagine. Se poate alterna originalul cu imaginea corectată; transcrierea manuală nu necesită model. Transliterarea folosește numai regulile proprii. Nu se pretinde recunoaștere universală a manuscriselor, nici localizare vizuală Tesseract certificată.
- Textul de lucru se păstrează la recrearea ecranului; exportul folosește un fișier temporar pentru a supraviețui pickerului Android, nu stochează automat o arhivă permanentă. Textul foarte lung se exportă în loc de copiere în clipboard.
- Sunetul este o opțiune explicită (oprit implicit); erorile de redare și timpii de expirare sunt semnalate.
- CI rulează testele unitare, compilează APK-ul și testele Android, verifică identificatorul și absența permisiunilor de internet/rețea/microfon. Testele native pe imagini reale, modelul importat și TTS necesită dispozitiv fizic. APK debug, nu pachet de producție certificat.
- Motor OCR suplimentar: Tesseract4Android 4.9.0 (https://github.com/adaptech-cz/Tesseract4Android), dependență de compilare JitPack; fără serviciu OCR extern la execuție.

# 1.2.1 — Paleografie cu sunet sincronizat

- Mod Paleo pe cameră, cadru înghețat și pagină corectată: pronunțiile explicite sunt redate secvențial în ordinea regulilor literale. Echivalentul vizual al caracterului/grupului rostit este evidențiat în rândul suprapus. Regula cea mai lungă rămâne prioritară; caracterele fără sunet definit nu sunt pronunțate prin presupunere.
- Formatul existent cu 2 coloane rămâne valid. Sunt permise coloanele opționale sound și language (BCP-47); exemplu CSV: Ж,Zh,je,ro-RO. În lipsa limbii se solicită o voce locală ro-RO. În JSON, sound și language sunt câmpuri opționale. Fișier demonstrativ: exemple/sistem-paleografie-sunet.csv. Valorile demonstrative nu reprezintă afirmații despre pronunția istorică.
- Sisteme / Sunete înregistrate: asociază un fișier audio la sursa exactă a unei reguli. O înregistrare validă are prioritate față de sinteza vocală. Maximum 10 s și 2 MiB per fișier; stocare locală cu limită globală de 64 MiB. Exportul tabelului TSV conține regulile scrise, nu fișierele audio atașate; păstrează separat înregistrările originale.
- Redare cu voce locală Android TTS numai dacă există o voce instalată pentru limba solicitată; dacă nu, regula rămâne vizuală și aplicația semnalează indisponibilitatea. TTS citește textul sound și nu garantează reproducerea unui fonem istoric sau IPA. Pentru sunet exact, atașează o înregistrare proprie.
- Un singur sunet activ, fără acumularea unei cozi de cadre; se oprește la dispariția rândului, expirare, schimbarea modului sau părăsirea ecranului. Progresul rândului se păstrează prin întreruperi scurte. Meniul Sunet Paleo permite oprirea și pornirea; comanda Original dezactivează suprapunerea sonoră și vizuală pentru cadrul înghețat. Exportul PNG păstrează textul transliterat, nu sunetul sau evidențierea temporară.
- Teste unitare pentru regulile audio, offseturi și progres, plus teste existente pentru geometrie și OCR. Redarea TTS/audio și alinierea în timp real necesită testare pe telefon; modelul OCR Latin nu este un recognizer universal al manuscriselor.

# 1.2.0 — Paleografie direct pe pagină / cameră

- Butonul Paleo activează/dezactivează transliterarea suprapusă direct peste textul camerei. Apăsarea lungă alege sistemul, iar Pe cameră din administratorul sistemelor activează modul. Detectarea tematică rămâne un mod separat: hărțile, culorile și setările nu sunt alterate.
- Un singur flux OCR; sistemul importat este compilat pe worker, nu la fiecare cadru. Pentru fiecare rând recunoscut se execută exact motorul de transliterare, inclusiv grupuri, semne, ligaturi și caractere neacoperite (păstrate). Rândurile neschimbate nu sunt acoperite.
- Suprapunerea este limitată la patrulaterul OCR real al rândului. Textul se ajustează în interiorul său. Rândurile fără coordonate valide, cu scoruri OCR pozitive prea mici, ori cu rezultate excesiv de mari rămân în original. Fiecare rând necesită confirmarea între cadre; invalidarea la mișcare, zoom sau expirare este păstrată. Rândurile separate nu sunt unite artificial; regulile care traversează rânduri se folosesc în modul text integral.
- În Cadru, când Paleo este activ, imaginea exactă capturată este analizată separat în două variante de contrast; suprapunerea concordantă este desenată pe copia capturii. Original/Paleo comută între imaginea brută și cea transliterată, iar Salvare exportă varianta afișată. Nu se reutilizează coordonatele live la înghețare.
- În Pagină / OCR aprofundat, Paleografie comută între transliterarea concordantă pe fotografia rectificată și detecția tematică; apăsarea lungă deschide textul OCR pentru editare/export.
- Transliterarea este strict literală. ML Kit Latin rămâne recognizerul; regulile Unicode importate nu fac automat un alfabet sau manuscris necunoscut recognoscibil. Afișarea unor caractere exotice depinde și de fonturile dispozitivului. Testele de geometrie și motor sunt unitare; testele pe OCR imagine se compilează, dar necesită Android pentru execuție.

# 1.1.9 — Paleografie / transliterare literală

- Meniu Paleografie: importă, creează, editează, selectează și exportă sisteme proprii. Până la 20.000 de reguli per sistem; UTF-8 TSV, CSV sau JSON. Fișierul demonstrativ este exemple/sistem-paleografie.tsv; nu este încărcat automat.
- Reguli exacte, sensibile la majuscule și la reprezentarea Unicode. Caractere, grupuri, ligaturi, semne, cifre și spații pot fi surse sau destinații; se aplică cea mai lungă regulă de la poziția curentă. Ieșirea nu este căutată din nou. Caracterele fără regulă sunt păstrate și listate; o destinație goală este o eliminare explicită.
- Text introdus manual, import TXT UTF-8, ultimul OCR din cameră, textul OCR al unui marcaj, text de probă sau textul OCR al paginii analizate. Originalul și transliterarea sunt separate. Rezultatul complet se poate copia sau exporta, chiar dacă previzualizarea este limitată.
- Sistemele sunt salvate local; textul de lucru nu este salvat automat. Operațiunile de import și transliterare se execută pe worker. Motorul nu folosește traducere semantică, dicționare, AI generativ sau rețea și nu modifică regulile hărților.
- TSV: header opțional source<TAB>target, două coloane; escapări pentru tab/newline/backslash/Unicode. CSV: source,target (sau antet sursa;destinatie pentru separator ;), ghilimele CSV standard. JSON: {"name":"Sistem","rules":[{"source":"Ж","target":"Zh"}]}. Intrările contradictorii sunt respinse integral.
- Modelul OCR ML Kit Latin rămâne neschimbat. Se poate translitera orice text Unicode valid furnizat de utilizator, dar alfabetul necunoscut motorului OCR nu devine automat recognoscibil din imagini. Nu se modifică interfața globală sau textul OCR brut. Teste unitare și test JSON pe Android; testele Android necesită dispozitiv.

# 1.1.8 — Punctuație și simboluri

- Adaugă în nod cuvinte, expresii sau semne: puncte, virgule, ghilimele, paranteze, liniuțe, §, #, |, +, combinații etc. Dialogul Adaugă termen oferă și o selecție multiplă de semne.
- Un index separat caută semnele exact în textul OCR, inclusiv în date numerice și cuvinte cu cratimă. Nu alterează delimitarea cuvintelor. „?!” diferă de „? !”, „…” de „...”, iar „-” de „–” și „—”. O căutare explicită pentru punct poate găsi și punctele dintr-o secvență de puncte.
- Marcajele semnelor folosesc numai dreptunghiuri de simbol OCR aliniate la text sau elemente OCR care sunt exact semnul căutat. Dacă lipsesc coordonatele, nu se colorează cuvântul învecinat. În Testează harta / Imagine, semnele citite fără localizare sigură sunt prezentate separat.
- Semnele live necesită trei citiri consecutive și respectă filtrul de încredere. Contururile sunt dimensionate pentru semne mici. Hărțile își păstrează culorile.
- În editorul text avansat, # la început rămâne titlu, | separator. Pentru caractere literale se folosesc \\# (scris cu un singur backslash), \\| și \\\\ pentru backslash; dialogul de adăugare face această escapare automat.
- Teste unitare pentru semne individuale, combinații, date, cuvinte compuse, două hărți și sintaxa de salvare. Testul Android folosește o pagină sintetică cu punctuație. Acuratețea ML Kit pentru semne mici și tipărituri vechi rămâne de măsurat pe telefon.

# 1.1.7 — Pagină / OCR aprofundat

- Meniul camerei oferă o analiză separată a unei capturi brute sau a unei fotografii importate. În Testează harta, Pagină limitează analiza la profilul curent.
- Patru colțuri reglabile pentru redresare de perspectivă, estimarea înclinării mici, corecție parabolică manuală a curburii verticale și normalizare locală a contrastului pentru hârtie îngălbenită/umbre. Fotografia originală rămâne nemodificată.
- Două citiri ale aceleiași imagini redresate (înainte și după normalizarea contrastului). Se afișează implicit doar potrivirile concordante ca text literal, identitate hartă/nod și geometrie; candidații rămași pot fi examinați explicit. Citirile nu sunt o garanție a corectitudinii OCR.
- Marcajele se desenează pe imaginea efectiv analizată; nicio coordonată a imaginii redresate nu se suprapune pe o previzualizare live ulterioară. Culorile individuale ale hărților sunt păstrate.
- În live, verificarea geometriei expresiilor folosește direcția rândului OCR când este disponibilă. Modul de corectare a imaginii este pentru pagini statice, nu redresare 3D automată în timp real.
- Teste unitare pentru perspectivă, curbură, contrast, înclinare, concordanță și coloane; test Android pe pagină tipografică sintetică îngălbenită și înclinată. Testele Android se compilează în CI, dar trebuie rulate pe Android. Modelul ML Kit rămâne Latin; tipăriturile istorice reale și alfabetul chirilic necesită evaluare/model adecvat. Corecția manuală a curburii este aproximativă, nu reconstrucție completă a suprafeței cărții.

# 1.1.6 — Verificare OCR în cadre consecutive

- Un termen literal trebuie să reapară în aceeași poziție și în aceeași hartă în minimum două citiri OCR; trei pentru încredere intermediară/necunoscută sau pentru scene cu continuitate necunoscută. Scorurile pozitive sub 0,72 sunt respinse. Scorurile sunt semnale euristice ML Kit, nu probabilități calibrate.
- Verificarea independentă a textului brut și a apartenenței la nod/hartă are loc după căutarea în index. Marcajele folosesc numai geometria din cadrul curent; dispar imediat dacă textul nu mai este confirmat.
- Un eșantion mic al luminanței urmărește schimbarea fizică a paginii și invalidează cadrele vechi. Nu modifică imaginea și nu rulează un al doilea OCR.
- Editorul are „Testează harta”: text introdus manual sau imagine locală, pentru izolarea erorilor lexicale de erorile OCR. Testul pe o imagine afișează candidați, nu confirmări live. Harta testată rămâne independentă de selecția live.
- Teste unitare de regresie și test Android end-to-end pe o pagină tipografică sintetică. Compilarea testului Android nu echivalează cu rularea pe dispozitiv.
- Nu include rectificare geometrică a curburii paginii sau un model OCR separat pentru tipărituri istorice; acestea necesită evaluare cu imagini reprezentative și transformare corectă a coordonatelor.

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

### APK separat

În GitHub Actions, alege artefactul `Biblio-Paleografie-1.4.1-Separat`. Verifică suma SHA-256 inclusă. Instalează separat; nu dezinstala versiunile care conțin date de lucru.
