package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class VerifiedFrameTrackerTest {
    private VerifiedFrameTracker.Reading<String> at(String key,float x,int required,boolean repair){
        return new VerifiedFrameTracker.Reading<>(key,x,100,40,20,required,repair,key);
    }
    @Test public void literalRequiresTwoStableFramesAndNoStaleHold(){
        VerifiedFrameTracker<String> t=new VerifiedFrameTracker<>();
        assertTrue(t.update(Arrays.asList(at("religie/biserică",10,2,false)),PageContinuity.State.STABLE,100).isEmpty());
        assertEquals(1,t.update(Arrays.asList(at("religie/biserică",12,2,false)),PageContinuity.State.STABLE,200).size());
        assertTrue(t.update(Collections.emptyList(),PageContinuity.State.STABLE,300).isEmpty());
        assertTrue(t.update(Arrays.asList(at("religie/biserică",12,2,false)),PageContinuity.State.STABLE,400).isEmpty());
    }
    @Test public void unknownAndModerateConfidenceRequireThreeReadings(){
        VerifiedFrameTracker<String> t=new VerifiedFrameTracker<>();
        for(int i=1;i<=3;i++) assertEquals(i==3?1:0,t.update(Arrays.asList(at("a",20,3,false)),PageContinuity.State.UNKNOWN,i*100).size());
    }
    @Test public void differentMapWordPageOrPositionCannotConfirm(){
        VerifiedFrameTracker<String> t=new VerifiedFrameTracker<>();
        t.update(Arrays.asList(at("religie/preot",10,2,false)),PageContinuity.State.STABLE,100);
        assertTrue(t.update(Arrays.asList(at("istorie/preot",10,2,false),at("religie/preot",70,2,false)),PageContinuity.State.STABLE,200).isEmpty());
        assertTrue(t.update(Arrays.asList(at("religie/preot",70,2,false)),PageContinuity.State.CHANGED,300).isEmpty());
        assertTrue(t.update(Arrays.asList(at("religie/preot",70,2,false)),PageContinuity.State.MOVED,400).isEmpty());
    }
    @Test public void duplicatesAndLowConfidenceNeverConfirm(){
        VerifiedFrameTracker<String> t=new VerifiedFrameTracker<>();
        t.update(Arrays.asList(at("a",10,2,false)),PageContinuity.State.STABLE,100);
        assertTrue(t.update(Arrays.asList(at("a",10,2,false)),PageContinuity.State.STABLE,100).isEmpty());
        assertTrue(t.update(Arrays.asList(at("a",10,0,false)),PageContinuity.State.STABLE,200).isEmpty());
        assertTrue(t.update(Arrays.asList(at("a",10,2,false)),PageContinuity.State.STABLE,300).isEmpty());
    }
    @Test public void reconstructionNeedsStablePage(){
        VerifiedFrameTracker<String> t=new VerifiedFrameTracker<>();
        for(int i=0;i<4;i++)assertTrue(t.update(Arrays.asList(at("a",10,3,true)),PageContinuity.State.UNKNOWN,100+i*100).isEmpty());
    }
}
