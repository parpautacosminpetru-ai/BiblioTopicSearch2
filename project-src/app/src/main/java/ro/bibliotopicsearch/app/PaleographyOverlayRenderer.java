package ro.bibliotopicsearch.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.List;

/** Paint inside the current OCR quadrilateral; the camera image is never edited.
 * A line may grow/shrink without shifting neighbouring lines. */
final class PaleographyOverlayRenderer {
    private final Paint paper=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path selection=new android.graphics.Path();
    PaleographyOverlayRenderer(){paper.setColor(Color.rgb(247,244,231));paper.setStyle(Paint.Style.FILL);}

    void draw(Canvas canvas,List<PaleographyLine> lines){draw(canvas,lines,null,null);}

    void draw(Canvas canvas,List<PaleographyLine> lines,PaleographyLine speaking,LiteralTransliterator.RuleMatch cue){
        for(PaleographyLine line:lines){
            boolean active=speaking!=null&&cue!=null&&PaleographySoundPlayer.same(line,speaking);
            if(line.source.equals(line.target)&&!active)continue;
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
                if(active&&cue.start>=0&&cue.end>cue.start&&cue.end<=line.target.length()){
                    selection.reset();line.layout.getSelectionPath(cue.start,cue.end,selection);
                    highlight.setColor(0xffefc65a);highlight.setStyle(Paint.Style.FILL);
                    canvas.drawPath(selection,highlight);
                }
                line.layout.draw(canvas);
            }
            if(active&&(cue.start==cue.end||line.layout==null)){
                // An explicit deletion has no output glyph to highlight.
                highlight.setColor(0xffefc65a);highlight.setStyle(Paint.Style.STROKE);
                highlight.setStrokeWidth(Math.max(.5f,line.height*.08f));
                canvas.drawRect(0,0,line.width,line.height,highlight);
            }
            canvas.restoreToCount(save);
        }
    }
}
