package ro.bibliotopicsearch.app;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uses the literal engine with the original per-line OCR text. Rules crossing
 * line boundaries remain available in the full-text paleography workspace. */
final class PaleographyProjection {
    private static final int MAX_LINES=180, MAX_LINE_INPUT=4096,MAX_LINE_OUTPUT=4096;
    private final VerifiedFrameTracker<PaleographyLine> tracker=new VerifiedFrameTracker<>();
    private PageContinuity.Sample previous;
    private long epoch=Long.MIN_VALUE,viewport=Long.MIN_VALUE;

    static List<PaleographyLine> lines(Text text,LiteralTransliterator engine){
        List<PaleographyLine> out=new ArrayList<>();
        if(text==null||engine==null)return out;
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
            if(Thread.currentThread().isInterrupted())return Collections.emptyList();
            if(out.size()>=MAX_LINES)return out;
            String raw=line.getText();
            if(raw==null||raw.isEmpty()||raw.length()>MAX_LINE_INPUT)continue;
            Rect box=line.getBoundingBox();Point[] points=line.getCornerPoints();
            if(box==null||points==null||points.length!=4)continue;
            float[] q=new float[8];boolean valid=true;
            for(int i=0;i<4;i++){if(points[i]==null){valid=false;break;}q[2*i]=points[i].x;q[2*i+1]=points[i].y;}
            if(!valid||!PaleographyGeometry.valid(q,box.left,box.top,box.right,box.bottom))continue;
            OcrConfidence confidence=new OcrConfidence();
            for(Text.Element e:line.getElements())confidence.add(e.getConfidence());
            float score=confidence.value();
            if(OcrConfidence.requiredFrames(score,false)==0)continue;
            try{
                LiteralTransliterator.Result result=engine.apply(raw);
                if(result.substitutions==0||raw.equals(result.text)||result.text.length()>MAX_LINE_OUTPUT)continue;
                out.add(new PaleographyLine(raw,result.text,new RectF(box),q,score));
            }catch(IllegalArgumentException invalid){/* keep original image rather than guess */}
        }
        return out;
    }

    List<PaleographyLine> update(long generation,long revision,Text text,LiteralTransliterator engine,long timestamp){
        if(epoch!=generation||viewport!=revision){
            tracker.clear();previous=null;epoch=generation;viewport=revision;
        }
        PageContinuity.Sample sample=new PageContinuity.Sample();
        if(text!=null)for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines())
            for(Text.Element e:line.getElements()){
                Rect box=e.getBoundingBox();
                if(box==null||box.height()<=0)continue;
                String raw=e.getText();
                if(raw!=null&&!raw.isEmpty())sample.add(raw,box.exactCenterX(),box.exactCenterY(),box.height());
            }
        PageContinuity.State state=PageContinuity.compare(previous,sample);
        previous=sample;
        List<VerifiedFrameTracker.Reading<PaleographyLine>> readings=new ArrayList<>();
        for(PaleographyLine line:lines(text,engine)){
            int required=OcrConfidence.requiredFrames(line.confidence,false);
            readings.add(new VerifiedFrameTracker.Reading<>(line.key(),line.bounds.centerX(),line.bounds.centerY(),
                    line.bounds.width(),line.height,required,false,line));
        }
        return tracker.update(readings,state,timestamp);
    }

    static List<PaleographyLine> agree(Text before,Text after,LiteralTransliterator engine){
        return PageEvidence.agree(evidence(lines(before,engine)),evidence(lines(after,engine)));
    }
    private static List<PageEvidence.Reading<PaleographyLine>> evidence(List<PaleographyLine> lines){
        List<PageEvidence.Reading<PaleographyLine>> out=new ArrayList<>();
        for(PaleographyLine line:lines)out.add(new PageEvidence.Reading<>(line.key(),line.rectangle(),line));
        return out;
    }
}
