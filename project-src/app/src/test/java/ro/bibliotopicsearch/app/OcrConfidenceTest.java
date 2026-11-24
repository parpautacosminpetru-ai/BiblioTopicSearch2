package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrConfidenceTest {
    @Test public void weakWordRejectsEntirePhrase() {
        OcrConfidence c=new OcrConfidence();c.add(.97f);c.add(.60f);c.add(0f);
        assertEquals(0,OcrConfidence.requiredFrames(c.value(),false));
    }
    @Test public void unknownAndModerateNeedExtraReading() {
        OcrConfidence c=new OcrConfidence();c.add(.94f);c.add(0f);
        assertEquals(3,OcrConfidence.requiredFrames(c.value(),false));
        assertEquals(3,OcrConfidence.requiredFrames(.80f,false));
        assertEquals(2,OcrConfidence.requiredFrames(.95f,false));
        assertEquals(3,OcrConfidence.requiredFrames(.95f,true));
    }
}
