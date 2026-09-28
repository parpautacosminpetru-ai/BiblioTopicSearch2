# Lupa Semantică 2.2 — validare

Build GitHub Actions reușit: run `31888079609`.

## Verificări trecute

- patch semantic explicit-only aplicat;
- Notebook local verbatim aplicat;
- model multilingual ONNX qint8 inclus în APK;
- vocabular inclus;
- proiecția Dense inclusă;
- compilare Android reușită;
- manifestul final nu cere `android.permission.INTERNET`;
- `NotebookActivity` este inclus;
- rezultatele Notebook sunt extrase cu `full.substring(startChar, endChar)` din sursa locală;
- etichetele de compresie sunt acceptate doar dacă apar ca substring exact în dovada OCR;
- fără strat de generare, rezumare sau parafrazare.

APK: `Lupa-Semantica-2.2.apk`

SHA-256:

`7980bd72949466529bacd61636435a60e7ce3b7db5f7f2cd01e462e95549b73f`

Compatibilitate minimă: Android 7.0 / API 24, necesară de ONNX Runtime 1.26.
