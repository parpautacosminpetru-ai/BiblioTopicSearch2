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
    static final String OPEN_IMAGE="open_image",OPEN_MODELS="open_models";
    private static final int PICK=72, PICK_MODEL=73;
    private Uri sourceUri;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap source,review;
    private float[] corners={0,0,1,0,1,1,0,1};
    private List<MatchHit> verified=Collections.emptyList(),candidates=Collections.emptyList();
    private PagePreviewView page;
    private TextView status;
    private Button open,edit,analyse,historical,transliterateDraft,compareOriginal;
    private LinearLayout historicalPanel;
    private android.widget.EditText historicalText;
    private boolean historicalOriginal;
    private String historicalOrigin="Transcriere manuală";
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
        open=button("Imagine");edit=button("Colțuri");analyse=button("OCR latin");
        historical=button("OCR istoric");
        for(Button button:new Button[]{open,edit,analyse,historical})bar.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
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
        historicalPanel=new LinearLayout(this);historicalPanel.setOrientation(LinearLayout.VERTICAL);
        TextView warning=new TextView(this);
        warning.setText("Transcriere de lucru — confruntă fiecare semn cu imaginea. Nu este o lectură certificată.");
        historicalPanel.addView(warning);
        historicalText=new android.widget.EditText(this);
        historicalText.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        historicalText.setGravity(android.view.Gravity.TOP|android.view.Gravity.START);
        historicalText.setMinLines(2);historicalText.setMaxLines(4);
        historicalText.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(LiteralTransliterator.MAX_INPUT)});
        historicalText.setHint("Transcrie sau corectează textul OCR");
        historicalPanel.addView(historicalText,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout draftBar=new LinearLayout(this);
        compareOriginal=button("Original");
        transliterateDraft=button("Transliterează");
        Button hideDraft=button("Ascunde");
        for(Button control:new Button[]{compareOriginal,transliterateDraft,hideDraft})draftBar.addView(control,new LinearLayout.LayoutParams(0,dp(44),1));
        historicalPanel.addView(draftBar);root.addView(historicalPanel);historicalPanel.setVisibility(View.GONE);
        compareOriginal.setOnClickListener(v->{
            if(source==null||busy)return;
            historicalOriginal=!historicalOriginal;
            page.showResult(historicalOriginal||review==null?source:review,Collections.emptyList());
            compareOriginal.setText(historicalOriginal?"Corectată":"Original");
        });
        transliterateDraft.setOnClickListener(v->{
            if(!busy)PaleographyActivity.open(this,historicalText.getText().toString(),historicalOrigin+" · de verificat");
        });
        hideDraft.setOnClickListener(v->{
            historicalPanel.setVisibility(View.GONE);
            if(showingResult&&review!=null)renderResult();else if(source!=null)page.showOriginal(source,corners);
        });
        historical.setOnClickListener(v->historicalOptions());
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
            if(!showingResult&&historicalPanel.getVisibility()!=View.VISIBLE)corners=new float[]{0,0,1,0,1,1,0,1};
            showingResult=false;showingPaleography=false;historicalPanel.setVisibility(View.GONE);
            soundPlayer.setEnabled(false);
            paleography.setVisibility(View.GONE);
            page.showOriginal(source,corners);edit.setText("Resetează");showCandidates.setVisibility(View.GONE);
            status.setText("Ajustează cele patru colțuri");
        });
        analyse.setOnClickListener(v->readLatinPage());
        setContentView(root);UiInsets.apply(root);
        Bitmap draft=PageDraft.take();
        if(draft!=null)useSource(draft);
        else if(saved!=null&&saved.getString("source_uri")!=null){
            sourceUri=Uri.parse(saved.getString("source_uri"));loadImage(sourceUri,saved.getString("historical_text",""));
        }else{status.setText("Alege o imagine sau importă un model OCR.");setBusy(false);
            if(saved==null&&getIntent().getBooleanExtra(OPEN_IMAGE,false))open.post(this::pick);
            else if(saved==null&&getIntent().getBooleanExtra(OPEN_MODELS,false))historical.post(this::historicalOptions);
        }
    }
    private void pick(){
        if(busy)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK);
    }
    private void useSource(Bitmap bitmap){
        Bitmap oldSource=source,oldReview=review;
        source=bitmap;review=null;verified=Collections.emptyList();candidates=Collections.emptyList();
        historicalPanel.setVisibility(View.GONE);historicalText.setText("");historicalOriginal=false;
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
    private void readLatinPage(){
        if(busy||source==null)return;
        if(!showingResult&&historicalPanel.getVisibility()!=View.VISIBLE)corners=page.corners();
        final Bitmap input=source;final float[] quad=corners.clone();final boolean small=page.hasRegionSelection();
        final int curve=bow.getProgress()-100,strength=contrast.getProgress();final boolean straighten=deskew.isChecked();
        if(historicalText.length()>0){
            new AlertDialog.Builder(this).setTitle("Înlocuiești textul de lucru?")
                .setNegativeButton("Renunță",null).setPositiveButton("Rulează OCR",(d,w)->readLatinPageConfirmed(input,quad,small,curve,strength,straighten)).show();
        }else readLatinPageConfirmed(input,quad,small,curve,strength,straighten);
    }
    private void readLatinPageConfirmed(Bitmap input,float[] quad,boolean small,int curve,int strength,boolean straighten){
        soundPlayer.setEnabled(false);setBusy(true);status.setText("OCR latin local…");
        worker.execute(()->{
            Bitmap rectified=null,enhanced=null;TextRecognizer client=null;
            try{
                rectified=small?PageImageProcessor.rectifyGlyph(input,quad):PageImageProcessor.rectify(input,quad,curve,straighten);
                enhanced=PageImageProcessor.historicalContrast(rectified,strength);
                client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                String result=Tasks.await(client.process(InputImage.fromBitmap(enhanced,0)),30,TimeUnit.SECONDS).getText();
                if(result.length()>LiteralTransliterator.MAX_INPUT)throw new IllegalArgumentException("Text prea lung; selectează o zonă mai mică.");
                final Bitmap output=enhanced;enhanced=null;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){output.recycle();return;}
                    Bitmap old=review;review=output;showingResult=true;lastOcrText=result;
                    verified=Collections.emptyList();candidates=Collections.emptyList();rawPageOcr=null;correctedPageOcr=null;paleographyLines=Collections.emptyList();
                    setBusy(false);showHistoricalDraft(result,"OCR latin (candidat)");
                    if(result.isEmpty())status.setText("Niciun text recunoscut. Încearcă altă scriere sau transcriere manuală.");
                    if(old!=null&&old!=source&&old!=output&&!old.isRecycled())old.recycle();
                });
            }catch(Exception|LinkageError e){error(new IllegalStateException(e.getMessage()==null?"OCR indisponibil.":e.getMessage(),e));}
            finally{if(client!=null)client.close();if(rectified!=null)rectified.recycle();if(enhanced!=null)enhanced.recycle();}
        });
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
                    historicalPanel.setVisibility(View.GONE);
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
    private void historicalOptions(){
        if(busy)return;
        if(source==null){
            new AlertDialog.Builder(this).setTitle("OCR istoric")
                .setItems(new String[]{"Alege imaginea","Importă model OCR (.traineddata)","Modele disponibile"},(d,i)->{
                    if(i==0)pick();
                    else if(i==1)pickHistoricalModel();
                    else new AlertDialog.Builder(this).setTitle("Modele OCR disponibile").setMessage(android.text.TextUtils.join(", ",HistoricalOcr.models(this))).setPositiveButton("Închide",null).show();
                }).setNegativeButton("Închide",null).show();return;
        }
        String[] options={"Transcriere manuală / continuă textul","Importă model OCR (.traineddata)",
            "OCR multialfabet — selectează modele","Învață semnul selectat","Identifică semnul selectat","Gestionează catalogul de semne","Selectează zonă / semn prin glisare","Căutare tematică (hărți)"};
        new AlertDialog.Builder(this).setTitle("Paleografie universală").setItems(options,(d,which)->{
            if(which==0){
                if(!showingResult&&historicalPanel.getVisibility()!=View.VISIBLE)corners=page.corners();
                showHistoricalDraft(historicalText.getText().toString(),"Transcriere manuală");
            }else if(which==1){
                pickHistoricalModel();
            }else if(which==2){
                HistoricalOcrOptions.choose(this,HistoricalOcr.models(this),(models,mode)->{
                    if(historicalText.length()>0)new AlertDialog.Builder(this).setTitle("Înlocuiești transcrierea de lucru?")
                        .setMessage("Exportă întâi corecturile importante. OCR-ul nou este doar un candidat.")
                        .setNegativeButton("Renunță",null).setPositiveButton("Rulează OCR",(x,y)->historicalAnalyse(models,mode)).show();
                    else historicalAnalyse(models,mode);
                });
            }else if(which==6)selectGlyphRegion();else if(which==7)analyse();else glyphDialog(which);
        }).setNegativeButton("Închide",null).show();
    }
    private void pickHistoricalModel(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,PICK_MODEL);
    }
    private void selectGlyphRegion(){
        if(busy||source==null)return;
        showingResult=false;showingPaleography=false;soundPlayer.setEnabled(false);
        historicalPanel.setVisibility(View.GONE);paleography.setVisibility(View.GONE);showCandidates.setVisibility(View.GONE);
        page.selectRegion(source);edit.setText("Colțuri");
        status.setText("Glisează de la un colț la cel opus în jurul semnului. Apoi revino la OCR istoric → Învață sau Identifică.");
    }
    private void glyphDialog(int action){
        if(busy||source==null)return;
        if(action!=5&&!page.hasRegionSelection()){
            selectGlyphRegion();return;
        }
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(16),0,dp(16),0);
        android.widget.EditText system=new android.widget.EditText(this);system.setSingleLine(true);
        system.setHint("Nume sistem de scriere");system.setText(getPreferences(MODE_PRIVATE).getString("glyph_system","Sistemul meu"));fields.addView(system);
        android.widget.EditText label=new android.widget.EditText(this);
        label.setHint("Semn Unicode sau cod propriu, ex. [S001]");label.setSingleLine(true);
        if(action==3)fields.addView(label);
        TextView help=new TextView(this);help.setText(action==5?"Alege catalogul ale cărui exemple vrei să le gestionezi.":"Încadrează un singur semn cu margine. Formele similare sunt propuneri, nu traduceri.");fields.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(action==3?"Învață semn":action==4?"Compară semn":"Catalog vizual")
            .setView(fields).setNegativeButton("Renunță",null).setPositiveButton("Continuă",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            try{
                String name=GlyphLibrary.validate(system.getText().toString(),"Sistem");
                String code=action==3?GlyphLibrary.validate(label.getText().toString(),"Semn"):"";
                getPreferences(MODE_PRIVATE).edit().putString("glyph_system",name).apply();
                dialog.dismiss();if(action==5)manageGlyphs(name);else processGlyph(name,code,action==3);
            }catch(IllegalArgumentException e){help.setText(e.getMessage());}
        }));dialog.show();
    }
    private void manageGlyphs(String system){
        setBusy(true);
        worker.execute(()->{
            try{
                List<String> labels=GlyphLibrary.labels(this,system);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setBusy(false);
                    if(labels.isEmpty()){status.setText("Catalogul nu are exemple.");return;}
                    new AlertDialog.Builder(this).setTitle("Ștergere exemple — "+system).setItems(labels.toArray(new String[0]),(d,i)->{
                        String label=labels.get(i);
                        new AlertDialog.Builder(this).setMessage("Ștergi toate exemplele vizuale pentru "+label+"?").setNegativeButton("Renunță",null)
                            .setPositiveButton("Șterge",(x,y)->{
                                setBusy(true);worker.execute(()->{try{GlyphLibrary.remove(this,system,label);
                                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);status.setText("Exemple șterse.");}});
                                }catch(Exception e){error(e);}});
                            }).show();
                    }).setNegativeButton("Închide",null).show();
                });
            }catch(Exception e){error(e);}
        });
    }
    private void processGlyph(String system,String label,boolean learn){
        corners=page.corners();final float[] quad=corners.clone();final Bitmap input=source;
        setBusy(true);status.setText(learn?"Se salvează exemplul vizual…":"Se compară formele…");
        worker.execute(()->{
            Bitmap cropped=null;
            try{
                cropped=PageImageProcessor.rectifyGlyph(input,quad);
                GlyphShape shape=GlyphLibrary.shape(cropped);
                if(learn){GlyphLibrary.add(this,system,label,shape);
                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);status.setText("Exemplu salvat: "+label+" · adaugă variante ale semnului.");}});
                }else{
                    List<GlyphShape.Candidate> ranked=GlyphShape.rank(shape,GlyphLibrary.examples(this,system));
                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);showGlyphCandidates(ranked);}});
                }
            }catch(Exception e){error(e);}
            finally{if(cropped!=null)cropped.recycle();}
        });
    }
    private void showGlyphCandidates(List<GlyphShape.Candidate> ranked){
        if(ranked.isEmpty()){status.setText("Nu există exemple pentru sistem. Învață mai întâi semne.");return;}
        int n=Math.min(5,ranked.size());String[] options=new String[n+1];
        for(int i=0;i<n;i++)options[i]=ranked.get(i).label+" · distanță "+String.format(java.util.Locale.ROOT,"%.2f",ranked.get(i).distance);
        options[n]="Necunoscut / fără confirmare";
        new AlertDialog.Builder(this).setTitle(GlyphShape.strong(ranked)?"Potrivire vizuală posibilă — confirmă":"Forme ambigue — verifică atent")
            .setItems(options,(d,i)->{
                if(i==n){status.setText("Semn neconfirmat; textul nu a fost modificat.");return;}
                String next=historicalText.getText().toString()+ranked.get(i).label;
                if(next.length()>LiteralTransliterator.MAX_INPUT){status.setText("Text prea lung; exportă fragmentul.");return;}
                showHistoricalDraft(next,"Semne confirmate manual");
            }).setNegativeButton("Renunță",null).show();
    }
    private void showHistoricalDraft(String text,String origin){
        historicalOrigin=origin;showingPaleography=false;soundPlayer.setEnabled(false);
        historicalText.setText(text);historicalPanel.setVisibility(View.VISIBLE);
        showCandidates.setVisibility(View.GONE);paleography.setVisibility(View.GONE);
        historicalOriginal=review==null;compareOriginal.setText(historicalOriginal?"Corectată":"Original");
        compareOriginal.setEnabled(review!=null);
        page.showResult(review==null?source:review,Collections.emptyList());
        status.setText(origin+" · verifică manual înainte de citare");
    }
    private void historicalAnalyse(List<String> models,int mode){
        final String model=HistoricalModelName.expression(models);
        if(busy||source==null)return;
        if(!showingResult&&historicalPanel.getVisibility()!=View.VISIBLE)corners=page.corners();
        final Bitmap input=source;final float[] quad=corners.clone();
        final int curve=bow.getProgress()-100,strength=contrast.getProgress();
        final boolean small=page.hasRegionSelection()&&(mode==7||mode==8||mode==10);
        final boolean straighten=deskew.isChecked();
        soundPlayer.setEnabled(false);setBusy(true);status.setText("OCR local "+model+"…");
        worker.execute(()->{
            Bitmap rectified=null,enhanced=null;
            try{
                rectified=small?PageImageProcessor.rectifyGlyph(input,quad):PageImageProcessor.rectify(input,quad,curve,straighten);
                enhanced=PageImageProcessor.historicalContrast(rectified,strength);
                String text=HistoricalOcr.recognize(this,models,mode,enhanced);
                if(text.length()>LiteralTransliterator.MAX_INPUT)throw new IllegalArgumentException("Text prea lung; selectează o zonă mai mică.");
                if(Thread.currentThread().isInterrupted())return;
                Bitmap output=enhanced;enhanced=null;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){output.recycle();return;}
                    Bitmap old=review;review=output;showingResult=true;
                    verified=Collections.emptyList();candidates=Collections.emptyList();
                    lastOcrText="";rawPageOcr=null;correctedPageOcr=null;paleographyLines=Collections.emptyList();
                    setBusy(false);showHistoricalDraft(text,"OCR "+model+" (candidat)");
                    if(text.isEmpty())status.setText("Niciun text recunoscut. Transcrie manual sau încearcă alt model.");
                    if(old!=null&&old!=source&&old!=output&&!old.isRecycled())old.recycle();
                });
            }catch(Exception|LinkageError e){error(new IllegalStateException(e.getMessage()==null?"Motor OCR indisponibil.":e.getMessage(),e));}
            finally{if(rectified!=null)rectified.recycle();if(enhanced!=null)enhanced.recycle();}
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
        if(request==PICK_MODEL){
            if(result!=RESULT_OK||data==null||data.getData()==null||busy)return;
            Uri uri=data.getData();setBusy(true);status.setText("Se verifică modelul OCR…");
            worker.execute(()->{
                try{
                    String model=HistoricalOcr.install(this,uri);
                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){setBusy(false);status.setText("Model instalat: "+model);historicalOptions();}});
                }catch(Exception|LinkageError e){error(new IllegalStateException(e.getMessage()==null?"Model OCR indisponibil.":e.getMessage(),e));}
            });
            return;
        }
        if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(SecurityException ignored){}
        sourceUri=uri;loadImage(uri,"");
    }
    private void loadImage(Uri uri,String restoredText){
        setBusy(true);status.setText("Se deschide imaginea…");
        worker.execute(()->{
            try{
                Bitmap bitmap=decode(uri);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){bitmap.recycle();return;}useSource(bitmap);
                    if(!restoredText.isEmpty())showHistoricalDraft(restoredText,"Transcriere restaurată");
                });
            }catch(Exception e){error(e);}
        });
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        if(sourceUri!=null){state.putString("source_uri",sourceUri.toString());state.putString("historical_text",historicalText.getText().toString());}
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
        historical.setEnabled(!value);
        historicalText.setEnabled(!value);transliterateDraft.setEnabled(!value);
        compareOriginal.setEnabled(!value&&review!=null);
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
