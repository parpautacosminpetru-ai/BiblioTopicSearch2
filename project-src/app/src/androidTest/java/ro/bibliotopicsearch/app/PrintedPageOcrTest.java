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

/** End-to-end image test with the bundled recognizer (requires Android device/emulator). */
@RunWith(AndroidJUnit4.class)
public class PrintedPageOcrTest {
    @Test public void printedReligionTermsNeverMarkUnrelatedWords() throws Exception {
        Bitmap page=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(page);canvas.drawColor(Color.WHITE);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);
        ink.setTypeface(Typeface.create("serif",Typeface.NORMAL));ink.setTextSize(90);
        canvas.drawText("BISERICA  PREOT  CREDINTA",80,170,ink);
        canvas.drawText("POLITICA  SOCIETATE  STATUIE",80,330,ink);
        canvas.drawText("ISTORIE  INSTITUTIE  ECONOMIE",80,490,ink);
        TextRecognizer ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{
            Text result=Tasks.await(ocr.process(InputImage.fromBitmap(page,0)),30,TimeUnit.SECONDS);
            TopicNode religion=new TopicNode("religie","Religie","Termeni","Termeni",1);
            religion.enabled=true;religion.terms.addAll(Arrays.asList("biserica","preot","credinta"));
            TopicNode other=new TopicNode("other","Altă hartă","Termeni","Termeni",1);
            other.enabled=false;other.terms.add("politica");
            TopicMatcher.SearchPlan plan=TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(religion,other)),
                    false,0,AppPrefs.MatchMode.EXACT);
            List<MatchHit> hits=TopicMatcher.find(result,plan);
            assertFalse("OCR must recognize at least one intended term",hits.isEmpty());
            for(MatchHit hit:hits){
                assertEquals("religie",hit.node.profileId);
                assertTrue(religion.terms.contains(hit.searchTerm));
                assertEquals(hit.searchTerm,hit.originalText.toLowerCase(java.util.Locale.ROOT));
                assertTrue("Marker must remain in first line",hit.box.bottom<260);
            }
        }finally{ocr.close();page.recycle();}
    }
}
