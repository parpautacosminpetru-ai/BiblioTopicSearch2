# Lupa Semantică 2.2

Lupa Semantică este o aplicație Android offline pentru scanarea textului tipărit cu camera și detectarea **ideilor exprimate explicit**, nu doar a cuvintelor identice.

## Principiul semantic

Motorul 2.2 este extractiv și explicit-only:

- nu generează explicații;
- nu produce interpretări literare sau psihologice;
- nu etichetează o concluzie care nu este susținută de textul OCR;
- fiecare rezultat păstrează dovada exactă START → END din pagină;
- etichetele de compresie sunt fragmente copiate din dovadă, nu texte inventate.

Fluxul este:

`OCR → unități de text → embeddings locale → similaritate semantică → nucleu → extindere START/END → topic/nod → categorie → etichete extractive → highlight`

## Căutare semantică liberă

Bara `Caută semantic o idee explicită…` acceptă o formulare liberă. De exemplu, căutarea `restricție de deplasare` poate găsi un pasaj precum `Nu avea voie să părăsească orașul`, chiar dacă termenii nu coincid literal.

Dacă bara este goală, aplicația scanează semantic nodurile tematice active din harta utilizatorului.

## Evidențiere OCR live

Camera rulează OCR continuu cu CameraX + ML Kit. Motorul semantic primește numai cadrul OCR cel mai nou, iar rezultatele sunt desenate direct peste coordonatele textului din `PreviewView`.

- highlight-ul urmărește textul în timp real;
- pentru un span semantic START → END sunt evidențiate toate liniile OCR care aparțin dovezii;
- bounding-box-urile individuale sunt netezite între cadre pentru a reduce tremurul;
- o potrivire poate fi păstrată foarte scurt dacă OCR-ul o pierde într-un singur cadru, pentru a evita pâlpâirea;
- textul afișat rămâne verbatim din OCR/sursă; nu se parafrazează și nu se rezumă.

## Notebook local, fără sinteză

Modul `NOTEBOOK` păstrează surse text local pe dispozitiv și permite căutare semantică în toate sursele indexate. Rezultatul este întotdeauna un fragment exact din sursa originală, cu poziția START → END. Nu există răspuns generat, rezumat sau parafrază.

OCR-ul curent poate fi trimis în Notebook ca sursă, iar fișiere text pot fi importate local. Embeddings sunt salvate numai în baza SQLite locală a aplicației.

## Delimitarea ideii complete

Un rezultat nu este doar propoziția cu scorul maxim. Motorul găsește nucleul semantic și extinde spre stânga/dreapta numai cât timp textul rămâne coerent cu aceeași idee. Spanul final este reverificat semantic ca întreg înainte să fie afișat.

Fiecare `MatchHit` semantic conține:

- topic/nod tematic;
- categorie semantică;
- scor de similaritate;
- textul-dovadă complet;
- începutul și sfârșitul dovezii;
- bounding-box-urile OCR ale spanului;
- maximum trei etichete de compresie extractive;
- noduri tematice alternative suficient de apropiate.

## Zoom semantic

Butonul `ZOOM` are trei niveluri:

1. `TOPIC` — bloc compact pentru nodul tematic;
2. `IDEE` — evidențiază întregul span START → END și categoria;
3. `DETALIU` — păstrează spanul și afișează etichetele de compresie extractive.

Atingerea unui rezultat deschide dovada completă, cu START, END, categorie, topic/nod și etichete.

## Categorii semantice

Categoriile integrate descriu numai relații/forme care pot fi ancorate în text, precum definiție, cauză, consecință, condiție, scop, exemplu, comparație, contrast, restricție, obligație, permisiune, interdicție, schimbare și relație.

Categoria este lăsată goală dacă pragul explicit nu este atins.

## Model semantic offline

Build-ul include local `sentence-transformers/distiluse-base-multilingual-cased-v2` (Apache-2.0), în varianta ONNX qint8 pentru ARM64, împreună cu vocabularul și stratul Dense al modelului. Modelul produce embeddings 512-dimensionale pentru căutare semantică multilinguală.

Modelul este descărcat la build dintr-un commit Hugging Face fixat și este apoi inclus în APK. Aplicația nu are nevoie de internet la rulare și nu cere permisiunea `INTERNET`.

## Fallback

Dacă modelul semantic nu poate fi inițializat, aplicația păstrează motorul lexical existent ca fallback, astfel încât OCR-ul și hărțile de termeni să rămână utilizabile.

## Confidențialitate

- OCR local cu ML Kit;
- embeddings locale cu ONNX Runtime;
- Notebook local cu SQLite;
- fără upload de cameră sau text;
- fără permisiune INTERNET;
- nimic nu se salvează automat;
- un cadru se salvează numai după acțiunea explicită a utilizatorului.

## Build

- Java 17
- Android API 36
- Android Gradle Plugin 8.13.2
- CameraX 1.6.1
- ML Kit Text Recognition bundled 16.0.1
- ONNX Runtime Android 1.26.0
- minSdk 24
- versionCode 22
- versionName 2.2.0

Repository-ul păstrează proiectul în `BiblioTopicSearch_FINAL.zip`. Workflow-ul GitHub reconstruiește stratul semantic, integrează Notebook-ul local, injectează modelul pinned în `app/src/main/assets/semantic/`, verifică SHA-256 pentru fișierele binare principale și rulează `:app:assembleDebug`.
