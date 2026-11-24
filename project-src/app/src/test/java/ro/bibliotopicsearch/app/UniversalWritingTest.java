package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class UniversalWritingTest {
    @Test public void modelCombinationIsExplicitAndSafe(){
        assertEquals("Cyrillic+Arabic+HanT_vert",HistoricalModelName.expression(Arrays.asList("Cyrillic","Arabic","HanT_vert")));
        for(List<String> models:Arrays.asList(Collections.<String>emptyList(),Arrays.asList("a","a"),Arrays.asList("a+b"),Arrays.asList("../x"),Arrays.asList("a","b","c","d"))){
            try{HistoricalModelName.expression(models);fail();}catch(IllegalArgumentException expected){}
        }
    }
    private GlyphShape shape(boolean cross,int shift,boolean inverted){
        int w=80,h=80;int[] pixels=new int[w*h];Arrays.fill(pixels,inverted?0:255);
        for(int y=12;y<52;y++)for(int x=12;x<52;x++){
            boolean ink=cross?(Math.abs(x-32)<4||Math.abs(y-32)<4):(x<19||y>44);
            if(ink)pixels[(y+shift)*w+x+shift]=inverted?255:0;
        }
        return GlyphShape.extract(pixels,w,h);
    }
    @Test public void sameShapeAtDifferentPositionAndPolarity(){
        GlyphShape a=shape(true,0,false),b=shape(true,8,true);
        assertEquals(0,a.distance(b),.00001);
        assertTrue(a.distance(shape(false,0,false))>.2);
    }
    @Test public void ambiguityIsNotAutomaticallyAccepted(){
        GlyphShape a=shape(true,0,false),b=shape(false,0,false);
        List<GlyphShape.Candidate> unique=GlyphShape.rank(a,Arrays.asList(new GlyphShape.Example("𒀀",a),new GlyphShape.Example("[custom]",b)));
        assertEquals("𒀀",unique.get(0).label);assertTrue(GlyphShape.strong(unique));
        assertFalse(GlyphShape.strong(GlyphShape.rank(a,Arrays.asList(new GlyphShape.Example("𒀀",a),new GlyphShape.Example("[different]",a)))));
        assertFalse(GlyphShape.strong(Collections.emptyList()));
    }
    @Test public void blankImageCannotTrain(){
        int[] blank=new int[32*32];Arrays.fill(blank,255);
        try{GlyphShape.extract(blank,32,32);fail();}catch(IllegalArgumentException expected){}
    }
}
