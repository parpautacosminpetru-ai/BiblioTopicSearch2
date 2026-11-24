package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class LineGeometryTest {
    private float[] line(float x,float y,float w,float h,float angle){
        double radians=Math.toRadians(angle),c=Math.cos(radians),s=Math.sin(radians);
        float[] out=new float[8],p={0,0,w,0,w,h,0,h};
        for(int i=0;i<4;i++){out[i*2]=(float)(x+p[i*2]*c-p[i*2+1]*s);out[i*2+1]=(float)(y+p[i*2]*s+p[i*2+1]*c);}
        return out;
    }
    @Test public void sameTiltedLineAcceptsWordsNotDifferentRows(){
        LineGeometry g=LineGeometry.from(line(0,0,400,20,15));
        double r=Math.toRadians(15);
        float dx=(float)(60*Math.cos(r)),dy=(float)(60*Math.sin(r));
        assertTrue(g.consecutive(30,10,30+dx,10+dy,40,40));
        assertFalse(g.consecutive(30,10,30+dx,50+dy,40,40));
        assertFalse(g.consecutive(30,10,30+dx*4,10+dy*4,40,40));
    }
    @Test public void tiltedAdjacentRowsButNotAnotherColumn(){
        LineGeometry g=LineGeometry.from(line(40,20,400,20,10));
        float[] next=line(35,49,380,20,10);
        assertTrue(g.nextLine(LineGeometry.from(next),next));
        float[] other=line(600,49,380,20,10);
        assertFalse(g.nextLine(LineGeometry.from(other),other));
    }
}
