package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoricalPageCorrectionTest{
    @Test public void correctedYellowedTiltedPageKeepsOnlyConfiguredTerms()throws Exception{
        Bitmap source=Bitmap.createBitmap(1500,1000,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(source);c.drawColor(0xffe4d2aa);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(0xff53422f);ink.setTypeface(Typeface.create("serif",Typeface.NORMAL));ink.setTextSize(80);
        c.save();c.rotate(6f,750,500);
        c.drawText("BISERICA  PREOT  CREDINTA",100,260,ink);
        c.drawText("POLITICA  SOCIETATE  STATUIE",100,430,ink);
        c.drawText("ISTORIE  INSTITUTIE  ECONOMIE",100,600,ink);
        c.restore();
        Bitmap rectified=null,enhanced=null;TextRecognizer client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{
            rectified=PageImageProcessor.rectify(source,new float[]{0,0,1,0,1,1,0,1},0,true);
            enhanced=PageImageProcessor.historicalContrast(rectified,55);
            assertEquals(rectified.getWidth(),enhanced.getWidth());
            assertEquals(rectified.getHeight(),enhanced.getHeight());
            TopicNode n=new TopicNode("religie","Religie","Termeni","Termeni",1);n.enabled=true;
            n.terms.addAll(Arrays.asList("biserica","preot","credinta"));
            TopicMatcher.SearchPlan plan=TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(n)),false,0,AppPrefs.MatchMode.EXACT);
            List<MatchHit> hits=TopicMatcher.find(Tasks.await(client.process(InputImage.fromBitmap(enhanced,0)),30,TimeUnit.SECONDS),plan);
            assertFalse("OCR must recognize at least one explicit religion term",hits.isEmpty());
            for(MatchHit h:hits){
                assertEquals("religie",h.node.profileId);assertTrue(n.terms.contains(h.searchTerm));
                assertEquals(h.searchTerm,h.originalText.toLowerCase(java.util.Locale.ROOT));
                assertTrue(h.box.left>=0&&h.box.top>=0&&h.box.right<=enhanced.getWidth()&&h.box.bottom<=enhanced.getHeight());
            }
        }finally{client.close();source.recycle();if(rectified!=null)rectified.recycle();if(enhanced!=null)enhanced.recycle();}
    }
}
