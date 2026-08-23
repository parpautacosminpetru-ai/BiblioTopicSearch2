package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Color;

/**
 * Built-in, theme-independent lexical libraries.
 * They are search aids only: a hit is a candidate that the user validates in context.
 */
public final class BuiltInMaps {
    public static final String TEXTUAL_PREFIX = "TEXTUAL";
    public static final String SEMANTIC_PREFIX = "SEMANTIC";
    public static final String PUNCTUATION_TITLE = "PUNCTUAȚIE";

    private BuiltInMaps() {}

    public static TopicMap textual(Context context) {
        TopicMap map = TopicMapStore.parseForProfile(
                context,
                "__builtin_textual",
                "Categorii textuale",
                TEXTUAL_RAW
        );
        styleBuiltIn(map, true);
        return map;
    }

    public static TopicMap semantic(Context context) {
        TopicMap map = TopicMapStore.parseForProfile(
                context,
                "__builtin_semantic",
                "Categorii semantice",
                SEMANTIC_RAW
        );
        styleBuiltIn(map, false);
        return map;
    }

    public static boolean isBuiltInPath(String path) {
        if (path == null) return false;
        return path.equals(TEXTUAL_PREFIX)
                || path.startsWith(TEXTUAL_PREFIX + " > ")
                || path.equals(SEMANTIC_PREFIX)
                || path.startsWith(SEMANTIC_PREFIX + " > ");
    }

    public static boolean isPunctuationNode(TopicNode node) {
        return node != null
                && PUNCTUATION_TITLE.equals(node.title)
                && node.path != null
                && node.path.startsWith(TEXTUAL_PREFIX + " > ");
    }

    private static void styleBuiltIn(TopicMap map, boolean textual) {
        if (map == null) return;
        int[] textualPalette = {
                Color.rgb(40, 146, 177), Color.rgb(53, 126, 173), Color.rgb(66, 151, 142),
                Color.rgb(89, 115, 170), Color.rgb(119, 101, 173), Color.rgb(48, 139, 164)
        };
        int[] semanticPalette = {
                Color.rgb(210, 126, 49), Color.rgb(190, 92, 58), Color.rgb(162, 105, 51),
                Color.rgb(188, 77, 95), Color.rgb(136, 103, 62), Color.rgb(204, 145, 48)
        };
        int[] palette = textual ? textualPalette : semanticPalette;
        int index = 0;
        for (TopicNode node : map.nodes) {
            // Root is a namespace, never a search term.
            if (node.level == 1) {
                node.enabled = false;
                continue;
            }
            node.enabled = true;
            node.color = palette[index++ % palette.length];
            node.symbol = textual ? "T" : "S";
        }
    }

    private static final String TEXTUAL_RAW = String.join("\n",
            "# TEXTUAL",
            "## PUNCTUAȚIE",

            "## TEMATIZARE / INTRODUCERE NOD",
            "în ceea ce privește | în ceea ce priveste | în privința | in privinta | cu privire la | referitor la | referitoare la | privind | cât despre | cat despre | în legătură cu | in legatura cu",

            "## ADĂUGARE / COORDONARE",
            "precum și | precum si | cât și | cat si | de asemenea | totodată | in plus | în plus | pe lângă aceasta | pe langa aceasta | împreună cu | impreuna cu | alături de | alaturi de",

            "## OPOZIȚIE / CONTRAST",
            "dar | însă | insa | ci | totuși | totusi | dimpotrivă | dimpotriva | în schimb | in schimb | spre deosebire de | în contrast cu | in contrast cu | în opoziție cu | in opozitie cu | pe când | pe cand | în vreme ce | in vreme ce | mai degrabă | mai degraba | de fapt | în realitate | in realitate",

            "## CAUZĂ TEXTUALĂ",
            "deoarece | fiindcă | fiindca | întrucât | intrucat | pentru că | pentru ca | din cauză că | din cauza ca | dat fiind că | dat fiind ca | având în vedere că | avand in vedere ca | ca urmare a faptului că | cauza | cauze | cauzal | cauzală | cauzala | motiv | motive | factor | factori | determină | determina | determinat | determinată | determinata | provoacă | provoaca | provocat | provocată | provocata | generează | genereaza | generat | contribuie | contribuit | contribuind | contribuție | contributie | favorizează | favorizeaza | favorizat | facilitează | faciliteaza | facilitat | permite | înlesnește | inlesneste | stimulează | stimuleaza | creează condițiile | creeaza conditiile",

            "## CONSECINȚĂ / REZULTAT TEXTUAL",
            "deci | așadar | asadar | astfel | prin urmare | în consecință | in consecinta | ca urmare | drept urmare | de aici | rezultă | rezulta | rezultat | rezultatul | conduce la | a condus la | conducând la | conducand la | duce la | a dus la | ducând la | ducand la | produce | produs | producând | producand | consecință | consecinta | consecințe | consecinte | efect | efecte | urmări | urmari",

            "## SCOP / FINALITATE TEXTUALĂ",
            "pentru a | pentru ca | ca să | ca sa | în vederea | in vederea | cu scopul de | în scopul | in scopul | scop | scopuri | obiectiv | obiective | finalitate | urmărește | urmareste | urmărea | urmarea | urmărit | urmarit | vizează | vizeaza | vizat | destinat să | destinat sa | destinată să | destinata sa | menit să | menit sa | menită să | menita sa",

            "## CONDIȚIE",
            "dacă | daca | în cazul în care | in cazul in care | în situația în care | in situatia in care | cu condiția să | cu conditia sa | cu condiția ca | cu conditia ca | numai dacă | numai daca | doar dacă | doar daca | exclusiv dacă | exclusiv daca | atât timp cât | atat timp cat | cât timp | cat timp | până când | pana cand | condiție | conditie | condiții | conditii | condiționat | conditionat | condiționată | conditionata",

            "## CONCESIE",
            "deși | desi | chiar dacă | chiar daca | cu toate că | cu toate ca | măcar că | macar ca | în ciuda faptului că | in ciuda faptului ca | în ciuda | in ciuda | în pofida | in pofida | cu toate acestea | chiar și așa | chiar si asa",

            "## ALTERNATIVĂ",
            "sau | ori | fie | alternativă | alternativa | alternative | variantă | varianta | variante | opțiune | optiune | opțiuni | optiuni",

            "## EXPLICAȚIE",
            "aceasta se explică prin | aceasta se explica prin | acest lucru se explică prin | acest lucru se explica prin | faptul se explică prin | faptul se explica prin | explicație | explicatie | explicații | explicatii | explică | explica | explicat | explicată | explicata | explicând | explicand | motivul este | rațiune | ratiune | rațiuni | ratiuni",

            "## REFORMULARE",
            "adică | adica | cu alte cuvinte | altfel spus | spus altfel | în alți termeni | in alti termeni | mai bine spus | ceea ce înseamnă | ceea ce inseamna | aceasta înseamnă | aceasta inseamna | prin aceasta se înțelege | prin aceasta se intelege",

            "## PRECIZARE / RECTIFICARE",
            "mai exact | mai precis | anume | respectiv | respectivă | respectiva | concret | în mod concret | in mod concret | sau mai exact | sau mai precis | mai bine zis",

            "## EXEMPLIFICARE",
            "de exemplu | spre exemplu | de pildă | de pilda | bunăoară | bunaoara | cum ar fi | precum | printre care | printre acestea | un exemplu | exemple | exemplul | cazul | cazurile | exemplifică | exemplifica | exemplificat | ilustrează | ilustreaza | ilustrat",

            "## GENERALIZARE",
            "în general | in general | în ansamblu | in ansamblu | în linii generale | in linii generale | de regulă | de regula | în mod obișnuit | in mod obisnuit | în cele mai multe cazuri | in cele mai multe cazuri | în majoritatea cazurilor | in majoritatea cazurilor | în totalitate | in totalitate | în întregime | in intregime | per ansamblu | global",

            "## PARTICULARIZARE / FOCALIZARE",
            "în particular | in particular | în special | in special | mai ales | îndeosebi | indeosebi | cu precădere | cu precadere | specific | în cazul | in cazul | tocmai | chiar | în primul rând | in primul rand | în principal | in principal | în mod special | in mod special | mai cu seamă | mai cu seama",

            "## ENUMERARE / ORDINE",
            "următoarele | urmatoarele | printre | se disting | pot fi distinse | pot fi enumerate | în primul rând | in primul rand | în al doilea rând | in al doilea rand | în al treilea rând | in al treilea rand | întâi | intai | apoi | ulterior | primul | prima | primii | primele | al doilea | a doua | următorul | urmatorul | următoarea | urmatoarea | ultimul | ultima | în sfârșit | in sfarsit | în final | in final | printre altele",

            "## SECVENȚĂ / STADIU",
            "mai întâi | mai intai | inițial | initial | la început | la inceput | într-o primă etapă | intr-o prima etapa | după aceea | dupa aceea | în continuare | in continuare | urmează | urmeaza | succesiv | treptat | gradual | progresiv | pe rând | pe rand | la final | în cele din urmă | in cele din urma | începe să | incepe sa | a început să | a inceput sa | continuă să | continua sa | a continuat să | a continuat sa | încetează să | inceteaza sa | a încetat să | a incetat sa | nu mai | ajunge să | ajunge sa | a ajuns să | a ajuns sa | reușește să | reuseste sa | rămâne | ramane | persistă | persista",

            "## TEMPORALITATE",
            "înainte | inainte | înainte de | inainte de | anterior | anterioară | anterioara | precede | precedent | precedentă | precedenta | mai devreme | până atunci | pana atunci | după | dupa | după ce | dupa ce | ulterior | ulterioară | ulterioara | după aceea | mai târziu | mai tarziu | simultan | simultană | simultana | concomitent | concomitentă | concomitenta | în același timp | in acelasi timp | în timp ce | in timp ce | timp de | vreme de | de-a lungul | pe parcursul | pe durata | în cursul | in cursul | mereu | întotdeauna | intotdeauna | frecvent | adesea | deseori | uneori | rar | periodic | repetat",

            "## COREFERINȚĂ",
            "acesta | aceasta | aceștia | acestia | acestea | acela | aceea | aceia | acelea | el | ea | ei | ele | lui | lor | respectivul | respectiva | respectivii | respectivele | primul | prima | ultimul | ultima | cel dintâi | cel dintai | cel din urmă | cel din urma | același | acelasi | aceeași | aceeasi | aceiași | aceiasi | aceleași | aceleasi | mai sus | menționat anterior | mentionat anterior | amintit mai sus | precedent",

            "## IDENTITATE / ECHIVALARE",
            "este | sunt | reprezintă | reprezinta | constituie | înseamnă | inseamna | desemnează | desemneaza | se numește | se numeste | numit | numită | numita | denumit | denumită | denumita | cunoscut ca | cunoscută ca | cunoscuta ca | se definește | se defineste | definit | definită | definita | este definit ca | este definită ca | este definita ca",

            "## DIFERENȚIERE / DELIMITARE",
            "diferă | difera | diferă de | difera de | se deosebește | se deosebeste | distinct | distinctă | distincta | separat | separată | separata | nu trebuie confundat cu | nu trebuie confundată cu | nu trebuie confundata cu | se separă | se separa | s-a separat | separare | delimitează | delimiteaza | delimitat | delimitată | delimitata",

            "## PARTE / ÎNTREG",
            "aparține | apartine | aparțin | apartin | face parte din | fac parte din | inclus în | inclus in | inclusă în | inclusa in | conține | contine | conțin | contin | cuprinde | cuprind | include | includ | înglobează | inglobeaza | alcătuit din | alcatuit din | alcătuită din | alcatuita din | format din | formată din | formata din | compus din | compusă din | compusa din | constituit din | constituită din | constituita din | parte | părți | parti | componentă | componenta | componente | element | elemente | segment | secțiune | sectiune",

            "## AGENT PASIV / AUTOR ACȚIUNE",
            "de către | de catre | realizat de | realizată de | realizata de | efectuat de | efectuată de | efectuata de | produs de | produsă de | produsa de | creat de | creată de | creata de | inițiator | initiator | inițiatoare | initiatoare | autor | autoare | responsabil | responsabilă | responsabila",

            "## OBIECT / ȚINTĂ",
            "asupra | asupra lui | asupra acesteia | asupra acestora | împotriva | impotriva | față de | fata de | către | catre | vizează | vizeaza | vizat | vizată | vizata | afectează | afecteaza | afectat | afectată | afectata | afectați | afectati | afectate",

            "## INSTRUMENT / MIJLOC",
            "prin intermediul | cu ajutorul | folosind | utilizând | utilizand | mijloc | mijloace | instrument | instrumente | metodă | metoda | metode | procedeu | procedee",

            "## BENEFICIAR / DESTINATAR",
            "în beneficiul | in beneficiul | în folosul | in folosul | în favoarea | in favoarea | destinat | destinată | destinata | destinați | destinati | destinate | adresat | adresată | adresata | adresate",

            "## SUBIECT CANDIDAT — PRONUME",
            "eu | tu | el | ea | noi | voi | ei | ele | acesta | aceasta | aceștia | acestia | acestea | acela | aceea | aceia | acelea | cine | care | ceea ce | cel care | cea care | cei care | cele care | cineva | unul | una | unii | unele | fiecare | oricine | nimeni",

            "## PREDICAT / COPULĂ — INDICII",
            "este | sunt | era | erau | a fost | au fost | devine | devenea | rămâne | ramane | rămân | raman | pare | părea | parea | există | exista | existau | apare | apar | apărut | aparut | reprezintă | reprezinta | constituie",

            "## NEGAȚIE",
            "nu | nici | fără | fara | niciodată | niciodata | nicidecum | deloc | în niciun caz | in niciun caz | sub nicio formă | sub nicio forma | absență | absenta | lipsă | lipsa | absent | absentă | absenta | nu există | nu exista | nu existau | nu are | nu avea | nu dispune | nu dispunea",

            "## RESTRICȚIE / EXCEPȚIE",
            "numai | doar | exclusiv | limitat la | limitată la | limitata la | restrâns la | restrans la | restrânsă la | restransa la | exceptând | exceptand | cu excepția | cu exceptia | în afară de | in afara de | exceptat | exceptată | exceptata | cel puțin | cel putin | cel mult | minimum | maximum | până la | pana la",

            "## MODALITATE",
            "poate | pot | putea | puteau | ar putea | posibil | posibilă | posibila | este posibil | era posibil | trebuie | trebuia | necesar | necesară | necesara | obligatoriu | obligatorie | indispensabil | indispensabilă | indispensabila | permis | permisă | permisa | este permis | era permis | se admite | admis | admisă | admisa | interzis | interzisă | interzisa | nu este permis | nu era permis | nu se admite | capabil | capabilă | capabila | capacitate | posibilitatea de a",

            "## CERTITUDINE / STATUT EPISTEMIC",
            "cert | certă | certa | sigur | sigură | sigura | cu certitudine | fără îndoială | fara indoiala | evident | evidentă | evidenta | clar | clară | clara | probabil | probabilă | probabila | probabilitate | cel mai probabil | incert | incertă | incerta | nesigur | nesigură | nesigura | nu se știe | nu se stie | necunoscut | necunoscută | necunoscuta | pare | părea | parea | se pare | se părea | se parea | aparent | aparentă | aparenta | în aparență | in aparenta | ipoteză | ipoteza | ipoteze | presupune | presupun | presupus | presupusă | presupusa | se presupune",

            "## CANTITATE / GRAD",
            "tot | toată | toata | toți | toti | toate | întreg | intreg | întreaga | intreaga | integral | total | majoritate | majoritatea | cei mai mulți | cei mai multi | cea mai mare parte | unii | unele | câțiva | cativa | câteva | cateva | parte dintre | parțial | partial | în parte | in parte | orice | fiecare | pretutindeni | niciun | nicio | nimeni | nimic | foarte | extrem de | puternic | puternică | puternica | intens | intensă | intensa | profund | profundă | profunda | considerabil | considerabilă | considerabila | moderat | moderată | moderata | relativ | relativă | relativa | într-o anumită măsură | intr-o anumita masura | slab | slabă | slaba | redus | redusă | redusa | puțin | putin | aproximativ | circa | aproape | în jur de | in jur de",

            "## COMPARAȚIE",
            "asemenea | similar | similară | similara | asemănător | asemanator | asemănătoare | asemanatoare | analog | analogă | analoga | diferit | diferită | diferita | mai mare | mai mult | superior | superioară | superioara | depășește | depaseste | mai mic | mai puțin | mai putin | inferior | inferioară | inferioara",

            "## ATRIBUIRE / VORBIRE RAPORTATĂ",
            "afirmă | afirma | afirmat | afirmată | afirmata | afirmând | afirmand | susține | sustine | susțin | sustin | susținea | sustinea | susținut | sustinut | susținând | sustinand | consideră | considera | considerat | considerată | considerata | considerând | considerand | scrie | scriau | scris | scrisă | scrisa | scriind | menționează | mentioneaza | menționat | mentionat | menționată | mentionata | declară | declara | declarat | declarată | declarata | potrivit | conform | în opinia | in opinia | în viziunea | in viziunea | din perspectiva",

            "## DOVADĂ / ARGUMENT",
            "dovadă | dovada | dovezi | probă | proba | probe | evidență | evidenta | mărturie | marturie | mărturii | marturii | document | documente | arată | arata | arătat | aratat | indică | indica | indicat | demonstrează | demonstreaza | demonstrat | confirmă | confirma | confirmat | contrazice | contrazis | contestă | contesta | contestat | infirmă | infirma | respinge | respins",

            "## EVALUAREA AUTORULUI",
            "important | importantă | importanta | importanță | importanta | esențial | esential | esențială | esentiala | fundamental | fundamentală | fundamentala | semnificativ | semnificativă | semnificativa | relevant | relevantă | relevanta | remarcabil | remarcabilă | remarcabila | problematic | problematică | problematica | discutabil | discutabilă | discutabila | controversat | controversată | controversata | surprinzător | surprinzator | surprinzătoare | surprinzatoare | curios | curioasă | curioasa | neobișnuit | neobisnuit | neobișnuită | neobisnuita",

            "## PERSPECTIVĂ / SCOPE",
            "din perspectiva | din punctul de vedere | în viziunea | in viziunea | în concepția | in conceptia | în cadrul | in cadrul | în contextul | in contextul | la nivelul | în domeniul | in domeniul | în sfera | in sfera | sub aspectul | sub raportul | în cazul | in cazul | în aceste condiții | in aceste conditii | în măsura în care | in masura in care | numai în | numai in | doar în | doar in | pentru cazul",

            "## PROBLEMĂ / ÎNTREBARE / RĂSPUNS",
            "problemă | problema | probleme | problema este | se pune problema | chestiune | chestiunea | întrebare | intrebare | întrebări | intrebari | întrebarea este | intrebarea este | se pune întrebarea | se pune intrebarea | răspuns | raspuns | răspunsuri | raspunsuri | răspunsul | raspunsul | răspunde | raspunde | soluție | solutie | soluții | solutii | rezolvare | rezolvări | rezolvari",

            "## TRANZIȚIE DISCURSIVĂ",
            "în continuare | in continuare | de asemenea | totodată | totodata | mai departe | pe de altă parte | pe de alta parte | în altă ordine de idei | in alta ordine de idei | revenind la | după cum s-a arătat | dupa cum s-a aratat | după cum am menționat | dupa cum am mentionat | așa cum s-a menționat | asa cum s-a mentionat",

            "## CONCLUZIE",
            "în concluzie | in concluzie | așadar | asadar | prin urmare | se poate concluziona | rezultă că | rezulta ca | în final | in final | în esență | in esenta | de aici rezultă | de aici rezulta | ceea ce arată că | ceea ce arata ca",

            "## INSERȚIE / PARANTEZĂ VERBALĂ",
            "de altfel | dealtminteri | într-adevăr | intr-adevar | desigur | firește | fireste | trebuie spus | trebuie menționat | trebuie mentionat | este de precizat | este de observat",

            "## NUANȚARE",
            "totuși | totusi | însă | insa | în parte | in parte | relativ | într-o anumită măsură | intr-o anumita masura | cu toate acestea | nu înseamnă că | nu inseamna ca | fără a | fara a | chiar dacă | chiar daca | de regulă | de regula | adesea | uneori | în mare parte | in mare parte",

            "## ANCORĂ NEUTRĂ — PERCEPȚIE",
            "percepție | perceptie | percepția | perceptia | percepții | perceptii | percepere | perceperea | imagine | imagini | reprezentare | reprezentări | reprezentari | atitudine | atitudini",

            "## ANCORĂ NEUTRĂ — ORGANIZARE",
            "organizare | organizarea | structură | structura | structuri | funcționare | functionare | funcționarea | functionarea | sistem | sisteme",

            "## ANCORĂ NEUTRĂ — EVOLUȚIE",
            "evoluție | evolutie | evoluția | evolutia | evoluții | evolutii | dezvoltare | dezvoltarea | transformare | transformarea | schimbare | schimbarea",

            "## ANCORĂ NEUTRĂ — RELAȚIE",
            "relație | relatie | relația | relatia | relații | relatii | raport | raporturi | legătură | legatura | legături | legaturi | interacțiune | interactiune",

            "## ANCORĂ NEUTRĂ — ORIGINE",
            "origine | originea | origini | geneză | geneza | apariție | aparitie | apariția | aparitia | formare | formarea",

            "## ANCORĂ NEUTRĂ — TRANSMITERE / RECEPTARE",
            "transmitere | transmiterea | răspândire | raspandire | răspândirea | raspandirea | difuzare | difuzarea | circulație | circulatie | receptare | receptarea | recepție | receptie | acceptare | respingere | reacție | reactie | reacții | reactii",

            "## ANCORĂ NEUTRĂ — INFLUENȚĂ / CONFLICT",
            "influență | influenta | influențe | influente | impact | impactul | efect | efecte | consecință | consecinta | consecințe | consecinte | conflict | conflicte | confruntare | confruntări | confruntari | opoziție | opozitie | rivalitate | rivalități | rivalitati"
    );

    private static final String SEMANTIC_RAW = String.join("\n",
            "# SEMANTIC",
            "## IDENTITATE / DEFINIȚIE",
            "înseamnă | inseamna | se definește | se defineste | definit ca | definită ca | definita ca | reprezintă | reprezinta | constituie | desemnează | desemneaza | se numește | se numeste | categorie | clasă | clasa | tip | formă | forma | variantă | varianta | aparține | apartine | face parte din",

            "## ORIGINE / GENEZĂ",
            "origine | origini | geneză | geneza | apariție | aparitie | naștere | nastere | formare | constituire | începuturi | inceputuri | se formează | se formeaza | ia naștere | ia nastere | provine din | derivă din | deriva din | rădăcini | radacini | antecedent | antecedente | precursor | precursori",

            "## CONȚINUT / STRUCTURĂ INTERNĂ",
            "conținut | continut | cuprinde | conține | contine | include | alcătuit din | alcatuit din | format din | compus din | componentă | componenta | componente | principiu | principii | teză | teza | teze | doctrină | doctrina | învățătură | invatatura | dogmă | dogma | caracteristică | caracteristica | caracteristici | trăsătură | trasatura | trăsături | trasaturi | prevede | prevederi | normă | norma | norme | regulă | regula | reguli | noutate | inovație | inovatie | curent | curente | ramură | ramura | ramuri",

            "## TIMP",
            "în anul | in anul | în anii | in anii | în secolul | in secolul | în perioada | in perioada | în epoca | in epoca | în deceniul | in deceniul | la începutul | la inceputul | la mijlocul | la sfârșitul | la sfarsitul | anterior | precedent | ulterior | simultan | concomitent | durată | durata | etapă | etapa | etape | fază | faza | faze",

            "## SPAȚIU",
            "se află | se afla | se găsește | se gaseste | situat | situată | situata | localizat | localizată | localizata | regiune | regiunea | zonă | zona | teritoriu | teritoriul | originar din | originară din | originara din | se extinde | extindere | expansiune | răspândire | raspandire | pătrunde în | patrunde in | ajunge în | ajunge in | spre | către | catre | vecinătate | vecinatate",

            "## ACTORI / ROLURI",
            "actor | actori | participant | participanți | participanti | persoană | persoana | persoane | grup | grupuri | comunitate | populație | populatie | inițiator | initiator | conducător | conducator | lider | autoritate | susținător | sustinator | susținători | sustinatori | oponent | adversar | adversari | beneficiar | beneficiari | afectat | afectată | afectata | victime | rol | funcție | functie",

            "## CONTEXT",
            "în contextul | in contextul | în cadrul | in cadrul | în condițiile | in conditiile | în împrejurările | in imprejurarile | pe fondul | politic | politică | politica | economic | economie | social | societate | religios | religie | cultural | cultură | cultura | militar | juridic | drept | intelectual | idei | gândire | gandire",

            "## PREMISE / CONDIȚII DE POSIBILITATE",
            "premisă | premisa | premise | condiții prealabile | conditii prealabile | situație anterioară | situatie anterioara | preexistent | antecedent | antecedente | a pregătit terenul | a pregatit terenul | a creat cadrul | a creat condițiile | a creat conditiile | acumulare | a făcut posibil | a facut posibil | a permis | a facilitat | a favorizat",

            "## CAUZE / FACTORI / DECLANȘATOR",
            "cauză | cauza | cauze | din cauza | cauzat de | cauzată de | cauzata de | determină | determina | determinat de | provoacă | provoaca | provocat de | generează | genereaza | factor | factori | factor determinant | factor favorizant | factor decisiv | contribuie la | contribuție | contributie | favorizează | favorizeaza | facilitează | faciliteaza | permite | stimulează | stimuleaza | declanșează | declanseaza | declanșare | declansare | factor declanșator | factor declansator | precipită | precipita | motiv | motive | deoarece | fiindcă | fiindca | întrucât | intrucat",

            "## MOTIVAȚII / INTERESE",
            "motiv | motive | motivație | motivatie | motivat de | interes | interese | în interesul | in interesul | intenție | intentie | intenționează | intentioneaza | își propune | isi propune | urmărea | urmarea | urmărește | urmareste | nevoie | necesitate | considera necesar",

            "## SCOP / FINALITATE",
            "scop | scopuri | cu scopul | în scopul | in scopul | pentru a | în vederea | in vederea | obiectiv | obiective | țintă | tinta | ținte | tinte | finalitate | urmărește | urmareste | vizează | vizeaza | destinat să | destinat sa | menit să | menit sa",

            "## MIJLOACE / RESURSE",
            "mijloc | mijloace | instrument | instrumente | metodă | metoda | metode | procedeu | procedee | prin intermediul | cu ajutorul | folosind | utilizând | utilizand | resursă | resursa | resurse | efective | finanțe | finante | materiale | infrastructură | infrastructura | bani | capacitate | capacități | capacitati | avantaj | avantaje",

            "## CONSTRÂNGERI / LIMITE",
            "constrângere | constrangere | constrângeri | constrangeri | limitare | limitări | limitari | obstacol | obstacole | dificultate | dificultăți | dificultati | împiedică | impiedica | blochează | blocheaza | frânează | franeaza | limitează | limiteaza | restrânge | restrange | inhibă | inhiba | lipsă | lipsa | insuficiență | insuficienta | deficit | presiune | opoziție | opozitie | rezistență | rezistenta",

            "## MECANISM / FUNCȚIONARE",
            "mecanism | mecanisme | funcționare | functionare | funcționează | functioneaza | proces prin care | mod de funcționare | mod de functionare | modalitate | cale | procedeu | interacțiune | interactiune | influențează | influenteaza | acționează asupra | actioneaza asupra | se transformă | se transforma | devine | se modifică | se modifica | evoluează | evolueaza",

            "## DESFĂȘURARE / ETAPE",
            "desfășurare | desfasurare | se desfășoară | se desfasoara | evoluție | evolutie | evoluează | evolueaza | succesiune | începe | incepe | debut | izbucnește | izbucneste | pornește | porneste | continuă | continua | etapă | etapa | etape | fază | faza | faze | stadiu | stadii | treptat | gradual | progresiv | culminație | culminatie | se încheie | se incheie | se termină | se termina",

            "## COTITURĂ / TRANZIȚIE",
            "moment de cotitură | moment de cotitura | punct de cotitură | punct de cotitura | moment decisiv | punct decisiv | tranziție | tranzitie | trecere | trecerea de la | ruptură | ruptura | discontinuitate | întrerupere | intrerupere | criză | criza | accelerare | intensificare | amplificare | escaladare | inversare | schimbare de direcție | schimbare de directie | recul | revenire",

            "## TRANSMITERE / DIFUZARE",
            "transmite | transmitere | comunică | comunica | comunicare | răspândește | raspandeste | răspândire | raspandire | difuzează | difuzeaza | difuzare | propagă | propaga | propagare | circulație | circulatie | prin intermediul | prin rețele | prin retele | predică | predica | predicare | propovăduiește | propovaduieste | publică | publica | tipărește | tipareste | tipar | carte | broșură | brosura | pamflet | traducere | instituție | institutie | școală | scoala | universitate",

            "## RECEPTARE",
            "receptare | recepție | receptie | primit | primită | primita | perceput | percepută | perceputa | percepție | perceptie | văzut ca | vazut ca | privit ca | privită ca | privita ca | considerat | considerată | considerata | înțeles ca | inteles ca | înțeleasă ca | inteleasa ca | interpretat ca | interpretată ca | interpretata ca | atitudine | atitudini | poziție | pozitie | poziții | pozitii | reacție | reactie | reacții | reactii | acceptă | accepta | acceptat | acceptată | acceptata | acceptare | adoptă | adopta | adoptare | aderă | adera | adeziune | susține | sustine | sprijină | sprijina | sprijin | aprobă | aproba | încurajează | incurajeaza | favorabil | popularitate | succes | adepți | adepti | respinge | respins | respinsă | respinsa | respingere | refuză | refuza | refuz | se opune | opoziție | opozitie | împotrivire | impotrivire | rezistență | rezistenta | condamnă | condamna | condamnat | condamnată | condamnata | condamnare | anatemă | anatema | excomunicare | cenzură | cenzura | interdicție | interdictie | critică | critica | contestă | contesta | polemică | polemica | reacție oficială | reactie oficiala | poziție oficială | pozitie oficiala | decret | edict | conciliu | sinod | ostil | ostilă | ostila | ambivalent | rezerve | polarizare | tabere | dezacord | contemporanii | contemporan | posteritate | reinterpretat | reinterpretare | reevaluare | reconsiderat",

            "## EFECTE / CONSECINȚE / IMPACT",
            "efect | efecte | produce | provoacă | provoaca | determină | determina | generează | genereaza | consecință | consecinta | consecințe | consecinte | ca urmare | drept urmare | în consecință | in consecinta | rezultat | rezultate | rezultă | rezulta | se ajunge la | conduce la | duce la | se soldează cu | se soldeaza cu | impact | influență | influenta | repercusiune | repercusiuni | urmări | urmari | imediat | pe termen scurt | pe termen mediu | pe termen lung | efecte de durată | efecte de durata | consecințe durabile | consecinte durabile",

            "## SCHIMBARE / CONTINUITATE",
            "schimbare | se schimbă | se schimba | modificare | se modifică | se modifica | transformare | devine | creștere | crestere | extindere | expansiune | intensificare | scădere | scadere | reducere | declin | diminuare | restrângere | restrangere | apariție | aparitie | se formează | se formeaza | continuitate | continuă | continua | persistă | persista | se menține | se mentine | rămâne | ramane | se păstrează | se pastreaza | supraviețuiește | supravietuieste | se perpetuează | se perpetueaza | dispare | dispariție | disparitie | încetează | inceteaza | se stinge | reapare | reapariție | reapariţie | revine | revenire | renaște | renaste",

            "## VARIAȚIE",
            "variază | variaza | variație | variatie | diferă | difera | diferențe | diferente | particularități | particularitati | specificități | specificitati | regional | local | diferă de la | difera de la | specific zonei | într-o primă etapă | intr-o prima etapa | se modifică în timp | se modifica in timp | pentru unii | diferit pentru | în funcție de | in functie de",

            "## RELAȚII / DEPENDENȚĂ",
            "relație | relatie | legătură | legatura | raport | conexiune | asociere | interacțiune | interactiune | influențează | influenteaza | influențat de | influentat de | contribuie la | depinde de | dependent de | în funcție de | in functie de | condiționat de | conditionat de | interdependență | interdependenta | reciproc | influență reciprocă | influenta reciproca | depind reciproc | parte din | componentă | componenta | include | cuprinde | aparține | apartine",

            "## FEEDBACK / RECIPROCITATE",
            "reciproc | influență reciprocă | influenta reciproca | se influențează reciproc | se influenteaza reciproc | interacțiune reciprocă | interactiune reciproca | reacție | reactie | reacționează | reactioneaza | ca răspuns la | ca raspuns la | răspunde prin | raspunde prin | la rândul său | la randul sau | ceea ce apoi | care ulterior | determină la rândul său | determina la randul sau",

            "## INTERN / EXTERN",
            "intern | internă | interna | din interior | factori interni | cauze interne | probleme interne | extern | externă | externa | din exterior | factori externi | presiune externă | presiune externa | intervenție externă | interventie externa | influență externă | influenta externa | reacție internă | reactie interna | impact extern | raport intern-extern",

            "## SEMNIFICAȚIE / IMPORTANȚĂ",
            "importanță | importanta | important | importantă | esențial | esential | esențială | esentiala | fundamental | fundamentală | fundamentala | decisiv | decisivă | decisiva | crucial | crucială | cruciala | semnificație | semnificatie | însemnătate | insemnatate | relevanță | relevanta | valoare istorică | valoare istorica | rol | a contribuit la | a făcut posibil | a facut posibil | a pregătit | a pregatit | a modificat | moștenire | mostenire | influență ulterioară | influenta ulterioara | impact ulterior",

            "## DOVADĂ / ATRIBUIRE",
            "dovadă | dovada | dovezi | probă | proba | probe | evidență | evidenta | document | documente | mărturie | marturie | mărturii | marturii | arată că | arata ca | indică faptul că | indica faptul ca | demonstrează că | demonstreaza ca | dovedește că | dovedeste ca | relevă că | releva ca | confirmă | confirma | coroborează | coroboreaza | potrivit | conform | în opinia | in opinia | susține | sustine | afirmă | afirma | consideră | considera | autor | cercetător | cercetator | istoric | specialist | lucrare | studiu | nu există dovezi | nu exista dovezi | lipsa dovezilor | nu este atestat",

            "## FAPT / INTERPRETARE / ISTORIOGRAFIE",
            "este atestat | documentat | confirmat | demonstrat | cert | interpretare | interpretări | interpretari | interpretează | interpreteaza | consideră | considera | susține că | sustine ca | în opinia | in opinia | viziune | ipoteză | ipoteza | ipoteze | presupune | probabil | posibil | ar putea | incert | nu se știe | nu se stie | necunoscut | controversat | disputat | istoriografie | istoriografic | istorici | dezbatere istoriografică | dezbatere istoriografica | a fost interpretat | a fost considerat | istoricii consideră | istoricii considera | reevaluare | reinterpretare | contestă | contesta | respinge | contrazice | critică | critica | infirmă | infirma | perspective diferite | opinii diferite | dezacord | controverse",

            "## TEORIE / MODEL EXPLICATIV",
            "teorie | teorii | model | modele | model explicativ | explicație | explicatie | explicații | explicatii | teză | teza | teze | concepție | conceptie | concepții | conceptii | școală | scoala | școli | scoli | interpretare | interpretări | mecanism explicativ | cauză privilegiată | cauza privilegiata | unitate de analiză | unitate de analiza | dovezi | critică | critica | critici | limită | limita | limite | alternativă | alternativa | alternative",

            "## ALTERNATIVE",
            "alternativă | alternativa | alternative | altă posibilitate | alta posibilitate | variantă | varianta | variante | opțiune | optiune | opțiuni | optiuni | alegere | alegeri | putea alege | propune | propunere | soluție alternativă | solutie alternativa | respinge | refuză | refuza | abandonează | abandoneaza | renunță la | renunta la",

            "## COMPARAȚIE / SCARĂ",
            "asemănare | asemanare | similar | asemănător | asemanator | analog | comparabil | diferență | diferenta | diferă | difera | distinct | spre deosebire de | în contrast cu | in contrast cu | în opoziție cu | in opozitie cu | dimpotrivă | dimpotriva | mai mare decât | mai mare decat | mai mic decât | mai mic decat | superior | inferior | macro | mezo | micro | general | ansamblu | sistem | societate | stat | instituție | institutie | grup | comunitate | regiune | sector | individ | local | caz particular | exemplu concret | la nivelul | în ansamblu | in ansamblu | la nivel local | la nivel regional | la nivel individual"
    );
}
