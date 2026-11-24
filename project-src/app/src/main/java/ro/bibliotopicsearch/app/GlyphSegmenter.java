package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative projection segmentation. Boxes are proposals, not proven
 * graphemes. Connected writing, damage and multicolumn pages require review.
 * The algorithm does not know or guess the script. */
final class GlyphSegmenter {
    static final int LTR=0,RTL=1,VERTICAL_RTL=2,VERTICAL_LTR=3,MAX_REGIONS=400;
    static final class Box {
        final int left,top,right,bottom,line;
        Box(int left,int top,int right,int bottom,int line){this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.line=line;}
    }
    static final class Result {
        final List<Box> boxes;final boolean truncated;
        Result(List<Box> boxes,boolean truncated){this.boxes=Collections.unmodifiableList(boxes);this.truncated=truncated;}
    }
    private static List<int[]> runs(boolean[] active,int gap){
        List<int[]> out=new ArrayList<>();int first=-1,last=-1;
        for(int i=0;i<active.length;i++)if(active[i]){
            if(first<0){first=last=i;}else if(i-last-1<=gap){last=i;}else{out.add(new int[]{first,last+1});first=last=i;}
        }
        if(first>=0)out.add(new int[]{first,last+1});return out;
    }
    static Result segment(int[] gray,int w,int h,int direction){
        if(w<8||h<8||w>2000||h>2000||gray==null||gray.length!=w*h||direction<0||direction>3)
            throw new IllegalArgumentException("Imagine sau direcție invalidă.");
        int[] histogram=new int[256];long sum=0,border=0;int bn=0,min=255,max=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=gray[y*w+x];if(g<0||g>255)throw new IllegalArgumentException("Imagine invalidă.");
            histogram[g]++;sum+=g;min=Math.min(min,g);max=Math.max(max,g);
            if(x==0||y==0||x==w-1||y==h-1){border+=g;bn++;}
        }
        List<Box> boxes=new ArrayList<>();
        if(max-min<25)return new Result(boxes,false);
        long part=0;int count=0,threshold=min;double best=-1;
        for(int i=min;i<max;i++){
            count+=histogram[i];part+=(long)i*histogram[i];if(count==0||count==gray.length)continue;
            double a=(double)part/count,b=(double)(sum-part)/(gray.length-count),d=a-b;
            double score=(double)count*(gray.length-count)*d*d;if(score>best){best=score;threshold=i;}
        }
        boolean dark=(double)border/bn>threshold,vertical=direction>=2;
        boolean[] ink=new boolean[gray.length];int total=0;
        for(int i=0;i<gray.length;i++){ink[i]=dark?gray[i]<=threshold:gray[i]>threshold;if(ink[i])total++;}
        if(total<4||total>gray.length*.75)return new Result(boxes,false);
        int major=vertical?w:h,minor=vertical?h:w;boolean[] bands=new boolean[major];
        for(int a=0;a<major;a++)for(int b=0;b<minor;b++)if(ink[vertical?b*w+a:a*w+b]){bands[a]=true;break;}
        List<int[]> lines=runs(bands,Math.max(1,major/1000));
        if(direction==VERTICAL_RTL)Collections.reverse(lines);
        int line=0;
        for(int[] band:lines){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Analiză întreruptă.");
            boolean[] columns=new boolean[minor];
            for(int b=0;b<minor;b++)for(int a=band[0];a<band[1];a++)if(ink[vertical?b*w+a:a*w+b]){columns[b]=true;break;}
            List<int[]> spans=runs(columns,Math.max(0,(band[1]-band[0])/80));
            if(direction==RTL)Collections.reverse(spans);
            for(int[] span:spans){
                int l=w,t=h,r=-1,b=-1,n=0;
                for(int a=band[0];a<band[1];a++)for(int q=span[0];q<span[1];q++){
                    int x=vertical?a:q,y=vertical?q:a;
                    if(ink[y*w+x]){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);n++;}
                }
                // Do not silently erase dots or isolated marks with a minimum height.
                if(n<2)continue;
                if(boxes.size()>=MAX_REGIONS)return new Result(boxes,true);
                boxes.add(new Box(l,t,r+1,b+1,line));
            }
            line++;
        }
        return new Result(boxes,false);
    }
}
