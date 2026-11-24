package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Verification runs off the UI thread. No retained hits or stale geometry. */
final class LiveFrameStabilizer {
    private final VerifiedFrameTracker<MatchHit> tracker = new VerifiedFrameTracker<>();
    private PageContinuity.Sample previous;
    private long epoch=-1, viewport=-1;

    List<MatchHit> update(long generation,long viewportRevision,Text text,List<MatchHit> hits,long timestamp,int precision) {
        if(epoch!=generation||viewport!=viewportRevision){
            tracker.clear();previous=null;epoch=generation;viewport=viewportRevision;
        }
        PageContinuity.Sample sample=new PageContinuity.Sample();
        if(text!=null) for(Text.TextBlock block:text.getTextBlocks()) for(Text.Line line:block.getLines())
            for(Text.Element element:line.getElements()){
                Rect box=element.getBoundingBox();
                if(box==null||box.height()<=0)continue;
                String value=TopicMatcher.normalize(element.getText(),false);
                if(!value.isEmpty()&&value.indexOf(' ')<0)
                    sample.add(value,box.exactCenterX(),box.exactCenterY(),box.height());
            }
        PageContinuity.State state=PageContinuity.compare(previous,sample);
        previous=sample;
        List<VerifiedFrameTracker.Reading<MatchHit>> readings=new ArrayList<>(hits.size());
        for(MatchHit raw:hits){
            MatchHit hit=raw.withTimestamp(timestamp);
            boolean reconstructed=hit.kind!=MatchHit.Kind.LITERAL;
            int required=OcrConfidence.requiredFrames(hit.confidence,reconstructed);
            // Use original text, not a corrected string or a map title.
            String key=hit.identityKey()+"\u0001"+hit.originalText+"\u0001"+hit.kind;
            readings.add(new VerifiedFrameTracker.Reading<>(key,hit.box.centerX(),hit.box.centerY(),
                    hit.box.width(),Math.max(1f,hit.box.height()),required,reconstructed,hit));
        }
        return tracker.update(readings,state,timestamp);
    }
}
