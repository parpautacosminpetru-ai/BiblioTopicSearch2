package ro.bibliotopicsearch.app;

import static androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED;

import android.Manifest;
import android.app.ActivityManager;
import android.os.PowerManager;
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
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.text.TextUtils;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 101;
    private static final int REQ_SAVE_FRAME = 501;
    private final FrameFreshness frameFreshness = new FrameFreshness();
    private static final String TAG = "BiblioLive";

    private PreviewView previewView;
    private ImageView freezeImage;
    private OverlayView overlayView;
    private PaleographyOverlayView paleographyOverlay;
    private Button paleoButton;
    private volatile boolean paleographyEnabled;
    private volatile LiteralTransliterator livePaleographyEngine;
    private Map<String,java.io.File> liveSoundClips=Collections.emptyMap();
    private Map<String,java.io.File> frozenSoundClips=Collections.emptyMap();
    private List<PaleographyLine> frozenPaleoLines=Collections.emptyList();
    private PaleographySoundPlayer paleoSound;
    private String livePaleographyName = "";
    private final PaleographyProjection paleographyProjection = new PaleographyProjection();
    private Button mapButton;
    private TextView statusText;
    private Button ocrButton, freezeButton, saveButton, torchButton;
    private HorizontalScrollView legendScroll;
    private LinearLayout legendPanel;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private AdaptiveAnalyzer analyzer;
    private final LiveFrameStabilizer stabilizer = new LiveFrameStabilizer();
    private volatile int scanPrecision = 100;
    private long overlayDeadline;
    private String latestOcrText = "", frozenOcrText = "";
    private long latestOcrAt = -1L;
    private float previousZoom = -1f;
    private boolean lowRam, reducedResolution;
    private volatile long lastResolutionChange = -60000L;
    private volatile int recoveryAttempts;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService matchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService planExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LatestOnly<Long> planJobs = new LatestOnly<>(planExecutor, this::compilePlan);
    private final LatestOnly<Frame> frameJobs = new LatestOnly<>(matchExecutor, this::matchFrame);
    private final LatestOnly<FrameResult> uiJobs = new LatestOnly<>(command -> mainHandler.post(command), this::applyFrame);

    private volatile TopicMatcher.SearchPlan searchPlan;
    private volatile long scanEpoch, planRequest;
    private volatile boolean resumed, destroyed, frozen, ocrEnabled = true;
    private TopicMap topicMap;
    private List<MapProfile> allProfiles = Collections.emptyList();
    private Set<String> selectedProfiles = new LinkedHashSet<>();
    private boolean torchEnabled;
    private Bitmap frozenBitmap, frozenOriginal, frozenPaleography;
    private boolean frozenPaleographyCapture, frozenRevealOriginal, freezeProcessing;
    private volatile long freezeRevision;
    private final ExecutorService stillExecutor = Executors.newSingleThreadExecutor();
    private final LatestOnly<FrozenJob> stillJobs = new LatestOnly<>(stillExecutor, this::analyseFrozenPaleography);
    private ToneGenerator toneGenerator;
    private long lastFeedbackAt;
    private String lastFeedbackSignature = "";
    private boolean floatingLabelsEnabled;
    private final Set<String> visibleNodePaths = new HashSet<>();
    private final Set<String> knownNodePaths = new HashSet<>();
    private List<MatchHit> latestAllHits = Collections.emptyList();
    private String lastLegendSignature = "";

    private static final class Frame {
        final long epoch, receivedAt;
        final TopicMatcher.SearchPlan plan;
        final Text text;
        Frame(long epoch, TopicMatcher.SearchPlan plan, Text text, long startedAt) {
            this.epoch = epoch;
            this.plan = plan;
            this.text = text;
            receivedAt = startedAt;
        }
    }

    private static final class FrameResult {
        final Frame frame;
        final List<MatchHit> hits;
        final int words;
        final List<PaleographyLine> paleography;
        FrameResult(Frame frame, List<MatchHit> hits, int words, List<PaleographyLine> paleography) {
            this.frame = frame;
            this.hits = hits;
            this.words = words;
            this.paleography = paleography;
        }
    }

    private static final class FrozenJob {
        final long revision;
        final Bitmap source;
        final LiteralTransliterator engine;
        FrozenJob(long revision,Bitmap source,LiteralTransliterator engine){
            this.revision=revision;this.source=source;this.engine=engine;
        }
    }

    private final Runnable retainedExpiry = this::expireRetained;
    private final Runnable staleOverlayClear = () -> {
        if (resumed && !frozen && ocrEnabled && !destroyed) {
            clearMatches();
            idleStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ocrEnabled = AppPrefs.ocrEnabled(this);
        floatingLabelsEnabled = AppPrefs.floatingLabels(this);
        paleographyEnabled = AppPrefs.paleographyLive(this);
        buildUi();
        paleoSound=new PaleographySoundPlayer(this,new PaleographySoundPlayer.Listener(){
            @Override public void highlight(PaleographyLine line,LiteralTransliterator.RuleMatch cue){
                if(!destroyed&&paleographyOverlay!=null)paleographyOverlay.setSpeaking(line,cue);
            }
            @Override public void unavailable(String language){
                if(resumed&&!destroyed)toast("Sunet indisponibil: "+language);
            }
        });
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 22);
        overlayView.setOnHitTapListener(this::showHitChoices);

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
        resumed = true;
        scanPrecision = AppPrefs.precision(this);
        recoveryAttempts = 0;
        ocrEnabled = AppPrefs.ocrEnabled(this);
        floatingLabelsEnabled = AppPrefs.floatingLabels(this);
        paleographyEnabled = AppPrefs.paleographyLive(this)&&!TransliterationStore.selected(this).isEmpty();
        if(!paleographyEnabled)AppPrefs.setPaleographyLive(this,false);
        updateOcrButton();
        updatePaleoUi();
        reloadPlan();
        if(frozen&&frozenPaleographyCapture)displayFrozenPaleography();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if(paleoSound!=null)paleoSound.setEnabled(false);
        ++planRequest;
        planJobs.clear();
        stopAnalysis();
        clearMatches();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if(paleoSound!=null)paleoSound.close();
        ++freezeRevision;
        stopAnalysis();
        if (cameraController != null) cameraController.unbind();
        if (recognizer != null) recognizer.close();
        planJobs.close();
        stillJobs.close();
        stillExecutor.shutdownNow();
        frameJobs.close();
        uiJobs.close();
        mainHandler.removeCallbacksAndMessages(null);
        analysisExecutor.shutdown();
        matchExecutor.shutdownNow();
        planExecutor.shutdownNow();
        ioExecutor.shutdown();
        if (toneGenerator != null) toneGenerator.release();
        super.onDestroy();
    }

    private void reloadPlan() {
        stopAnalysis();
        clearMatches();
        searchPlan = null;
        livePaleographyEngine = null;
        liveSoundClips=Collections.emptyMap();
        livePaleographyName = "";
        if(paleoSound!=null&&!frozen)paleoSound.clear(true);
        mapButton.setEnabled(false);
        mapButton.setText("Hărți…");
        if (!frozen) setStatus("Se încarcă hărțile…", Color.rgb(244, 188, 77));
        planJobs.offer(++planRequest);
    }

    private void compilePlan(long request) {
        if (destroyed || request != planRequest) return;
        try {
            List<MapProfile> profiles = TopicLibraryStore.list(this);
            List<MapProfile> selected = TopicLibraryStore.scanProfiles(this, profiles);
            TopicMap map = TopicMapStore.loadForScanning(this, selected);
            TopicMatcher.SearchPlan plan = TopicMatcher.compile(this, map);
            LiteralTransliterator imported = null;
            Map<String,java.io.File> soundFiles=Collections.emptyMap();
            String systemName = "";
            if (paleographyEnabled) {
                String systemId = TransliterationStore.selected(this);
                try {
                    if (!systemId.isEmpty()) {
                        imported = TransliterationStore.engine(this, systemId);
                        try{soundFiles=PaleographyAudioFiles.available(this,systemId,imported);}
                        catch(RuntimeException audioError){Log.w(TAG,"Recorded sounds unavailable",audioError);}
                        for (TransliterationStore.Profile profile : TransliterationStore.list(this))
                            if (profile.id.equals(systemId)) systemName = profile.name;
                    }
                } catch (RuntimeException error) {
                    Log.w(TAG, "Paleography system unavailable", error);
                }
            }
            final LiteralTransliterator engine = imported;
            final Map<String,java.io.File> clips=soundFiles;
            final String selectedName = systemName;
            Set<String> ids = new LinkedHashSet<>();
            for (MapProfile profile : selected) ids.add(profile.id);
            mainHandler.post(() -> {
                if (destroyed || !resumed || request != planRequest) return;
                allProfiles = profiles;
                selectedProfiles = ids;
                topicMap = map;
                searchPlan = plan;
                livePaleographyEngine = engine;
                liveSoundClips=clips;
                livePaleographyName = selectedName;
                updatePaleoUi();
                syncVisibleNodes();
                mapButton.setText("Hărți " + ids.size() + "/" + profiles.size() + "  ▾");
                mapButton.setEnabled(true);
                idleStatus();
                attachAnalyzer();
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Map compilation failed", e);
            mainHandler.post(() -> {
                if (destroyed || !resumed || request != planRequest) return;
                mapButton.setText("Reîncarcă hărțile");
                mapButton.setEnabled(true);
                setStatus("Eroare la încărcarea hărților", Color.rgb(235, 100, 100));
            });
        }
    }

    private void showMapSelection() {
        if (searchPlan == null) { reloadPlan(); return; }
        List<MapProfile> profiles = allProfiles;
        CharSequence[] labels = new CharSequence[profiles.size()];
        boolean[] checked = new boolean[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            MapProfile profile = profiles.get(i);
            android.text.SpannableString label = new android.text.SpannableString("●  " + profile.folder + " / " + profile.name);
            label.setSpan(new android.text.style.ForegroundColorSpan(profile.color), 0, 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            labels[i] = label;
            checked[i] = selectedProfiles.contains(profile.id);
        }
        new AlertDialog.Builder(this)
                .setTitle("Hărți pentru OCR")
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setNeutralButton("Toate", (dialog, which) -> {
                    Set<String> ids = new LinkedHashSet<>();
                    for (MapProfile profile : profiles) ids.add(profile.id);
                    TopicLibraryStore.setScanProfiles(this, ids);
                    reloadPlan();
                })
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Aplică", (dialog, which) -> {
                    Set<String> ids = new LinkedHashSet<>();
                    for (int i = 0; i < profiles.size(); i++) if (checked[i]) ids.add(profiles.get(i).id);
                    TopicLibraryStore.setScanProfiles(this, ids);
                    reloadPlan();
                }).show();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, match());

        freezeImage = new ImageView(this);
        freezeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        freezeImage.setBackgroundColor(Color.BLACK);
        freezeImage.setVisibility(View.GONE);
        root.addView(freezeImage, match());

        overlayView = new OverlayView(this);
        root.addView(overlayView, match());
        paleographyOverlay = new PaleographyOverlayView(this);
        root.addView(paleographyOverlay, match());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10), dp(4), dp(10), dp(5));
        top.setBackgroundColor(Color.argb(215, 18, 28, 38));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mapButton = toolbarButton("Hărți…");
        mapButton.setEnabled(false);
        header.addView(mapButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        ocrButton = toolbarButton("OCR");
        header.addView(ocrButton, toolbarLp());
        paleoButton = toolbarButton("Paleo");
        paleoButton.setMinWidth(dp(64));
        paleoButton.setContentDescription("OCR multialfabet; apăsare lungă pentru regulile de transliterare");
        header.addView(paleoButton, toolbarLp());
        paleoButton.setOnClickListener(v -> togglePaleography());
        paleoButton.setOnLongClickListener(v -> {
            if(!frozen)openPaleography();
            return true;
        });
        Button moreButton = toolbarButton("⋯");
        moreButton.setMinWidth(dp(48));
        moreButton.setContentDescription("Meniu");
        header.addView(moreButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        moreButton.setOnClickListener(v -> showQuickMenu());
        top.addView(header);
        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setPadding(dp(4), 0, dp(4), dp(3));
        top.addView(statusText);

        legendScroll = new HorizontalScrollView(this);
        legendScroll.setHorizontalScrollBarEnabled(false);
        legendPanel = new LinearLayout(this);
        legendPanel.setOrientation(LinearLayout.HORIZONTAL);
        legendPanel.setGravity(Gravity.CENTER_VERTICAL);
        legendScroll.addView(legendPanel, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        legendScroll.setVisibility(View.GONE);
        top.addView(legendScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.argb(225, 18, 28, 38));
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        Button libraryButton = toolbarButton("Teme");
        torchButton = toolbarButton("Lumină");
        freezeButton = toolbarButton("Cadru");
        saveButton = toolbarButton("Salvează");
        for (Button button : new Button[]{libraryButton, torchButton, freezeButton, saveButton}) {
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(dp(2), 0, dp(2), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            toolbar.addView(button, lp);
        }
        root.addView(toolbar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56), Gravity.BOTTOM));

        mapButton.setOnClickListener(v -> showMapSelection());
        ocrButton.setOnClickListener(v -> toggleOcr());
        libraryButton.setOnClickListener(v -> openLibrary());
        freezeButton.setOnClickListener(v -> toggleFreeze());
        saveButton.setOnClickListener(v -> saveFrozenFrame());
        torchButton.setOnClickListener(v -> toggleTorch());
        saveButton.setEnabled(false);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        updatePaleoUi();
        idleStatus();
    }

    private void setupCamera() {
        if (cameraController != null || destroyed) return;
        cameraController = new LifecycleCameraController(this);
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        lowRam = am != null && am.isLowRamDevice();
        reducedResolution = lowRam;
        setAnalysisResolution(reducedResolution);
        cameraController.setTapToFocusEnabled(true);
        cameraController.setPinchToZoomEnabled(true);
        previewView.setController(cameraController);
        previewView.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (l != ol || t != ot || r != or || b != ob) invalidateViewport();
        });
        cameraController.getZoomState().observe(this, zoom -> {
            if (zoom == null) return;
            float ratio = zoom.getZoomRatio();
            if (previousZoom >= 0 && Math.abs(ratio-previousZoom) > 0.001f) invalidateViewport();
            previousZoom = ratio;
        });
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraController.bindToLifecycle(this);
        idleStatus();
        attachAnalyzer();
    }

    private boolean accepts(long epoch) {
        return resumed && !destroyed && ocrEnabled && !frozen && scanEpoch == epoch;
    }

    private void setAnalysisResolution(boolean reduced) {
        Size target = reduced ? new Size(960, 720) : new Size(1600, 1200);
        cameraController.setImageAnalysisResolutionSelector(new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(target,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build());
    }

    private void attachAnalyzer() {
        TopicMatcher.SearchPlan plan = searchPlan;
        if (!resumed || destroyed || frozen || !ocrEnabled || plan == null ||
                cameraController == null || analyzer != null) return;
        if (recognizer == null) {
            try { recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); }
            catch (RuntimeException error) { Log.w(TAG, "OCR unavailable", error); return; }
        }
        long epoch = ++scanEpoch;
        TextRecognizer client = recognizer;
        AdaptiveOcrPolicy policy = new AdaptiveOcrPolicy();
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        MlKitAnalyzer delegate = new MlKitAnalyzer(Collections.singletonList(client), COORDINATE_SYSTEM_VIEW_REFERENCED,
                analysisExecutor, result -> {
                    if (!accepts(epoch)) return;
                    long timestamp = result.getTimestamp();
                    Throwable error = result.getThrowable(client);
                    long started = policy.finish(timestamp, SystemClock.elapsedRealtime(), error == null);
                    if (started < 0) return;
                    if (error != null) {
                        handleOcrFailure(epoch, plan, policy, error);
                        return;
                    }
                    Text text = result.getValue(client);
                    if (frameFreshness.accepts(started, SystemClock.elapsedRealtime()))
                        frameJobs.offer(new Frame(epoch, plan, text, started));
                    // Rate/resolution decisions run independently of matching.
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastResolutionChange >= 20000L &&
                            (policy.shouldReduceResolution() || policy.canRestoreResolution())) {
                        mainHandler.post(() -> considerResolution(epoch, policy));
                    }
                    if (recoveryAttempts > 0) mainHandler.post(() -> {
                        if (accepts(epoch)) recoveryAttempts = 0;
                    });
                });
        analyzer = new AdaptiveAnalyzer(delegate, policy, power, () -> accepts(epoch),
                (timestamp, error) -> {
                    if (!accepts(epoch)) return;
                    if (policy.finish(timestamp, SystemClock.elapsedRealtime(), false) >= 0)
                        handleOcrFailure(epoch, plan, policy, error);
                }, () -> { if (accepts(epoch)) invalidateViewport(); });
        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer);
    }

    private void invalidateViewport() {
        long changed = frameFreshness.invalidate(SystemClock.elapsedRealtime());
        mainHandler.post(() -> {
            if (destroyed || !resumed || frozen) return;
            if (overlayDeadline != 0 && overlayDeadline - FrameFreshness.MAX_AGE_MS <= changed) {
                clearMatches();
                idleStatus();
            }
        });
    }

    private void considerResolution(long epoch, AdaptiveOcrPolicy policy) {
        if (!accepts(epoch) || SystemClock.elapsedRealtime() - lastResolutionChange < 20000L) return;
        boolean next = reducedResolution;
        if (!reducedResolution && policy.shouldReduceResolution()) next = true;
        else if (reducedResolution && !lowRam && policy.canRestoreResolution()) next = false;
        if (next == reducedResolution) return;
        stopAnalysis();
        clearMatches();
        lastResolutionChange = SystemClock.elapsedRealtime();
        try {
            setAnalysisResolution(next);
            reducedResolution = next;
        } catch (RuntimeException error) {
            Log.w(TAG, "Adaptive resolution unavailable; keep current mode", error);
            try { setAnalysisResolution(reducedResolution); }
            catch (RuntimeException ignored) { Log.w(TAG, "Resolution fallback unavailable", ignored); }
        }
        attachAnalyzer();
    }

    private void handleOcrFailure(long epoch, TopicMatcher.SearchPlan plan, AdaptiveOcrPolicy policy, Throwable error) {
        Log.w(TAG, "OCR frame failed", error);
        if (!accepts(epoch)) return;
        // Invalid recognizer output is never treated as evidence of a page.
        frameJobs.offer(new Frame(epoch, plan, null, SystemClock.elapsedRealtime()));
        if (policy.consecutiveFailures() < 3) return;
        mainHandler.post(() -> {
            if (!accepts(epoch)) return;
            stopAnalysis();
            clearMatches();
            if (recognizer != null) recognizer.close();
            recognizer = null;
            long generation = scanEpoch;
            long delay = Math.min(8000L, 1000L << Math.min(3, recoveryAttempts++));
            setStatus("OCR se reia…", Color.rgb(244, 188, 77));
            mainHandler.postDelayed(() -> {
                if (destroyed || !resumed || frozen || !ocrEnabled || scanEpoch != generation) return;
                recreateRecognizer();
            }, delay);
        });
    }

    private void recreateRecognizer() {
        if (recognizer == null) {
            try { recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); }
            catch (RuntimeException error) {
                Log.w(TAG, "OCR restart failed", error);
                setStatus("OCR indisponibil", Color.rgb(235, 100, 100));
                return;
            }
        }
        attachAnalyzer();
        idleStatus();
    }

    private void stopAnalysis() {
        ++scanEpoch;
        if (cameraController != null) cameraController.clearImageAnalysisAnalyzer();
        analyzer = null;
        frameJobs.clear();
        uiJobs.clear();
        mainHandler.removeCallbacks(staleOverlayClear);
        mainHandler.removeCallbacks(retainedExpiry);
    }

    private void matchFrame(Frame frame) {
        if (!accepts(frame.epoch) || !frameFreshness.accepts(frame.receivedAt,SystemClock.elapsedRealtime())) return;
        try {
            List<MatchHit> hits=Collections.emptyList();
            List<PaleographyLine> lines=Collections.emptyList();
            long revision=frameFreshness.revision();
            if(paleographyEnabled){
                LiteralTransliterator engine=livePaleographyEngine;
                if(engine!=null) lines=paleographyProjection.update(frame.epoch,revision,frame.text,engine,frame.receivedAt);
            }else{
                hits=TopicMatcher.find(frame.text,frame.plan);
                if(!accepts(frame.epoch)||!frameFreshness.accepts(frame.receivedAt,SystemClock.elapsedRealtime()))return;
                hits=stabilizer.update(frame.epoch,revision,frame.text,hits,frame.receivedAt,scanPrecision);
            }
            if(!accepts(frame.epoch)||!frameFreshness.accepts(frame.receivedAt,SystemClock.elapsedRealtime()))return;
            uiJobs.offer(new FrameResult(frame,hits,countRecognizedWords(frame.text),lines));
        }catch(RuntimeException e){Log.e(TAG,"Frame matching failed",e);}
    }

    private void applyFrame(FrameResult result) {
        if(!accepts(result.frame.epoch)||searchPlan!=result.frame.plan||
                !frameFreshness.accepts(result.frame.receivedAt,SystemClock.elapsedRealtime()))return;
        latestOcrText=result.frame.text==null?"":result.frame.text.getText();
        latestOcrAt=result.frame.receivedAt;
        if(paleographyEnabled){
            if(!beginOverlayFrame(result.frame.receivedAt))return;
            paleographyOverlay.setLines(result.paleography);
            if(paleoSound!=null)paleoSound.update(result.paleography,liveSoundClips);
            setStatus(livePaleographyEngine==null?"Alege un sistem":
                    result.paleography.isEmpty()?livePaleographyName:
                    livePaleographyName+" · "+result.paleography.size()+" rânduri",
                    Color.rgb(163,221,183));
        }else applyHits(result.hits,result.words,result.frame.plan.termCount(),result.frame.receivedAt);
    }

    private void toggleOcr() {
        ocrEnabled = !ocrEnabled;
        AppPrefs.setOcrEnabled(this, ocrEnabled);
        stopAnalysis();
        clearMatches();
        if (ocrEnabled) attachAnalyzer();
        idleStatus();
        updateOcrButton();
        updatePaleoUi();
    }

    private void updateOcrButton() {
        if (ocrButton == null) return;
        ocrButton.setText(ocrEnabled ? "OCR ●" : "OCR ‖");
        ocrButton.setContentDescription(ocrEnabled ? "Oprește OCR" : "Pornește OCR");
        ocrButton.setBackgroundTintList(ColorStateList.valueOf(
                ocrEnabled ? Color.rgb(42, 128, 94) : Color.rgb(93, 105, 116)
        ));
    }

    private void toggleFloatingLabels() {
        floatingLabelsEnabled = !floatingLabelsEnabled;
        AppPrefs.setFloatingLabels(this, floatingLabelsEnabled);
        redrawVisibleHits();
    }

    private void showQuickMenu() {
        String labels = floatingLabelsEnabled ? "Ascunde etichetele" : "Afișează etichetele";
        String soundLabel=AppPrefs.paleographySound(this)?"Sunet Paleo: oprește":"Sunet Paleo: pornește";
        new AlertDialog.Builder(this).setItems(new String[]{"Noduri afișate", labels, "Pagină / OCR aprofundat", "Sisteme paleografie", soundLabel, "Setări", "OCR universal pe cameră"},
                (dialog, which) -> {
                    if (which == 0) {
                        if (topicMap != null && searchPlan != null) showNodeSelection();
                    } else if (which == 1) toggleFloatingLabels();
                    else if (which == 2) openPageReview();
                    else if (which == 3) showPaleographySystems();
                    else if (which == 4) {
                        AppPrefs.setPaleographySound(this,!AppPrefs.paleographySound(this));
                        updatePaleoUi();
                        if(frozen&&frozenPaleographyCapture)displayFrozenPaleography();
                    }else if(which==6)startActivity(new Intent(this, LiveHistoricalActivity.class));
                    else startActivity(new Intent(this, SettingsActivity.class));
                }).setNegativeButton("Închide", null).show();
    }

    private void togglePaleography(){
        if(frozen){
            if(frozenPaleographyCapture){frozenRevealOriginal=!frozenRevealOriginal;displayFrozenPaleography();}
            return;
        }
        if(paleographyEnabled){
            paleographyEnabled=false;AppPrefs.setPaleographyLive(this,false);updatePaleoUi();reloadPlan();return;
        }
        startActivity(new Intent(this,LiveHistoricalActivity.class));
    }

    private void updatePaleoUi(){
        if(paleoButton==null)return;
        paleoButton.setEnabled(!frozen||frozenPaleographyCapture);
        paleoButton.setText(frozen&&frozenPaleographyCapture
                ?(frozenRevealOriginal?"Paleo":"Original")
                :(paleographyEnabled?(AppPrefs.paleographySound(this)?"Paleo ♪":"Paleo ●"):"Paleo"));
        paleoButton.setBackgroundTintList(ColorStateList.valueOf(paleographyEnabled
                ?Color.rgb(116,83,153):Color.rgb(53,92,125)));
        if(overlayView!=null)overlayView.setVisibility(!frozen&&!paleographyEnabled?View.VISIBLE:View.GONE);
        boolean showFrozen=frozen&&frozenPaleographyCapture&&!frozenRevealOriginal&&frozenPaleography!=null;
        if(paleographyOverlay!=null){
            paleographyOverlay.setVisibility((!frozen&&paleographyEnabled)||showFrozen?View.VISIBLE:View.GONE);
            if(showFrozen&&frozenOriginal!=null)paleographyOverlay.setImageSize(frozenOriginal.getWidth(),frozenOriginal.getHeight());
            else paleographyOverlay.setImageSize(0,0);
        }
        if(paleoSound!=null)paleoSound.setEnabled(resumed&&AppPrefs.paleographySound(this)&&
                (showFrozen||(!frozen&&paleographyEnabled&&ocrEnabled)));
    }

    private void showPaleographySystems(){
        List<TransliterationStore.Profile> profiles=TransliterationStore.list(this);
        if(profiles.isEmpty()){openPaleography();return;}
        String selected=TransliterationStore.selected(this);
        String[] labels=new String[profiles.size()];
        int checked=-1;
        for(int i=0;i<profiles.size();i++){
            labels[i]=profiles.get(i).toString();
            if(profiles.get(i).id.equals(selected))checked=i;
        }
        final int[] index={checked};
        new AlertDialog.Builder(this).setTitle("Sistem paleografic")
                .setSingleChoiceItems(labels,checked,(d,w)->index[0]=w)
                .setNeutralButton("Administrează",(d,w)->openPaleography())
                .setNegativeButton("Renunță",null)
                .setPositiveButton("Aplică",(d,w)->{
                    if(index[0]<0||frozen)return;
                    TransliterationStore.select(this,profiles.get(index[0]).id);
                    paleographyEnabled=true;AppPrefs.setPaleographyLive(this,true);
                    ocrEnabled=true;AppPrefs.setOcrEnabled(this,true);
                    updateOcrButton();updatePaleoUi();reloadPlan();
                }).show();
    }

    private void openPaleography() {
        String raw = frozen ? frozenOcrText :
                (latestOcrAt >= 0 && frameFreshness.accepts(latestOcrAt,SystemClock.elapsedRealtime())
                        ? latestOcrText : "");
        PaleographyActivity.open(this,raw,raw.isEmpty()?"Original":"Ultimul text OCR");
    }

    private void openPageReview(){
        // Use a raw preview, never the bitmap with an earlier live overlay.
        Bitmap raw=frozen?(frozenOriginal==null?null:frozenOriginal.copy(Bitmap.Config.ARGB_8888,false)):previewView.getBitmap();
        PageDraft.offer(raw);
        startActivity(new Intent(this,PageReviewActivity.class));
    }

    private void syncVisibleNodes() {
        Set<String> current = new HashSet<>();
        if (topicMap != null) for (TopicNode node : topicMap.nodes) {
            if (node.enabled) current.add(node.identityKey());
        }
        visibleNodePaths.retainAll(current);
        for (String key : current) if (!knownNodePaths.contains(key)) visibleNodePaths.add(key);
        knownNodePaths.clear();
        knownNodePaths.addAll(current);
    }

    private void showNodeSelection() {
        if (topicMap == null) return;
        List<TopicNode> nodes = new ArrayList<>();
        for (TopicNode node : topicMap.nodes) if (node.enabled) nodes.add(node);
        String[] labels = new String[nodes.size()];
        boolean[] checked = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            TopicNode node = nodes.get(i);
            labels[i] = node.profileName + " / " + node.path;
            checked[i] = visibleNodePaths.contains(node.identityKey());
        }
        new AlertDialog.Builder(this).setTitle("Noduri afișate")
                .setMultiChoiceItems(labels, checked, (d, which, value) -> checked[which] = value)
                .setNeutralButton("Toate", (d, which) -> {
                    visibleNodePaths.clear();
                    visibleNodePaths.addAll(knownNodePaths);
                    redrawVisibleHits();
                })
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Aplică", (d, which) -> {
                    visibleNodePaths.clear();
                    for (int i = 0; i < nodes.size(); i++) if (checked[i]) visibleNodePaths.add(nodes.get(i).identityKey());
                    redrawVisibleHits();
                }).show();
    }

    private List<MatchHit> filterVisibleHits(List<MatchHit> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<MatchHit> out = new java.util.ArrayList<>();
        for (MatchHit hit : source) {
            if (hit != null && hit.node != null && visibleNodePaths.contains(hit.node.identityKey())) out.add(hit);
        }
        return out;
    }

    private void redrawVisibleHits() {
        if (overlayView == null || paleographyEnabled || frozen) return;
        List<MatchHit> visible = filterVisibleHits(latestAllHits);
        overlayView.clearHits();
        overlayView.updateHits(visible, AppPrefs.precision(this), floatingLabelsEnabled);
        updateLegend(visible);
        if (!frozen && ocrEnabled && searchPlan != null && searchPlan.termCount() > 0) {
            setStatus(visible.isEmpty() ? "" : visible.size() + " potriviri", Color.rgb(98, 211, 255));
        }
    }

    private void clearMatches() {
        mainHandler.removeCallbacks(retainedExpiry);
        overlayDeadline = 0;
        latestOcrText = "";
        latestOcrAt = -1L;
        latestAllHits = Collections.emptyList();
        if (overlayView != null) overlayView.clearHits();
        if (paleographyOverlay != null) paleographyOverlay.clear();
        if(paleoSound!=null)paleoSound.clear(false);
        updateLegend(latestAllHits);
    }

    private void updateLegend(List<MatchHit> visibleHits) {
        if (legendPanel == null) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, MatchHit> first = new LinkedHashMap<>();
        for (MatchHit hit : visibleHits) {
            String id = hit.node.profileId;
            counts.put(id, counts.getOrDefault(id, 0) + 1);
            first.putIfAbsent(id, hit);
        }
        StringBuilder signatureText = new StringBuilder(counts.toString());
        for (MatchHit hit : first.values()) signatureText.append('|').append(hit.node.color);
        String signature = signatureText.toString();
        if (signature.equals(lastLegendSignature)) return;
        lastLegendSignature = signature;
        legendPanel.removeAllViews();
        legendScroll.setVisibility(counts.isEmpty() ? View.GONE : View.VISIBLE);
        for (Map.Entry<String, MatchHit> entry : first.entrySet()) {
            MatchHit hit = entry.getValue();
            String id = entry.getKey();
            TextView item = legendText(hit.node.profileName + " · " + counts.get(id), hit.node.color);
            item.setOnClickListener(v -> {
                List<MatchHit> current = new ArrayList<>();
                for (MatchHit match : filterVisibleHits(latestAllHits)) {
                    if (match.node.profileId.equals(id)) current.add(match);
                }
                showHitChoices(current);
            });
            legendPanel.addView(item, legendLp());
        }
    }

    private TextView legendText(String text, int color) {
        TextView view = new TextView(this);
        view.setText("● " + text);
        view.setTextColor(MapColorPolicy.contrastText(color));
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(MapColorPolicy.opaque(color));
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        return view;
    }

    private void openLibrary() {
        startActivity(new Intent(this, TopicLibraryActivity.class));
    }

    private boolean beginOverlayFrame(long capturedAt){
        if(!resumed||frozen||!ocrEnabled)return false;
        mainHandler.removeCallbacks(staleOverlayClear);
        overlayDeadline=frameFreshness.deadline(capturedAt);
        long remaining=overlayDeadline-SystemClock.elapsedRealtime();
        if(remaining<=0){clearMatches();idleStatus();return false;}
        mainHandler.postDelayed(staleOverlayClear,remaining);
        return true;
    }

    private void applyHits(List<MatchHit> hits, int recognizedWords, int searchableTerms, long capturedAt) {
        if(!beginOverlayFrame(capturedAt))return;
        latestAllHits = hits == null ? Collections.emptyList() : hits;
        trimAndScheduleRetained();
        List<MatchHit> visibleHits = filterVisibleHits(latestAllHits);
        overlayView.updateHits(visibleHits, AppPrefs.precision(this), floatingLabelsEnabled);
        updateLegend(visibleHits);
        if (searchableTerms <= 0) {
            setStatus("Niciun termen activ", Color.rgb(244, 188, 77));
        } else {
            String label = visibleHits.isEmpty() ? "" : visibleHits.size() + " potriviri";
            setStatus(label, visibleHits.isEmpty() ? Color.rgb(163, 221, 183) : Color.rgb(98, 211, 255));
        }
        if (!visibleHits.isEmpty()) {
            Set<String> nodes = new HashSet<>();
            for (MatchHit hit : visibleHits) if (!hit.retained) nodes.add(hit.node.identityKey());
            if (!nodes.isEmpty()) maybeFeedback(visibleHits, nodes);
        }
    }

    private void trimAndScheduleRetained() {
        mainHandler.removeCallbacks(retainedExpiry);
        long now = SystemClock.elapsedRealtime(), next = Long.MAX_VALUE;
        List<MatchHit> remaining = new ArrayList<>(latestAllHits.size());
        for (MatchHit hit : latestAllHits) {
            if (hit.retained) {
                long deadline = hit.detectedAt + TemporalEvidence.HOLD_MS;
                if (deadline <= now) continue;
                next = Math.min(next, deadline);
            }
            remaining.add(hit);
        }
        if (remaining.size() != latestAllHits.size()) latestAllHits = remaining;
        if (next != Long.MAX_VALUE) mainHandler.postDelayed(retainedExpiry, Math.max(1L, next - now));
    }

    private void expireRetained() {
        if (destroyed || frozen || !resumed || !ocrEnabled) return;
        trimAndScheduleRetained();
        redrawVisibleHits();
    }

    private void idleStatus() {
        if (frozen) setStatus(freezeProcessing?"OCR cadru…":"Cadru fix", Color.rgb(244,188,77));
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            setStatus("Permisiune cameră necesară", Color.rgb(235, 100, 100));
        else if (!ocrEnabled) setStatus("OCR în pauză", Color.rgb(190, 200, 210));
        else if (searchPlan == null) setStatus("Se încarcă hărțile…", Color.rgb(244, 188, 77));
        else if (paleographyEnabled) setStatus(livePaleographyEngine==null?"Alege un sistem":livePaleographyName,Color.rgb(163,221,183));
        else if (searchPlan.termCount() == 0) setStatus("Niciun termen activ", Color.rgb(244, 188, 77));
        else setStatus("", Color.rgb(163, 221, 183));
    }

    private void setStatus(String text, int color) {
        if (statusText != null) {
            if (!TextUtils.equals(statusText.getText(), text)) statusText.setText(text);
            statusText.setTextColor(color);
            statusText.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
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
        if (!frozen) {
            Bitmap bitmap = previewView.getBitmap();
            if (bitmap == null) { toast("Camera nu are încă un cadru disponibil."); return; }
            if(paleographyEnabled){
                double scale=Math.min(1d,Math.min(2200d/Math.max(bitmap.getWidth(),bitmap.getHeight()),
                        Math.sqrt(3500000d/((double)bitmap.getWidth()*bitmap.getHeight()))));
                if(scale<1d){
                    Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)Math.round(bitmap.getWidth()*scale)),
                            Math.max(1,(int)Math.round(bitmap.getHeight()*scale)),true);
                    if(resized!=bitmap){bitmap.recycle();bitmap=resized;}
                }
            }
            frozenOriginal=bitmap;
            frozenPaleographyCapture=paleographyEnabled;
            frozenRevealOriginal=false;
            frozenPaleography=null;
            frozenPaleoLines=Collections.emptyList();
            frozenSoundClips=liveSoundClips;
            if(paleoSound!=null)paleoSound.clear(true);
            freezeProcessing=false;
            long revision=++freezeRevision;
            frozenOcrText=latestOcrAt>=0&&frameFreshness.accepts(latestOcrAt,SystemClock.elapsedRealtime())
                    ?latestOcrText:"";
            frozen=true;
            if(frozenPaleographyCapture){
                // The preview bitmap and the prior OCR frame are not guaranteed
                // identical. Never burn live coordinates into this snapshot.
                frozenBitmap=bitmap;
                frozenOcrText="";
                LiteralTransliterator engine=livePaleographyEngine;
                if(engine!=null){freezeProcessing=true;stillJobs.offer(new FrozenJob(revision,bitmap,engine));}
            }else{
                Bitmap composite=Bitmap.createBitmap(Math.max(1,previewView.getWidth()),
                        Math.max(1,previewView.getHeight()),Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(composite);
                canvas.drawBitmap(bitmap,null,new Rect(0,0,composite.getWidth(),composite.getHeight()),null);
                if(overlayDeadline!=0&&frameFreshness.accepts(overlayDeadline-FrameFreshness.MAX_AGE_MS,
                        SystemClock.elapsedRealtime()))overlayView.draw(canvas);
                frozenBitmap=composite;
            }
            stopAnalysis();
            clearMatches();
            freezeImage.setImageBitmap(frozenBitmap);
            freezeImage.setVisibility(View.VISIBLE);
            freezeButton.setText("Reia");
            saveButton.setEnabled(!freezeProcessing);
            updatePaleoUi();
            idleStatus();
        }else{
            frozen=false;++freezeRevision;stillJobs.clear();
            frozenOcrText="";freezeProcessing=false;
            frozenPaleographyCapture=false;frozenRevealOriginal=false;
            clearMatches();
            frozenBitmap=null;frozenOriginal=null;frozenPaleography=null;
            frozenPaleoLines=Collections.emptyList();frozenSoundClips=Collections.emptyMap();
            if(paleoSound!=null)paleoSound.clear(true);
            freezeImage.setImageDrawable(null);freezeImage.setVisibility(View.GONE);
            freezeButton.setText("Cadru");saveButton.setEnabled(false);
            updatePaleoUi();reloadPlan();
        }
    }

    private void displayFrozenPaleography(){
        if(!frozen||!frozenPaleographyCapture)return;
        frozenBitmap=frozenRevealOriginal||frozenPaleography==null?frozenOriginal:frozenPaleography;
        // Show the unmodified bitmap plus the aligned overlay; the baked copy
        // is used only for export, so the current audio highlight stays live.
        freezeImage.setImageBitmap(frozenOriginal);
        saveButton.setEnabled(frozenBitmap!=null&&(!freezeProcessing||frozenRevealOriginal));
        updatePaleoUi();
        if(!frozenRevealOriginal&&frozenPaleography!=null){
            paleographyOverlay.setLines(frozenPaleoLines);
            if(paleoSound!=null)paleoSound.update(frozenPaleoLines,frozenSoundClips);
        }else{
            paleographyOverlay.clear();
            if(paleoSound!=null)paleoSound.clear(true);
        }
        idleStatus();
    }

    private void analyseFrozenPaleography(FrozenJob job){
        if(destroyed||job.revision!=freezeRevision)return;
        TextRecognizer client=null;
        Bitmap enhanced=null,rendered=null;
        try{
            client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text before=com.google.android.gms.tasks.Tasks.await(client.process(
                    com.google.mlkit.vision.common.InputImage.fromBitmap(job.source,0)),30,java.util.concurrent.TimeUnit.SECONDS);
            if(destroyed||job.revision!=freezeRevision)return;
            enhanced=PageImageProcessor.historicalContrast(job.source,35);
            Text after=com.google.android.gms.tasks.Tasks.await(client.process(
                    com.google.mlkit.vision.common.InputImage.fromBitmap(enhanced,0)),30,java.util.concurrent.TimeUnit.SECONDS);
            List<PaleographyLine> lines=PaleographyProjection.agree(before,after,job.engine);
            if(destroyed||job.revision!=freezeRevision)return;
            rendered=job.source.copy(Bitmap.Config.ARGB_8888,true);
            if(rendered==null)throw new IllegalStateException("Memorie insuficientă");
            new PaleographyOverlayRenderer().draw(new Canvas(rendered),lines);
            final Bitmap output=rendered;rendered=null;
            final String raw=before.getText();
            mainHandler.post(()->{
                if(destroyed||!frozen||job.revision!=freezeRevision){output.recycle();return;}
                frozenPaleography=output;frozenPaleoLines=lines;frozenOcrText=raw;freezeProcessing=false;
                displayFrozenPaleography();
                setStatus(lines.isEmpty()?"Niciun rând confirmat":lines.size()+" rânduri",Color.rgb(163,221,183));
            });
        }catch(Exception error){
            Log.w(TAG,"Frozen paleography failed",error);
            mainHandler.post(()->{
                if(destroyed||!frozen||job.revision!=freezeRevision)return;
                freezeProcessing=false;displayFrozenPaleography();toast("OCR cadru nereușit");
            });
        }finally{
            if(client!=null)client.close();
            if(enhanced!=null)enhanced.recycle();
            if(rendered!=null)rendered.recycle();
        }
    }

    private void saveFrozenFrame() {
        if (!frozen || frozenBitmap == null) {
            toast("Îngheață mai întâi cadrul pe care vrei să-l salvezi.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "BiblioTopicSearch_cadru.png");
        startActivityForResult(intent, REQ_SAVE_FRAME);
    }

    private void toggleTorch() {
        if (cameraController == null) {
            toast("Camera nu este încă pregătită.");
            return;
        }
        torchEnabled = !torchEnabled;
        cameraController.enableTorch(torchEnabled);
        torchButton.setText(torchEnabled ? "Stinge" : "Lumină");
    }

    private void showHitChoices(List<MatchHit> hits) {
        if (hits.isEmpty()) return;
        if (hits.size() == 1) { showHitDetails(hits.get(0)); return; }
        String[] choices = new String[hits.size()];
        for (int i = 0; i < hits.size(); i++) {
            MatchHit hit = hits.get(i);
            choices[i] = hit.node.displayName() + " · " + hit.originalText;
        }
        new AlertDialog.Builder(this).setTitle("Potriviri")
                .setItems(choices, (d, which) -> showHitDetails(hits.get(which)))
                .setNegativeButton("Închide", null).show();
    }

    private void showHitDetails(MatchHit hit) {
        DictionaryStore store = new DictionaryStore(this);
        DictionaryStore.Entry entry = store.lookup(hit.originalText);
        if (entry == null) entry = store.lookup(hit.searchTerm);

        StringBuilder message = new StringBuilder();
        message.append("Hartă: ").append(hit.node.profileName)
                .append("\nNod: ").append(hit.node.path)
                .append("\nTermen căutat: ").append(hit.searchTerm)
                .append("\nText OCR: ").append(hit.originalText);
        if (hit.confidence > 0f) message.append("\nÎncredere OCR: ").append(Math.round(hit.confidence * 100f)).append("%");
        message.append("\nConfirmat în cadre consecutive");
        if (hit.kind == MatchHit.Kind.OCR_REPAIR) message.append("\nPotrivire OCR confirmată în cadre succesive");
        else if (hit.kind == MatchHit.Kind.LINE_JOIN) message.append("\nCuvânt reunit la capăt de rând");
        if (hit.retained) message.append("\nPotrivire din cadrul anterior");
        if (entry != null) {
            message.append("\n\nDEFINIȚIE\n").append(entry.definition);
            if (!entry.synonyms.isEmpty()) message.append("\n\nSinonime: ").append(entry.synonyms);
            if (!entry.antonyms.isEmpty()) message.append("\nAntonime: ").append(entry.antonyms);
            if (!entry.source.isEmpty()) message.append("\nSursă dicționar: ").append(entry.source);
        }

        new AlertDialog.Builder(this)
                .setTitle(hit.originalText)
                .setMessage(message.toString())
                .setNeutralButton("Paleografie",(dialog,which)->
                        PaleographyActivity.open(this,hit.originalText,"Text OCR selectat"))
                .setPositiveButton("Închide", null)
                .show();
    }

    private Button toolbarButton(String text) {
        Button button = actionButton(text);
        button.setMinWidth(dp(70));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private LinearLayout.LayoutParams toolbarLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
        );
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams legendLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
        );
        lp.setMargins(0, 0, dp(4), 0);
        return lp;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return button;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_FRAME || resultCode != RESULT_OK ||
                data == null || data.getData() == null || frozenBitmap == null) return;

        Uri uri = data.getData();
        Bitmap snapshot = frozenBitmap;
        ioExecutor.execute(() -> {
            String message;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null || !snapshot.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new IllegalStateException("Salvare eșuată");
                message = "Cadrul a fost salvat.";
            } catch (Exception e) {
                message = "Salvare eșuată: " + e.getMessage();
            }
            final String result = message;
            mainHandler.post(() -> { if (!destroyed) toast(result); });
        });
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
                setStatus("Permisiune cameră necesară", Color.rgb(235, 100, 100));
            }
        }
    }
}
