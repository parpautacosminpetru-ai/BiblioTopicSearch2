# Sursă originală intactă — contract de implementare (1.11)

## Ce se păstrează

* PDF-ul original este copiat **octet cu octet**, neschimbat, în fișierele private ale aplicației. Se reține SHA-256. Reimportarea altui PDF peste aceeași identitate de sursă este refuzată.
* Pentru scanarea fizică, imaginea originală este JPEG-ul produs de CameraX cu metadatele EXIF originale; fișierul nu este rotit, decupat, rescris ori suprascris. Decodarea/rotația în memorie sunt exclusiv pentru afișare și OCR.
* Fiecare pagină citabilă indică exact o filă PDF sau un JPEG original. Dacă pagina are deja alt original, suprascrierea este refuzată.
* OCR-ul stă în tabel separat: textul recunoscut + dreptunghiurile normalizate pentru fiecare linie. Originalul este independent și poate fi verificat SHA-256.
* Hărțile și indicii sunt straturi derivate; algoritmii nu schimbă și nu reconstruiesc aspectul sursei.
* Evidențierea pasajelor este desenată temporar la afișare peste facsimil, fără a fi salvată în PDF/JPEG.

## Flux PDF

1. Creează/alege o sursă cu titlu.
2. Alege fișierul PDF și intervalul de pagini.
3. Importul arhivează fișierul ORIGINAL înainte de OCR; pentru fiecare pagină importată se salvează legătura la fila PDF, liniile OCR și cartografierea.
4. În **SURSA ORIGINALĂ**, accesează pagina ca facsimil, navighează între pagini sau caută cuvinte. Rezultatele sunt potriviri LEXICALE în OCR, nu interpretări.
5. Compară textul OCR cu pagina fizică/digitală și marchează manual **AM VERIFICAT PAGINA • ÎNCHEIE**. Importul PDF nu mai pretinde automat că un OCR nevid dovedește exhaustivitatea paginii.

## Flux scanare

1. Selectează **SCANARE ORIGINALĂ** și introdu numărul paginii.
2. **FOTOGRAFIAZĂ** creează un JPEG original, apoi OCR și cartografiere dintr-o copie temporară, orientată pentru lectură.
3. În **SURSA ORIGINALĂ**, vezi originalul în aceeași așezare vizuală și verifică OCR-ul. Încheierea paginii este manuală.
4. Scanările noi ale unei pagini deja arhivate sunt refuzate, pentru a nu înlocui în mod ascuns dovezile vechi. Pentru alt exemplar sau ediție, creează altă sursă.

## Limite actuale

* OCR-ul ML Kit nu garantează recunoașterea fiecărui cuvânt. **Absența unei potriviri OCR nu înseamnă absență în sursa originală.**
* Potrivirile în bara de căutare sunt lexicale în aceeași linie OCR. Nu sunt încă un motor universal de răspunsuri explicite la întrebări formulate liber, nici o certificare a exhaustivității semantic-istorice.
* Pe Android nu există garantarea absenței tuturor bugurilor. Urmează testarea instrumentală a camerei și PDF-urilor pe dispozitive reale, inclusiv imagini rotite, documente mari, scanări greșite și reluarea după întrerupere.
* Protecția fișierelor private nu este un backup extern. Nu dezinstala aplicația fără exportarea unei copii de siguranță a datelor.
