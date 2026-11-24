package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Still-page review: all geometry is drawn on the very bitmap recognized
 * by OCR, never projected onto a newer camera frame. */
public final class PageReviewActivity extends AppCompatActivity {
    private static final int PICK=72;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap source,review;
    private float[] corners={0,0,1,0,1,1,0,1};
    private List<MatchHit> verified=Collections.emptyList(),candidates=Collections.emptyList();
    private PagePreviewView page;
    private TextView status;
    private Button open,edit,analyse;
    private SeekBar bow,contrast;
    private CheckBox deskew,showCandidates;
    private boolean busy,showingResult;
    private String profileId;
    private String lastOcrText = "";
    private Button paleography;
    private Text rawPageOcr, correctedPageOcr;
    private List<PaleographyLine> paleographyLines=Collections.emptyList();
    private boolean showingPaleography;
    private String paleographySystem="";
    private PaleographySoundPlayer soundPlayer;
    private java.util.Map<String,java.io.File> soundClips=Collections.emptyMap();
    private boolean resumed;

    @Override protected void onCreate(@Nullable Bundle saved){
        super.onCreate(saved);
        profileId=getIntent().getStringExtra(MapTestActivity.PROFILE_ID);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(4),dp(10),dp(6));root.setBackgroundColor(Color.rgb(246,248,251));
        LinearLayout bar=new LinearLayout(this);
        open=button("Imagine");edit=button("Colțuri");analyse=button("OCR");
        for(Button button:new Button[]{open,edit,analyse})bar.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bar);
        page=new PagePreviewView(this);page.setListener(this::showHits);
        soundPlayer=new PaleographySoundPlayer(this,new PaleographySoundPlayer.Listener(){
            @Override public void highlight(PaleographyLine line,LiteralTransliterator.RuleMatch cue){
                if(!isDestroyed())page.setSpeaking(line,cue);
            }
            @Override public void unavailable(String language){
                if(resumed&&!isDestroyed())android.widget.Toast.makeText(PageReviewActivity.this,"Sunet indisponibil: "+language,android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        bow=slider(root,"Curbură",200,100);
        contrast=slider(root,"Contrast",100,55);
        deskew=new CheckBox(this);deskew.setText("Îndreptare automată");deskew.setChecked(true);root.addView(deskew);
        showCandidates=new CheckBox(this);showCandidates.setText("Arată și candidații");showCandidates.setVisibility(View.GONE);
        root.addView(showCandidates);
        showCandidates.setOnCheckedChangeListener((v,on)->{if(showingResult)renderResult();});
        status=new TextView(this);status.setTextColor(Color.rgb(31,42,52));root.addView(status);
        paleography=button("Paleografie");paleography.setVisibility(View.GONE);
        root.addView(paleography,new LinearLayout.LayoutParams(-1,dp(44)));
        paleography.setOnClickListener(v->togglePagePaleography());
        paleography.setOnLongClickListener(v->{
            if(!busy&&!lastOcrText.isEmpty())PaleographyActivity.open(this,lastOcrText,"Text OCR pagină");
            return true;
        });
        open.setOnClickListener(v->pick());
        edit.setOnClickListener(v->{
            if(busy||source==null)return;
            if(!showingResult)corners=new float[]{0,0,1,0,1,1,0,1};
            showingResult=false;showingPaleography=false;
            soundPlayer.setEnabled(false);
            paleography.setVisibility(View.GONE);
            page.showOriginal(source,corners);edit.setText("Resetează");showCandidates.setVisibility(View.GONE);
            status.setText("Ajustează cele patru colțuri");
        });
        analyse.setOnClickListener(v->analyse());
        setContentView(root);UiInsets.apply(root);
        Bitmap draft=PageDraft.take();
        if(draft!=null)useSource(draft);
        else{status.setText("Alege o imagine");setBusy(false);}
    }
    private void pick(){
        if(busy)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK);
    }
    private void useSource(Bitmap bitmap){
        Bitmap oldSource=source,oldReview=review;
        source=bitmap;review=null;verified=Collections.emptyList();candidates=Collections.emptyList();
        lastOcrText="";rawPageOcr=null;correctedPageOcr=null;paleographyLines=Collections.emptyList();
        showingPaleography=false;paleographySystem="";soundPlayer.setEnabled(false);soundClips=Collections.emptyMap();paleography.setText("Paleografie");paleography.setVisibility(View.GONE);
        corners=new float[]{0,0,1,0,1,1,0,1};showingResult=false;
        bow.setProgress(100);showCandidates.setChecked(false);showCandidates.setVisibility(View.GONE);
        page.showOriginal(source,corners);edit.setText("Resetează");status.setText("Ajustează cele patru colțuri");setBusy(false);
        if(oldSource!=null&&oldSource!=bitmap&&!oldSource.isRecycled())oldSource.recycle();
        if(oldReview!=null&&oldReview!=bitmap&&oldReview!=oldSource&&!oldReview.isRecycled())oldReview.recycle();
    }
    private TopicMatcher.SearchPlan plan(){
        List<MapProfile> profiles=TopicLibraryStore.list(this),chosen;
        if(profileId!=null){
            MapProfile p=TopicLibraryStore.get(this,profileId);
            if(p==null)throw new IllegalStateException("Harta nu mai există");
            chosen=Collections.singletonList(p);
        }else chosen=TopicLibraryStore.scanProfiles(this,profiles);
        // Still-page research always uses whole explicit terms. Diacritic
        // folding follows the user's setting; no speculative corrections.
        return TopicMatcher.compile(TopicMapStore.loadForScanning(this,chosen),
                AppPrefs.ignoreDiacritics(this),0,AppPrefs.MatchMode.EXACT);
    }
    private void analyse(){
        if(busy||source==null)return;
        if(!showingResult)corners=page.corners();
        final Bitmap input=source;
        final float[] quad=corners.clone();
        final int curve=bow.getProgress()-100,strength=contrast.getProgress();
        final boolean straighten=deskew.isChecked();
        soundPlayer.setEnabled(false);
        setBusy(true);status.setText("Se verifică pagina…");
        worker.execute(()->{
            Bitmap rectified=null,enhanced=null;
            TextRecognizer recognizer=null;
            try{
                TopicMatcher.SearchPlan plan=plan();
                rectified=PageImageProcessor.rectify(input,quad,curve,straighten);
                enhanced=PageImageProcessor.historicalContrast(rectified,strength);
                recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                Text before=Tasks.await(recognizer.process(InputImage.fromBitmap(rectified,0)),30,TimeUnit.SECONDS);
                Text after=Tasks.await(recognizer.process(InputImage.fromBitmap(enhanced,0)),30,TimeUnit.SECONDS);
                final String pageText=after.getText();
                List<MatchHit> raw=TopicMatcher.find(before,plan),improved=TopicMatcher.find(after,plan);
                List<MatchHit> agreed=PageEvidence.agree(readings(raw),readings(improved));
                final Bitmap output=enhanced;enhanced=null;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){output.recycle();return;}
                    Bitmap old=review;
                    review=output;verified=agreed;candidates=improved;showingResult=true;
                    lastOcrText=pageText;rawPageOcr=before;correctedPageOcr=after;
                    showingPaleography=false;paleographyLines=Collections.emptyList();paleographySystem="";
                    paleography.setText("Paleografie");paleography.setVisibility(pageText.isEmpty()?View.GONE:View.VISIBLE);
                    showCandidates.setChecked(false);showCandidates.setVisibility(View.VISIBLE);
                    edit.setText("Colțuri");setBusy(false);renderResult();
                    if(old!=null&&old!=source&&old!=output&&!old.isRecycled())old.recycle();
                });
            }catch(Exception e){error(e);}
            finally{
                if(recognizer!=null)recognizer.close();
                if(rectified!=null)rectified.recycle();
                if(enhanced!=null)enhanced.recycle();
            }
        });
    }
    private List<PageEvidence.Reading<MatchHit>> readings(List<MatchHit> hits){
        List<PageEvidence.Reading<MatchHit>> out=new ArrayList<>(hits.size());
        for(MatchHit h:hits){
            float[] geometry=new float[h.segments.size()*4];int i=0;
            for(RectF b:h.segments){geometry[i++]=b.left;geometry[i++]=b.top;geometry[i++]=b.right;geometry[i++]=b.bottom;}
            out.add(new PageEvidence.Reading<>(h.identityKey()+"\u0001"+h.originalText,geometry,h));
        }
        return out;
    }
    private void togglePagePaleography(){
        if(busy||!showingResult||review==null)return;
        if(showingPaleography){
            showingPaleography=false;
            soundPlayer.setEnabled(false);
            paleography.setText("Paleografie");
            showCandidates.setVisibility(View.VISIBLE);
            renderResult();return;
        }
        final String id=TransliterationStore.selected(this);
        if(id.isEmpty()){
            PaleographyActivity.open(this,lastOcrText,"Text OCR pagină");return;
        }
        final Text before=rawPageOcr,after=correctedPageOcr;
        if(before==null||after==null)return;
        setBusy(true);
        worker.execute(()->{
            try{
                LiteralTransliterator engine=TransliterationStore.engine(this,id);
                java.util.Map<String,java.io.File> clips=PaleographyAudioFiles.available(this,id,engine);
                List<PaleographyLine> lines=PaleographyProjection.agree(before,after,engine);
                String name="";
                for(TransliterationStore.Profile p:TransliterationStore.list(this))if(p.id.equals(id))name=p.name;
                final String title=name;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    paleographyLines=lines;showingPaleography=true;paleographySystem=title;soundClips=clips;
                    paleography.setText("Original / teme");showCandidates.setVisibility(View.GONE);
                    setBusy(false);renderResult();
                });
            }catch(Exception e){error(e);}
        });
    }
    private void renderResult(){
        if(review==null)return;
        if(showingPaleography){
            page.showPaleography(review,paleographyLines);
            soundPlayer.setEnabled(resumed&&AppPrefs.paleographySound(this));
            soundPlayer.update(paleographyLines,soundClips);
            status.setText(paleographySystem+" · "+paleographyLines.size()+" rânduri concordante");
            return;
        }
        soundPlayer.setEnabled(false);
        boolean all=showCandidates.isChecked();
        page.showResult(review,all?candidates:verified);
        status.setText(verified.size()+" concordante · "+Math.max(0,candidates.size()-verified.size())+" candidați");
    }
    private void showHits(List<MatchHit> hits){
        StringBuilder details=new StringBuilder();
        for(MatchHit h:hits){
            if(details.length()>0)details.append("\n\n");
            details.append(h.node.profileName).append(" / ").append(h.node.path).append("\n")
                    .append(h.originalText).append(" → ").append(h.searchTerm)
                    .append("\n").append(verified.contains(h)?"Două citiri concordante":"Candidat neconfirmat");
        }
        new AlertDialog.Builder(this).setTitle("Potriviri").setMessage(details).setPositiveButton("Închide",null).show();
    }
    @Override protected void onActivityResult(int request,int result,@Nullable Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        Uri uri=data.getData();setBusy(true);status.setText("Se deschide imaginea…");
        worker.execute(()->{
            try{
                Bitmap bitmap=decode(uri);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){bitmap.recycle();return;}useSource(bitmap);
                });
            }catch(Exception e){error(e);}
        });
    }
    private Bitmap decode(Uri uri)throws Exception{
        if(Build.VERSION.SDK_INT>=28){
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,src)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                double s=Math.min(1d,2600d/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,(int)Math.round(w*s)),Math.max(1,(int)Math.round(h*s)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(stream,null,opt);}
        if(opt.outWidth<=0||opt.outHeight<=0)throw new IllegalArgumentException("Imagine invalidă");
        int sample=1;while(Math.max(opt.outWidth,opt.outHeight)/sample>2600)sample*=2;
        opt.inJustDecodeBounds=false;opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try(InputStream stream=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(stream,null,opt);}
        if(bitmap==null)throw new IllegalArgumentException("Imagine invalidă");
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            androidx.exifinterface.media.ExifInterface exif=new androidx.exifinterface.media.ExifInterface(stream);
            int rotation=exif.getRotationDegrees();boolean flip=exif.isFlipped();
            if(rotation!=0||flip){
                Matrix matrix=new Matrix();if(flip)matrix.postScale(-1,1);matrix.postRotate(rotation);
                Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}
            }
        }catch(java.io.IOException ignored){}
        return bitmap;
    }
    private void setBusy(boolean value){
        busy=value;open.setEnabled(!value);edit.setEnabled(!value&&source!=null);analyse.setEnabled(!value&&source!=null);
        bow.setEnabled(!value);contrast.setEnabled(!value);deskew.setEnabled(!value);showCandidates.setEnabled(!value);
        page.setEnabled(!value);
        paleography.setEnabled(!value);
    }
    private void error(Exception e){
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);status.setText("OCR: "+(e.getMessage()==null?"eroare":e.getMessage()));}});
    }
    private SeekBar slider(LinearLayout root,String title,int maximum,int initial){
        TextView label=new TextView(this);label.setText(title);root.addView(label);
        SeekBar seek=new SeekBar(this);seek.setMax(maximum);seek.setProgress(initial);root.addView(seek);
        return seek;
    }
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(40,77,115)));return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){
        super.onResume();resumed=true;
        if(showingResult&&showingPaleography&&!busy)renderResult();
    }
    @Override protected void onPause(){
        resumed=false;if(soundPlayer!=null)soundPlayer.setEnabled(false);super.onPause();
    }
    @Override protected void onDestroy(){
        if(soundPlayer!=null)soundPlayer.close();worker.shutdownNow();super.onDestroy();
    }
}
