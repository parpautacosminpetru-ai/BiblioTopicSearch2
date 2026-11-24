package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaleographyAudioProgressTest{
    @Test public void interruptedLineResumesAtUnfinishedCharacter(){
        PaleographyAudioProgress p=new PaleographyAudioProgress();
        assertEquals(0,p.start(5));
        p.completeThrough(2);
        assertEquals(2,p.start(5));
        // Delayed callbacks cannot rewind completed character positions.
        p.completeThrough(1);
        assertEquals(2,p.start(5));
        assertFalse(p.done());
        p.completeThrough(5);p.finish();
        assertTrue(p.done());
        assertEquals(5,p.start(5));
    }
    @Test public void indicesStayWithinCurrentSequence(){
        PaleographyAudioProgress p=new PaleographyAudioProgress();
        p.completeThrough(4);assertEquals(2,p.start(2));assertEquals(0,p.start(0));
    }
}
