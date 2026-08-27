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
import android.text.TextWatcher;
import android.view.Gravity;
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private TextView zoomLabel;
    private TextView status;
    private Button ocrButton;
    private Button torchButton;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private MlKitAnalyzer analyzer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger queryGeneration = new AtomicInteger(0);
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile TopicMatcher.SearchPlan searchPlan;
    private volatile boolean destroyed;
    private String activeQuery = "";
    private boolean ocrEnabled = true;
    private boolean torchEnabled = false;

    private final Runnable applyQuery = this::applyQueryNow;
    private final Runnable clearStale = () -> {
        if (overlay != null && ocrEnabled && !destroyed) {
            overlay.clearHits();
            setStatus("OCR live • caut text", Color.rgb(76, 190, 123));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ocrEnabled = AppPrefs.ocrEnabled(this);
        buildUi();

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
        scheduleSearchPlanBuild(activeQuery);
        if (overlay != null) overlay.invalidate();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (cameraController != null) {
            cameraController.clearImageAnalysisAnalyzer();
            cameraController.unbind();
        }
        if (recognizer != null) recognizer.close();
        main.removeCallbacksAndMessages(null);
        analysisExecutor.shutdownNow();
        queryExecutor.shutdownNow();

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
        title.addView(badge, new LinearLayout.LayoutParams(dp(68), dp(28)));
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
        top.addView(controls);

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

                    TopicMatcher.SearchPlan plan = searchPlan;
                    List<MatchHit> hits = plan == null
                            ? Collections.emptyList()
                            : TopicMatcher.find(text, plan);
                    int words = countWords(text);
                    runOnUiThread(() -> {
                        if (!destroyed) applyFrame(hits, words);
                    });
                }
        );
        if (ocrEnabled) attachAnalyzer();
        cameraController.bindToLifecycle(this);
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
        queryExecutor.execute(() -> {
            try {
                TopicMap aliases = TopicMapStore.load(getApplicationContext());
                TopicMap lensMap = LensQueryMap.build(
                        query,
                        aliases,
                        LensPalette.get(getApplicationContext(), LensPalette.Role.TARGET)
                );
                TopicMatcher.SearchPlan compiled = TopicMatcher.compile(getApplicationContext(), lensMap);
                if (!destroyed && generation == queryGeneration.get() && query.equals(activeQuery)) {
                    searchPlan = compiled;
                }
            } catch (RuntimeException ignored) {
                // Keep the previous valid plan; typing/search UI must remain responsive.
            }
        });
    }

    private void applyFrame(List<MatchHit> hits, int words) {
        if (!ocrEnabled || destroyed) return;
        main.removeCallbacks(clearStale);
        main.postDelayed(clearStale, STALE_MS);
        overlay.updateTargetHits(hits);
        ResearchSemanticEngine.Answer answer = TopicMatcher.latestResearchAnswer();
        if (activeQuery.isEmpty()) {
            setStatus("OCR " + words + " cuvinte • cartografiere internă", Color.rgb(76, 190, 123));
        } else if (answer != null) {
            setStatus("OCR " + words + " • răspuns direct " + (int) Math.round(answer.score() * 100.0) + "%",
                    LensPalette.get(this, LensPalette.Role.ANSWER));
        } else {
            setStatus("OCR " + words + " • ținte " + (hits == null ? 0 : hits.size()) + " • fără răspuns direct încă",
                    LensPalette.get(this, LensPalette.Role.TARGET));
        }
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
