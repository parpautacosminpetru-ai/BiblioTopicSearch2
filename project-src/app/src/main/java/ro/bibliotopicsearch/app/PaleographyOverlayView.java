package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import java.util.Collections;
import java.util.List;

/** Passive live and FIT_CENTER still-image overlay. */
final class PaleographyOverlayView extends View {
    private final PaleographyOverlayRenderer renderer=new PaleographyOverlayRenderer();
    private List<PaleographyLine> lines=Collections.emptyList();
    private PaleographyLine speaking;
    private LiteralTransliterator.RuleMatch cue;
    private int imageWidth,imageHeight;
    PaleographyOverlayView(Context context){super(context);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setLines(List<PaleographyLine> value){lines=value==null?Collections.emptyList():value;invalidate();}
    void setSpeaking(PaleographyLine line,LiteralTransliterator.RuleMatch current){speaking=line;cue=current;invalidate();}
    void setImageSize(int width,int height){imageWidth=width;imageHeight=height;invalidate();}
    void clear(){speaking=null;cue=null;setLines(Collections.emptyList());}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(imageWidth>0&&imageHeight>0){
            float scale=Math.min(getWidth()/(float)imageWidth,getHeight()/(float)imageHeight);
            int save=c.save();
            c.translate((getWidth()-imageWidth*scale)*.5f,(getHeight()-imageHeight*scale)*.5f);
            c.scale(scale,scale);c.clipRect(0,0,imageWidth,imageHeight);
            renderer.draw(c,lines,speaking,cue);c.restoreToCount(save);
        }else renderer.draw(c,lines,speaking,cue);
    }
}
