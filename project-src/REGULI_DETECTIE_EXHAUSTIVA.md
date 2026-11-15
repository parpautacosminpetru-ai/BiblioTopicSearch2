# Reguli explicite și acoperire tematică — BiblioTopicSearch 2.3

Bază: cele nouă tematici transmise de utilizator (PDF/DOCX), organizate în **7 discipline / 144 subiecte**. Cele trei programe de **Europa medievală** (2020, seminar și partea a II-a 2026) au fost comasate conceptual. Fiecare subiect are **cel puțin 10 expresii**.

**Inventar la data compilării:** 2.223 asocieri expresie–subiect și 2.144 expresii lexicale distincte. Unele expresii se regăsesc corect în mai multe subiecte (de exemplu numele unui domnitor); se păstrează asocierile, dar aceeași regiune OCR este afișată o singură dată.

## Surse, extinderi și limitări

Subiectele sunt păstrate după ordinea și temele documentelor universitare. Sintagmele de căutare sunt **selectate și extinse editorial**, nu copiate integral din bibliografiile de la finalul PDF-urilor. Variantele suplimentare (nume istorice, traduceri și titulaturi, contexte, denumiri alternative) sunt propuneri de căutare, nu afirmații că autorii programelor au cerut explicit fiecare variantă. Bibliografiile, când apar în vocabular, sunt marcate `-` (doar evidențiere).

„Exhaustiv” înseamnă mai multe căi de recunoaștere **lexicală** pentru fiecare subiect, nu înțelegere semantică a fiecărui paragraf. Documentele-programă nu enumeră toate sinonimele din literatura istorică; nu se poate garanta un grad de recall de 100%. Nume istorice în alte limbi sau transcrieri, OCR degradat, sintagme fragmentate între blocuri și absența cuvintelor-cheie pot produce omisiuni.

## Reguli deterministe exacte

1. OCR camera: un cadru; potrivire de cuvinte întregi și grupuri contigue de cuvinte din același bloc de text ML Kit, inclusiv când grupul traversează **cel mult un salt de rând**. Toleranța la diacritice este activă implicit. Cratima/apostroful sunt delimitatori de cuvinte.
2. `!termen` — evidențiere și alertă pentru potrivire integrală, normalizată. `!1453` nu poate vibra: un an numeric izolat este probă prea slabă.
3. `?termen` sau `~termen` — indiciu contextual; singur e doar evidențiat. Două indicii **distincte din același SUBIECT** permit vibrație în modul ECHILIBRAT, trei în STRICT. O potrivire repetată pentru același termen nu ține loc de alt indiciu. Datele pur numerice nu sunt indicii de vibrație.
4. `-termen` — numai evidențiere; niciodată alertă; recomandat pentru bibliografii și nume foarte generale.
5. Termen personal fără semn: în EXPLORARE alertă pentru termen de cel puțin 5 caractere; în ECHILIBRAT pentru expresie de cel puțin 2 cuvinte; în STRICT pentru minimum 3 cuvinte.
6. Alerta vibrației necesită potrivire **exactă a expresiei după normalizare**, indiferent de opțiunile vizuale de căutare mai largă sau de familia flexionară. Prin urmare o potrivire vizuală de inflexiune poate exista fără vibrație.
7. Maximum o vibrație pentru o evaluare OCR, interval global de cel puțin **950 ms**; un semnal se rearmează după **2 cadre OCR fără acesta** și **minimum 2.200 ms** de la propria alertă.
8. Aceeași apariție fizică este **afișată o dată**, iar lista de subiecte care o revendică este păstrată în detaliile atingerii. Există o singură activare haptică, nu câte una pentru fiecare tematică.
9. Subiectele inactive și tematicile debifate nu participă la notificare. Pentru reproducere clară, recomandare: mod OCR **EXACT**, primele N caractere **0**, „Ignoră diacriticele” **activ**.

### Exemple
- `!Căderea Constantinopolului` într-un text OCR declanșează alertă directă, dacă semnalul este armat.
- `?1453` singur poate fi încadrat, nu declanșează alertă.
- `?armata bizantină` și `?flota bizantină` în **același subiect** formează un context suficient în ECHILIBRAT. Un indiciu din „Arta bizantină” nu este combinat arbitrar cu unul din „Armata bizantină”.
- `-Georges Ostrogorsky` doar evidențiază referința bibliografică.
- Numele `Ștefan cel Mare` poate apărea în subiecte despre diplomație, politică militară și ctitorii; se afișează o regiune OCR și sunt păstrate asocierile relevante.

## Verificare

`sql-tests/test_theme_assets.py` validează cele 7 fișiere, 144 de subiecte, minimum 10 ancore/su­biect, lipsa duplicărilor în interiorul fiecărui subiect, păstrarea a 38 de subiecte de examen și bibliografiile fără vibrație. `DeterministicAlertGateTest` verifică separarea contextuală pe subiect, anii fără alertă, rearmarea și refuzul dublelor semnale. Testul de instalare Android verifică la runtime că profilurile sunt disponibile în biblioteca locală.

**Verificarea CI nu înlocuiește testarea pe telefon**, cu pagini tipărite în condiții reale.
