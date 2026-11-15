# BiblioTopicSearch 2 — ghid pentru detector

## Cum folosești versiunea 2
1. Deschide aplicația: PORNEȘTE DETECTORUL / ALEGE TEMATICILE sunt acțiunile principale.
2. În biblioteca de teme, bifează una sau mai multe hărți simultan. Butonul EDITEAZĂ deschide numai editorul hărții alese; nu oprește celelalte hărți.
3. Revino la detector și răsfoiește continuu cartea cu camera. Toate potrivirile OCR sunt evidențiate. Vibrația apare doar pentru semnale calificate.
4. Alege modul de alertare în REGLAJE. CERCETARE AVANSATĂ păstrează vechile funcții de arhivare, PDF, surse și atelier.

## Filtrul determinist (fără AI)
- Potrivire nouă implicită: EXACT (ignorarea diacriticelor e opțională). Modul de potrivire vizuală se poate schimba independent, dar vibrația necesită potrivire lexicală exactă după normalizare.
- EXPLORARE: un termen de minimum cinci caractere poate semnala singur.
- ECHILIBRAT (implicit): sintagmă cu două sau mai multe cuvinte, ori doi termeni diferiți din aceeași hartă în același cadru.
- STRICT: sintagmă de minimum trei cuvinte, ori trei indicii distincte din aceeași hartă în același cadru.
- Pentru co-prezență, cuvintele izolate trebuie să aibă cel puțin șase caractere. Termenii din hărți diferite nu se combină artificial.
- Prefix manual !term: alertă imediată chiar pentru un singur cuvânt.
- Prefix manual ?term sau ~term: numai indiciu contextual, fără alertă singură.
- Prefix manual -term: numai evidențiere, fără alertă.
- De exemplu, în harta „Cuza”, !Cuza alertează singur; ?Cuza și „reforma agrară” ajută la selecția contextuală.
- Detectorul păstrează un singur impuls pentru toate semnalele dintr-un cadru; interval global minim 950 ms. Același semnal trebuie să lipsească din două cadre consecutive și să treacă minimum 2200 ms înainte de rearmare. Starea nu depinde de scor AI, ordine de parcurgere sau coordonate instabile ale camerei.

## Ce NU face
Nu decide dacă un pasaj este important istoric; nu deduce sinonime automat; nu garantează că toate paginile au fost observate la răsfoire rapidă. Pot apărea erori OCR și absențe de detecție. Pentru reproducibilitate științifică păstrează referința completă a sursei și verifică pagina fizică.

## Verificare
- Teste unitare: DeterministicAlertGateTest pentru filtrul pe tematici, marcaje, rearmare, potrivire exactă și cadre repetate.
- Compilarea și testarea Android rulează prin GitHub Actions la pull request.
- Artefactul CI este un APK de tip DEBUG pentru testare, nu un release semnat pentru magazin. Nu presupune că un APK vechi poate fi suprascris dacă a fost semnat cu alt certificat.

## Teme universitare 2026 — încorporate automat și fără dubluri

Nouă documente furnizate (două DOCX și șapte PDF) sunt transformate în
**șapte** hărți tematice importate automat la prima pornire.

1. Studiul izvoarelor / Științe speciale, 2026
2. Europa modernă, 2026
3. Istoria Bizanțului, 2026
4. Istoria modernă a românilor, 2026–2027
5. Europa medievală **UNIFICATĂ** din programele 2020, „Seminar Tematică” și partea a II-a 2026
6. Introducere în istoriografie, 2026
7. Stat, societate și mentalități în spațiul românesc, toate cele 38 de subiecte

Fiecare profil de tematică are termeni normalizați fără apariții duplicate
la nivelul acelui profil. În detectorul OCR, aceeași potrivire la aceleași
coordonate apare într-o singură evidențiere, chiar dacă o sintagmă aparține
mai multor discipline. Asocierea cu toate disciplinele este păstrată în
detaliile atingerii acelei evidențieri și în filtrul de alertare.

Toate cele șapte profiluri sunt bifate la prima pornire. Aplicația nu
suprascrie hărțile create sau modificate de utilizator și nu creează din
nou hărțile deja instalate. Datele personale/academice rămân offline.

Testul `project-src/sql-tests/test_theme_assets.py` verifică numărul hărților,
unicitatea internă a termenilor și păstrarea celor 38 de subiecte.

## Consolidarea secțiunilor medievale (beta 3)
După eliminarea termenilor repetați, temele suprapuse între programele medievale din 2020 și 2026 au fost comasate și conceptual: 42 de secțiuni inițiale -> 24 de subiecte canonice, fără pierderea celor 271 de expresii unice. Întregul catalog are 144 de secțiuni și 1.027 expresii distincte.
