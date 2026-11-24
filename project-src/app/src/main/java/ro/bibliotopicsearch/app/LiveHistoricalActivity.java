package ro.bibliotopicsearch.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent multialphabet camera preview. One bounded still at a time;
 * text is explicitly tied to the last analyzed frame, never positioned
 * over a later camera image. */
public final class LiveHistoricalActivity extends AppCompatActivity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PreviewView preview;
    private LifecycleCameraController camera;
    private TextView status,text;
    private Button start,choose,inspect,translit;
    private List<String> models=Collections.emptyList();
    private int mode=3;
    private boolean running,inFlight,resumed,destroyed;
    private long generation;
    private String lastText="";
    private Bitmap lastFrame;
    private HistoricalOcr.Session session; // worker only
    private String sessionKey="";
    private final Runnable tick=this::sample;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("OCR universal · cameră");title.setTextSize(20);root.addView(title);
        choose=button("Modele / așezare");start=button("Pornește");
        LinearLayout bar=new LinearLayout(this);bar.addView(choose,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(start,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,3));
        status=new TextView(this);status.setText("Alege modelele instalate din Pagină / OCR istoric.");root.addView(status);
        TextView notice=new TextView(this);notice.setText("Candidat din ultimul cadru analizat, nu suprapunere live certificată.");root.addView(notice);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        text=new TextView(this);text.setTextSize(19);text.setTextIsSelectable(true);text.setGravity(Gravity.TOP|Gravity.START);scroll.addView(text);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,2));
        inspect=button("Verifică cadrul");translit=button("Transliterează");
        LinearLayout actions=new LinearLayout(this);actions.addView(inspect,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(translit,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        inspect.setEnabled(false);translit.setEnabled(false);start.setEnabled(false);
        choose.setOnClickListener(v->{stop();HistoricalOcrOptions.choose(this,HistoricalOcr.models(this),(names,psm)->{
            models=new ArrayList<>(names);mode=psm;clearLast();status.setText("Selectat: "+HistoricalModelName.expression(models));start.setEnabled(camera!=null);
        });});
        start.setOnClickListener(v->{if(running){stop();status.setText("Oprit · ultimul cadru rămâne disponibil.");}else begin();});
        inspect.setOnClickListener(v->{if(lastFrame==null)return;stop();
            Bitmap copy=lastFrame.copy(Bitmap.Config.ARGB_8888,false);PageDraft.offer(copy);startActivity(new Intent(this,PageReviewActivity.class));});
        translit.setOnClickListener(v->{if(lastText.isEmpty())return;stop();PaleographyActivity.open(this,lastText,"OCR universal · candidat");});
        setContentView(root);UiInsets.apply(root);
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)setup();
        else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},91);
    }
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void setup(){
        if(camera!=null||destroyed)return;
        try{camera=new LifecycleCameraController(this);camera.setEnabledUseCases(0);preview.setController(camera);camera.bindToLifecycle(this);start.setEnabled(!models.isEmpty());}
        catch(RuntimeException e){camera=null;status.setText("Camera indisponibilă.");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==91&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)setup();
        else if(request==91)status.setText("Permisiunea pentru cameră este necesară.");
    }
    private void begin(){
        if(destroyed||!resumed||camera==null||models.isEmpty())return;
        running=true;generation++;start.setText("Oprește");status.setText("Se analizează cadre…");main.removeCallbacks(tick);main.post(tick);
    }
    private void stop(){running=false;generation++;main.removeCallbacks(tick);if(start!=null)start.setText("Pornește");}
    private void clearLast(){if(lastFrame!=null){lastFrame.recycle();lastFrame=null;}lastText="";text.setText("");inspect.setEnabled(false);translit.setEnabled(false);}
    private void sample(){
        if(!running||!resumed||destroyed)return;
        if(inFlight){main.postDelayed(tick,300);return;}
        Bitmap raw;
        try{raw=preview.getBitmap();}catch(RuntimeException e){raw=null;}
        if(raw==null){main.postDelayed(tick,600);return;}
        final Bitmap frame;
        double scale=Math.min(1,1280d/Math.max(raw.getWidth(),raw.getHeight()));
        try{frame=Bitmap.createScaledBitmap(raw,Math.max(1,(int)(raw.getWidth()*scale)),Math.max(1,(int)(raw.getHeight()*scale)),true);}
        catch(RuntimeException e){raw.recycle();stop();status.setText("Cadru indisponibil.");return;}
        if(frame!=raw)raw.recycle();
        long epoch=generation;List<String> selected=new ArrayList<>(models);int layout=mode;
        String key=HistoricalModelName.expression(selected)+":"+layout;
        inFlight=true;
        worker.execute(()->{
            String result="",failure=null;
            try{
                if(session==null||!key.equals(sessionKey)){if(session!=null)session.close();session=null;session=new HistoricalOcr.Session(this,selected,layout);sessionKey=key;}
                result=session.read(frame);
                if(result.length()>LiteralTransliterator.MAX_INPUT)throw new IllegalArgumentException("Text prea lung; restrânge zona.");
            }catch(Exception|LinkageError e){failure=e.getMessage()==null?"Motor OCR indisponibil.":e.getMessage();if(session!=null){try{session.close();}catch(RuntimeException ignored){}session=null;}sessionKey="";}
            final String value=result,error=failure;
            main.post(()->{
                inFlight=false;
                if(destroyed||!running||!resumed||epoch!=generation){frame.recycle();if(running&&!destroyed){main.removeCallbacks(tick);main.postDelayed(tick,300);}return;}
                if(error!=null){frame.recycle();stop();status.setText("OCR: "+error);return;}
                if(lastFrame!=null)lastFrame.recycle();lastFrame=frame;lastText=value;
                text.setText(value);inspect.setEnabled(true);translit.setEnabled(!value.isEmpty());
                status.setText(value.isEmpty()?"Ultimul cadru: niciun text recunoscut.":"Ultimul cadru · "+key.substring(0,key.lastIndexOf(':'))+" · de verificat");
                main.removeCallbacks(tick);main.postDelayed(tick,1200);
            });
        });
    }
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;stop();super.onPause();}
    @Override protected void onDestroy(){
        destroyed=true;stop();clearLast();if(camera!=null)camera.unbind();worker.execute(()->{if(session!=null){session.close();session=null;}});worker.shutdown();super.onDestroy();
    }
}
