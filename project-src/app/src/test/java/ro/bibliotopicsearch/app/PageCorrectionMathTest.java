package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageCorrectionMathTest {
    @Test public void convexPageCornersOnly(){
        assertTrue(PageCorrectionMath.validQuad(new float[]{.1f,.05f,.9f,.1f,.95f,.95f,.05f,.9f}));
        assertFalse(PageCorrectionMath.validQuad(new float[]{0,0,1,1,1,0,0,1}));
        assertFalse(PageCorrectionMath.validQuad(new float[]{0,0,.1f,0,.1f,.1f,0,.1f}));
        assertFalse(PageCorrectionMath.validQuad(new float[]{0,0,1,0,Float.NaN,1,0,1}));
    }
    @Test public void bowChangesCentreNotPageEdges(){
        assertEquals(50f,PageCorrectionMath.sourceY(50,0,101,1000,100),.001f);
        assertEquals(130f,PageCorrectionMath.sourceY(50,50,101,1000,100),.001f);
        assertEquals(50f,PageCorrectionMath.sourceY(50,100,101,1000,-100),.001f);
        assertEquals(-30f,PageCorrectionMath.sourceY(50,50,101,1000,-100),.001f);
    }
    @Test public void contrastPreservesInkOrderAndRemovesPaperShadow(){
        assertTrue(PageCorrectionMath.enhance(70,170,60)<PageCorrectionMath.enhance(120,170,60));
        assertTrue(PageCorrectionMath.enhance(170,170,60)>225);
        assertEquals(PageCorrectionMath.enhance(90,180,50),PageCorrectionMath.enhance(110,220,50),1);
    }
    @Test public void skewOfSyntheticPrintedLines(){
        int w=240,h=180;int[] image=new int[w*h];
        java.util.Arrays.fill(image,238);
        double slope=Math.tan(Math.toRadians(6));
        for(int x=15;x<w-15;x++)if(x%14<10)for(int baseline=32;baseline<155;baseline+=19){
            int y=(int)Math.round(baseline+slope*(x-w/2));
            for(int dy=0;dy<3;dy++)if(y+dy>=0&&y+dy<h)image[(y+dy)*w+x]=48;
        }
        assertEquals(-6f,PageCorrectionMath.estimateSkew(image,w,h),1.5f);
        java.util.Arrays.fill(image,230);
        assertEquals(0f,PageCorrectionMath.estimateSkew(image,w,h),.001f);
    }
}
