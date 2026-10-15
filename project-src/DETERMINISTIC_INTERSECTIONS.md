# Încrucișări deterministe de indici

Acest modul separă strict **co-prezența** de **încrucișarea demonstrată**.

## Contract

Un semn `×` este emis numai dacă sunt îndeplinite simultan următoarele condiții:

1. fiecare indice are o ancoră proprie, explicită, în aceeași propoziție semantică;
2. ancora este fie forma exactă a unui termen/alias din harta utilizatorului, fie o flexiune românească din aceeași familie lexicală îngustă;
3. cele două ancore ocupă span-uri diferite; același cuvânt nu poate demonstra două teme;
4. nodurile nu sunt părinte/copil în aceeași ramură a hărții;
5. una dintre regulile structurale de mai jos este satisfăcută.

Co-prezența la nivel de paragraf, pagină sau secțiune nu produce automat `×`.

## Reguli

### R1 — SUBJECT_TO_COMPLEMENT

Un indice este ancorat în subiectul propoziției, iar celălalt este ancorat explicit în obiect, slot semantic sau predicat.

Exemplu:

`Împăratul controlează armata.`

produce:

`Împărat × Armată [R1]`

Dacă propoziția conține `armata` și `patriarhul` numai ca două obiecte ale aceluiași subiect, acestea nu sunt intersectate între ele doar pentru că apar împreună.

### R2 — EXPLICIT_RELATIONAL_FRAME

Două ancore din același câmp semantic pot fi intersectate numai dacă acel câmp conține un cadru relațional explicit românesc de tip `între` / `dintre`.

Exemplu:

`Relația dintre împărat și patriarh...`

produce:

`Împărat × Biserică [R2]`

## Ce nu produce încrucișare

- două teme aflate doar pe aceeași pagină;
- două teme aflate doar în același paragraf;
- două noduri care folosesc aceeași ancoră lexicală;
- un nod și propriul lui subnod;
- o asociere bazată numai pe domeniu, funcție discursivă sau similaritate;
- o derivare lexicală largă. Derivational stemming rămâne util pentru recuperare, nu pentru identitatea unui indice.

## Auditabilitate

Fiecare rezultat păstrează:

- paragraful, propoziția și clauza;
- cele două noduri;
- termenul și suprafața OCR care au activat fiecare nod;
- tipul potrivirii: `EXACT` sau `INFLECTIONAL_FAMILY`;
- regula `R1` / `R2`;
- relația semantică detectată;
- operatorii propoziției, inclusiv negația/modalitatea când sunt prezenți.

În LUPĂ, încrucișările sunt afișate numai la zoom semantic **PROPOZIȚIE** și **SEGMENT**. Cele două ancore sunt încadrate separat și legate vizual; badge-ul arată regula (`× R1` / `× R2`). Bara de jos păstrează eticheta auditabilă.

## Principiu

> Niciun `×` fără două ancore independente și o dovadă structurală comună.

Motorul este intenționat conservator: o încrucișare ratată este preferabilă unei relații inventate.
