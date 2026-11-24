package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** The user owns the systems. Store normalized table serialization locally;
 * never persist the text being transliterated. */
final class TransliterationStore {
    private static final String FILE="bibliotopicsearch_paleography";
    private static final int MAX_SYSTEMS=32;
    static final class Profile {
        final String id,name;
        final int count;
        Profile(String id,String name,int count){this.id=id;this.name=name;this.count=count;}
        @Override public String toString(){return name+" · "+count+" reguli";}
    }
    static final class Imported {
        final String name;
        final List<LiteralTransliterator.Rule> rules;
        Imported(String name,List<LiteralTransliterator.Rule> rules){this.name=name;this.rules=rules;}
    }

    static Imported parse(String raw,String fallbackName)throws Exception{
        if(raw==null||raw.isEmpty())throw new IllegalArgumentException("Sistem gol.");
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>TransliterationTable.MAX_FILE_BYTES)
            throw new IllegalArgumentException("Sistemul depășește 1 MiB.");
        String candidate=raw.startsWith("\uFEFF")?raw.substring(1):raw;
        String name=fallbackName;
        List<LiteralTransliterator.Rule> rules;
        if(candidate.trim().startsWith("{")){
            JSONObject object=new JSONObject(candidate);
            if(object.has("name")){
                Object v=object.get("name");if(!(v instanceof String))throw new IllegalArgumentException("Numele JSON trebuie să fie text.");
                name=(String)v;
            }
            JSONArray array=object.getJSONArray("rules");
            if(array.length()>LiteralTransliterator.MAX_RULES)throw new IllegalArgumentException("Prea multe reguli.");
            List<LiteralTransliterator.Rule> values=new ArrayList<>();
            for(int i=0;i<array.length();i++){
                JSONObject item=array.getJSONObject(i);
                Object source=item.get("source"),target=item.get("target");
                if(!(source instanceof String)||!(target instanceof String))
                    throw new IllegalArgumentException("Regula "+(i+1)+": source și target trebuie să fie șiruri de caractere.");
                values.add(new LiteralTransliterator.Rule((String)source,(String)target));
            }
            rules=new LiteralTransliterator(values).rules();
        }else rules=TransliterationTable.parse(candidate);
        return new Imported(safeName(name),rules);
    }

    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    private static List<String> ids(SharedPreferences p){
        List<String> ids=new ArrayList<>();
        for(String id:p.getString("ids","").split(","))if(!id.isEmpty()&&!ids.contains(id))ids.add(id);
        return ids;
    }
    private static String key(String id,String field){return "system_"+id+"_"+field;}
    private static String safeName(String name){
        String value=name==null?"":name.trim();
        if(value.isEmpty())value="Sistem paleografic";
        if(value.length()>100)value=value.substring(0,100);
        return value;
    }
    static synchronized List<Profile> list(Context c){
        SharedPreferences p=prefs(c);List<Profile> out=new ArrayList<>();
        for(String id:ids(p))out.add(new Profile(id,p.getString(key(id,"name"),"Sistem"),
                p.getInt(key(id,"count"),0)));
        return Collections.unmodifiableList(out);
    }
    static synchronized String save(Context c,String id,String name,List<LiteralTransliterator.Rule> rules){
        List<LiteralTransliterator.Rule> verified=new LiteralTransliterator(rules).rules();
        String serialized=TransliterationTable.format(verified);
        if(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>TransliterationTable.MAX_FILE_BYTES)
            throw new IllegalArgumentException("Sistemul serializat depășește 1 MiB.");
        SharedPreferences p=prefs(c);List<String> ids=ids(p);
        if(id==null){
            if(ids.size()>=MAX_SYSTEMS)throw new IllegalArgumentException("Sunt permise maximum "+MAX_SYSTEMS+" sisteme.");
            id=UUID.randomUUID().toString();ids.add(id);
        }else if(!ids.contains(id))throw new IllegalArgumentException("Sistemul nu mai există.");
        SharedPreferences.Editor editor=p.edit().putString("ids",android.text.TextUtils.join(",",ids))
                .putString(key(id,"name"),safeName(name)).putString(key(id,"table"),serialized)
                .putInt(key(id,"count"),verified.size()).putString("selected",id);
        if(!editor.commit())throw new IllegalStateException("Sistemul nu a putut fi salvat.");
        return id;
    }
    static synchronized String table(Context c,String id){
        SharedPreferences p=prefs(c);
        if(id==null||!ids(p).contains(id))throw new IllegalArgumentException("Selectează un sistem.");
        String table=p.getString(key(id,"table"),null);
        if(table==null)throw new IllegalStateException("Tabel de reguli indisponibil.");
        return table;
    }
    static LiteralTransliterator engine(Context c,String id){
        return new LiteralTransliterator(TransliterationTable.parseTsv(table(c,id)));
    }
    static synchronized void delete(Context c,String id){
        SharedPreferences p=prefs(c);List<String> ids=ids(p);if(!ids.remove(id))return;
        SharedPreferences.Editor e=p.edit().putString("ids",android.text.TextUtils.join(",",ids))
                .remove(key(id,"name")).remove(key(id,"table")).remove(key(id,"count"));
        if(id.equals(p.getString("selected","")))e.remove("selected");
        if(!e.commit())throw new IllegalStateException("Sistemul nu a putut fi șters.");
    }
    static String selected(Context c){return prefs(c).getString("selected","");}
    static void select(Context c,String id){prefs(c).edit().putString("selected",id==null?"":id).apply();}
}
