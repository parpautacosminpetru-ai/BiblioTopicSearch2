package ro.bibliotopicsearch.app;
import android.content.Context;
import android.graphics.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocatedPaleographyTest {
    @Test public void unknownShapesStayUnknownUntilConfirmed(){
        Bitmap b=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.BLACK);
        c.drawRect(20,20,40,60,p);c.drawRect(80,20,100,60,p);
        try{
            GlyphStudy s=GlyphStudy.analyse(b,new RectF(0,0,200,100),GlyphSegmenter.LTR,"test",Collections.emptyList());
            assertEquals(2,s.regions.size());assertEquals("[?][?]",s.transcription());
            assertEquals(20,s.marks().get(0).bounds.left,.01);
            s.regions.get(0).confirmed="𐀀";assertEquals("𐀀[?]",s.transcription());assertEquals(1,s.confirmedCount());
            assertTrue(s.marks().get(0).confirmed);assertFalse(s.marks().get(1).confirmed);
        }finally{b.recycle();}
    }
    @Test public void tesseractReturnsLineCoordinatesOnExactBitmap()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();
        Bitmap b=Bitmap.createBitmap(1100,400,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(90);
        canvas.drawText("HELLO WORLD",60,120,p);canvas.drawText("SECOND LINE",60,300,p);
        try{
            HistoricalOcr.Recognition result=HistoricalOcr.locate(c,Collections.singletonList("eng"),6,b);
            assertTrue(result.text,result.text.toUpperCase(java.util.Locale.ROOT).contains("HELLO"));
            assertTrue("Expected real OCR line boxes",result.lines.size()>=2);
            for(LocatedMark m:result.lines){
                assertTrue(m.bounds.left>=0&&m.bounds.top>=0&&m.bounds.right<=b.getWidth()&&m.bounds.bottom<=b.getHeight());
                assertTrue(m.bounds.width()>0&&m.bounds.height()>0);assertFalse(m.detail.isEmpty());assertFalse(m.confirmed);
            }
        }finally{b.recycle();}
    }
}
