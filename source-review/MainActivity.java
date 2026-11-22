package ro.bibliotopicsearch.app;

import static androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.text.InputType;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 101;
    private static final int REQ_SAVE_FRAME = 501;
    private static final int REQ_EXPORT_NOTES = 502;
    private static final int REQ_IMPORT_ZIP = 503;
    private static final int INK = Color.rgb(10,24,34);
    private static final int SURFACE = Color.rgb(16,36,48);
    private static final int PRIMARY = Color.rgb(0,151,151);
    private static final long STALE_OVERLAY_MS = 750L;

    private PreviewView previewView;
    private ImageView freezeImage;
    private OverlayView overlayView;
    private AllMatchesView allMatchesView;
    private TextView mapTitle;
    private TextView mapMetrics;
    private TextView statusDot;
    private TextView statusText;
    private Button ocrButton;
    private Button freezeButton;
    private Button saveButton;
    private Button torchButton;
    private Button filterButton;
    private Button ruleButton;
    private Button notesButton;
    private boolean onlySameLine = true;
    private boolean frozenAnalyzing = false;
    private int frozenGeneration = 0;
    private String lastRenderedLens = "";
    private String pendingCsv;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private MlKitAnalyzer analyzer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile MultiThemeEngine.Plan multiPlan;
    private MultiThemeEngine.Scan lastScan;
    private Text lastOcrText;
    private String selectedLens = MultiThemeEngine.ALL;
    private String selectedCategory = CategoryIndex.ALL;
    private LinearLayout categoryStrip;
    private final Map<String,Button> categoryButtons = new LinkedHashMap<>();
    private LinearLayout lensStrip;
    private final Map<String, Button> lensButtons = new LinkedHashMap<>();

    private TopicMap topicMap;
    private boolean frozen = false;
    private boolean torchEnabled = false;
    private boolean ocrEnabled = true;
    private Bitmap frozenBitmap;
    private ToneGenerator toneGenerator;
    private long lastFeedbackAt = 0L;
    private String lastFeedbackSignature = "";

    private final Runnable staleOverlayClear = () -> {
        if (!frozen && ocrEnabled && overlayView != null) {
            lastScan = null;
            lastOcrText = null;
            overlayView.clearHits();
            if(allMatchesView!=null)allMatchesView.clearHits();
            refreshLensButtons();
            refreshCategoryButtons();
            setStatus("OCR live • caut text nou", Color.rgb(78, 201, 126));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ocrEnabled = AppPrefs.ocrEnabled(this);
        buildUi();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 22);
        overlayView.setOnHitTapListener(this::showHitDetails);
        allMatchesView.setOnHitTapListener(this::showHitDetails);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        topicMap = TopicMapStore.load(this);
        onlySameLine = getSharedPreferences("bts2_view", MODE_PRIVATE)
                .getBoolean("only_same_line", true);
        multiPlan = MultiThemeEngine.compile(this);
        if (!multiPlan.hasLens(selectedLens)) selectedLens = MultiThemeEngine.ALL;
        if(!CategoryIndex.known(selectedCategory))selectedCategory=CategoryIndex.ALL;
        if (frozen && lastOcrText != null) {
            lastScan = MultiThemeEngine.scan(lastOcrText, multiPlan);
        } else if (!frozen) {
            lastScan = null;
        }
        updateHeader();
        rebuildLensButtons();
        rebuildCategoryButtons();
        updateRuleButton();
        updateNotesButton();
        updateOcrButton();
        renderLens();
    }

    @Override
    protected void onDestroy() {
        if (cameraController != null) {
            cameraController.clearImageAnalysisAnalyzer();
            cameraController.unbind();
        }
        if (recognizer != null) recognizer.close();
        mainHandler.removeCallbacks(staleOverlayClear);
        analysisExecutor.shutdown();
        if (toneGenerator != null) toneGenerator.release();
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView=new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView,match());

        freezeImage=new ImageView(this);
        freezeImage.setScaleType(ImageView.ScaleType.FIT_XY);
        freezeImage.setBackgroundColor(Color.BLACK);
        freezeImage.setVisibility(View.GONE);
        root.addView(freezeImage,match());

        overlayView=new OverlayView(this);
        root.addView(overlayView,match());
        allMatchesView=new AllMatchesView(this);
        root.addView(allMatchesView,match());

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(15),dp(8),dp(15),dp(11));
        top.setBackgroundColor(Color.argb(241,10,24,34));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView appTitle=new TextView(this);
        appTitle.setText("BiblioTopicSearch");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(18f);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(appTitle,new LinearLayout.LayoutParams(0,dp(46),1f));
        notesButton=smallTopButton("FIȘE");
        Button settingsButton=smallTopButton("SETĂRI");
        titleRow.addView(notesButton);
        titleRow.addView(settingsButton);
        top.addView(titleRow);
        notesButton.setOnClickListener(v->showNotes());
        settingsButton.setOnClickListener(v->
                startActivity(new Intent(this,SettingsActivity.class)));

        LinearLayout mapRow=new LinearLayout(this);
        mapRow.setGravity(Gravity.CENTER_VERTICAL);
        mapTitle=new TextView(this);
        mapTitle.setTextSize(13);
        mapTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mapTitle.setTextColor(Color.rgb(223,240,245));
        mapTitle.setSingleLine(true);
        mapTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mapTitle.setOnClickListener(v->openLibrary());
        mapRow.addView(mapTitle,new LinearLayout.LayoutParams(0,dp(42),1f));
        filterButton=smallTopButton("FILTRE");
        mapRow.addView(filterButton);
        filterButton.setOnClickListener(v->showThemeFilter());
        Button importButton=smallTopButton("IMPORT ZIP");
        mapRow.addView(importButton);
        importButton.setOnClickListener(v->importThemeZip());
        top.addView(mapRow);

        mapMetrics=new TextView(this);
        mapMetrics.setTextColor(Color.rgb(158,191,204));
        mapMetrics.setTextSize(11.5f);
        mapMetrics.setPadding(0,0,0,dp(6));
        top.addView(mapMetrics);

        LinearLayout lensLabel=new LinearLayout(this);
        lensLabel.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=new TextView(this);
        label.setText("LENTILE");
        label.setTextColor(Color.rgb(167,204,214));
        label.setTextSize(10);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lensLabel.addView(label,new LinearLayout.LayoutParams(0,dp(42),1f));
        ruleButton=smallTopButton("REGULĂ: LINIE");
        lensLabel.addView(ruleButton);
        ruleButton.setOnClickListener(v->chooseIntersectionRule());
        top.addView(lensLabel);

        HorizontalScrollView lensScroll=new HorizontalScrollView(this);
        lensScroll.setHorizontalScrollBarEnabled(false);
        lensScroll.setFillViewport(false);
        lensStrip=new LinearLayout(this);
        lensStrip.setGravity(Gravity.CENTER_VERTICAL);
        lensStrip.setOrientation(LinearLayout.HORIZONTAL);
        lensScroll.addView(lensStrip);
        top.addView(lensScroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        LinearLayout categoryHeading=new LinearLayout(this);
        categoryHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView catTitle=new TextView(this);
        catTitle.setText("CATEGORII • detectate simultan");
        catTitle.setTextColor(Color.rgb(164,202,216));
        catTitle.setTextSize(10.5f);
        catTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        categoryHeading.addView(catTitle,new LinearLayout.LayoutParams(0,dp(36),1f));
        Button indexButton=smallTopButton("INDEX INTERN");
        categoryHeading.addView(indexButton);
        indexButton.setOnClickListener(v->editCategoryIndex());
        top.addView(categoryHeading);
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryStrip=new LinearLayout(this);
        categoryStrip.setOrientation(LinearLayout.HORIZONTAL);
        categoryStrip.setGravity(Gravity.CENTER_VERTICAL);
        categoryScroll.addView(categoryStrip);
        top.addView(categoryScroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        LinearLayout statusRow=new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0,dp(6),0,0);
        statusDot=new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(16);
        statusRow.addView(statusDot,new LinearLayout.LayoutParams(dp(24),dp(35)));
        statusText=new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(Color.rgb(230,243,244));
        statusText.setMaxLines(2);
        statusRow.addView(statusText,new LinearLayout.LayoutParams(0,dp(40),1f));
        top.addView(statusRow);
        FrameLayout.LayoutParams topLayout=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP);
        root.addView(top,topLayout);

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12),dp(12),dp(12),dp(14));
        GradientDrawable surface=new GradientDrawable();
        surface.setColor(Color.argb(244,10,24,34));
        surface.setCornerRadii(new float[]{dp(17),dp(17),dp(17),dp(17),0,0,0,0});
        bottom.setBackground(surface);

        ocrButton=primaryButton(ocrEnabled?"● OCR ACTIV":"Ⅱ OCR OPRIT");
        bottom.addView(ocrButton,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        ocrButton.setOnClickListener(v->toggleOcr());

        LinearLayout actions=new LinearLayout(this);
        actions.setPadding(0,dp(9),0,0);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button libraryButton=actionButton("HĂRȚI");
        freezeButton=actionButton("ÎNGHEAȚĂ");
        saveButton=actionButton("SALVEAZĂ");
        torchButton=actionButton("LUMINĂ");
        actions.addView(libraryButton,weightedButton());
        actions.addView(freezeButton,weightedButton());
        actions.addView(saveButton,weightedButton());
        actions.addView(torchButton,weightedButton());
        bottom.addView(actions);
        TextView note=new TextView(this);
        note.setText("OCR local • potrivirile nu reprezintă concluzii istorice");
        note.setTextColor(Color.rgb(152,180,192));
        note.setTextSize(10.5f);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(3),dp(7),dp(3),0);
        bottom.addView(note);
        root.addView(bottom,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM));

        libraryButton.setOnClickListener(v->openLibrary());
        freezeButton.setOnClickListener(v->toggleFreeze());
        saveButton.setOnClickListener(v->saveFrozenFrame());
        torchButton.setOnClickListener(v->toggleTorch());
        saveButton.setEnabled(false);
        setContentView(root);
        setStatus(ocrEnabled?"OCR local pregătit • orientează camera spre pagină":
                "Camera activă • OCR în pauză",
                ocrEnabled?Color.rgb(46,211,180):Color.rgb(174,184,191));
    }

    private Button smallTopButton(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(10.5f);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(41,79,95)));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));
        p.setMargins(dp(3),0,0,0);
        b.setLayoutParams(p);
        return b;
    }

    private void setupCamera() {
        if (cameraController != null) return;

        cameraController = new LifecycleCameraController(this);
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        previewView.setController(cameraController);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        analyzer = new MlKitAnalyzer(
                Collections.singletonList(recognizer),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                analysisExecutor,
                result -> {
                    if (!ocrEnabled || frozen) return;
                    Text text = result.getValue(recognizer);
                    if (text == null) {
                        runOnUiThread(() -> {
                            if (!ocrEnabled || frozen) return;
                            lastScan = null;
                            lastOcrText = null;
                            renderLens();
                            setStatus("OCR live • niciun text citit încă", Color.rgb(78, 201, 126));
                        });
                        return;
                    }

                    // OCR se face o singură dată; același text este verificat în toate hărțile.
                    MultiThemeEngine.Plan plan = multiPlan;
                    if (plan == null) return;
                    MultiThemeEngine.Scan scan = MultiThemeEngine.scan(text, plan);
                    int recognizedWords = countRecognizedWords(text);
                    runOnUiThread(() -> {
                        if (frozen || !ocrEnabled || plan != multiPlan) return;
                        lastOcrText = text;
                        lastScan = scan;
                        applyThemeScan(scan, recognizedWords, plan.termCount);
                    });
                }
        );

        if (ocrEnabled) attachAnalyzer();
        cameraController.bindToLifecycle(this);
    }

    private void attachAnalyzer() {
        if (cameraController != null && analyzer != null) {
            cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer);
        }
    }

    private void toggleOcr() {
        ocrEnabled = !ocrEnabled;
        AppPrefs.setOcrEnabled(this, ocrEnabled);
        mainHandler.removeCallbacks(staleOverlayClear);

        if (cameraController != null) {
            if (ocrEnabled) attachAnalyzer();
            else cameraController.clearImageAnalysisAnalyzer();
        }

        if (!ocrEnabled) {
            if (!frozen) {
                lastScan = null;
                lastOcrText = null;
                renderLens();
            }
            setStatus("Camera live • OCR în pauză", Color.rgb(170, 178, 186));
        } else {
            setStatus("OCR live • caut text", Color.rgb(78, 201, 126));
        }
        updateOcrButton();
    }

    private void updateOcrButton() {
        if (ocrButton == null) return;
        ocrButton.setText(ocrEnabled ? "● OCR ACTIV" : "Ⅱ OCR ÎN PAUZĂ");
        ocrButton.setBackgroundTintList(ColorStateList.valueOf(
                ocrEnabled ? PRIMARY : Color.rgb(73, 91, 102)
        ));
    }

    private void updateHeader() {
        MapProfile active = TopicLibraryStore.getActive(this);
        topicMap = TopicMapStore.load(this);
        int activeNodes = activeNodeCount(topicMap);
        int terms = activeTermCount(topicMap);
        if(mapTitle!=null)mapTitle.setText("CERCETARE PARALELĂ • "+
                (multiPlan==null?0:multiPlan.themes.size())+" TEME   ▾");
        if (mapMetrics != null) {
            MultiThemeEngine.Plan plan = multiPlan;
            int maps = plan == null ? 1 : plan.themes.size();
            int searched = plan == null ? terms : plan.termCount;
            mapMetrics.setText(maps + " teme active  •  " + searched
                    + " termeni unici  •  OCR offline");
        }
    }

    private void openLibrary() {
        startActivity(new Intent(this, TopicLibraryActivity.class));
    }

    private void applyThemeScan(MultiThemeEngine.Scan scan,int recognizedWords,
                                int searchableTerms) {
        if(frozen || !ocrEnabled)return;
        mainHandler.removeCallbacks(staleOverlayClear);
        mainHandler.postDelayed(staleOverlayClear,STALE_OVERLAY_MS);
        renderLens();
        if(searchableTerms<=0) {
            setStatus("Adaugă termeni și activează cel puțin o temă",
                    Color.rgb(245,192,103));return;
        }
        if(recognizedWords<=0) {
            setStatus("Apropie camera și stabilizează pagina",
                    Color.rgb(75,211,184));return;
        }
        setStatus(recognizedWords+" cuvinte OCR  ·  "+scan.uniqueMatches+
                " ocurențe  ·  "+scan.count(MultiThemeEngine.INTERSECTIONS,onlySameLine)+
                " intersecții • categoria: "+
                scan.count(selectedLens,selectedCategory,onlySameLine),
                Color.rgb(75,211,184));
        if(!MultiThemeEngine.OVERVIEW.equals(selectedLens) &&
                !MultiThemeEngine.ALL.equals(selectedLens)) {
            List<MatchHit> visible=scan.visible(selectedLens,selectedCategory,onlySameLine);
            if(!visible.isEmpty()) {
                Set<String> nodes=new HashSet<>();
                for(MatchHit hit:visible)nodes.add(hit.node.path);
                maybeFeedback(visible,nodes);
            }
        }
    }

    private void rebuildLensButtons() {
        if(lensStrip==null || multiPlan==null)return;
        lensStrip.removeAllViews();
        lensButtons.clear();
        addLensButton(MultiThemeEngine.ALL,"TOATE");
        addLensButton(MultiThemeEngine.OVERVIEW,"REZUMAT");
        addLensButton(MultiThemeEngine.INTERSECTIONS,"INTERSECȚII");
        for(MultiThemeEngine.Theme theme:multiPlan.themes) {
            addLensButton(theme.id,theme.name);
        }
        refreshLensButtons();
    }

    private void addLensButton(String id,String title) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(13),0,dp(13),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(45));
        lp.setMargins(0,0,dp(6),0);
        lensStrip.addView(button,lp);
        lensButtons.put(id,button);
        button.setContentDescription("Lentilă: "+title);
        button.setOnClickListener(v->{
            selectedLens=id;
            renderLens();
            if(frozen) setStatus("Cadru fix • "+title+" • "+
                    (lastScan==null?0:lastScan.count(id,selectedCategory,onlySameLine))+" rezultate",
                    Color.rgb(244,195,112));
            if(MultiThemeEngine.OVERVIEW.equals(id) && frozen)showSummary();
        });
        button.setOnLongClickListener(v->{
            if(MultiThemeEngine.INTERSECTIONS.equals(id))
                chooseIntersectionRule();
            else showSummary();
            return true;
        });
    }

    private void refreshLensButtons() {
        if(multiPlan==null)return;
        for(Map.Entry<String,Button> entry:lensButtons.entrySet()) {
            String id=entry.getKey();
            String title=MultiThemeEngine.ALL.equals(id)?"TOATE":
                    MultiThemeEngine.OVERVIEW.equals(id)?"REZUMAT":
                    MultiThemeEngine.INTERSECTIONS.equals(id)?"INTERSECȚII":
                            multiPlan.nameFor(id);
            if(title.length()>19)title=title.substring(0,18)+"…";
            int count=lastScan==null?0:lastScan.count(id,selectedCategory,onlySameLine);
            Button button=entry.getValue();
            String label=title+"  "+count;
            if(!label.contentEquals(button.getText()))button.setText(label);
            boolean active=id.equals(selectedLens);
            button.setBackgroundTintList(ColorStateList.valueOf(
                    active?PRIMARY:count>0?Color.rgb(42,85,94):Color.rgb(36,57,68)));
            button.setAlpha(count==0 && !active?0.78f:1.0f);
        }
    }

    private void renderLens() {
        if(overlayView==null||allMatchesView==null)return;
        overlayView.clearHits();
        List<MatchHit> visible=lastScan==null?Collections.emptyList():
                lastScan.visible(selectedLens,selectedCategory,onlySameLine);
        allMatchesView.setHits(visible,lastScan,selectedCategory);
        lastRenderedLens=selectedLens;
        refreshLensButtons();
        refreshCategoryButtons();
    }

    private void updateRuleButton() {
        if(ruleButton!=null)ruleButton.setText(onlySameLine?
                "REGULĂ: LINIE":"REGULĂ: BLOC");
    }

    private void chooseIntersectionRule() {
        final String[] choices={"Aceeași linie OCR (strict)",
                "Același bloc OCR (mai multe rezultate)"};
        new AlertDialog.Builder(this)
                .setTitle("Regulă de intersecție")
                .setSingleChoiceItems(choices,onlySameLine?0:1,(dialog,which)->{
                    onlySameLine=which==0;
                    getSharedPreferences("bts2_view",MODE_PRIVATE).edit()
                            .putBoolean("only_same_line",onlySameLine).apply();
                    updateRuleButton();
                    if(overlayView!=null)overlayView.clearHits();
                    renderLens();
                    dialog.dismiss();
                })
                .setNegativeButton("Anulează",null).show();
    }

    private void rebuildCategoryButtons() {
        if(categoryStrip==null)return;
        categoryStrip.removeAllViews();
        categoryButtons.clear();
        addCategoryButton(CategoryIndex.ALL);
        for(CategoryIndex.Category category:CategoryIndex.categories())
            addCategoryButton(category.id);
        refreshCategoryButtons();
    }

    private void addCategoryButton(String id) {
        Button button=new Button(this);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11f);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12),0,dp(12),0);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));
        params.setMargins(0,0,dp(6),0);
        categoryStrip.addView(button,params);
        categoryButtons.put(id,button);
        button.setOnClickListener(v->{
            selectedCategory=id;
            if(MultiThemeEngine.OVERVIEW.equals(selectedLens))
                selectedLens=MultiThemeEngine.ALL;
            renderLens();
            if(frozen)setStatus("Tip: "+CategoryIndex.label(id)+" • "+
                    (lastScan==null?0:lastScan.count(selectedLens,id,onlySameLine))+
                    " ocurențe",Color.rgb(241,186,110));
        });
    }

    private void refreshCategoryButtons() {
        if(categoryStrip==null)return;
        for(Map.Entry<String,Button> entry:categoryButtons.entrySet()) {
            String id=entry.getKey();
            Button button=entry.getValue();
            int count=lastScan==null?0:
                    lastScan.count(selectedLens,id,onlySameLine);
            String label=CategoryIndex.label(id)+"  "+count;
            if(!label.contentEquals(button.getText()))button.setText(label);
            boolean active=id.equals(selectedCategory);
            int color=CategoryIndex.ALL.equals(id)?PRIMARY:
                    CategoryIndex.category(id).color;
            button.setBackgroundTintList(ColorStateList.valueOf(
                    active?color:Color.rgb(39,62,76)));
            button.setAlpha(active?1f:count==0?.6f:.87f);
        }
    }

    private void editCategoryIndex() {
        CategoryIndex current=CategoryIndex.load(this,AppPrefs.ignoreDiacritics(this));
        EditText input=new EditText(this);
        input.setText(current.editableText());
        input.setTextSize(12.5f);
        input.setMinLines(10);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ScrollView scroll=new ScrollView(this);
        scroll.setPadding(dp(18),0,dp(18),0);
        scroll.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Index intern • ani, locuri, persoane etc.")
                .setView(scroll)
                .setPositiveButton("SALVEAZĂ",null)
                .setNegativeButton("Anulează",null)
                .setNeutralButton("Restabilește",(d,w)->{
                    CategoryIndex.save(this,CategoryIndex.DEFAULT_TEXT);
                    reloadThemePlan();
                }).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked->{
                    try{
                        CategoryIndex.save(this,input.getText().toString());
                        reloadThemePlan();
                        dialog.dismiss();
                        toast("Indexul de categorii a fost salvat local.");
                    } catch(Exception error) {
                        input.setError(error.getMessage());
                    }
                }));
        dialog.show();
    }

    private void importThemeZip() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        // Permite arhive ZIP care au MIME diferite în diverse aplicații de fișiere.
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,REQ_IMPORT_ZIP);
    }

    private void readThemeZip(Uri uri) {
        int added=0,skipped=0;
        Set<String> existing=new HashSet<>();
        for(MapProfile p:TopicLibraryStore.list(this))
            existing.add(p.name.toLowerCase(java.util.Locale.ROOT));
        try(InputStream stream=getContentResolver().openInputStream(uri)) {
            if(stream==null)throw new IllegalStateException("Arhiva nu poate fi deschisă");
            try(ZipInputStream zip=new ZipInputStream(stream)) {
                ZipEntry entry;
                long total=0;
                while((entry=zip.getNextEntry())!=null) {
                    String name=entry.getName().replace('\\','/');
                    name=name.substring(name.lastIndexOf('/')+1);
                    if(entry.isDirectory() || !name.matches("(?i)^(?:I|II|III|IV|V|VI|VII)_.+\\.txt$")){
                        zip.closeEntry();continue;
                    }
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] bytes=new byte[4096];
                    int len;
                    while((len=zip.read(bytes))!=-1) {
                        out.write(bytes,0,len);
                        total+=len;
                        if(total>2500000||out.size()>250000)
                            throw new IllegalStateException("Arhivă prea mare pentru import");
                    }
                    String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);
                    if(!raw.startsWith("#")||raw.length()<50) {
                        skipped++;zip.closeEntry();continue;
                    }
                    String title=raw.split("\\r?\\n",2)[0]
                            .replaceFirst("^#+\\s*","").trim();
                    if(title.length()>75)title=title.substring(0,75);
                    if(existing.contains(title.toLowerCase(java.util.Locale.ROOT))) {
                        skipped++;zip.closeEntry();continue;
                    }
                    TopicLibraryStore.create(this,title,"România modernă",raw);
                    existing.add(title.toLowerCase(java.util.Locale.ROOT));
                    added++;
                    zip.closeEntry();
                }
            }
        } catch(Exception error) {
            toast("Import incomplet: "+error.getMessage());
            reloadThemePlan();
            return;
        }
        getSharedPreferences(MultiThemeEngine.FILTER_PREFS,MODE_PRIVATE)
                .edit().remove(MultiThemeEngine.FILTER_EXCLUDED).apply();
        selectedLens=MultiThemeEngine.ALL;
        selectedCategory=CategoryIndex.ALL;
        reloadThemePlan();
        toast("Importate "+added+" hărți; "+skipped+" omise. Căutare paralelă activă.");
    }

    private void showThemeFilter() {
        List<MapProfile> all=TopicLibraryStore.list(this);
        String[] names=new String[all.size()];
        boolean[] checked=new boolean[all.size()];
        Set<String> excluded=getSharedPreferences(MultiThemeEngine.FILTER_PREFS,
                MODE_PRIVATE).getStringSet(MultiThemeEngine.FILTER_EXCLUDED,
                Collections.emptySet());
        if(excluded==null)excluded=Collections.emptySet();
        for(int i=0;i<all.size();i++) {
            names[i]=all.get(i).folder+" / "+all.get(i).name;
            checked[i]=!excluded.contains(all.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle("Teme cercetate în paralel")
                .setMultiChoiceItems(names,checked,(dialog,index,isChecked)->
                        checked[index]=isChecked)
                .setNegativeButton("Anulează",null)
                .setNeutralButton("Toate",(dialog,which)->{
                    getSharedPreferences(MultiThemeEngine.FILTER_PREFS,MODE_PRIVATE)
                            .edit().remove(MultiThemeEngine.FILTER_EXCLUDED).apply();
                    reloadThemePlan();
                })
                .setPositiveButton("Aplică",(dialog,which)->{
                    Set<String> hidden=new HashSet<>();
                    for(int i=0;i<all.size();i++)
                        if(!checked[i])hidden.add(all.get(i).id);
                    getSharedPreferences(MultiThemeEngine.FILTER_PREFS,MODE_PRIVATE)
                            .edit().putStringSet(MultiThemeEngine.FILTER_EXCLUDED,hidden).apply();
                    reloadThemePlan();
                }).show();
    }

    private void reloadThemePlan() {
        multiPlan=MultiThemeEngine.compile(this);
        if(!multiPlan.hasLens(selectedLens))selectedLens=MultiThemeEngine.ALL;
        if(lastOcrText!=null)lastScan=MultiThemeEngine.scan(lastOcrText,multiPlan);
        else lastScan=null;
        lastRenderedLens="";
        updateHeader();
        rebuildLensButtons();
        rebuildCategoryButtons();
        renderLens();
    }

    private void showSummary() {
        if(multiPlan==null)return;
        String summary=lastScan==null ?
                "Îndreaptă camera spre o pagină sau îngheață cadrul.":
                lastScan.summary(multiPlan,onlySameLine);
        new AlertDialog.Builder(this)
                .setTitle("Tablou de detecție")
                .setMessage(summary+"\nAceste numere indică potriviri lexicale, nu relevanță confirmată.")
                .setPositiveButton("Închide",null)
                .show();
    }

    private void updateNotesButton() {
        if(notesButton==null)return;
        try(ResearchNotes store=new ResearchNotes(this)) {
            notesButton.setText("FIȘE "+store.count());
        }
    }

    private void setStatus(String text, int dotColor) {
        if(statusText!=null && !text.contentEquals(statusText.getText()))statusText.setText(text);
        if(statusDot!=null)statusDot.setTextColor(dotColor);
    }

    private int countRecognizedWords(Text text) {
        int count = 0;
        if (text == null) return 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) count += line.getElements().size();
        }
        return count;
    }

    private void maybeFeedback(List<MatchHit> hits, Set<String> nodes) {
        long now = System.currentTimeMillis();
        String signature = nodes.toString() + "|" + Math.min(9, hits.size());
        if (signature.equals(lastFeedbackSignature) && now - lastFeedbackAt < 1200L) return;
        if (now - lastFeedbackAt < 650L) return;
        lastFeedbackSignature = signature;
        lastFeedbackAt = now;

        if (AppPrefs.haptic(this)) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                int amplitude = Math.min(150, 45 + nodes.size() * 18 + hits.size() * 4);
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(24L, amplitude));
                } else {
                    vibrator.vibrate(24L);
                }
            }
        }
        if (AppPrefs.sound(this) && toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
        }
    }

    private void toggleFreeze() {
        if(!frozen) {
            Bitmap screenshot=previewView.getBitmap();
            if(screenshot==null) {
                toast("Camera nu are încă o imagine. Încearcă din nou.");
                return;
            }
            frozenBitmap=screenshot;
            frozen=true;
            frozenAnalyzing=true;
            final int generation=++frozenGeneration;
            mainHandler.removeCallbacks(staleOverlayClear);
            if(cameraController!=null)cameraController.clearImageAnalysisAnalyzer();
            lastScan=null;
            lastOcrText=null;
            overlayView.clearHits();
            allMatchesView.clearHits();
            freezeImage.setScaleType(ImageView.ScaleType.FIT_XY);
            freezeImage.setImageBitmap(frozenBitmap);
            freezeImage.setVisibility(View.VISIBLE);
            freezeButton.setText("REIA");
            saveButton.setEnabled(false);
            refreshLensButtons();
            setStatus("Analizez exact imaginea înghețată…",Color.rgb(245,192,103));

            if(recognizer==null) {
                frozenAnalyzing=false;
                saveButton.setEnabled(true);
                setStatus("OCR indisponibil pe cadrul înghețat",Color.rgb(240,145,110));
                return;
            }

            // Coordonatele provin din acelasi bitmap afisat, nu din cadrul live anterior.
            recognizer.process(InputImage.fromBitmap(screenshot,0))
                    .addOnSuccessListener(result->{
                        MultiThemeEngine.Plan plan=multiPlan;
                        if(plan==null || !frozen || generation!=frozenGeneration ||
                                analysisExecutor.isShutdown() || isDestroyed())return;
                        analysisExecutor.execute(()->{
                            MultiThemeEngine.Scan scan=MultiThemeEngine.scan(result,plan);
                            runOnUiThread(()->{
                                if(!frozen || generation!=frozenGeneration ||
                                        multiPlan!=plan)return;
                                lastOcrText=result;
                                lastScan=scan;
                                frozenAnalyzing=false;
                                saveButton.setEnabled(true);
                                overlayView.clearHits();
                                renderLens();
                                setStatus("Cadru fix • "+scan.uniqueMatches+
                                        " potriviri • comută lentilele",Color.rgb(245,192,103));
                            });
                        });
                    })
                    .addOnFailureListener(error->{
                        if(!frozen || generation!=frozenGeneration)return;
                        frozenAnalyzing=false;
                        saveButton.setEnabled(true);
                        setStatus("OCR cadru indisponibil • poți salva imaginea",
                                Color.rgb(240,145,110));
                    });
        } else {
            ++frozenGeneration;
            frozen=false;
            frozenAnalyzing=false;
            if(ocrEnabled)attachAnalyzer();
            lastScan=null;
            lastOcrText=null;
            overlayView.clearHits();
            allMatchesView.clearHits();
            freezeImage.setImageDrawable(null);
            freezeImage.setVisibility(View.GONE);
            // ML Kit poate încă procesa această imagine; o eliberăm prin GC
            // după terminarea procesării asincrone, evitând reciclarea prematură.
            frozenBitmap=null;
            lastRenderedLens="";
            refreshLensButtons();
            freezeButton.setText("ÎNGHEAȚĂ");
            saveButton.setEnabled(false);
            setStatus(ocrEnabled?"Scanare live reluată":"Camera activă • OCR în pauză",
                    ocrEnabled?Color.rgb(75,211,184):Color.rgb(167,183,193));
        }
    }

    private void saveFrozenFrame() {
        if (!frozen || frozenBitmap == null || frozenAnalyzing) {
            toast("Îngheață mai întâi cadrul pe care vrei să-l salvezi.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "BiblioTopicSearch_pasaj.png");
        startActivityForResult(intent, REQ_SAVE_FRAME);
    }

    private void toggleTorch() {
        if (cameraController == null) {
            toast("Camera nu este încă pregătită.");
            return;
        }
        torchEnabled = !torchEnabled;
        cameraController.enableTorch(torchEnabled);
        torchButton.setText(torchEnabled ? "STINGE" : "LUMINĂ");
    }

    private void showHitDetails(MatchHit hit) {
        DictionaryStore.Entry entry;
        try(DictionaryStore store=new DictionaryStore(this)) {
            entry=store.lookup(hit.originalText);
            if(entry==null)entry=store.lookup(hit.searchTerm);
        }

        StringBuilder message=new StringBuilder();
        message.append("Cuvânt/expresie: ").append(hit.originalText)
                .append("\nCăutat: ").append(hit.searchTerm);
        if(lastScan!=null && multiPlan!=null)
            message.append("\n\n").append(lastScan.explain(hit,multiPlan));

        if(entry!=null) {
            message.append("\n\nDefiniție locală:\n").append(entry.definition);
            if(!entry.synonyms.isEmpty())
                message.append("\nSinonime: ").append(entry.synonyms);
            if(!entry.source.isEmpty())
                message.append("\nSursă dicționar: ").append(entry.source);
        }
        new AlertDialog.Builder(this)
                .setTitle("Potrivire OCR")
                .setMessage(message.toString())
                .setPositiveButton("Închide",null)
                .setNeutralButton("FIȘĂ",(d,w)->{
                    if(!frozen || frozenAnalyzing) {
                        toast("Îngheață mai întâi pagina pentru o fișă verificabilă.");
                        return;
                    }
                    editResearchNote(hit);
                })
                .show();
    }

    private EditText noteField(String hint,String value,boolean multiline) {
        EditText input=new EditText(this);
        input.setHint(hint);
        input.setText(value==null?"":value);
        input.setTextSize(13f);
        input.setSingleLine(!multiline);
        if(multiline) {
            input.setMinLines(2);
            input.setMaxLines(5);
            input.setInputType(InputType.TYPE_CLASS_TEXT|
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        input.setPadding(dp(8),dp(8),dp(8),dp(8));
        return input;
    }

    private String capturedOcrBlock(MatchHit hit) {
        if(lastOcrText!=null) {
            for(Text.TextBlock block:lastOcrText.getTextBlocks()) {
                Rect box=block.getBoundingBox();
                if(box!=null && box.contains(Math.round(hit.box.centerX()),
                        Math.round(hit.box.centerY())))return block.getText();
            }
        }
        return hit.originalText;
    }

    private void editResearchNote(MatchHit hit) {
        String suggested=lastScan==null || multiPlan==null?""
                :lastScan.suggestions(hit,multiPlan);
        String previousSource=getSharedPreferences("bts2_view",MODE_PRIVATE)
                .getString("last_source","");
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(12),dp(20),dp(8));
        EditText source=noteField("Sursa / cartea",previousSource,false);
        EditText page=noteField("Pagina (verificată manual)","",false);
        EditText themes=noteField("Teme confirmate de tine",suggested,true);
        EditText excerpt=noteField("Fragment OCR — verifică și corectează",
                capturedOcrBlock(hit),true);
        EditText notes=noteField("Observația ta / relațiile identificate","",true);
        content.addView(source);content.addView(page);
        content.addView(themes);content.addView(excerpt);content.addView(notes);
        TextView warning=new TextView(this);
        warning.setText("Potrivirile și transcrierea OCR sunt sugestii; "
                +"verifică integral pasajul înainte să salvezi.");
        warning.setTextSize(11);
        warning.setPadding(0,dp(10),0,dp(5));
        content.addView(warning);
        scroll.addView(content);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Fișă de cercetare")
                .setView(scroll)
                .setNegativeButton("Anulează",null)
                .setPositiveButton("SALVEAZĂ",null).create();
        dialog.setOnShowListener(ignored->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    String src=source.getText().toString().trim();
                    String pg=page.getText().toString().trim();
                    if(src.isEmpty()) {source.setError("Completează sursa");return;}
                    if(pg.isEmpty()) {page.setError("Verifică pagina");return;}
                    try(ResearchNotes db=new ResearchNotes(this)) {
                        db.add(src,pg,hit.searchTerm,
                                themes.getText().toString().trim(),
                                lastScan==null?"Potrivire OCR":
                                        lastScan.explain(hit,multiPlan),
                                excerpt.getText().toString().trim(),
                                notes.getText().toString().trim());
                    } catch(Exception e) {
                        toast("Nu pot salva fișa: "+e.getMessage());return;
                    }
                    getSharedPreferences("bts2_view",MODE_PRIVATE).edit()
                            .putString("last_source",src).apply();
                    updateNotesButton();
                    dialog.dismiss();
                    toast("Fișă salvată local");
                }));
        dialog.show();
    }

    private void showNotes() {
        List<ResearchNotes.Note> list;
        try(ResearchNotes store=new ResearchNotes(this)) {
            list=store.recent(60);
        }
        StringBuilder message=new StringBuilder();
        if(list.isEmpty())message.append("Nu există încă fișe. Atinge un termen detectat și alege FIȘĂ.");
        for(ResearchNotes.Note note:list) {
            message.append("\n#").append(note.id).append("  ")
                    .append(note.source).append(", p. ").append(note.page).append("\n")
                    .append("Termen: ").append(note.term).append("\n")
                    .append("Teme: ").append(note.themes).append("\n");
            if(!note.observation.isEmpty())
                message.append("Notă: ").append(note.observation).append("\n");
            message.append("──────────\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("Fișele mele • ultimele "+list.size())
                .setMessage(message.toString())
                .setNegativeButton("Închide",null)
                .setPositiveButton("EXPORT CSV",(d,w)->exportResearchNotes())
                .show();
    }

    private void exportResearchNotes() {
        try(ResearchNotes store=new ResearchNotes(this)) {
            pendingCsv=store.exportCsv();
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,"BiblioTopicSearch_fise_istorie.csv");
        startActivityForResult(intent,REQ_EXPORT_NOTES);
    }

    private int activeNodeCount(TopicMap map) {
        int count = 0;
        if (map != null) for (TopicNode node : map.nodes) if (node.enabled) count++;
        return count;
    }

    private int activeTermCount(TopicMap map) {
        int count = 0;
        if (map != null) {
            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;
                count += Math.max(1, node.terms.size());
            }
        }
        return count;
    }

    private TextView badge(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(9);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(PRIMARY));
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11.5f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(41, 79, 95)));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(49), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_IMPORT_ZIP) {
            readThemeZip(uri);
            return;
        }
        if(requestCode==REQ_EXPORT_NOTES) {
            if(pendingCsv==null)return;
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null)throw new IllegalStateException("Destinație indisponibilă");
                out.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                toast("Fișele au fost exportate");
            } catch(Exception e) {
                toast("Export eșuat: "+e.getMessage());
            } finally {pendingCsv=null;}
            return;
        }
        if(requestCode!=REQ_SAVE_FRAME || frozenBitmap==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
            if(out==null)throw new IllegalStateException("Destinație indisponibilă");
            Bitmap composite=Bitmap.createBitmap(
                    Math.max(1,previewView.getWidth()),
                    Math.max(1,previewView.getHeight()),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(composite);
            canvas.drawBitmap(frozenBitmap,null,
                    new Rect(0,0,composite.getWidth(),composite.getHeight()),null);
            overlayView.draw(canvas);
            allMatchesView.draw(canvas);
            composite.compress(Bitmap.CompressFormat.PNG,100,out);
            composite.recycle();
            toast("Imagine salvată");
        } catch(Exception e) {
            toast("Salvare eșuată: "+e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                setStatus("Permisiunea camerei este necesară pentru OCR live.", Color.rgb(235, 100, 100));
            }
        }
    }
}
