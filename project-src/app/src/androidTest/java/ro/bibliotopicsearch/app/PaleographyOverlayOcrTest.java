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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PaleographyOverlayOcrTest {
    @Test public void projectionUsesActualImageCoordinatesAndLiteralRules()throws Exception{
        Bitmap image=Bitmap.createBitmap(1300,480,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);c.drawColor(Color.WHITE);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTypeface(Typeface.MONOSPACE);ink.setTextSize(110);
        c.drawText("ABC ABC",90,200,ink);
        c.drawText("KEEP",90,380,ink);
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Bitmap rendered=null;
        try{
            Text ocr=Tasks.await(recognizer.process(InputImage.fromBitmap(image,0)),30,TimeUnit.SECONDS);
            LiteralTransliterator engine=new LiteralTransliterator(Arrays.asList(
                    new LiteralTransliterator.Rule("ABC","Ж"),
                    new LiteralTransliterator.Rule("A","X")));
            List<PaleographyLine> lines=PaleographyProjection.lines(ocr,engine);
            assertFalse("Expected recognized source line",lines.isEmpty());
            for(PaleographyLine line:lines){
                assertEquals(engine.apply(line.source).text,line.target);
                assertTrue(line.bounds.top<260);
                assertTrue(line.bounds.left>=0&&line.bounds.right<=image.getWidth());
            }
            rendered=image.copy(Bitmap.Config.ARGB_8888,true);
            new PaleographyOverlayRenderer().draw(new Canvas(rendered),lines);
            assertEquals(image.getPixel(10,10),rendered.getPixel(10,10));
            assertEquals(image.getPixel(100,340),rendered.getPixel(100,340));
        }finally{recognizer.close();image.recycle();if(rendered!=null)rendered.recycle();}
    }
}
