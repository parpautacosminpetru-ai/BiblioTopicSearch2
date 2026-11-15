package ro.bibliotopicsearch.app;

import static androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LUPĂ v9.1 Stability: one query, progressive semantic zoom and live OCR.
 * Persistent indexing and query-plan compilation are sidecars and never block the camera/UI threads.
 */
public final class LensActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 901;
    private static final long STALE_MS = 850L;

    private PreviewView preview;
    private LensOverlayView overlay;
    private EditText search;
    private EditText pageEdit;
    private TextView zoomLabel;
    private TextView status;
    private Button ocrButton;
    private Button torchButton;
    private Button autoCaptureButton;
    private Button archiveKeysButton;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private MlKitAnalyzer analyzer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger queryGeneration = new AtomicInteger(0);
    private final AtomicBoolean autoCaptureBusy = new AtomicBoolean(false);
    private final PageStabilityTracker pageStabilityTracker = new PageStabilityTracker();
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile TopicMatcher.SearchPlan searchPlan;
    private volatile DeterministicMultiIndexMatcher.Plan evidencePlan;
    private LiveDetectionPipeline<Text, LiveFrame> livePipeline;
    private volatile LiveEvidenceAnalyzer.Snapshot latestEvidence;

    private static final class LiveFrame {
        final List<MatchHit> hits;
        final int words;
        final int generation;
        final LiveEvidenceAnalyzer.Snapshot evidence;
        LiveFrame(List<MatchHit> hits, int words, int generation,
                  LiveEvidenceAnalyzer.Snapshot evidence) {
            this.hits = hits;
            this.words = words;
            this.generation = generation;
            this.evidence = evidence;
        }
    }
    private volatile Text latestOcrText;
    private volatile PageStabilityTracker.Observation latestPageStability;
    private volatile String manualPageOverride = "";
    private volatile boolean destroyed;
    private String activeQuery = "";
    private String stabilitySourceId = "";
    private boolean ocrEnabled = true;
    private boolean torchEnabled = false;
    private volatile boolean autoCaptureEnabled = false;
    private boolean archiveKeysEnabled = false;

    private final Runnable applyQuery = this::applyQueryNow;
    private final Runnable clearStale = () -> {
        if (overlay != null && ocrEnabled && !destroyed) {
            overlay.clearHits();
            latestEvidence = null;
            setStatus("OCR live • caut text", Color.rgb(76, 190, 123));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ocrEnabled = AppPrefs.ocrEnabled(this);
        // LIVE OCR is a preview, never an immutable original. Historic auto-save
        // from transient frames cannot meet the source integrity contract.
        autoCaptureEnabled = false;
        AppPrefs.setAutoExactCapture(this, false);
        archiveKeysEnabled = AppPrefs.archiveKeys(this);
        stabilitySourceId = IndexCoreSourceRegistry.activeSourceId(this);
        buildUi();
        initializeLiveDetection();

        // Start persistent storage outside the UI thread. Initial OCR frames may be
        // intentionally skipped by the index until loading is complete.
        LivingIndexDispatcher.start(getApplicationContext(), System.currentTimeMillis());

        activeQuery = AppPrefs.researchQuery(this);
        search.setText(activeQuery);
        search.setSelection(search.length());
        scheduleSearchPlanBuild(activeQuery);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) setupCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateZoomLabel();
        refreshStabilitySource();
        scheduleSearchPlanBuild(activeQuery);
        if (overlay != null) overlay.invalidate();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        queryGeneration.incrementAndGet();
        if (livePipeline != null) livePipeline.close();
        if (cameraController != null) {
            cameraController.clearImageAnalysisAnalyzer();
            cameraController.unbind();
        }
        if (recognizer != null) recognizer.close();
        main.removeCallbacksAndMessages(null);
        analysisExecutor.shutdownNow();
        queryExecutor.shutdownNow();
        captureExecutor.shutdownNow();

        // Flush/close is queued on the index worker and never blocks Activity teardown.
        LivingIndexDispatcher.stop();
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(preview, match());

        overlay = new LensOverlayView(this);
        overlay.setOnHitTapListener(this::showTargetDetails);
        root.addView(overlay, match());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(8));
        top.setBackgroundColor(Color.argb(220, 19, 29, 37));

        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText("LUPĂ");
        name.setTextColor(Color.WHITE);
        name.setTextSize(19);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView badge = new TextView(this);
        badge.setText("OFFLINE");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(9);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundColor(Color.rgb(67, 88, 104));
        title.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(28)));

        Button advancedToggle = button("AVANSAT ▾");
        title.addView(advancedToggle, new LinearLayout.LayoutParams(dp(92), dp(30)));
        top.addView(title);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = button("−");
        minus.setOnClickListener(v -> { LensUiState.farther(this); updateZoomLabel(); overlay.invalidate(); });
        controls.addView(minus, smallLp());

        zoomLabel = new TextView(this);
        zoomLabel.setTextColor(Color.WHITE);
        zoomLabel.setTextSize(10.5f);
        zoomLabel.setGravity(Gravity.CENTER);
        controls.addView(zoomLabel, new LinearLayout.LayoutParams(dp(92), dp(40)));

        Button plus = button("+");
        plus.setOnClickListener(v -> { LensUiState.closer(this); updateZoomLabel(); overlay.invalidate(); });
        controls.addView(plus, smallLp());

        Button index = button("INDEX");
        index.setOnClickListener(v -> startActivity(new Intent(this, LivingIndexActivity.class)));
        controls.addView(index, controlLp());

        Button colors = button("CULORI");
        colors.setOnClickListener(v -> startActivity(new Intent(this, LensColorActivity.class)));
        controls.addView(colors, controlLp());

        ocrButton = button(ocrEnabled ? "OCR ●" : "OCR Ⅱ");
        ocrButton.setOnClickListener(v -> toggleOcr());
        controls.addView(ocrButton, controlLp());

        torchButton = button("LUMINĂ");
        torchButton.setOnClickListener(v -> toggleTorch());
        controls.addView(torchButton, controlLp());
        controls.setVisibility(View.GONE);
        advancedToggle.setOnClickListener(v -> {
            boolean opening = controls.getVisibility() != View.VISIBLE;
            controls.setVisibility(opening ? View.VISIBLE : View.GONE);
            advancedToggle.setText(opening ? "AVANSAT ▴" : "AVANSAT ▾");
        });
        top.addView(controls);

        LinearLayout cartography = new LinearLayout(this);
        cartography.setGravity(Gravity.CENTER_VERTICAL);

        pageEdit = new EditText(this);
        pageEdit.setHint("pag.");
        pageEdit.setSingleLine(true);
        pageEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageEdit.setTextColor(Color.WHITE);
        pageEdit.setHintTextColor(Color.rgb(148, 166, 178));
        pageEdit.setTextSize(11f);
        pageEdit.setGravity(Gravity.CENTER);
        pageEdit.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(78, 117, 142)));
        pageEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                manualPageOverride = s == null ? "" : s.toString().trim();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        cartography.addView(pageEdit, new LinearLayout.LayoutParams(dp(58), dp(40)));

        Button capture = button("SCAN ORIGINAL");
        capture.setOnClickListener(v -> captureCurrentFrame(false));
        cartography.addView(capture, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button completePage = button("VERIFICĂ ✓");
        completePage.setOnClickListener(v -> captureCurrentFrame(true));
        cartography.addView(completePage, new LinearLayout.LayoutParams(0, dp(40), 1f));

        autoCaptureButton = button("AUTO?");
        autoCaptureButton.setOnClickListener(v -> toggleAutoCapture());
        cartography.addView(autoCaptureButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button map = button("HARTĂ");
        map.setOnClickListener(v -> startActivity(new Intent(this, IndexCoreActivity.class)));
        cartography.addView(map, new LinearLayout.LayoutParams(0, dp(40), 1f));

        top.addView(cartography);

        LinearLayout archive = new LinearLayout(this);
        archive.setGravity(Gravity.CENTER_VERTICAL);
        Button themes = button("TEME");
        themes.setOnClickListener(v -> startActivity(new Intent(this, TopicLibraryActivity.class)));
        archive.addView(themes, new LinearLayout.LayoutParams(0, dp(38), 1f));

        Button settings = button("SETĂRI");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        archive.addView(settings, new LinearLayout.LayoutParams(0, dp(38), 1f));

        archiveKeysButton = button(archiveKeysEnabled ? "PEDALĂ ●" : "PEDALĂ ○");
        archiveKeysButton.setOnClickListener(v -> toggleArchiveKeys());
        archive.addView(archiveKeysButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        Button evidenceButton = button("INDICI");
        evidenceButton.setOnClickListener(v -> openLensEvidence());
        archive.addView(evidenceButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        top.addView(archive);

        status = new TextView(this);
        status.setTextSize(10.5f);
        status.setTextColor(Color.rgb(198, 215, 225));
        status.setPadding(dp(2), dp(2), 0, 0);
        top.addView(status);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout searchPanel = new LinearLayout(this);
        searchPanel.setGravity(Gravity.CENTER_VERTICAL);
        searchPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        searchPanel.setBackgroundColor(Color.argb(236, 15, 24, 31));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Caută subiect sau întrebare…");
        search.setHintTextColor(Color.rgb(148, 166, 178));
        search.setTextColor(Color.WHITE);
        search.setTextSize(14);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(78, 117, 142)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                main.removeCallbacks(applyQuery);
                main.postDelayed(applyQuery, 180L);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchPanel.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button clear = button("×");
        clear.setOnClickListener(v -> search.setText(""));
        searchPanel.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(44)));

        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68), Gravity.BOTTOM);
        root.addView(searchPanel, searchLp);

        setContentView(root);
        updateZoomLabel();
        updateOcrButton();
        setStatus("LUPĂ pregătită • bara goală = cartografiere internă", Color.rgb(76, 190, 123));
    }

    private void setupCamera() {
        if (cameraController != null || destroyed) return;
        cameraController = new LifecycleCameraController(this);
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        cameraController.setPinchToZoomEnabled(true); // physical zoom stays a gesture; +/- is semantic zoom.
        preview.setController(cameraController);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        analyzer = new MlKitAnalyzer(
                Collections.singletonList(recognizer),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                analysisExecutor,
                result -> {
                    if (!ocrEnabled || destroyed) return;
                    Text text = result.getValue(recognizer);
                    if (text == null) {
                        runOnUiThread(() -> {
                            if (destroyed) return;
                            overlay.clearHits();
                            setStatus("OCR live • fără text", Color.rgb(76, 190, 123));
                        });
                        return;
                    }

                    latestOcrText = text;
                    String detectedPage = ExactCartographyCapture.detectPage(text);
                    String stabilityPage = detectedPage.isEmpty() ? manualPageOverride : detectedPage;
                    PageStabilityTracker.Observation stability = pageStabilityTracker.observe(
                            text.getText(),
                            stabilityPage,
                            System.currentTimeMillis()
                    );
                    latestPageStability = stability;
                    if (autoCaptureEnabled && stability.shouldCapture
                            && !stability.page.isEmpty()
                            && autoCaptureBusy.compareAndSet(false, true)) {
                        runAutoExactCapture(text, stability);
                    }
                    if (!detectedPage.isEmpty()) {
                        runOnUiThread(() -> {
                            if (!destroyed && pageEdit != null && !pageEdit.hasFocus()
                                    && pageEdit.getText().toString().trim().isEmpty()) {
                                pageEdit.setText(detectedPage);
                            }
                        });
                    }
                    // Never perform heavy topic matching on the CameraX analyzer.
                    if (livePipeline != null) livePipeline.offer(text);
                }
        );
        if (ocrEnabled) attachAnalyzer();
        cameraController.bindToLifecycle(this);
    }

    private void initializeLiveDetection() {
        livePipeline = new LiveDetectionPipeline<>(
                260L,
                this::runOnUiThread,
                text -> {
                    TopicMatcher.SearchPlan plan = searchPlan;
                    DeterministicMultiIndexMatcher.Plan indices = evidencePlan;
                    int generation = queryGeneration.get();
                    if (plan == null || indices == null) return null;
                    List<MatchHit> hits = TopicMatcher.find(text, plan);
                    List<String> blocks = new ArrayList<>();
                    for (Text.TextBlock block : text.getTextBlocks()) blocks.add(block.getText());
                    LiveEvidenceAnalyzer.Snapshot evidence =
                            LiveEvidenceAnalyzer.inspect(blocks, indices);
                    return new LiveFrame(hits, countWords(text), generation, evidence);
                },
                new LiveDetectionPipeline.Listener<LiveFrame>() {
                    @Override public void onResult(LiveFrame frame) {
                        if (frame == null || destroyed || !ocrEnabled
                                || frame.generation != queryGeneration.get()) return;
                        latestEvidence = frame.evidence;
                        applyFrame(frame.hits, frame.words);
                    }

                    @Override public void onError(Exception failure) {
                        android.util.Log.w("BiblioLens", "Live detection error", failure);
                        if (!destroyed && ocrEnabled) {
                            setStatus("OCR activ • rezultat omis după eroare",
                                    Color.rgb(230, 155, 72));
                        }
                    }
                }
        );
    }

    private void attachAnalyzer() {
        if (cameraController != null && analyzer != null && !destroyed) {
            cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer);
        }
    }

    private void applyQueryNow() {
        if (search == null || destroyed) return;
        String value = search.getText().toString().trim();
        if (value.equals(activeQuery) && searchPlan != null) return;
        activeQuery = value;
        AppPrefs.setResearchQuery(this, value);
        TopicMatcher.setResearchQuery(value);
        scheduleSearchPlanBuild(value);
        overlay.clearHits();
        overlay.invalidate();
        setStatus(value.isEmpty()
                        ? "CARTOGRAFIERE • caut subiectul/nucleul"
                        : "CĂUTARE • " + value,
                value.isEmpty() ? Color.rgb(76, 190, 123) : LensPalette.get(this, LensPalette.Role.TARGET));
    }

    private void scheduleSearchPlanBuild(String querySnapshot) {
        if (destroyed) return;
        final int generation = queryGeneration.incrementAndGet();
        final String query = querySnapshot == null ? "" : querySnapshot;
        searchPlan = null;
        evidencePlan = null;
        latestEvidence = null;
        queryExecutor.execute(() -> {
            if (destroyed || generation != queryGeneration.get()) return;
            try {
                TopicMap aliases = TopicMapStore.load(getApplicationContext());
                TopicMatcher.setIntersectionThemeMap(aliases);
                TopicMap lensMap = LensQueryMap.build(
                        query,
                        aliases,
                        LensPalette.get(getApplicationContext(), LensPalette.Role.TARGET)
                );
                TopicMatcher.SearchPlan compiled = TopicMatcher.compile(getApplicationContext(), lensMap);
                // The live inspector always searches the user's full active index.
                DeterministicMultiIndexMatcher.Plan indices =
                        DeterministicMultiIndexMatcher.compile(aliases);
                if (!destroyed && generation == queryGeneration.get() && query.equals(activeQuery)) {
                    evidencePlan = indices;
                    searchPlan = compiled;
                }
            } catch (RuntimeException failure) {
                android.util.Log.w("BiblioLens", "Index compile failed", failure);
            }
        });
    }

    private void applyFrame(List<MatchHit> hits, int words) {
        if (!ocrEnabled || destroyed) return;
        main.removeCallbacks(clearStale);
        main.postDelayed(clearStale, STALE_MS);
        overlay.updateTargetHits(hits);
        ResearchSemanticEngine.Answer answer = TopicMatcher.latestResearchAnswer();
        String auto = "";
        PageStabilityTracker.Observation stability = latestPageStability;
        if (autoCaptureEnabled && stability != null) {
            auto = " • AUTO: " + stability.compact();
            if (stability.stable && stability.page.isEmpty()) auto += " • pagină?";
        }
        if (activeQuery.isEmpty()) {
            setStatus("OCR " + words + " cuvinte • cartografiere internă" + auto, Color.rgb(76, 190, 123));
        } else if (answer != null) {
            setStatus("OCR " + words + " • răspuns direct " + (int) Math.round(answer.score() * 100.0) + "%" + auto,
                    LensPalette.get(this, LensPalette.Role.ANSWER));
        } else {
            setStatus("OCR " + words + " • ținte " + (hits == null ? 0 : hits.size())
                            + " • fără răspuns direct încă" + auto,
                    LensPalette.get(this, LensPalette.Role.TARGET));
        }
    }

    private void captureCurrentFrame(boolean verifyPage) {
        // OCR LIVE carries no immutable image, and may describe a previous frame.
        // All durable captures come from CameraScanActivity's real CameraX JPEG.
        Intent intent = new Intent(this,
                verifyPage ? OriginalPageActivity.class : CameraScanActivity.class);
        if (verifyPage) {
            intent.putExtra(OriginalPageActivity.EXTRA_SOURCE_ID,
                    IndexCoreSourceRegistry.activeSourceId(this));
            if (pageEdit != null) {
                intent.putExtra(OriginalPageActivity.EXTRA_PAGE,
                        pageEdit.getText().toString().trim());
            }
        }
        startActivity(intent);
    }

    private void runAutoExactCapture(Text snapshot, PageStabilityTracker.Observation stability) {
        if (snapshot == null || stability == null || stability.page.isEmpty()) {
            autoCaptureBusy.set(false);
            return;
        }
        captureExecutor.execute(() -> {
            try {
                TopicMap thematicMap = TopicMapStore.load(getApplicationContext());
                ExactCartographyCapture.Report report = ExactCartographyCapture.capture(
                        getApplicationContext(),
                        snapshot,
                        thematicMap,
                        stability.page,
                        false
                );
                pageStabilityTracker.markCaptured(stability);
                IndexCoreDatabase core = IndexCoreDatabase.get(getApplicationContext());
                String activeSource = IndexCoreSourceRegistry.activeSourceId(getApplicationContext());
                IndexCoreCartographyReader.Stats coverage =
                        IndexCoreCartographyReader.stats(core, activeSource);
                String progress = coverage.scopeDefined()
                        ? " • " + coverage.pagesComplete + "/" + coverage.expectedPages + " pag. încheiate"
                        : "";
                runOnUiThread(() -> {
                    if (!destroyed) {
                        setStatus("AUTO CAPTURĂ • " + report.compact() + progress,
                                Color.rgb(76, 190, 123));
                    }
                });
            } catch (RuntimeException failure) {
                runOnUiThread(() -> {
                    if (!destroyed) setStatus("AUTO CAPTURĂ • nereușită; voi reîncerca după stabilizare",
                            Color.rgb(230, 155, 72));
                });
            } finally {
                autoCaptureBusy.set(false);
            }
        });
    }

    private void runExactCapture(Text snapshot, String page, boolean completePage) {
        if (completePage && SourceArchiveStore.page(this,
                IndexCoreSourceRegistry.activeSourceId(this), page) == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Originalul paginii lipsește")
                    .setMessage("OCR-ul live nu conține fotografia/PDF-ul original. "
                            + "Pentru a încheia riguros pagina, fotografiază mai întâi originalul "
                            + "în SCANARE ORIGINALĂ și verifică OCR-ul în vizualizator.")
                    .setPositiveButton("SCANARE ORIGINALĂ", (d, w) ->
                            startActivity(new Intent(this, CameraScanActivity.class)))
                    .setNegativeButton("ANULEAZĂ", null).show();
            return;
        }
        setStatus(completePage ? "CAPTURĂ EXACTĂ • închid pagina…" : "CAPTURĂ EXACTĂ • procesez cadrul…",
                Color.rgb(230, 190, 80));
        captureExecutor.execute(() -> {
            ExactCartographyCapture.Report report;
            try {
                TopicMap thematicMap = TopicMapStore.load(getApplicationContext());
                report = ExactCartographyCapture.capture(
                        getApplicationContext(),
                        snapshot,
                        thematicMap,
                        page,
                        completePage
                );
            } catch (RuntimeException failure) {
                runOnUiThread(() -> {
                    if (!destroyed) setStatus("CAPTURĂ • eroare; cadrul nu a fost declarat complet",
                            Color.rgb(230, 92, 92));
                });
                return;
            }

            IndexCoreDatabase core = IndexCoreDatabase.get(getApplicationContext());
            String activeSource = IndexCoreSourceRegistry.activeSourceId(getApplicationContext());
            IndexCoreCartographyReader.Stats coverage =
                    IndexCoreCartographyReader.stats(core, activeSource);
            String progress = coverage.scopeDefined()
                    ? " • acoperire " + coverage.pagesComplete + "/" + coverage.expectedPages
                        + " (" + coverage.coveragePercent() + "%)"
                        + (coverage.exhaustive() ? " ✓" : "")
                    : "";

            runOnUiThread(() -> {
                if (destroyed) return;
                int color = report.map != null && report.map.ambiguousCount() > 0
                        ? Color.rgb(230, 155, 72)
                        : Color.rgb(76, 190, 123);
                setStatus("CAPTURĂ • " + report.compact() + progress, color);
                pageStabilityTracker.markCurrentCaptured(report.page);
                if (report.pageComplete) {
                    pageStabilityTracker.markPageClosed(report.page);
                    if (pageEdit != null) {
                        // Never guess the next physical page: clear the field and let OCR
                        // or the historian identify the next page explicitly.
                        pageEdit.setText("");
                    }
                }
            });
        });
    }

    private void toggleAutoCapture() {
        new AlertDialog.Builder(this)
                .setTitle("AUTO OCR este doar orientativ")
                .setMessage("Nu se poate certifica o pagină dintr-un cadru OCR LIVE "
                        + "fără fotografia originală. Folosește SCANARE ORIGINALĂ "
                        + "pentru a păstra imaginea nemodificată și a indexa verificabil.")
                .setPositiveButton("SCANARE ORIGINALĂ",
                        (d, w) -> startActivity(new Intent(this, CameraScanActivity.class)))
                .setNegativeButton("ÎNCHIDE", null).show();
    }

    private void refreshStabilitySource() {
        String active = IndexCoreSourceRegistry.activeSourceId(this);
        if (!active.equals(stabilitySourceId)) {
            stabilitySourceId = active;
            pageStabilityTracker.resetForNewSource();
            latestPageStability = null;
            manualPageOverride = "";
            if (pageEdit != null) pageEdit.setText("");
        }
    }

    private void toggleArchiveKeys() {
        archiveKeysEnabled = !archiveKeysEnabled;
        AppPrefs.setArchiveKeys(this, archiveKeysEnabled);
        if (archiveKeysButton != null) {
            archiveKeysButton.setText(
                    archiveKeysEnabled ? "PEDALĂ / TASTE ●" : "PEDALĂ / TASTE ○"
            );
            archiveKeysButton.setBackgroundTintList(ColorStateList.valueOf(
                    archiveKeysEnabled ? Color.rgb(42, 128, 94) : Color.rgb(53, 92, 125)
            ));
        }
        setStatus(
                archiveKeysEnabled
                        ? "MOD ARHIVĂ • PageUp/Vol− = scanare originală • PageDown/Vol+ = verificare original"
                        : "Modul de pedală/taste este oprit",
                archiveKeysEnabled ? Color.rgb(76, 190, 123) : Color.rgb(155, 164, 171)
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!archiveKeysEnabled) return super.onKeyDown(keyCode, event);
        if (event != null && event.getRepeatCount() > 0) return true;

        if (keyCode == KeyEvent.KEYCODE_PAGE_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_F7) {
            captureCurrentFrame(false);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_F8) {
            captureCurrentFrame(true);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleOcr() {
        ocrEnabled = !ocrEnabled;
        AppPrefs.setOcrEnabled(this, ocrEnabled);
        if (cameraController != null) {
            if (ocrEnabled) attachAnalyzer(); else cameraController.clearImageAnalysisAnalyzer();
        }
        if (!ocrEnabled) overlay.clearHits();
        updateOcrButton();
        setStatus(ocrEnabled ? "OCR live" : "OCR în pauză",
                ocrEnabled ? Color.rgb(76, 190, 123) : Color.rgb(155, 164, 171));
    }

    private void updateOcrButton() {
        if (ocrButton == null) return;
        ocrButton.setText(ocrEnabled ? "OCR ●" : "OCR Ⅱ");
        ocrButton.setBackgroundTintList(ColorStateList.valueOf(
                ocrEnabled ? Color.rgb(42, 128, 94) : Color.rgb(74, 84, 94)));
    }

    private void toggleTorch() {
        if (cameraController == null) return;
        torchEnabled = !torchEnabled;
        cameraController.enableTorch(torchEnabled);
        torchButton.setText(torchEnabled ? "STINGE" : "LUMINĂ");
    }

    private void updateZoomLabel() {
        if (zoomLabel == null) return;
        LensDisplayPolicy.Level level = LensUiState.level(this);
        zoomLabel.setText((level.ordinal() + 1) + "/5\n" + level.labelRo());
    }

    /**
     * Read-only LIVE evidence. Every dialog uses one captured snapshot, never
     * mixes OCR text from two frames or rewrites its original quotation.
     */
    private void openLensEvidence() {
        final LiveEvidenceAnalyzer.Snapshot snapshot = latestEvidence;
        if (snapshot == null) {
            showLensEvidenceMessage("INDICI", "Indexurile se pregătesc sau camera nu a citit încă text.");
            return;
        }
        String[] options = {
                "OCURENȚE • " + snapshot.groups.size() + " indici / " + snapshot.occurrences + " apariții",
                "COOCURENȚE • " + snapshot.cooccurrences(),
                "SUPRAPUNERI • " + snapshot.overlaps(),
                "CANDIDAȚI CRONOLOGICI • " + snapshot.candidates.size()
        };
        new AlertDialog.Builder(this).setTitle("INDICI • ce vrei să vezi?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (snapshot.groups.isEmpty()) {
                            showLensEvidenceMessage("OCURENȚE", "Niciun indice prezent în cadrul OCR.");
                            return;
                        }
                        int count = Math.min(100, snapshot.groups.size());
                        String[] rows = new String[count];
                        for (int i = 0; i < count; i++) {
                            LiveEvidenceAnalyzer.Group group = snapshot.groups.get(i);
                            rows[i] = group.title + " • " + group.count() + " apariții";
                        }
                        new AlertDialog.Builder(this).setTitle("OCURENȚE (primele " + count + ")")
                                .setItems(rows, (d, index) -> {
                                    LiveEvidenceAnalyzer.Group group = snapshot.groups.get(index);
                                    StringBuilder detail = new StringBuilder("Index: ")
                                            .append(group.indexId).append("\n")
                                            .append(group.count()).append(" ocurențe distincte");
                                    int n = Math.min(30, group.occurrences.size());
                                    for (int j = 0; j < n; j++) {
                                        LiveEvidenceAnalyzer.Spot spot = group.occurrences.get(j);
                                        detail.append("\n\n").append(spot.position())
                                                .append("\n«").append(spot.quote).append("»");
                                    }
                                    showLensEvidenceMessage(group.title, detail.toString());
                                }).setNegativeButton("ÎNCHIDE", null).show();
                    } else if (which == 1 || which == 2) {
                        boolean overlaps = which == 2;
                        List<LiveEvidenceAnalyzer.Pair> pairs = new ArrayList<>();
                        for (LiveEvidenceAnalyzer.Pair pair : snapshot.pairs) {
                            if (pair.thematicOverlap == overlaps) pairs.add(pair);
                        }
                        if (pairs.isEmpty()) {
                            showLensEvidenceMessage(overlaps ? "SUPRAPUNERI" : "COOCURENȚE",
                                    "Nu s-au găsit perechi de acest tip.");
                            return;
                        }
                        int count = Math.min(100, pairs.size());
                        String[] rows = new String[count];
                        for (int i = 0; i < count; i++) rows[i] = pairs.get(i).label();
                        new AlertDialog.Builder(this)
                                .setTitle((overlaps ? "SUPRAPUNERI" : "COOCURENȚE")
                                        + (snapshot.pairsLimited ? " • listă limitată" : ""))
                                .setItems(rows, (d, index) -> {
                                    LiveEvidenceAnalyzer.Pair pair = pairs.get(index);
                                    showLensEvidenceMessage(pair.label(), "CITAT OCR EXACT:\n«"
                                            + pair.quote + "»\n\n"
                                            + "«" + pair.left.original + "» / «" + pair.right.original + "»"
                                            + (pair.thematicOverlap ? "\nAceeași zonă OCR."
                                            : "\nSegment exact între indicii: «"
                                            + pair.exactBetween + "»"
                                            + (pair.visibleConnector.isEmpty() ? ""
                                            : "\nConector vizibil: «" + pair.visibleConnector + "»"))
                                            + "\n\nCoocurența nu demonstrează o relație istorică sau sintactică.");
                                }).setNegativeButton("ÎNCHIDE", null).show();
                    } else {
                        if (snapshot.candidates.isEmpty()) {
                            showLensEvidenceMessage("CANDIDAȚI", "Nicio formă cronologică nouă în OCR.");
                            return;
                        }
                        int count = Math.min(100, snapshot.candidates.size());
                        String[] rows = new String[count];
                        for (int i = 0; i < count; i++) {
                            LiveEvidenceAnalyzer.Candidate item = snapshot.candidates.get(i);
                            rows[i] = item.type + " • «" + item.original + "»";
                        }
                        new AlertDialog.Builder(this).setTitle("CANDIDAȚI • nevalidați")
                                .setItems(rows, (d, index) -> {
                                    LiveEvidenceAnalyzer.Candidate item = snapshot.candidates.get(index);
                                    showLensEvidenceMessage(item.type, "«" + item.quote + "»\n\n"
                                            + "Candidat determinist. Nu este introdus automat în index.");
                                }).setNegativeButton("ÎNCHIDE", null).show();
                    }
                }).setNegativeButton("ÎNCHIDE", null).show();
    }

    private void showLensEvidenceMessage(String title, String message) {
        if (destroyed || isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("ÎNCHIDE", null).show();
    }

    private void showTargetDetails(MatchHit hit) {
        if (hit == null) return;
        new AlertDialog.Builder(this)
                .setTitle("ȚINTĂ")
                .setMessage("Text OCR: " + hit.originalText + "\nCăutat: " + hit.searchTerm
                        + "\n\nLUPĂ marchează locația; nu formulează o afirmație în locul tău.")
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private int countWords(Text text) {
        int count = 0;
        if (text == null) return 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) count += line.getElements().size();
        }
        return count;
    }

    private void setStatus(String value, int color) {
        if (status != null) { status.setText(value); status.setTextColor(color); }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(9.5f);
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return b;
    }

    private LinearLayout.LayoutParams smallLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(1), 0, dp(1), 0);
        return lp;
    }

    private LinearLayout.LayoutParams controlLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(1), 0, dp(1), 0);
        return lp;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) setupCamera();
        else setStatus("Permisiunea camerei este necesară pentru LUPĂ.", Color.rgb(230, 92, 92));
    }
}
