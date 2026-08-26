package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Broad deterministic Romanian domain routing built on lexical families. */
public final class RomanianDomainLexicon {
    private RomanianDomainLexicon() {}

    private static final Map<String, String[]> DOMAINS = build();

    public static List<String> facetsFor(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, String[]> entry : DOMAINS.entrySet()) {
            if (RomanianMorphology.containsAnyFamily(text, entry.getValue())) {
                out.add("DOMAIN=" + entry.getKey());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    public static List<String> domains() {
        return Collections.unmodifiableList(new ArrayList<>(DOMAINS.keySet()));
    }

    private static Map<String, String[]> build() {
        LinkedHashMap<String, String[]> m = new LinkedHashMap<>();
        m.put("HISTORY", a("istorie","istoric","epocă","secol","cronologie","domnie","dinastie","reformă","revoluție","răscoală","unire","independență","tratat","imperiu","regat","voievod","arhivă"));
        m.put("RELIGION", a("religie","religios","biserică","cult","credință","confesiune","protestant","catolic","ortodox","calvin","luteran","islam","iudaism","budism","ritual","cler","sinod"));
        m.put("THEOLOGY", a("teologie","dogmă","doctrină","scriptură","biblic","evanghelie","sacrament","mântuire","har","ecleziologie","hristologie"));
        m.put("POLITICS", a("politică","politic","guvern","partid","alegeri","parlament","stat","putere","regim","președinte","ministru","ideologie","democrație","autoritar","suveranitate"));
        m.put("ADMINISTRATION", a("administrație","administrativ","instituție","minister","primărie","prefectură","funcționar","procedură","competență","autoritate"));
        m.put("INTERNATIONAL_RELATIONS", a("diplomație","diplomatic","alianță","coaliție","geopolitic","relații internaționale","ambasadă","frontieră","sancțiune","negociere"));
        m.put("ECONOMICS", a("economie","economic","piață","inflație","preț","cerere","ofertă","producție","consum","fiscal","monetar","PIB","șomaj","venit","cost","productivitate"));
        m.put("FINANCE", a("finanță","financiar","bancă","credit","dobândă","investiție","capital","buget","monedă","curs","bursă","acțiune","obligațiune","lichiditate"));
        m.put("BUSINESS", a("afacere","companie","firmă","întreprindere","management","marketing","vânzare","client","strategie","profit","concurență","antreprenor"));
        m.put("LAW", a("drept","juridic","lege","constituție","decret","ordonanță","regulament","directivă","articol","cod","instanță","tribunal","judecător","jurisprudență","contract","obligație","răspundere"));
        m.put("CRIMINOLOGY", a("infracțiune","criminal","penal","delict","pedeapsă","victimă","anchetă","procuror","poliție","recidivă"));
        m.put("MEDICINE", a("medicină","medical","pacient","boală","diagnostic","tratament","clinic","simptom","terapie","medic","spital","prognostic","patologie","farmacologie"));
        m.put("ANATOMY", a("anatomie","organ","țesut","os","mușchi","nerv","arteră","venă","creier","inimă","plămân"));
        m.put("PUBLIC_HEALTH", a("epidemiologie","epidemie","pandemie","incidență","prevalență","vaccin","sănătate publică","screening","mortalitate","morbiditate"));
        m.put("PSYCHOLOGY", a("psihologie","psihic","cognitiv","emoție","comportament","memorie","percepție","motivație","personalitate","traumă","anxietate","depresie"));
        m.put("SOCIETY", a("societate","social","comunitate","grup","clasă socială","inegalitate","familie","instituție socială","mobilitate","normă socială"));
        m.put("SOCIOLOGY", a("sociologie","sociologic","socializare","stratificare","devianță","rol social","status","urbanizare","modernizare"));
        m.put("DEMOGRAPHY", a("demografie","demografic","populație","natalitate","mortalitate","migrație","fertilitate","speranță de viață","densitate"));
        m.put("ANTHROPOLOGY", a("antropologie","antropologic","etnografie","etnologie","rudenie","trib","cultură materială","ritual"));
        m.put("ARCHAEOLOGY", a("arheologie","arheologic","săpătură","artefact","sit","stratigrafie","ceramică","vestigiu"));
        m.put("GEOGRAPHY", a("geografie","geografic","regiune","teritoriu","oraș","țară","continent","relief","râu","munte","câmpie","bazin","hartă","localizare"));
        m.put("GEOLOGY", a("geologie","geologic","rocă","mineral","sediment","tectonic","fosilă","strat","vulcan","seism"));
        m.put("ECOLOGY", a("ecologie","ecologic","ecosistem","habitat","biodiversitate","specie","mediu","poluare","conservare","resursă naturală"));
        m.put("AGRICULTURE", a("agricultură","agricol","cultură agricolă","sol","recoltă","fermă","irigație","zootehnie","silvicultură"));
        m.put("BIOLOGY", a("biologie","biologic","celulă","organism","genă","genetic","ADN","proteină","evoluție","specie","metabolism","fiziologie"));
        m.put("CHEMISTRY", a("chimie","chimic","moleculă","atom","compus","reacție","acid","bază","soluție","catalizator","element chimic"));
        m.put("PHYSICS", a("fizică","fizic","energie","forță","masă","particulă","undă","câmp","electric","magnetic","termodinamic","cuantic"));
        m.put("MATHEMATICS", a("matematică","matematic","ecuație","funcție","teoremă","demonstrație","algebră","geometrie","calcul","probabilitate","statistică","matrice"));
        m.put("ASTRONOMY", a("astronomie","astronomic","planetă","stea","galaxie","orbită","cosmic","univers","satelit","telescop"));
        m.put("SCIENCE", a("știință","științific","cercetare","experiment","ipoteză","teorie","metodologie","observație","date","măsurare"));
        m.put("ENGINEERING", a("inginerie","inginer","proiectare","mecanic","structură","material","sistem","control","rezistență","prototip"));
        m.put("TECHNOLOGY", a("tehnologie","tehnologic","digital","dispozitiv","automatizare","robot","senzor","procesor","rețea","platformă"));
        m.put("COMPUTING", a("informatică","calculator","software","hardware","algoritm","program","cod","bază de date","rețea","server","memorie","procesor","inteligență artificială"));
        m.put("ARCHITECTURE", a("arhitectură","arhitect","clădire","construcție","fațadă","plan","spațiu construit","urbanism"));
        m.put("MILITARY", a("militar","armată","război","bătălie","ofensivă","defensivă","regiment","general","strategie militară","tactică","front","armament"));
        m.put("EDUCATION", a("educație","educațional","școală","universitate","elev","student","profesor","examen","curriculum","învățare","predare","didactic"));
        m.put("LINGUISTICS", a("lingvistică","limbă","fonetică","fonologie","morfologie","sintaxă","semantică","pragmatică","lexic","gramatică","dialect","etimologie"));
        m.put("LITERATURE", a("literatură","literar","roman","poezie","poem","proză","narațiune","personaj","autor","gen literar","metaforă"));
        m.put("PHILOSOPHY", a("filosofie","filozofie","filosofic","ontologie","epistemologie","etică","logică","metafizică","rațiune","cunoaștere","adevăr"));
        m.put("CULTURE", a("cultură","cultural","tradiție","patrimoniu","identitate","obicei","simbol","mit","folclor"));
        m.put("ART", a("artă","artistic","pictură","sculptură","artist","compoziție","stil","estetică","expoziție"));
        m.put("MUSIC", a("muzică","muzical","melodie","ritm","armonie","compozitor","orchestră","instrument","partitură"));
        m.put("MEDIA", a("media","presă","jurnalism","ziar","televiziune","radio","publicație","comunicare de masă","știre"));
        m.put("COMMUNICATION", a("comunicare","mesaj","emitent","receptor","canal","discurs","retorică","argumentare","informație"));
        return Collections.unmodifiableMap(m);
    }

    private static String[] a(String... values) { return values; }
}
