package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageMotionGateTest {
    private int[] pattern(boolean reverse,int brightness){
        int[] a=new int[1280];
        for(int i=0;i<a.length;i++) a[i]=((i%7<3)^reverse?90:170)+brightness;
        return a;
    }
    @Test public void exposureAloneDoesNotInvalidate(){
        PageMotionGate gate=new PageMotionGate();
        assertFalse(gate.changed(pattern(false,0)));
        assertFalse(gate.changed(pattern(false,20)));
        assertFalse(gate.changed(pattern(false,-20)));
    }
    @Test public void newTextureClearsOldEvidenceAndThenSettles(){
        PageMotionGate gate=new PageMotionGate();
        assertFalse(gate.changed(pattern(false,0)));
        assertTrue(gate.changed(pattern(true,0)));
        assertFalse(gate.changed(pattern(true,0)));
    }
    @Test public void sampleCadenceIsBoundedAndResettable(){
        PageMotionGate gate=new PageMotionGate();
        assertTrue(gate.shouldSample(0));
        assertFalse(gate.shouldSample(30));
        assertTrue(gate.shouldSample(110));
        gate.reset();
        assertTrue(gate.shouldSample(120));
    }
}
