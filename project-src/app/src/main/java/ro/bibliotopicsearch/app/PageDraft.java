package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Single-use handoff; geometry and OCR are bound to the captured bitmap. */
final class PageDraft {
    static final class Analysis{
        final Bitmap bitmap;final String text;final List<LocatedMark> marks;
        Analysis(Bitmap bitmap,String text,List<LocatedMark> marks){this.bitmap=bitmap;this.text=text;this.marks=Collections.unmodifiableList(new ArrayList<>(marks));}
    }
    private static final AtomicReference<Analysis> SLOT=new AtomicReference<>();
    private PageDraft(){}
    static void offer(Bitmap bitmap){offerAnalysis(bitmap,"",Collections.emptyList());}
    static void offerAnalysis(Bitmap bitmap,String text,List<LocatedMark> marks){
        Analysis old=SLOT.getAndSet(bitmap==null?null:new Analysis(bitmap,text,marks));
        if(old!=null&&old.bitmap!=bitmap&&!old.bitmap.isRecycled())old.bitmap.recycle();
    }
    static Analysis takeAnalysis(){return SLOT.getAndSet(null);}
    static Bitmap take(){Analysis item=takeAnalysis();return item==null?null:item.bitmap;}
}
