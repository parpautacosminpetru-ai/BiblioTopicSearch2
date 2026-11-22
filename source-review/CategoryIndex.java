package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dicționar lexical offline editabil. Categoriile sunt etichete euristice,
 * nu interpretări automatizate ale unui text istoric.
 */
public final class CategoryIndex {
    public static final String ALL="__toate_categoriile__";
    public static final String ANI="ani", LOCURI="locuri", PERSOANE="persoane";
    public static final String INSTITUTII="institutii", EVENIMENTE="evenimente";
    public static final String CONCEPTE="concepte", ALTE="alte";
    private static final String FILE="bibliotopicsearch_category_index";
    private static final String TEXT_KEY="term_sets";
    public static final class Category {
        public final String id,label;
        public final int color;
        Category(String id,String label,int color) {
            this.id=id;this.label=label;this.color=color;
        }
    }
    private static final List<Category> CATEGORIES=Collections.unmodifiableList(Arrays.asList(
            new Category(ANI,"Ani",Color.rgb(225,172,68)),
            new Category(LOCURI,"Locuri",Color.rgb(75,165,228)),
            new Category(PERSOANE,"Persoane",Color.rgb(186,135,222)),
            new Category(INSTITUTII,"Instituții",Color.rgb(90,201,181)),
            new Category(EVENIMENTE,"Evenimente",Color.rgb(237,142,93)),
            new Category(CONCEPTE,"Concepte",Color.rgb(157,195,107)),
            new Category(ALTE,"Altele",Color.rgb(161,181,198))
    ));
    public static final String DEFAULT_TEXT=
            "# Format: categorie | termen | sinonim sau variantă\n"+
            "# Poți adăuga termeni în mai multe categorii dacă este necesar.\n"+
            "PERSOANE | Carol I | Carol întâi | Carol I al României | Alexandru Ioan Cuza | Mihail Kogălniceanu | Ion C. Brătianu | I. C. Brătianu | P. P. Carp | Petre P. Carp | Take Ionescu | Dimitrie Brătianu | Titu Maiorescu | Lascăr Catargiu | Nicolae Iorga | Nicolae Titulescu | Ferdinand I\n"+
            "LOCURI | România | București | Iași | Ploiești | Transilvania | Basarabia | Bucovina | Dobrogea | Cadrilater | Silistra | Balcani | Serbia | Bulgaria | Grecia | Constantinopol | Istanbul | Viena | Berlin | Paris | Imperiul Rus | Imperiul Otoman | Austro-Ungaria | Muntenia | Moldova | Dunărea | Plevna | Grivița | Smârdan | Macedonia\n"+
            "INSTITUTII | Parlament | Senat | Camera Deputaților | Adunarea Deputaților | Guvern | Consiliul de Miniștri | Ministerul Afacerilor Străine | Curtea de Casație | Partidul Conservator | Partidul Național Liberal | PNL | Biserica Ortodoxă Română | Comisia Europeană a Dunării | Corpurile legiuitoare | Coroana | Monarhie | Ministerul de Externe\n"+
            "EVENIMENTE | Războiul de independență | războiul din 1877 | Congresul de la Berlin | Pacea de la Berlin | Pacea de la București | războaiele balcanice | răscoala din 1907 | răscoala din 1888 | Republica de la Ploiești | Primul Război Mondial | Marea Unire | proclamarea Regatului | Conferința de pace | criza orientală\n"+
            "CONCEPTE | independență | suveranitate | suzeranitate | constituție | constituționalism | drept de vot | vot universal | vot cenzitar | guvernare | parlamentarism | dizolvare | responsabilitate ministerială | liberalism | conservatorism | naționalism | emancipare | cetățenie | discriminare | reformă agrară | proprietate funciară | modernizare | politică externă | alianță | neutralitate | autonomie | autocefalie | reprezentare politică\n"+
            "ANI | 1859 | 1866 | 1870 | 1871 | 1877 | 1878 | 1879 | 1881 | 1883 | 1888 | 1907 | 1912 | 1913 | 1914 | 1916 | 1918\n";

    private final Map<String,Set<String>> byTerm=new LinkedHashMap<>();
    private final boolean strip;
    private final String rawText;

    private CategoryIndex(String text,boolean strip) {
        this.strip=strip;
        this.rawText=text;
        int total=0;
        for(String line:text.split("\\r?\\n")) {
            String trimmed=line.trim();
            if(trimmed.isEmpty()||trimmed.startsWith("#"))continue;
            String[] parts=trimmed.split("\\|");
            if(parts.length<2)continue;
            String category=parseId(parts[0]);
            if(category==null||ALTE.equals(category))continue;
            for(int j=1;j<parts.length && total<3000;j++) {
                String key=normalize(parts[j],strip);
                if(key.isEmpty())continue;
                byTerm.computeIfAbsent(key,k->new LinkedHashSet<>()).add(category);
                total++;
            }
        }
    }

    public static CategoryIndex load(Context context,boolean strip) {
        SharedPreferences preferences=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        String custom=preferences.getString(TEXT_KEY,DEFAULT_TEXT);
        if(custom==null||custom.trim().isEmpty())custom=DEFAULT_TEXT;
        return new CategoryIndex(custom,strip);
    }
    public static void save(Context context,String text) {
        if(text==null||text.length()>80000)throw new IllegalArgumentException(
                "Indexul nu poate depăși 80.000 caractere.");
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
                .putString(TEXT_KEY,text).apply();
    }
    public String editableText() {return rawText;}
    public Set<String> extraTerms() {return Collections.unmodifiableSet(byTerm.keySet());}
    public static List<Category> categories() {return CATEGORIES;}
    public static Category category(String id) {
        for(Category c:CATEGORIES)if(c.id.equals(id))return c;
        return CATEGORIES.get(CATEGORIES.size()-1);
    }
    public static String label(String id) {
        if(ALL.equals(id))return "Toate tipurile";
        return category(id).label;
    }
    public static boolean known(String id) {
        if(ALL.equals(id))return true;
        for(Category c:CATEGORIES)if(c.id.equals(id))return true;
        return false;
    }
    public Set<String> classify(String raw,Collection<String> nodePaths) {
        String key=normalize(raw,strip);
        Set<String> exact=byTerm.get(key);
        if(exact!=null&&!exact.isEmpty())return new LinkedHashSet<>(exact);
        LinkedHashSet<String> found=new LinkedHashSet<>();
        if(key.matches("(?:1[5-9]\\d{2}|20\\d{2})(?:[\\-–—/](?:1[5-9]\\d{2}|20\\d{2}))?"))found.add(ANI);
        if(found.isEmpty() && nodePaths!=null)for(String node:nodePaths) {
            String n=normalize(node,true);
            if(n.contains("personalitat")||n.contains("personaj")||n.contains("actor")
                ||n.contains("lider")||n.contains("decident"))found.add(PERSOANE);
            else if(n.contains("teritor")||n.contains("oras")||n.contains("regiun")||
                n.contains("capital")||n.contains("geografi"))found.add(LOCURI);
            else if(n.contains("institut")||n.contains("minister")||n.contains("parlament"))found.add(INSTITUTII);
            else if(n.contains("razbo")||n.contains("eveniment")||n.contains("criz")||n.contains("revol"))found.add(EVENIMENTE);
            else if(n.contains("concept")||n.contains("ideolog")||n.contains("princip")||n.contains("proces"))found.add(CONCEPTE);
        }
        if(found.isEmpty())found.add(ALTE);
        return found;
    }
    public static String normalize(String raw,boolean strip) {
        if(raw==null)return "";
        String value=raw.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
        if(strip)value=Normalizer.normalize(value,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
        return value;
    }
    private static String parseId(String input) {
        String key=normalize(input,true).replace("_","").replace(" ","");
        if(key.equals("ani")||key.equals("an")||key.equals("date"))return ANI;
        if(key.equals("locuri")||key.equals("geografie")||key.equals("teritorii"))return LOCURI;
        if(key.equals("persoane")||key.equals("personalitati")||key.equals("personaje"))return PERSOANE;
        if(key.equals("institutii")||key.equals("organizatii"))return INSTITUTII;
        if(key.equals("evenimente")||key.equals("procese"))return EVENIMENTE;
        if(key.equals("concepte")||key.equals("termeni"))return CONCEPTE;
        return null;
    }
}