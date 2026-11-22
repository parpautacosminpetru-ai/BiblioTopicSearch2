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
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 101;
    private static final int REQ_SAVE_FRAME = 501;
    private static final long STALE_OVERLAY_MS = 750L;

    private PreviewView previewView;
    private ImageView freezeImage;
    private OverlayView overlayView;
    private TextView mapTitle;
    private TextView mapMetrics;
    private TextView statusDot;
    private TextView statusText;
    private Button ocrButton;
    private Button freezeButton;
    private Button saveButton;
    private Button torchButton;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private MlKitAnalyzer analyzer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile MultiThemeEngine.Plan multiPlan;
    private MultiThemeEngine.Scan lastScan;
    private Text lastOcrText;
    private String selectedLens = MultiThemeEngine.OVERVIEW;
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
            overlayView.clearHits();
            refreshLensButtons();
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
        multiPlan = MultiThemeEngine.compile(this);
        if (!multiPlan.hasLens(selectedLens)) selectedLens = MultiThemeEngine.OVERVIEW;
        if (frozen && lastOcrText != null) {
            lastScan = MultiThemeEngine.scan(lastOcrText, multiPlan);
        } else if (!frozen) {
            lastScan = null;
        }
        updateHeader();
        rebuildLensButtons();
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(9), dp(14), dp(10));
        top.setBackgroundColor(Color.argb(226, 27, 39, 51));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView appTitle = new TextView(this);
        appTitle.setText("Biblio Topic Search");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(19);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(appTitle, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView localBadge = badge("OFFLINE", Color.rgb(69, 92, 113));
        titleRow.addView(localBadge);
        top.addView(titleRow);

        mapTitle = new TextView(this);
        mapTitle.setTextColor(Color.rgb(238, 242, 245));
        mapTitle.setTextSize(14);
        mapTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mapTitle.setPadding(0, dp(3), 0, 0);
        mapTitle.setOnClickListener(v -> openLibrary());
        top.addView(mapTitle);

        mapMetrics = new TextView(this);
        mapMetrics.setTextColor(Color.rgb(190, 204, 215));
        mapMetrics.setTextSize(11);
        mapMetrics.setOnClickListener(v -> openLibrary());
        top.addView(mapMetrics);

        HorizontalScrollView lensScroll = new HorizontalScrollView(this);
        lensScroll.setHorizontalScrollBarEnabled(false);
        lensScroll.setFillViewport(false);
        lensStrip = new LinearLayout(this);
        lensStrip.setOrientation(LinearLayout.HORIZONTAL);
        lensStrip.setGravity(Gravity.CENTER_VERTICAL);
        lensScroll.addView(lensStrip);
        top.addView(lensScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(43)
        ));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(4), 0, 0);

        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(15);
        statusRow.addView(statusDot, new LinearLayout.LayoutParams(dp(22), dp(26)));

        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(Color.rgb(244, 240, 230));
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, dp(28), 1f));
        top.addView(statusRow);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(top, topLp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(7), dp(7), dp(7), dp(10));
        bottom.setBackgroundColor(Color.argb(228, 27, 39, 51));

        ocrButton = primaryButton(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR ÎN PAUZĂ");
        bottom.addView(ocrButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ));
        ocrButton.setOnClickListener(v -> toggleOcr());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(5), 0, 0);

        Button libraryButton = actionButton("TEME");
        freezeButton = actionButton("CADRU");
        saveButton = actionButton("SALVEAZĂ");
        torchButton = actionButton("LUMINĂ");
        Button settingsButton = actionButton("SETĂRI");

        actions.addView(libraryButton, weightedButton());
        actions.addView(freezeButton, weightedButton());
        actions.addView(saveButton, weightedButton());
        actions.addView(torchButton, weightedButton());
        actions.addView(settingsButton, weightedButton());
        bottom.addView(actions);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(bottom, bottomLp);

        libraryButton.setOnClickListener(v -> openLibrary());
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        freezeButton.setOnClickListener(v -> toggleFreeze());
        saveButton.setOnClickListener(v -> saveFrozenFrame());
        torchButton.setOnClickListener(v -> toggleTorch());

        saveButton.setEnabled(false);
        setContentView(root);
        setStatus(
                ocrEnabled ? "Pregătit • OCR local live" : "Camera live • OCR în pauză",
                ocrEnabled ? Color.rgb(78, 201, 126) : Color.rgb(170, 178, 186)
        );
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
        ocrButton.setText(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR ÎN PAUZĂ");
        ocrButton.setBackgroundTintList(ColorStateList.valueOf(
                ocrEnabled ? Color.rgb(42, 128, 94) : Color.rgb(93, 105, 116)
        ));
    }

    private void updateHeader() {
        MapProfile active = TopicLibraryStore.getActive(this);
        topicMap = TopicMapStore.load(this);
        int activeNodes = activeNodeCount(topicMap);
        int terms = activeTermCount(topicMap);
        if (mapTitle != null) mapTitle.setText(active.folder + "  ›  " + active.name + "   ▾");
        if (mapMetrics != null) {
            MultiThemeEngine.Plan plan = multiPlan;
            int maps = plan == null ? 1 : plan.themes.size();
            int searched = plan == null ? terms : plan.termCount;
            mapMetrics.setText(maps + " hărți analizate în paralel • " + searched
                    + " intrări de căutare • alege lentila mai jos");
        }
    }

    private void openLibrary() {
        startActivity(new Intent(this, TopicLibraryActivity.class));
    }

    private void applyThemeScan(MultiThemeEngine.Scan scan, int recognizedWords, int searchableTerms) {
        if (frozen || !ocrEnabled) return;
        mainHandler.removeCallbacks(staleOverlayClear);
        mainHandler.postDelayed(staleOverlayClear, STALE_OVERLAY_MS);
        renderLens();

        if (searchableTerms <= 0) {
            setStatus("OCR: " + recognizedWords + " cuvinte • adaugă termeni în hărți",
                    Color.rgb(244, 188, 77));
            return;
        }
        if (recognizedWords <= 0) {
            setStatus("OCR live • apropie camera și ține textul clar",
                    Color.rgb(78, 201, 126));
            return;
        }

        if (scan.uniqueMatches == 0) {
            setStatus("OCR: " + recognizedWords + " cuvinte • fără potriviri",
                    Color.rgb(78, 201, 126));
            return;
        }

        List<MatchHit> visible = scan.visible(selectedLens);
        setStatus("OCR: " + recognizedWords + " cuvinte • " + scan.uniqueMatches
                + " ocurențe • " + scan.blockIntersections + " blocuri cu intersecții",
                Color.rgb(98, 211, 255));
        if (!MultiThemeEngine.OVERVIEW.equals(selectedLens) && !visible.isEmpty()) {
            Set<String> nodes = new HashSet<>();
            for (MatchHit hit : visible) nodes.add(hit.node.path);
            maybeFeedback(visible, nodes);
        }
    }

    private void rebuildLensButtons() {
        if (lensStrip == null || multiPlan == null) return;
        lensStrip.removeAllViews();
        lensButtons.clear();
        addLensButton(MultiThemeEngine.OVERVIEW, "REZUMAT");
        addLensButton(MultiThemeEngine.INTERSECTIONS, "INTERSECȚII");
        for (MultiThemeEngine.Theme t : multiPlan.themes) {
            String shortName = t.name == null ? "Temă" : t.name.trim();
            if (shortName.length() > 17) shortName = shortName.substring(0, 16) + "…";
            addLensButton(t.id, shortName);
        }
        refreshLensButtons();
    }

    private void addLensButton(String id, String name) {
        Button button = actionButton(name);
        button.setTextSize(11f);
        button.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)
        );
        lp.setMargins(0, 0, dp(4), 0);
        lensStrip.addView(button, lp);
        lensButtons.put(id, button);
        button.setOnClickListener(v -> {
            selectedLens = id;
            renderLens();
            if (frozen) setStatus("Cadru înghețat • " + name
                    + " • " + (lastScan == null ? 0 : lastScan.count(id))
                    + " potriviri", Color.rgb(244, 188, 77));
        });
    }

    private void refreshLensButtons() {
        if (multiPlan == null) return;
        for (Map.Entry<String, Button> entry : lensButtons.entrySet()) {
            String id = entry.getKey();
            Button button = entry.getValue();
            String label;
            if (MultiThemeEngine.OVERVIEW.equals(id)) label = "REZUMAT";
            else if (MultiThemeEngine.INTERSECTIONS.equals(id)) label = "INTERSECȚII";
            else {
                label = multiPlan.nameFor(id);
                if (label.length() > 17) label = label.substring(0, 16) + "…";
            }
            int count = lastScan == null ? 0 : lastScan.count(id);
            button.setText(label + " · " + count);
            boolean selected = id.equals(selectedLens);
            button.setBackgroundTintList(ColorStateList.valueOf(selected
                    ? Color.rgb(16, 137, 178) : Color.rgb(53, 92, 125)));
        }
    }

    private void renderLens() {
        if (overlayView == null) return;
        List<MatchHit> hits = lastScan == null
                ? Collections.emptyList() : lastScan.visible(selectedLens);
        overlayView.updateHits(hits, AppPrefs.precision(this), AppPrefs.showLabels(this));
        refreshLensButtons();
    }

    private void setStatus(String text, int dotColor) {
        if (statusText != null) statusText.setText(text);
        if (statusDot != null) statusDot.setTextColor(dotColor);
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
            if (bitmap == null) {
                toast("Camera nu are încă un cadru disponibil.");
                return;
            }
            // Imaginea și potrivirile OCR sunt păstrate separat: lentilele rămân interactive.
            frozenBitmap = bitmap;
            frozen = true;
            mainHandler.removeCallbacks(staleOverlayClear);
            if (cameraController != null) cameraController.clearImageAnalysisAnalyzer();
            freezeImage.setScaleType(ImageView.ScaleType.FIT_XY);
            freezeImage.setImageBitmap(frozenBitmap);
            freezeImage.setVisibility(View.VISIBLE);
            overlayView.setVisibility(View.VISIBLE);
            renderLens();
            freezeButton.setText("REIA");
            saveButton.setEnabled(true);
            setStatus("Cadru înghețat • comută lentilele • salvează doar la cerere",
                    Color.rgb(244, 188, 77));
        } else {
            frozen = false;
            if (ocrEnabled) attachAnalyzer();
            overlayView.clearHits();
            lastScan = null;
            lastOcrText = null;
            frozenBitmap = null;
            freezeImage.setImageDrawable(null);
            freezeImage.setVisibility(View.GONE);
            overlayView.setVisibility(View.VISIBLE);
            refreshLensButtons();
            freezeButton.setText("CADRU");
            saveButton.setEnabled(false);
            setStatus(
                    ocrEnabled ? "Scanare live reluată" : "Camera live • OCR în pauză",
                    ocrEnabled ? Color.rgb(78, 201, 126) : Color.rgb(170, 178, 186)
            );
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
        torchButton.setText(torchEnabled ? "STINGE" : "LUMINĂ");
    }

    private void showHitDetails(MatchHit hit) {
        DictionaryStore store = new DictionaryStore(this);
        DictionaryStore.Entry entry = store.lookup(hit.originalText);
        if (entry == null) entry = store.lookup(hit.searchTerm);

        StringBuilder message = new StringBuilder();
        message.append("Nod: ").append(hit.node.path)
                .append("\nTermen căutat: ").append(hit.searchTerm)
                .append("\nText OCR: ").append(hit.originalText);
        if (lastScan != null && multiPlan != null) {
            message.append("\n\n").append(lastScan.explain(hit, multiPlan));
        }
        if (entry != null) {
            message.append("\n\nDEFINIȚIE\n").append(entry.definition);
            if (!entry.synonyms.isEmpty()) message.append("\n\nSinonime: ").append(entry.synonyms);
            if (!entry.antonyms.isEmpty()) message.append("\nAntonime: ").append(entry.antonyms);
            if (!entry.source.isEmpty()) message.append("\nSursă dicționar: ").append(entry.source);
        } else {
            message.append("\n\nNu există o intrare în dicționarul local pentru acest termen.");
        }

        new AlertDialog.Builder(this)
                .setTitle(hit.originalText)
                .setMessage(message.toString())
                .setPositiveButton("Închide", null)
                .show();
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
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(42, 128, 94)));
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(9.5f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_FRAME || resultCode != RESULT_OK ||
                data == null || data.getData() == null || frozenBitmap == null) return;

        Uri uri = data.getData();
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, "wt");
            if (output == null) throw new IllegalStateException("Nu pot deschide destinația.");
            try (OutputStream out = output) {
                Bitmap composite = Bitmap.createBitmap(
                        Math.max(1, previewView.getWidth()),
                        Math.max(1, previewView.getHeight()),
                        Bitmap.Config.ARGB_8888
                );
                Canvas canvas = new Canvas(composite);
                canvas.drawBitmap(frozenBitmap, null,
                        new Rect(0, 0, composite.getWidth(), composite.getHeight()), null);
                overlayView.draw(canvas);
                composite.compress(Bitmap.CompressFormat.PNG, 100, out);
                composite.recycle();
            }
            toast("Cadrul a fost salvat.");
        } catch (Exception e) {
            toast("Salvare eșuată: " + e.getMessage());
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
