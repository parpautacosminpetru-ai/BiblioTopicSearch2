# Atelierul istoricului — afirmații, dovezi și critică a sursei

Versiunea 1.8.0 adaugă un strat metodologic explicit peste cartografierea exhaustivă.

## Principiu

> Aplicația nu formulează adevărul istoric. Istoricul formulează afirmația; aplicația păstrează dovezile, contradicțiile, contextul și proveniența.

Cartografierea rămâne stratul descriptiv al corpusului. **Atelierul Istoric** este stratul în care cercetătorul leagă acea cartografiere de propria argumentație.

## Afirmații umane

O afirmație de cercetare este introdusă exclusiv de utilizator și poate avea una dintre stările:

- `OPEN` — problemă/afirmație încă deschisă;
- `PROVISIONAL` — formulare provizorie;
- `SUPPORTED` — cercetătorul o consideră susținută;
- `DISPUTED` — cercetătorul o consideră disputată;
- `RETIRED` — afirmația a fost retrasă din argumentația activă.

Starea nu este calculată din numărul de dovezi. Ea este o decizie metodologică explicită.

## Rețeaua de dovezi

Orice unitate cartografiată exact poate fi legată de o afirmație printr-un rol ales de istoric:

- `SUPPORTS`;
- `CONTRADICTS`;
- `NUANCES`;
- `EXEMPLIFIES`;
- `CONTEXTUALIZES`.

Legătura păstrează `unit_id` și deci traseul complet:

`AFIRMAȚIE → ROL → UNITATE → SURSĂ → PAGINĂ → PARAGRAF → PROPOZIȚIE/CLAUZĂ → INDICI`

Aceeași unitate poate servi mai multor afirmații fără duplicarea dovezii.

## Critica sursei

Fiecare sursă poate primi o fișă critică introdusă de cercetător:

- tip / statut;
- gen;
- datare;
- origine;
- limbă;
- proveniență;
- tradiție / transmitere;
- public / destinatar;
- proximitate față de evenimente;
- perspectivă / interese observate;
- autenticitate / integritate / atribuire;
- ediție / traducere / aparat critic;
- observații metodologice.

Aplicația nu acordă automat scoruri de credibilitate.

## Jurnal metodologic

Crearea și modificarea afirmațiilor, schimbarea stării, legarea/eliminarea dovezilor și actualizarea criticii sursei intră într-un jurnal local. Astfel cercetarea nu este numai rezultatul final, ci și un traseu revizuibil al deciziilor.

## Dosar reproductibil

Atelierul poate exporta un fișier text care conține:

- harta tematică activă;
- sursele și starea cartografierii;
- fișele de critică a sursei;
- afirmațiile cercetătorului;
- toate dovezile legate și rolurile lor;
- adresele exacte în corpus;
- jurnalul de decizii metodologice.

Fișierul nu este o sinteză automată. Este o descriere reproductibilă a modului în care cercetătorul și-a construit argumentația.
