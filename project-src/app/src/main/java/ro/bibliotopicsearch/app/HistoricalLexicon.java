package ro.bibliotopicsearch.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, bundled copy of the supplied Romanian lexicon. No network.
 * Verification covers every category/term, in source order, before installation. */
final class HistoricalLexicon {
    static final String FOLDER="Lexicon istoric";
    static final int CATEGORY_COUNT=448,TERM_COUNT=76100,FINGERPRINT=0x4550401a;
    static final String ID_PREFIX="lexhist-v1-";
    private HistoricalLexicon(){}
    static List<MapProfile> load(Context context){
        ArrayList<MapProfile> maps=new ArrayList<>();Set<String> names=new HashSet<>();
        int total=0,hash=0x811c9dc5;
        try{
            String[] files=context.getAssets().list("historical_lexicon");
            if(files==null||files.length!=7)throw new IOException("Pachetul lexiconului este incomplet.");
            Arrays.sort(files);
            for(String file:files){
                if(!file.matches("part-0[1-7]\\.txt"))throw new IOException("Fișier neașteptat în lexicon.");
                try(BufferedReader input=new BufferedReader(new InputStreamReader(context.getAssets().open("historical_lexicon/"+file),StandardCharsets.UTF_8))){
                    String name=null,line;List<String> terms=new ArrayList<>();
                    while((line=input.readLine())!=null){
                        if(line.startsWith("# ")){
                            if(name!=null){hash=add(maps,names,name,terms,hash);total+=terms.size();}
                            name=line.substring(2).trim();terms=new ArrayList<>();
                        }else if(!line.trim().isEmpty()){
                            if(name==null)throw new IOException("Termeni fără categorie.");
                            for(String value:line.split("\\|",-1)){String term=value.trim();if(!term.isEmpty())terms.add(term);}
                        }
                    }
                    if(name!=null){hash=add(maps,names,name,terms,hash);total+=terms.size();}
                }
            }
        }catch(IOException|RuntimeException e){throw new IllegalStateException("Lexiconul istoric nu a putut fi verificat.",e);}
        if(maps.size()!=CATEGORY_COUNT||total!=TERM_COUNT||hash!=FINGERPRINT)
            throw new IllegalStateException("Lexicon incomplet sau modificat; importul a fost oprit.");
        return maps;
    }
    private static int add(List<MapProfile> maps,Set<String> names,String name,List<String> terms,int hash)throws IOException{
        if(name.isEmpty()||!names.add(name)||terms.isEmpty()||new HashSet<>(terms).size()!=terms.size())
            throw new IOException("Categorie invalidă: "+name);
        int index=maps.size();if(index>=CATEGORY_COUNT)throw new IOException("Prea multe categorii.");
        // Short lines are much cheaper to render/edit than a 375,000-character line.
        StringBuilder raw=new StringBuilder("# ").append(name).append('\n');
        hash=hashTerm(hash,name);
        for(int i=0;i<terms.size();i++){
            String term=terms.get(i);
            if(i>0)raw.append(i%16==0?'\n':'|');
            raw.append(MapTermSyntax.encode(term));hash=hashTerm(hash,term);
        }
        raw.append('\n');
        maps.add(new MapProfile(ID_PREFIX+(index+1),name,FOLDER,raw.toString(),0,HistoricalLexiconColors.color(index)));
        return hash;
    }
    static int hashTerm(int hash,String value){
        for(int i=0;i<value.length();){int cp=value.codePointAt(i);hash=(hash^cp)*16777619;i+=Character.charCount(cp);}
        return (hash^'\n')*16777619;
    }
}
