package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
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
public class PunctuationOcrTest {
    @Test public void punctuationOnlyMapNeverMarksWholeWords()throws Exception{
        Bitmap image=Bitmap.createBitmap(1800,620,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);
        ink.setTypeface(Typeface.create("serif",Typeface.NORMAL));ink.setTextSize(110);
        canvas.drawText("Biserica? Preot! Credinta.",80,180,ink);
        canvas.drawText("Politica; societate, economie.",80,365,ink);
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{
            Text ocr=Tasks.await(recognizer.process(InputImage.fromBitmap(image,0)),30,TimeUnit.SECONDS);
            TopicNode node=new TopicNode("signs","Semne","Punctuatie","Punctuatie",1);node.enabled=true;
            node.terms.addAll(Arrays.asList("?","!",".",";",","));
            TopicMatcher.SearchPlan plan=TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(node)),
                    false,0,AppPrefs.MatchMode.EXACT);
            List<TopicMatcher.TextProbeHit> read=TopicMatcher.probeText(ocr.getText(),plan);
            assertFalse("OCR should read some punctuation",read.isEmpty());
            for(TopicMatcher.TextProbeHit hit:read)assertEquals(hit.term,hit.original);
            List<MatchHit> hits=TopicMatcher.find(ocr,plan);
            for(MatchHit hit:hits){
                assertEquals("signs",hit.node.profileId);
                assertEquals(hit.searchTerm,hit.originalText);
                assertTrue(PunctuationText.isPattern(hit.originalText));
                assertTrue("single sign rectangle must not contain a whole long word",hit.box.width()<110);
            }
            // A missing symbol box is not an excuse to invent geometry.
            assertTrue(hits.size()<=read.size());
        }finally{recognizer.close();image.recycle();}
    }
}
