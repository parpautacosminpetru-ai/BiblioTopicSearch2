# Detector live pentru indici: modul verificabil Beta5

Acesta este un **prim increment** peste sursele **2.3.0-beta5-complet**.
Nu înlocuiește CAPTURĂ EXACTĂ și nici nu generează interpretări istorice.

## Ce funcționează în cod

- Pipeline cu maximum **un singur rezultat OCR în procesare** și o pauză minimă
  între rezultate acceptate (260 ms): cadrele suplimentare sunt eliminate, nu
  acumulate. Camera și motorul de căutare au executori separați.
- Planul indexurilor se compilează în fundal când temele sunt schimbate, nu la
  fiecare cadru (ecranul principal); în LUPĂ compilarea exista deja în fundal.
- Motorul multipattern existent emite **toate ocurențele** în blocurile OCR;
  duplicatele aceleiași forme sub același nod canonic nu se recompilă.
- Identitățile distincte, chiar dacă au aceeași formă, nu se confundă:
  suprapunerile de interval sunt raportate separat de coocurențe.
- Butonul **INDICI** deschide separat O C U R E N Ț E, C O O C U R E N Ț E,
  S U P R A P U N E R I și candidați cronologici, fără etichete simultane care
  să acopere pagina.
- Citatele și intervalele sunt copiate **literal din rezultatul OCR**; se afișează
  conectori lexicali când există în segmentul dintre doi indici, dar aceștia
  **nu dovedesc de unii singuri** o relație sintactică sau istorică.
- Formele numerice posibile pentru ani și cele pentru secole apar ca
  **candidați nevalidați**, nu se transformă automat în fapte canonice.

## Delimitări metodologice

- OCR LIVE este provizoriu. Un citat academic trebuie verificat prin fotografia
  păstrată de SCAN ORIGINAL / CAPTURĂ EXACTĂ.
- O coocurență în aceeași propoziție **nu este** o relație istorică sau
  sintactică demonstrată. Nici o subordonare gramaticală ambigua nu se inventează.
- Algoritmul delimitării propozițiilor este euristic, cu precauții pentru câteva
  abrevieri; nu este un parser sintactic general al limbii române.
- Inspectorul live limitează listele vizibile la 100 intrări și calculează cel
  mult 400 perechi per cadru; dacă limita este atinsă, o marchează. Aceasta nu
  reprezintă un verdict de exhaustivitate al unei pagini.
- Candidații cronologici încă **nu sunt salvați într-un inbox persistent**.
  Importurile de tezaure canonice externe (RAMEAU, GND, Wikidata etc.), validarea
  manuală a candidaților și exporturile de indici sunt **etape viitoare**.
- Încă nu există test de performanță pe un dispozitiv fizic cu zeci de mii de
  intrări și nici confirmare că APK-ul nou poate înlocui instalarea Beta5
  existentă fără pierderea datelor. Nu dezinstala aplicația înainte de backup.

## Testare

CI rulează testele JUnit și construiește APK-ul debug din project-src.
Testele adăugate acoperă:
- numărarea reală a repetițiilor, fără recompilarea duplicatelor;
- distincția între aceeași suprafață OCR pe doi indici și coocurențe;
- limite de propoziție, citat literal și segment exact dintre indici;
- clasificarea cronologică drept candidat, nu fapt validat;
- pipeline fără coadă, deblocare după excepție, închidere.

Pentru recepție sunt necesare teste de 5–10 minute de OCR continuu pe telefon,
cu mai multe hărți, pagini dense și alternarea rapidă a modurilor.
