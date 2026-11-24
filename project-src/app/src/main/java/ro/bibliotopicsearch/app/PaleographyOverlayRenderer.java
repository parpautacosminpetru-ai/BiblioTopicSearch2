package ro.bibliotopicsearch.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.List;

/** Paint inside the current OCR quadrilateral; the camera image is never edited.
 * A line may grow/shrink without shifting neighbouring lines. */
final class PaleographyOverlayRenderer {
    private final Paint paper=new Paint(Paint.ANTI_ALIAS_FLAG);
    PaleographyOverlayRenderer(){paper.setColor(Color.rgb(247,244,231));paper.setStyle(Paint.Style.FILL);}

    void draw(Canvas canvas,List<PaleographyLine> lines){
        for(PaleographyLine line:lines){
            int save=canvas.save();
            canvas.concat(line.transform);
            canvas.clipRect(0,0,line.width,line.height);
            canvas.drawRect(0,0,line.width,line.height,paper);
            if(line.layout!=null){
                float pad=Math.min(1.2f,line.height*.06f);
                float w=Math.max(.1f,line.width-2*pad),h=Math.max(.1f,line.height-2*pad);
                float sx=w/Math.max(1,line.layout.getWidth()),sy=h/Math.max(1,line.layout.getHeight());
                // Keep glyph proportions when possible; contract horizontally
                // only if a literal expansion would overflow the source line.
                sx=Math.min(sx,sy);
                float usedWidth=line.layout.getWidth()*sx,usedHeight=line.layout.getHeight()*sy;
                boolean rtl=line.layout.getParagraphDirection(0)==-1;
                canvas.translate(pad+(rtl?w-usedWidth:0f),pad+(h-usedHeight)*.5f);
                canvas.scale(sx,sy);
                line.layout.draw(canvas);
            }
            canvas.restoreToCount(save);
        }
    }
}
