package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UniversalRuntimeTest {
    private static Button find(View v,String name){
        if(v instanceof Button&&name.contentEquals(((TextView)v).getText()))return (Button)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=find(g.getChildAt(i),name);if(b!=null)return b;}}
        return null;
    }
    @Test public void freshHomeHasAllIndependentEntryPoints(){
        try(ActivityScenario<PaleographyHomeActivity> s=ActivityScenario.launch(PaleographyHomeActivity.class)){
            s.onActivity(a->{View root=a.findViewById(android.R.id.content);
                for(String name:new String[]{"CAMERĂ OCR","DESCHIDE IMAGINE","MODELE ȘI CATALOG DE SEMNE","TEXT ȘI REGULI","CĂUTARE TEMATICĂ BIBLIO"}){
                    Button b=find(root,name);assertNotNull(name,b);assertTrue(name,b.isEnabled());
                }
            });
        }
    }
    @Test public void cameraStartsWithoutTransliterationAndCanFreeze()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(c.getPackageName(),android.Manifest.permission.CAMERA);
        try(ActivityScenario<LiveHistoricalActivity> s=ActivityScenario.launch(LiveHistoricalActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+25000;
            while(android.os.SystemClock.elapsedRealtime()<deadline&&!ready.get()){
                s.onActivity(a->{View root=a.findViewById(android.R.id.content);
                    Button b=find(root,"Fixează imaginea");
                    assertNotNull(b);assertTrue(find(root,"Din galerie").isEnabled());
                    ready.set(b.isEnabled());
                });
                if(!ready.get())android.os.SystemClock.sleep(250);
            }
            assertTrue("CameraX preview must stream on the Android emulator",ready.get());
        }
    }
    @Test public void pageOcrActuallyProducesEditableText()throws Exception{
        PageDraft.offer(printed("HELLO WORLD"));
        try(ActivityScenario<PageReviewActivity> s=ActivityScenario.launch(PageReviewActivity.class)){
            s.onActivity(a->{Button b=find(a.findViewById(android.R.id.content),"OCR latin");assertNotNull(b);assertTrue(b.isEnabled());b.performClick();});
            java.util.concurrent.atomic.AtomicBoolean recognized=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+35000;
            while(android.os.SystemClock.elapsedRealtime()<deadline&&!recognized.get()){
                s.onActivity(a->recognized.set(hasEditableText(a.findViewById(android.R.id.content),"HELLO")));
                if(!recognized.get())android.os.SystemClock.sleep(250);
            }
            assertTrue("Page OCR must expose recognized text in the editable field",recognized.get());
        }
    }
    private static boolean hasEditableText(View v,String fragment){
        if(v instanceof android.widget.EditText&&v.isShown()&&((android.widget.EditText)v).getText().toString().toUpperCase(java.util.Locale.ROOT).contains(fragment))return true;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasEditableText(g.getChildAt(i),fragment))return true;}
        return false;
    }
    @Test public void bundledModelsCanInitializeOnActualAndroid()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();
        List<String> names=Arrays.asList("ron","eng","rus","ell","grc","ara","heb","hin","chi_sim","chi_tra","jpn","kor");
        assertTrue(HistoricalOcr.models(c).containsAll(names));
        for(String model:names)try(HistoricalOcr.Session ignored=new HistoricalOcr.Session(c,Collections.singletonList(model),6)){}
    }
    private Bitmap printed(String line){
        Bitmap bitmap=Bitmap.createBitmap(1300,320,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(96);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        canvas.drawText(line,55,175,paint);return bitmap;
    }
    @Test public void nativeOcrRecognizesLatinAndCyrillicAndReusesSession(){
        Context c=ApplicationProvider.getApplicationContext();
        Bitmap latin=printed("HELLO WORLD"),cyr=printed("ПРИВЕТ МИР");
        try(HistoricalOcr.Session engine=new HistoricalOcr.Session(c,Arrays.asList("eng","rus"),7)){
            String a=engine.read(latin),b=engine.read(cyr),again=engine.read(latin);
            assertTrue(a,a.toUpperCase(java.util.Locale.ROOT).contains("HELLO"));
            assertTrue(b,b.codePoints().anyMatch(cp->cp>=0x0400&&cp<=0x04ff));
            assertEquals(a,again);
            LiteralTransliterator.Result result=LiteralTransliterator.original().apply(b);assertEquals(b,result.text);
        }finally{latin.recycle();cyr.recycle();}
    }
    @Test public void customGlyphsCanBeSavedAndRecognized()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();String system="ci-"+java.util.UUID.randomUUID();
        Bitmap b=printed("X");
        try{
            GlyphShape shape=GlyphLibrary.shape(b);
            GlyphLibrary.add(c,system,"[X]",shape);
            List<GlyphShape.Candidate> ranked=GlyphShape.rank(shape,GlyphLibrary.examples(c,system));
            assertEquals("[X]",ranked.get(0).label);assertTrue(GlyphShape.strong(ranked));
        }finally{GlyphLibrary.remove(c,system,"[X]");b.recycle();}
    }
}
