package ro.bibliotopicsearch.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PaleographyFirstUseUiTest {
    private static <T extends View> T find(View v,Class<T> type,String text){
        if(type.isInstance(v)&&(text==null||(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))))return type.cast(v);
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++){T result=find(group.getChildAt(i),type,text);if(result!=null)return result;}
        }
        return null;
    }
    @Test public void cameraAndTextWithoutTransliterationProfile(){
        try(ActivityScenario<PaleographyActivity> scenario=ActivityScenario.launch(PaleographyActivity.class)){
            scenario.onActivity(a->{
                View root=a.findViewById(android.R.id.content);
                Spinner spinner=find(root,Spinner.class,null);
                assertNotNull(spinner);spinner.setSelection(0);
                Button camera=find(root,Button.class,"CAMERĂ · OCR");
                assertNotNull(camera);assertTrue(camera.isEnabled());
                assertTrue(find(root,Button.class,"IMAGINE · SEMNE · OCR").isEnabled());
                EditText source=find(root,EditText.class,null);assertNotNull(source);source.setText("𒀀 Α");
            });
            scenario.onActivity(a->{
                View root=a.findViewById(android.R.id.content);
                Button verify=find(root,Button.class,"Verifică textul");
                assertNotNull(verify);assertTrue(verify.isEnabled());
                assertTrue(find(root,Button.class,"CAMERĂ · OCR").isEnabled());
            });
        }
    }
}
