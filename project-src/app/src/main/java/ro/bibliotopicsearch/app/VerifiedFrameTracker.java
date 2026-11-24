package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Consecutive readings of the same original evidence in the same map and
 * same spatial occurrence. Never carries a missing hit into a new frame. */
final class VerifiedFrameTracker<T> {
    private static final float CELL = 48f;
    private static final long MAX_GAP_MS = 1500L;

    static final class Reading<T> {
        final String key;
        final float x,y,width,height;
        final int required;
        final boolean reconstructed;
        final T value;
        Reading(String key,float x,float y,float width,float height,int required,boolean reconstructed,T value) {
            this.key=key;this.x=x;this.y=y;this.width=width;this.height=height;
            this.required=required;this.reconstructed=reconstructed;this.value=value;
        }
    }
    private static final class Track<T> {
        final Reading<T> reading;
        final int streak, required;
        Track(Reading<T> reading,int streak,int required) {
            this.reading=reading;this.streak=streak;this.required=required;
        }
    }
    private List<Track<T>> last = new ArrayList<>();
    private long lastTime = Long.MIN_VALUE;

    void clear() { last.clear(); lastTime=Long.MIN_VALUE; }

    private int cell(float x) { return (int)Math.floor(x/CELL); }
    private String bucket(String key,int x,int y) { return key+"\u0001"+x+":"+y; }

    List<T> update(List<Reading<T>> current,PageContinuity.State state,long now) {
        List<T> out=new ArrayList<>();
        // A duplicate/out-of-order callback cannot count as a second reading.
        if (now<=lastTime) return out;
        boolean comparable=lastTime!=Long.MIN_VALUE && now-lastTime<=MAX_GAP_MS &&
                state!=PageContinuity.State.CHANGED && state!=PageContinuity.State.MOVED;
        Map<String,List<Track<T>>> buckets=new HashMap<>();
        if (comparable) for (Track<T> t:last) {
            Reading<T> old=t.reading;
            buckets.computeIfAbsent(bucket(old.key,cell(old.x),cell(old.y)),k->new ArrayList<>()).add(t);
        }
        List<Track<T>> next=new ArrayList<>();
        Set<Track<T>> used=new HashSet<>();
        for (Reading<T> fresh:current) {
            if (fresh.required<=0 || fresh.width<=0 || fresh.height<=0) continue;
            Track<T> nearest=null;
            float best=Float.MAX_VALUE;
            float limit=Math.max(4f,Math.min(18f,fresh.height*0.45f));
            int cx=cell(fresh.x),cy=cell(fresh.y);
            boolean allowComparison=!fresh.reconstructed || state==PageContinuity.State.STABLE;
            if (allowComparison) for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++){
                List<Track<T>> candidates=buckets.get(bucket(fresh.key,cx+dx,cy+dy));
                if(candidates==null)continue;
                for(Track<T> candidate:candidates){
                    if(used.contains(candidate))continue;
                    Reading<T> old=candidate.reading;
                    float wr=fresh.width/old.width,hr=fresh.height/old.height;
                    if(wr<0.72f||wr>1.38f||hr<0.72f||hr>1.38f)continue;
                    float distance=(float)Math.hypot(fresh.x-old.x,fresh.y-old.y);
                    if(distance<=limit&&distance<best){best=distance;nearest=candidate;}
                }
            }
            if(nearest!=null) used.add(nearest);
            int required=fresh.required;
            if(nearest!=null) required=Math.max(required,nearest.required);
            int streak=nearest==null?1:Math.min(3,nearest.streak+1);
            next.add(new Track<>(fresh,streak,required));
            int threshold=Math.max(required,state==PageContinuity.State.UNKNOWN?3:2);
            if(streak>=threshold)out.add(fresh.value);
        }
        last=next;lastTime=now;
        return out;
    }
}
