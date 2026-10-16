# Trusă digitală pentru arhivă și corpus

BiblioTopicSearch 1.9.0 extinde aceeași metodă deterministă la surse digitale și la lucru hands-free.

## PDF offline

Importul PDF folosește numai componente locale ale dispozitivului. Fiecare pagină este randată temporar, recunoscută OCR și trimisă prin același motor de CAPTURĂ EXACTĂ folosit de cameră. Randarea temporară este eliberată după procesare; PDF-ul original este păstrat separat, intact și verificabil prin SHA-256 în arhiva locală a sursei (începând cu 1.11). Încheierea paginii necesită confirmare umană după verificarea facsimilului și OCR-ului.

Maparea paginilor este explicită:

`pagina PDF → pagina tipărită`

Astfel front matter-ul, scanările cu offset și extrasele pot fi cartografiate fără a confunda coordonatele fișierului cu paginarea citabilă.

O pagină fără text OCR nu este declarată completă. Ea rămâne vizibilă ca lacună în registrul exhaustivității.

## Fațete cronologice și spațiale

Cartografierea păstrează ani expliciți și sloturi temporale/spațiale detectate în clauze. Acestea devin indecși secundari ai aceleiași unități, nu afirmații istorice noi.

Exemplu:

`1054 → pag. 73 → P4 → C2 → BIS × IMP`

Istoricul poate trece de la an sau loc direct la dovada sursă.

## Relații între surse și martori

Relațiile dintre ediții, traduceri, manuscrise, copii sau texte dependente sunt declarate de utilizator. Aplicația le păstrează ca graf de proveniență bibliografică și le exportă în dosarul reproductibil.

## Control hands-free

Modul de pedală/taste reduce interacțiunea fizică în arhivă. Comenzile hardware nu schimbă criteriile de rigoare; ele declanșează aceleași operații CAPTURĂ EXACTĂ și ÎNCHEIE PAGINA.

## Invariant

Viteza poate crește prin automatizarea mișcărilor, loturilor și indexării, dar nu prin relaxarea criteriului de dovadă.
