package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent readings of the same rectified image must agree on the
 * explicit map/node/term, original text and every word's geometry. */
final class PageEvidence {
    static final class Reading<T>{
        final String key;
        final float[] rectangles;
        final T value;
        Reading(String key,float[] rectangles,T value){
            this.key=key;this.rectangles=rectangles.clone();this.value=value;
        }
    }
    static <T> List<T> agree(List<Reading<T>> base,List<Reading<T>> enhanced){
        Map<String,List<Reading<T>>> index=new HashMap<>();
        for(Reading<T> r:base)index.computeIfAbsent(r.key,k->new ArrayList<>()).add(r);
        Set<Reading<T>> used=new HashSet<>();
        List<T> out=new ArrayList<>();
        for(Reading<T> r:enhanced){
            List<Reading<T>> same=index.get(r.key);
            if(same==null)continue;
            Reading<T> best=null;float score=.55f;
            for(Reading<T> previous:same){
                if(used.contains(previous))continue;
                float overlap=overlap(previous.rectangles,r.rectangles);
                if(overlap>=score){score=overlap;best=previous;}
            }
            if(best!=null){used.add(best);out.add(r.value);}
        }
        return out;
    }
    static float overlap(float[] a,float[] b){
        if(a.length==0||a.length!=b.length||a.length%4!=0)return 0;
        float minimum=1;
        for(int i=0;i<a.length;i+=4){
            float aw=a[i+2]-a[i],ah=a[i+3]-a[i+1],bw=b[i+2]-b[i],bh=b[i+3]-b[i+1];
            if(aw<=0||ah<=0||bw<=0||bh<=0)return 0;
            float iw=Math.max(0,Math.min(a[i+2],b[i+2])-Math.max(a[i],b[i]));
            float ih=Math.max(0,Math.min(a[i+3],b[i+3])-Math.max(a[i+1],b[i+1]));
            float intersection=iw*ih;
            minimum=Math.min(minimum,intersection/(aw*ah+bw*bh-intersection));
        }
        return minimum;
    }
}
