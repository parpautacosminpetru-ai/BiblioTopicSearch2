# BiblioTopicSearch2 — index tematic + categorii (versiune de test)

## Funcții

- **TOATE** cercetează toate hărțile active simultan, fără rescannarea paginii.
- Filtre independente pentru **TEME** și **CATEGORII**: Ani, Locuri, Persoane, Instituții, Evenimente, Concepte, Altele.
- **INDEX INTERN**: dicționar lexical editabil; formă pe linie `PERSOANE | Carol I | Mihail Kogălniceanu` etc. Termenii pot fi asociați și mai multor categorii.
- Culori stabile pe categorii, sublinieri discrete. Punct lângă termen când aparține mai multor teme.
- Motor global: un singur OCR și o singură căutare a fiecărui termen distinct, cu distribuire către toate temele indexate.
- Ani cu patru cifre recunoscuți și dacă nu apar într-o hartă.
- **IMPORT ZIP**: permite importarea celor 7 TXT din `BiblioTopicSearch2_7_harti_Romania_Moderna.zip`, fără a suprascrie hărțile existente.
- **INTERSECȚII**: termeni diferiți în aceeași linie sau același bloc OCR, configurabil. Doar propunere de verificare manuală.
- **CADRU**: OCR pe imaginea înghețată; după fixare poți comuta tema și categoria. Fișe de cercetare locale și export CSV.

## Validare și limite

- APK compilat ca **debug**, fără semnătură permanentă pentru distribuție.
- Necesar test real pe Android pentru alinierea coordonatelor și performanța cu multe hărți.
- Dicționarul este o euristică lexicală, nu clasificare semantică validată; termenii ambigui trebuie revizuiți.
- O apariție nu dovedește o relație istorică. Interpretarea revine utilizatorului.

## Import rapid

Deschide aplicația → **IMPORT ZIP** → selectează `BiblioTopicSearch2_7_harti_Romania_Moderna.zip` → **TOATE** → alege o categorie. Nu e necesară scanare nouă pentru a schimba lentila unui cadru înghețat.

## Instalare sigură
Acest APK de test folosește ID-ul de aplicație `ro.bibliotopicsearch.app.parallel`, diferit de aplicația originală. Cele două pot coexista. Datele se păstrează separat; importă hărțile ZIP în noua aplicație. Nu dezinstala aplicația veche înainte să verifici noua versiune.
