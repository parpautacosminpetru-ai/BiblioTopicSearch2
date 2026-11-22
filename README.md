# BiblioTopicSearch2 PRO — OCR euristic cu lentile tematice

Versiune **1.2.0 de test**, bazată pe aplicația Android offline din august 2026.

## Ce este nou
- Interfață mai lizibilă: cameră centrală, stare OCR, lentile mari, controale separate de text.
- **Filtru de hărți:** alegi ce teme sunt analizate, fără să ștergi hărțile sau termenii.
- **Index global:** un concept repetat în mai multe hărți este căutat o singură dată; rezultatul este asociat tuturor nodurilor relevante.
- **REZUMAT** fără highlight, **INTERSECȚII** cu regulă de linie sau bloc OCR, plus o lentilă pentru fiecare temă.
- **ÎNGHEAȚĂ** recunoaște din nou, o singură dată, exact imaginea înghețată; comutarea lentilelor nu rescanează cadrul.
- **FIȘE**: după ce verifici contextul, poți salva local sursa, pagina, termenul, temele, fragmentul OCR corectat și observația personală; export CSV manual.
- Fără permisiune Internet, fără trimiterea imaginilor către un model AI. Interpretarea rămâne exclusiv la utilizator.

## Limite
- Coapariția termenilor pe aceeași linie sau în același bloc OCR este **semnal lexical**, nu dovadă de relație sintactică ori cauzală.
- Blocurile OCR nu corespund întotdeauna paragrafelor reale; rezultatele OCR pot conține erori.
- Nu a fost încă testată în mod practic pe un telefon fizic; APK-ul este compilat prin GitHub Actions.
- Semnarea GitHub Actions produce un **APK debug**, nu un release semnat cu o cheie de distribuție.
- Fișele și configurațiile sunt păstrate pe dispozitiv; exportă-le înainte de a dezinstala aplicația.

