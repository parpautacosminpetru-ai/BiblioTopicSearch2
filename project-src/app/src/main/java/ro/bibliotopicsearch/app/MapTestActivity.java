package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Local diagnostic for one explicit profile. It never changes the active
 * scanning maps or stores the text or imported image. */
public final class MapTestActivity extends AppCompatActivity {
    static final String PROFILE_ID="profile_id";
    private static final int PICK_IMAGE=71;
    private static final int MAX_INPUT=100000;
    private static final int MAX_RESULTS=300;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String profileId;
    private EditText input;
    private TextView title,summary,marked;
    private LinearLayout rows;
    private Button run,image;
    private boolean busy;

    @Override protected void onCreate(@Nullable Bundle saved){
        super.onCreate(saved);
        profileId=getIntent().getStringExtra(PROFILE_ID);
        MapProfile profile=TopicLibraryStore.get(this,profileId);
        if(profile==null){finish();return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(28));root.setBackgroundColor(Color.rgb(246,248,251));
        scroll.addView(root);
        title=text(profile.name,22);title.setTextColor(profile.color);root.addView(title);
        input=new EditText(this);
        input.setHint("Text de probă");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setGravity(Gravity.TOP|Gravity.START);input.setMinLines(4);input.setMaxLines(9);
        root.addView(input,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);
        run=button("Testează");image=button("Imagine");
        Button pageButton=button("Pagină");
        bar.addView(run,new LinearLayout.LayoutParams(0,dp(48),1));
        bar.addView(image,new LinearLayout.LayoutParams(0,dp(48),1));
        bar.addView(pageButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);
        pageButton.setOnClickListener(v->{if(busy)return;Intent i=new Intent(this,PageReviewActivity.class);i.putExtra(PROFILE_ID,profileId);startActivity(i);});
        summary=text("",15);root.addView(summary);
        marked=text("",16);marked.setTextIsSelectable(true);marked.setVisibility(View.GONE);root.addView(marked);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);root.addView(rows);
        run.setOnClickListener(v->testText());image.setOnClickListener(v->{
            if(busy)return;
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");
            startActivityForResult(intent,PICK_IMAGE);
        });
        setContentView(scroll);UiInsets.apply(scroll);
    }

    private TopicMatcher.SearchPlan plan(){
        MapProfile profile=TopicLibraryStore.get(this,profileId);
        if(profile==null)throw new IllegalStateException("Harta nu mai există.");
        return TopicMatcher.compile(this,TopicMapStore.parseForProfile(this,profile.id,profile.name,profile.rawText));
    }
    private void setBusy(boolean value){
        busy=value;run.setEnabled(!value);image.setEnabled(!value);
        if(value){summary.setText("Se verifică…");rows.removeAllViews();marked.setVisibility(View.GONE);}
    }
    private void testText(){
        if(busy)return;
        String raw=input.getText().toString();
        if(raw.length()>MAX_INPUT){input.setError("Text prea lung");return;}
        setBusy(true);
        worker.execute(()->{
            try {
                TopicMatcher.SearchPlan plan=plan();
                List<TopicMatcher.TextProbeHit> hits=TopicMatcher.probeText(raw,plan);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    setBusy(false);
                    summary.setText(hits.size()+" potriviri în text · "+plan.termCount()+" termeni activi");
                    SpannableString display=new SpannableString(raw);
                    rows.removeAllViews();
                    int count=0;
                    for(TopicMatcher.TextProbeHit h:hits){
                        if(count++>=MAX_RESULTS)break;
                        int color=(h.node.color&0xffffff)|0x44000000;
                        display.setSpan(new BackgroundColorSpan(color),h.start,h.end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        addResult(h.original,h.term,h.node.path,h.node.color,h.kind==MatchHit.Kind.LITERAL?"":"Reconstrucție");
                    }
                    marked.setText(display);marked.setVisibility(View.VISIBLE);
                    if(hits.size()>MAX_RESULTS)rows.addView(text("Afișate primele "+MAX_RESULTS,13));
                });
            }catch(Exception e){showError(e);}
        });
    }
    @Override protected void onActivityResult(int request,int result,@Nullable Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        Uri uri=data.getData();setBusy(true);
        worker.execute(()->{
            Bitmap bitmap=null;TextRecognizer recognizer=null;
            try{
                TopicMatcher.SearchPlan plan=plan();
                bitmap=decode(uri);
                recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                Text recognized=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
                String raw=recognized.getText();
                List<MatchHit> hits=TopicMatcher.find(recognized,plan);
                // A recognized sign without reliable symbol geometry remains
                // visible as text-only evidence, not as a camera highlight.
                List<TopicMatcher.TextProbeHit> signs=new ArrayList<>();
                for(TopicMatcher.TextProbeHit h:TopicMatcher.probeText(raw,plan))
                    if(PunctuationText.isPattern(h.term))signs.add(h);
                java.util.Map<String,Integer> localized=new java.util.HashMap<>();
                for(MatchHit h:hits)if(PunctuationText.isPattern(h.searchTerm)){
                    String key=h.node.identityKey()+"\u0001"+h.searchTerm;
                    localized.put(key,localized.getOrDefault(key,0)+1);
                }
                java.util.List<TopicMatcher.TextProbeHit> withoutGeometry=new java.util.ArrayList<>();
                for(TopicMatcher.TextProbeHit h:signs){
                    String key=h.node.identityKey()+"\u0001"+h.term;
                    int remaining=localized.getOrDefault(key,0);
                    if(remaining>0)localized.put(key,remaining-1);
                    else withoutGeometry.add(h);
                }
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    setBusy(false);
                    summary.setText(hits.size()+" candidați localizați"
                            +(withoutGeometry.isEmpty()?"":" · "+withoutGeometry.size()+" semne fără localizare sigură"));
                    input.setText(raw);
                    marked.setText(raw);marked.setVisibility(View.VISIBLE);
                    rows.removeAllViews();
                    int count=0;
                    for(MatchHit h:hits){
                        if(count++>=MAX_RESULTS)break;
                        String detail=h.confidence>0?Math.round(h.confidence*100)+"% OCR":"Încredere indisponibilă";
                        addResult(h.originalText,h.searchTerm,h.node.path,h.node.color,detail);
                    }
                    for(TopicMatcher.TextProbeHit h:withoutGeometry){
                        if(count++>=MAX_RESULTS)break;
                        addResult(h.original,h.term,h.node.path,h.node.color,"Citit în text; fără localizare sigură");
                    }
                    if(hits.size()+withoutGeometry.size()>MAX_RESULTS)rows.addView(text("Afișate primele "+MAX_RESULTS,13));
                });
            }catch(Exception e){showError(e);}
            finally{
                if(recognizer!=null)recognizer.close();
                if(bitmap!=null)bitmap.recycle();
            }
        });
    }

    /** Bound memory, honor EXIF orientation, and avoid saving a local copy. */
    private Bitmap decode(Uri uri)throws Exception{
        if(Build.VERSION.SDK_INT>=28){
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                double scale=Math.min(1d,2600d/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,(int)Math.round(w*scale)),Math.max(1,(int)Math.round(h*scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(stream,null,opt);}
        if(opt.outWidth<=0||opt.outHeight<=0)throw new IllegalArgumentException("Imagine invalidă.");
        int sample=1;while(Math.max(opt.outWidth,opt.outHeight)/sample>2600)sample*=2;
        opt.inJustDecodeBounds=false;opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try(InputStream stream=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(stream,null,opt);}
        if(bitmap==null)throw new IllegalArgumentException("Imagine invalidă.");
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            androidx.exifinterface.media.ExifInterface exif=new androidx.exifinterface.media.ExifInterface(stream);
            int rotation=exif.getRotationDegrees();
            boolean flip=exif.isFlipped();
            if(rotation!=0||flip){
                Matrix matrix=new Matrix();
                if(flip)matrix.postScale(-1,1);
                matrix.postRotate(rotation);
                Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}
            }
        }catch(java.io.IOException ignored){/* non-EXIF image */}
        return bitmap;
    }

    private void addResult(String original,String term,String path,int color,String detail){
        TextView row=text(original+"  →  "+term+"\n"+path+(detail.isEmpty()?"":" · "+detail),14);
        row.setPadding(dp(8),dp(7),dp(8),dp(7));
        row.setTextColor(Color.rgb(25,34,44));
        row.setBackgroundColor((color&0xffffff)|0x22000000);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);
        rows.addView(row,lp);
    }
    private void showError(Exception e){
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);summary.setText("Test nereușit: "+(e.getMessage()==null?"OCR indisponibil":e.getMessage()));}});
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(24,32,43));t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(String value){Button v=new Button(this);v.setText(value);v.setAllCaps(false);v.setTextColor(Color.WHITE);v.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(40,77,115)));return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
