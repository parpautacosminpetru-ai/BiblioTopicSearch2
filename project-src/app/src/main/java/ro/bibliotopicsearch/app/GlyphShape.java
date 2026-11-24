package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Script-agnostic silhouette comparison, not a language model. */
final class GlyphShape {
    static final int GRID=24, CELLS=GRID*GRID;
    final byte[] cells;
    final double aspect;
    GlyphShape(byte[] cells,double aspect){
        if(cells==null||cells.length!=CELLS||(Double.isNaN(aspect)||Double.isInfinite(aspect))||aspect<=0||aspect>512||aspect<1d/512)
            throw new IllegalArgumentException("Șablon vizual invalid.");
        this.cells=cells.clone();this.aspect=aspect;
    }
    static GlyphShape extract(int[] gray,int w,int h){
        if(w<8||h<8||w>512||h>512||gray==null||gray.length!=w*h)throw new IllegalArgumentException("Selecția semnului este invalidă.");
        int[] histogram=new int[256];int min=255,max=0;long sum=0,border=0;int bn=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=gray[y*w+x];if(g<0||g>255)throw new IllegalArgumentException("Imagine invalidă.");
            histogram[g]++;sum+=g;min=Math.min(min,g);max=Math.max(max,g);
            if(y==0||x==0||y==h-1||x==w-1){border+=g;bn++;}
        }
        if(max-min<25)throw new IllegalArgumentException("Contrast insuficient; selectează un semn clar cu margine.");
        long part=0;int count=0,threshold=min;double best=-1;
        for(int i=min;i<max;i++){
            count+=histogram[i];part+=(long)i*histogram[i];if(count==0||count==gray.length)continue;
            double a=(double)part/count,b=(double)(sum-part)/(gray.length-count),d=a-b;
            double v=(double)count*(gray.length-count)*d*d;if(v>best){best=v;threshold=i;}
        }
        boolean dark=(double)border/bn>threshold;boolean[] ink=new boolean[gray.length];
        int left=w,top=h,right=-1,bottom=-1,n=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=gray[y*w+x];boolean yes=dark?g<=threshold:g>threshold;ink[y*w+x]=yes;
            if(yes){n++;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
        }
        if(n<8||n>gray.length*.85||right-left<1||bottom-top<1)
            throw new IllegalArgumentException("Semn neizolat sau selecție fără detalii suficiente.");
        int bw=right-left+1,bh=bottom-top+1;byte[] cells=new byte[CELLS];
        // Oversampling avoids losing thin strokes during normalization.
        for(int gy=0;gy<GRID;gy++)for(int gx=0;gx<GRID;gx++){
            int hits=0;
            for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++){
                int x=left+Math.min(bw-1,(int)((gx+(sx+.5)/4)*bw/GRID));
                int y=top+Math.min(bh-1,(int)((gy+(sy+.5)/4)*bh/GRID));
                if(ink[y*w+x])hits++;
            }
            cells[gy*GRID+gx]=(byte)Math.round(hits*255f/16);
        }
        return new GlyphShape(cells,(double)bw/bh);
    }
    double distance(GlyphShape other){
        double diff=0,union=0;for(int i=0;i<CELLS;i++){int a=cells[i]&255,b=other.cells[i]&255;diff+=Math.abs(a-b);union+=Math.max(a,b);}
        return (union==0?1:diff/union)+.18*Math.min(2,Math.abs(Math.log(aspect/other.aspect)));
    }
    static final class Example {
        final String label;final GlyphShape shape;
        Example(String label,GlyphShape shape){this.label=label;this.shape=shape;}
    }
    static final class Candidate {
        final String label;final double distance;
        Candidate(String label,double distance){this.label=label;this.distance=distance;}
    }
    static List<Candidate> rank(GlyphShape query,List<Example> examples){
        Map<String,Double> distances=new LinkedHashMap<>();
        for(Example e:examples){double d=query.distance(e.shape);Double old=distances.get(e.label);if(old==null||d<old)distances.put(e.label,d);}
        List<Candidate> result=new ArrayList<>();
        for(Map.Entry<String,Double> e:distances.entrySet())result.add(new Candidate(e.getKey(),e.getValue()));
        java.util.Collections.sort(result,(a,b)->Double.compare(a.distance,b.distance));return result;
    }
    static boolean strong(List<Candidate> ranked){
        if(ranked.isEmpty()||ranked.get(0).distance>.20)return false;
        return ranked.size()==1||ranked.get(1).distance-ranked.get(0).distance>=.08;
    }
}
