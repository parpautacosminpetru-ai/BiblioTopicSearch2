package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Candidate segmentation and human-only confirmation. */
final class GlyphStudy {
    static final class Region{
        final RectF box;final int line;final List<GlyphShape.Candidate> candidates;final GlyphShape shape;
        String confirmed;
        Region(RectF box,int line,GlyphShape shape,List<GlyphShape.Candidate> candidates){this.box=box;this.line=line;this.shape=shape;this.candidates=candidates;}
    }
    final String system;final List<Region> regions;final boolean truncated;
    private GlyphStudy(String system,List<Region> regions,boolean truncated){this.system=system;this.regions=regions;this.truncated=truncated;}
    static GlyphStudy analyse(Bitmap source,RectF zone,int direction,String system,List<GlyphShape.Example> examples){
        int l=Math.max(0,(int)Math.floor(zone.left)),t=Math.max(0,(int)Math.floor(zone.top));
        int r=Math.min(source.getWidth(),(int)Math.ceil(zone.right)),b=Math.min(source.getHeight(),(int)Math.ceil(zone.bottom));
        if(r-l<8||b-t<8)throw new IllegalArgumentException("Zona de analiză este prea mică.");
        Bitmap crop=Bitmap.createBitmap(source,l,t,r-l,b-t);
        double scale=Math.min(1,1600d/Math.max(crop.getWidth(),crop.getHeight()));
        Bitmap image=Bitmap.createScaledBitmap(crop,Math.max(8,(int)Math.round(crop.getWidth()*scale)),Math.max(8,(int)Math.round(crop.getHeight()*scale)),true);
        try{
            int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
            for(int i=0;i<pixels.length;i++){int p=pixels[i];pixels[i]=(77*((p>>>16)&255)+150*((p>>>8)&255)+29*(p&255))>>>8;}
            GlyphSegmenter.Result split=GlyphSegmenter.segment(pixels,w,h,direction);List<Region> found=new ArrayList<>();
            float sx=(r-l)/(float)w,sy=(b-t)/(float)h;
            for(GlyphSegmenter.Box box:split.boxes){
                if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Analiză întreruptă.");
                RectF mapped=new RectF(l+box.left*sx,t+box.top*sy,l+box.right*sx,t+box.bottom*sy);
                GlyphShape shape=null;List<GlyphShape.Candidate> ranked=Collections.emptyList();
                // Preserve the exact region while adding only a small border for shape matching.
                int x0=Math.max(l,(int)Math.floor(mapped.left)-2),y0=Math.max(t,(int)Math.floor(mapped.top)-2);
                int x1=Math.min(r,(int)Math.ceil(mapped.right)+2),y1=Math.min(b,(int)Math.ceil(mapped.bottom)+2);
                if(x1-x0>=2&&y1-y0>=2){
                    Bitmap glyph=Bitmap.createBitmap(source,x0,y0,x1-x0,y1-y0);
                    try{shape=GlyphLibrary.shape(glyph);List<GlyphShape.Candidate> all=GlyphShape.rank(shape,examples);
                        ranked=new ArrayList<>(all.subList(0,Math.min(5,all.size())));
                    }catch(IllegalArgumentException ignored){}finally{if(glyph!=source)glyph.recycle();}
                }
                found.add(new Region(mapped,box.line,shape,ranked));
            }
            return new GlyphStudy(system,found,split.truncated);
        }finally{if(image!=crop&&image!=source)image.recycle();if(crop!=source)crop.recycle();}
    }
    List<LocatedMark> marks(){
        List<LocatedMark> marks=new ArrayList<>();int i=0;
        for(Region r:regions){
            String tag=r.confirmed!=null?r.confirmed:GlyphShape.strong(r.candidates)?"? "+r.candidates.get(0).label:"?";
            marks.add(new LocatedMark(r.box,(++i)+" · "+tag,"",r.confirmed!=null));
        }
        return marks;
    }
    int confirmedCount(){int n=0;for(Region r:regions)if(r.confirmed!=null)n++;return n;}
    String transcription(){
        StringBuilder out=new StringBuilder();int previous=-1;
        for(Region region:regions){if(previous>=0&&region.line!=previous)out.append('\n');
            out.append(region.confirmed==null?"[?]":region.confirmed);previous=region.line;
        }
        if(truncated)out.append("\n[zone suplimentare neanalizate]");
        return out.toString();
    }
}
