package ro.bibliotopicsearch.app;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.graphics.Color;
import android.graphics.Paint;

/** A line-local literal replacement and its exact current OCR quadrilateral.
 * Layout is built off the UI thread and never changes after publication. */
final class PaleographyLine {
    final String source,target;
    final RectF bounds;
    final float[] corners;
    final float confidence,width,height;
    final StaticLayout layout;
    final Matrix transform;
    final java.util.List<LiteralTransliterator.RuleMatch> matches;

    PaleographyLine(String source,String target,RectF bounds,float[] corners,float confidence,
                    java.util.List<LiteralTransliterator.RuleMatch> matches){
        this.source=source;this.target=target;this.bounds=new RectF(bounds);this.corners=corners.clone();
        this.matches=matches;
        this.confidence=confidence;width=PaleographyGeometry.width(corners);height=PaleographyGeometry.height(corners);
        transform=new Matrix();
        if(!transform.setPolyToPoly(new float[]{0,0,width,0,width,height,0,height},0,this.corners,0,4))
            throw new IllegalArgumentException("Geometrie OCR invalidă");
        if(target.isEmpty()){layout=null;return;}
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(Color.rgb(25,29,30));paint.setTextSize(40f);
        float desired=Layout.getDesiredWidth(target,paint);
        if(Float.isNaN(desired)||Float.isInfinite(desired)||desired>180000f)throw new IllegalArgumentException("Rând prea lung");
        int textWidth=Math.max(1,(int)Math.ceil(desired)+2);
        layout=StaticLayout.Builder.obtain(target,0,target.length(),paint,textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
    }

    String key(){return source.length()+":"+source+target;}
    float[] rectangle(){return new float[]{bounds.left,bounds.top,bounds.right,bounds.bottom};}
}
