package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GlyphSegmenterTest{
    private static int[] image(boolean inverted){
        int[] p=new int[100*80];Arrays.fill(p,inverted?0:255);
        for(int y:new int[]{10,40})for(int x:new int[]{10,35,60})
            for(int yy=y;yy<y+14;yy++)for(int xx=x;xx<x+8;xx++)p[yy*100+xx]=inverted?255:0;
        return p;
    }
    @Test public void separatesHorizontalLinesAndColumns(){
        GlyphSegmenter.Result a=GlyphSegmenter.segment(image(false),100,80,GlyphSegmenter.LTR);
        assertEquals(6,a.boxes.size());assertEquals(10,a.boxes.get(0).left);assertEquals(18,a.boxes.get(0).right);
        assertEquals(40,a.boxes.get(3).top);assertEquals(1,a.boxes.get(3).line);
        assertFalse(a.truncated);
    }
    @Test public void rtlAndVerticalRespectChosenOrder(){
        assertEquals(60,GlyphSegmenter.segment(image(false),100,80,GlyphSegmenter.RTL).boxes.get(0).left);
        assertEquals(60,GlyphSegmenter.segment(image(false),100,80,GlyphSegmenter.VERTICAL_RTL).boxes.get(0).left);
        assertEquals(40,GlyphSegmenter.segment(image(false),100,80,GlyphSegmenter.VERTICAL_LTR).boxes.get(1).top);
    }
    @Test public void inverseAndEmpty(){
        assertEquals(6,GlyphSegmenter.segment(image(true),100,80,GlyphSegmenter.LTR).boxes.size());
        int[] blank=new int[6400];Arrays.fill(blank,255);
        assertTrue(GlyphSegmenter.segment(blank,80,80,0).boxes.isEmpty());
    }
    @Test public void connectedMarksAreNotFalselySplit(){
        int[] p=new int[40*40];Arrays.fill(p,255);
        for(int y=10;y<20;y++)for(int x=5;x<30;x++)p[y*40+x]=0;
        assertEquals(1,GlyphSegmenter.segment(p,40,40,0).boxes.size());
    }
}
