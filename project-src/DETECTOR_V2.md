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
