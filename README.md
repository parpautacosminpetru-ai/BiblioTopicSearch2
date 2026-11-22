# BiblioTopicSearch2 — Lentile multitematice

Versiune experimentală pe baza snapshotului din 10 august 2026 (`89342b0b79df`).

Arhiva de la rădăcină (`BiblioTopicSearch_FINAL.zip`) conține codul Android actualizat. GitHub Actions o dezarhivează și construiește APK-ul.


## Lentile multitematice — versiunea de lucru (noiembrie 2026)

- Aplicația citește textul **o singură dată** cu OCR, apoi aplică planurile compilate pentru **toate hărțile din bibliotecă**. Căutarea funcționează offline.
- Bara orizontală de lentile conține **REZUMAT** (număr de potriviri, fără culori suprapuse), **INTERSECȚII** (coapariții de termeni diferiți în aceeași linie / același bloc OCR) și câte o lentilă pentru fiecare hartă. O singură lentilă colorează textul.
- Dacă un termen aparține mai multor hărți, este detectat în fiecare, dar numărul de **ocurențe fizice** îl numără o singură dată. Atingerea termenului explică temele și nodurile asociate.
- Butonul **CADRU** păstrează temporar imaginea **separată** de rezultatele OCR; poți comuta lentilele pe pagina înghețată. **SALVEAZĂ** produce la cerere PNG-ul cu evidențierile lentilei active.
- Semnalizarea unei intersecții este exclusiv **lexicală / geometrică**, nu afirmă relații sintactice, cauzale sau istorice. Un „bloc OCR” poate să nu corespundă cu un paragraf. Verifică întotdeauna contextul manual.
- Versiunea nu persistă automat imaginile sau transcrierea OCR și nu atribuie automat pasajele unei teme ca dovezi validate.


## Reguli de verificare

1. Căutarea se face pe o singură recunoaștere OCR, dar în toate hărțile disponibile.
2. Numerele din bare sunt potriviri, nu evaluări istorice.
3. **REZUMAT** nu colorează cuvintele. Lentilele tematice arată doar propriile rezultate.
4. **INTERSECȚII** necesită minimum doi termeni diferiți, în bloc sau pe aceeași linie OCR, plus apartenențe la cel puțin două teme/noduri.
5. Cadrele înghețate se pot comuta între lentile; salvarea rămâne exclusiv manuală.

Codul-sursă modificat este expus în `source-review/` pentru revizuire, pe lângă arhiva completă.
