package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable after compilation. Compact exact-key index; avoids a separate
 * HashMap and node for every character of tens of thousands of phrases.
 * Prefix and containment probe only possible prefixes of the OCR token. */
final class TermIndex<T> {
    private final Map<String,List<T>> entries=new HashMap<>();
    private int maxLength;
    void add(String key,T value){
        if(key.isEmpty())return;
        entries.computeIfAbsent(key,k->new ArrayList<>(1)).add(value);
        maxLength=Math.max(maxLength,key.length());
    }
    List<T> exact(String text){
        List<T> values=entries.get(text);
        return values==null?Collections.emptyList():values;
    }
    List<T> prefix(String text){return prefixAt(text,0);}
    List<T> prefixAt(String text,int start){
        if(start<0||start>=text.length()||maxLength==0)return Collections.emptyList();
        List<T> out=new ArrayList<>();
        int limit=Math.min(text.length(),start+maxLength);
        for(int end=start+1;end<=limit;end++){
            List<T> values=entries.get(text.substring(start,end));
            if(values!=null)out.addAll(values);
        }
        return out;
    }
    List<T> contains(String text){
        Set<T> out=new LinkedHashSet<>();
        for(int start=0;start<text.length();start++)out.addAll(prefixAt(text,start));
        return new ArrayList<>(out);
    }
}
