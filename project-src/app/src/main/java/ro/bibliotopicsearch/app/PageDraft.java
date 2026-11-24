package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import java.util.concurrent.atomic.AtomicReference;

/** Single-use in-process handoff; the camera image is not written to disk. */
final class PageDraft {
    private static final AtomicReference<Bitmap> SLOT=new AtomicReference<>();
    private PageDraft(){}
    static void offer(Bitmap bitmap){
        Bitmap old=SLOT.getAndSet(bitmap);
        if(old!=null&&old!=bitmap&&!old.isRecycled())old.recycle();
    }
    static Bitmap take(){return SLOT.getAndSet(null);}
}
