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
    private Button start,choose,inspect,translit,importModel;
    private static final int PICK_MODEL=92;
    private boolean autoStart=true,importing;
    private com.google.mlkit.vision.text.TextRecognizer latin; // worker only
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
        choose=button("Alege scrierea");start=button("Pornește");
        LinearLayout bar=new LinearLayout(this);bar.addView(choose,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(start,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);
        importModel=button("Importă model OCR (.traineddata)");
        root.addView(importModel,new LinearLayout.LayoutParams(-1,dp(44)));
        importModel.setOnClickListener(v->{
            if(importing)return;stop();autoStart=false;
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent,PICK_MODEL);
        });
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,3));
        status=new TextView(this);status.setText("Latin rapid inclus · Alege scrierea pentru alte modele.");root.addView(status);
        TextView notice=new TextView(this);notice.setText("Candidat din ultimul cadru analizat, nu suprapunere live certificată.");root.addView(notice);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        text=new TextView(this);text.setTextSize(19);text.setTextIsSelectable(true);text.setGravity(Gravity.TOP|Gravity.START);scroll.addView(text);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,2));
        inspect=button("Verifică cadrul");translit=button("Transliterează");
        LinearLayout actions=new LinearLayout(this);actions.addView(inspect,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(translit,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        inspect.setEnabled(false);translit.setEnabled(false);start.setEnabled(false);
        choose.setOnClickListener(v->{
            stop();autoStart=false;
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Recunoașterea scrierii")
                .setItems(new String[]{"Latin rapid — inclus","Alte scrieri / mai multe modele"},(d,i)->{
                    if(i==0){models=Collections.emptyList();mode=3;clearLast();begin();}
                    else chooseModels();
                }).setNegativeButton("Renunță",null).show();
        });
        start.setOnClickListener(v->{if(running){stop();status.setText("Oprit · ultimul cadru rămâne disponibil.");}else begin();});
        inspect.setOnClickListener(v->{if(lastFrame==null)return;stop();
            Bitmap copy=lastFrame.copy(Bitmap.Config.ARGB_8888,false);PageDraft.offer(copy);startActivity(new Intent(this,PageReviewActivity.class));});
        translit.setOnClickListener(v->{if(lastText.isEmpty())return;stop();PaleographyActivity.open(this,lastText,"OCR universal · candidat");});
        setContentView(root);UiInsets.apply(root);
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)setup();
        else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},91);
    }
    private void chooseModels(){
        HistoricalOcrOptions.choose(this,HistoricalOcr.models(this),(names,psm)->{
            models=new ArrayList<>(names);mode=psm;clearLast();status.setText("Selectat: "+HistoricalModelName.expression(models));
            if(camera!=null){start.setEnabled(true);begin();}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_MODEL||result!=RESULT_OK||data==null||data.getData()==null||importing)return;
        android.net.Uri uri=data.getData();stop();importing=true;choose.setEnabled(false);start.setEnabled(false);importModel.setEnabled(false);
        status.setText("Se verifică modelul OCR…");
        worker.execute(()->{
            String name=null,error=null;
            try{name=HistoricalOcr.install(this,uri);}
            catch(Exception|LinkageError e){error=e.getMessage()==null?"Model incompatibil.":e.getMessage();}
            final String selected=name,failure=error;
            main.post(()->{
                if(destroyed)return;importing=false;choose.setEnabled(true);start.setEnabled(camera!=null);importModel.setEnabled(true);
                if(failure!=null){status.setText("Import: "+failure);return;}
                models=Collections.singletonList(selected);mode=3;clearLast();status.setText("Model disponibil: "+selected);
                if(resumed)begin();
            });
        });
    }
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void setup(){
        if(camera!=null||destroyed)return;
        try{camera=new LifecycleCameraController(this);camera.setEnabledUseCases(0);preview.setController(camera);camera.bindToLifecycle(this);start.setEnabled(!importing);if(resumed&&autoStart){autoStart=false;begin();}}
        catch(RuntimeException e){camera=null;status.setText("Camera indisponibilă.");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==91&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)setup();
        else if(request==91)status.setText("Permisiunea pentru cameră este necesară.");
    }
    private void begin(){
        if(destroyed||!resumed||camera==null||importing)return;
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
        String key=(selected.isEmpty()?"Latin rapid":HistoricalModelName.expression(selected))+":"+layout;
        inFlight=true;
        worker.execute(()->{
            String result="",failure=null;
            try{
                if(selected.isEmpty()){
                    if(session!=null){session.close();session=null;sessionKey="";}
                    if(latin==null)latin=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
                    result=com.google.android.gms.tasks.Tasks.await(latin.process(com.google.mlkit.vision.common.InputImage.fromBitmap(frame,0)),30,java.util.concurrent.TimeUnit.SECONDS).getText();
                }else{
                    if(session==null||!key.equals(sessionKey)){if(session!=null)session.close();session=null;session=new HistoricalOcr.Session(this,selected,layout);sessionKey=key;}
                    result=session.read(frame);
                }
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
    @Override protected void onResume(){super.onResume();resumed=true;if(camera!=null&&autoStart){autoStart=false;begin();}}
    @Override protected void onPause(){resumed=false;stop();super.onPause();}
    @Override protected void onDestroy(){
        destroyed=true;stop();clearLast();if(camera!=null)camera.unbind();worker.execute(()->{if(session!=null){session.close();session=null;}if(latin!=null){latin.close();latin=null;}});worker.shutdown();super.onDestroy();
    }
}
