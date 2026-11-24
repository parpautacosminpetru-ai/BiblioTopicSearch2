package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import java.util.Collections;
import java.util.List;

/** Passive overlay: focus, zoom and gestures still go to the camera. */
final class PaleographyOverlayView extends View {
    private final PaleographyOverlayRenderer renderer=new PaleographyOverlayRenderer();
    private List<PaleographyLine> lines=Collections.emptyList();
    PaleographyOverlayView(Context context){super(context);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setLines(List<PaleographyLine> value){lines=value==null?Collections.emptyList():value;invalidate();}
    void clear(){setLines(Collections.emptyList());}
    @Override protected void onDraw(Canvas c){super.onDraw(c);renderer.draw(c,lines);}
}
