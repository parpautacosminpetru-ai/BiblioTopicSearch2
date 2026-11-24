package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaleographyGeometryTest {
    @Test public void tiltedTextUsesItsOwnQuadrilateral(){
        float[] q={10,10,110,30,106,50,6,30};
        assertTrue(PaleographyGeometry.valid(q,6,10,110,50));
        assertEquals((float)Math.hypot(100,20),PaleographyGeometry.width(q),.01f);
        assertEquals((float)Math.hypot(4,20),PaleographyGeometry.height(q),.01f);
    }
    @Test public void badCoordinatesNeverCoverAnUnrelatedLine(){
        assertFalse(PaleographyGeometry.valid(new float[]{0,0,100,20,100,0,0,20},0,0,100,20));
        assertFalse(PaleographyGeometry.valid(new float[]{0,0,100,0,100,20,0,20},150,150,250,170));
        assertFalse(PaleographyGeometry.valid(new float[]{0,0,100,0,100,0,0,0},0,0,100,20));
        assertFalse(PaleographyGeometry.valid(null,0,0,100,20));
    }
}
