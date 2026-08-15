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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
    private Button nodesButton;
    private Button labelsButton;
    private LinearLayout nodePanel;
    private LinearLayout nodeChips;
    private LinearLayout legendPanel;

    private LifecycleCameraController cameraController;
    private TextRecognizer recognizer;
    private MlKitAnalyzer analyzer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile TopicMatcher.SearchPlan searchPlan;

    private TopicMap topicMap;
    private boolean frozen = false;
    private boolean torchEnabled = false;
    private boolean ocrEnabled = true;
    private Bitmap frozenBitmap;
    private ToneGenerator toneGenerator;
    private long lastFeedbackAt = 0L;
    private String lastFeedbackSignature = "";
    private boolean floatingLabelsEnabled = false;
    private final Set<String> visibleNodePaths = new HashSet<>();
    private final Set<String> knownNodePaths = new HashSet<>();
    private String visibleProfileId = "";
    private List<MatchHit> latestAllHits = Collections.emptyList();
    private String lastLegendSignature = "";

    private final Runnable staleOverlayClear = () -> {
        if (!frozen && ocrEnabled && overlayView != null) {
            overlayView.clearHits();
            updateLegend(Collections.emptyList());
            setStatus("OCR live • caut text nou", Color.rgb(78, 201, 126));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ocrEnabled = AppPrefs.ocrEnabled(this);
        floatingLabelsEnabled = AppPrefs.floatingLabels(this);
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
        MapProfile activeProfile = TopicLibraryStore.getActive(this);
        topicMap = TopicMapStore.load(this);
        searchPlan = TopicMatcher.compile(this, topicMap);
        syncVisibleNodes(activeProfile.id);
        updateHeader();
        updateOcrButton();
        updateLabelsButton();
        rebuildNodeChips();
        redrawVisibleHits();
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

        HorizontalScrollView legendScroll = new HorizontalScrollView(this);
        legendScroll.setHorizontalScrollBarEnabled(false);
        legendPanel = new LinearLayout(this);
        legendPanel.setOrientation(LinearLayout.HORIZONTAL);
        legendPanel.setGravity(Gravity.CENTER_VERTICAL);
        legendPanel.setPadding(0, dp(3), 0, dp(3));
        legendScroll.addView(legendPanel, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)
        ));
        top.addView(legendScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        ));
        updateLegend(Collections.emptyList());

        HorizontalScrollView toolScroll = new HorizontalScrollView(this);
        toolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(4), 0, dp(3));

        ocrButton = toolbarButton(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR PAUZĂ");
        nodesButton = toolbarButton("NODURI");
        labelsButton = toolbarButton("ETICHETE");
        Button libraryButton = toolbarButton("TEME");
        freezeButton = toolbarButton("CADRU");
        saveButton = toolbarButton("SALVEAZĂ");
        torchButton = toolbarButton("LUMINĂ");
        Button settingsButton = toolbarButton("SETĂRI");

        toolbar.addView(ocrButton, toolbarLp());
        toolbar.addView(nodesButton, toolbarLp());
        toolbar.addView(labelsButton, toolbarLp());
        toolbar.addView(libraryButton, toolbarLp());
        toolbar.addView(freezeButton, toolbarLp());
        toolbar.addView(saveButton, toolbarLp());
        toolbar.addView(torchButton, toolbarLp());
        toolbar.addView(settingsButton, toolbarLp());
        toolScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
        ));
        top.addView(toolScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ));

        nodePanel = new LinearLayout(this);
        nodePanel.setOrientation(LinearLayout.VERTICAL);
        nodePanel.setVisibility(View.GONE);
        nodePanel.setPadding(0, dp(4), 0, dp(4));

        TextView nodeHint = new TextView(this);
        nodeHint.setText("AFIȘARE LIVE • căutarea rămâne activă în fundal");
        nodeHint.setTextColor(Color.rgb(205, 216, 225));
        nodeHint.setTextSize(10);
        nodePanel.addView(nodeHint);

        HorizontalScrollView nodeScroll = new HorizontalScrollView(this);
        nodeScroll.setHorizontalScrollBarEnabled(false);
        nodeChips = new LinearLayout(this);
        nodeChips.setOrientation(LinearLayout.HORIZONTAL);
        nodeChips.setGravity(Gravity.CENTER_VERTICAL);
        nodeScroll.addView(nodeChips, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)
        ));
        nodePanel.addView(nodeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ));
        top.addView(nodePanel);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(top, topLp);

        ocrButton.setOnClickListener(v -> toggleOcr());
        nodesButton.setOnClickListener(v -> {
            nodePanel.setVisibility(nodePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            nodesButton.setText(nodePanel.getVisibility() == View.VISIBLE ? "ÎNCHIDE NODURI" : "NODURI");
        });
        labelsButton.setOnClickListener(v -> toggleFloatingLabels());
        libraryButton.setOnClickListener(v -> openLibrary());
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        freezeButton.setOnClickListener(v -> toggleFreeze());
        saveButton.setOnClickListener(v -> saveFrozenFrame());
        torchButton.setOnClickListener(v -> toggleTorch());

        saveButton.setEnabled(false);
        setContentView(root);
        updateOcrButton();
        updateLabelsButton();
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
                            overlayView.clearHits();
                            latestAllHits = Collections.emptyList();
                            updateLegend(Collections.emptyList());
                            setStatus("OCR live • niciun text citit încă", Color.rgb(78, 201, 126));
                        });
                        return;
                    }

                    TopicMatcher.SearchPlan activePlan = searchPlan;
                    if (activePlan == null) {
                        TopicMap activeMap = topicMap == null ? TopicMapStore.load(MainActivity.this) : topicMap;
                        activePlan = TopicMatcher.compile(MainActivity.this, activeMap);
                        searchPlan = activePlan;
                    }

                    List<MatchHit> hits = TopicMatcher.find(text, activePlan);
                    int recognizedWords = countRecognizedWords(text);
                    int searchableTerms = activePlan.termCount();
                    runOnUiThread(() -> applyHits(hits, recognizedWords, searchableTerms));
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
            overlayView.clearHits();
            updateLegend(Collections.emptyList());
            setStatus("Camera live • OCR în pauză", Color.rgb(170, 178, 186));
        } else {
            setStatus("OCR live • caut text", Color.rgb(78, 201, 126));
        }
        updateOcrButton();
    }

    private void updateOcrButton() {
        if (ocrButton == null) return;
        ocrButton.setText(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR PAUZĂ");
        ocrButton.setBackgroundTintList(ColorStateList.valueOf(
                ocrEnabled ? Color.rgb(42, 128, 94) : Color.rgb(93, 105, 116)
        ));
    }

    private void toggleFloatingLabels() {
        floatingLabelsEnabled = !floatingLabelsEnabled;
        AppPrefs.setFloatingLabels(this, floatingLabelsEnabled);
        updateLabelsButton();
        redrawVisibleHits();
    }

    private void updateLabelsButton() {
        if (labelsButton == null) return;
        labelsButton.setText(floatingLabelsEnabled ? "ETICHETE ON" : "ETICHETE OFF");
        labelsButton.setBackgroundTintList(ColorStateList.valueOf(
                floatingLabelsEnabled ? Color.rgb(123, 88, 154) : Color.rgb(53, 92, 125)
        ));
    }

    private void syncVisibleNodes(String profileId) {
        Set<String> current = new HashSet<>();
        if (topicMap != null) {
            for (TopicNode node : topicMap.nodes) {
                if (node.enabled) current.add(node.path);
            }
        }

        boolean profileChanged = profileId != null && !profileId.equals(visibleProfileId);
        if (profileChanged || knownNodePaths.isEmpty()) {
            visibleNodePaths.clear();
            visibleNodePaths.addAll(current);
        } else {
            visibleNodePaths.retainAll(current);
            for (String path : current) {
                if (!knownNodePaths.contains(path)) visibleNodePaths.add(path);
            }
        }
        knownNodePaths.clear();
        knownNodePaths.addAll(current);
        visibleProfileId = profileId == null ? "" : profileId;
    }

    private void rebuildNodeChips() {
        if (nodeChips == null) return;
        nodeChips.removeAllViews();

        Button all = nodeChip("TOATE", true);
        all.setOnClickListener(v -> {
            visibleNodePaths.clear();
            visibleNodePaths.addAll(knownNodePaths);
            rebuildNodeChips();
            redrawVisibleHits();
        });
        nodeChips.addView(all, chipLp());

        Button levelOne = nodeChip("DOAR NIVEL 1", true);
        levelOne.setOnClickListener(v -> {
            visibleNodePaths.clear();
            if (topicMap != null) {
                for (TopicNode node : topicMap.nodes) {
                    if (node.enabled && node.level == 1) visibleNodePaths.add(node.path);
                }
            }
            rebuildNodeChips();
            redrawVisibleHits();
        });
        nodeChips.addView(levelOne, chipLp());

        Button none = nodeChip("ASCUNDE TOATE", false);
        none.setOnClickListener(v -> {
            visibleNodePaths.clear();
            rebuildNodeChips();
            redrawVisibleHits();
        });
        nodeChips.addView(none, chipLp());

        if (topicMap == null) return;
        for (TopicNode node : topicMap.nodes) {
            if (!node.enabled) continue;
            boolean visible = visibleNodePaths.contains(node.path);
            StringBuilder prefix = new StringBuilder();
            for (int i = 1; i < node.level; i++) prefix.append("↳");
            String text = (visible ? "● " : "○ ") + prefix + node.title;
            Button chip = nodeChip(text, visible);
            chip.setOnClickListener(v -> {
                if (visibleNodePaths.contains(node.path)) visibleNodePaths.remove(node.path);
                else visibleNodePaths.add(node.path);
                rebuildNodeChips();
                redrawVisibleHits();
            });
            nodeChips.addView(chip, chipLp());
        }
    }

    private List<MatchHit> filterVisibleHits(List<MatchHit> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<MatchHit> out = new java.util.ArrayList<>();
        for (MatchHit hit : source) {
            if (hit != null && hit.node != null && visibleNodePaths.contains(hit.node.path)) out.add(hit);
        }
        return out;
    }

    private void redrawVisibleHits() {
        if (overlayView == null) return;
        List<MatchHit> visible = filterVisibleHits(latestAllHits);
        overlayView.updateHits(visible, AppPrefs.precision(this), floatingLabelsEnabled);
        updateLegend(visible);
    }

    private void updateLegend(List<MatchHit> visibleHits) {
        if (legendPanel == null) return;

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, MatchHit> first = new LinkedHashMap<>();
        if (visibleHits != null) {
            for (MatchHit hit : visibleHits) {
                if (hit == null || hit.node == null) continue;
                counts.put(hit.node.path, counts.getOrDefault(hit.node.path, 0) + 1);
                first.putIfAbsent(hit.node.path, hit);
            }
        }

        StringBuilder signatureBuilder = new StringBuilder(counts.toString());
        for (Map.Entry<String, MatchHit> entry : first.entrySet()) {
            signatureBuilder.append('|').append(entry.getKey()).append('=').append(entry.getValue().originalText);
        }
        String signature = signatureBuilder.toString();
        if (signature.equals(lastLegendSignature) && legendPanel.getChildCount() > 0) return;
        lastLegendSignature = signature;
        legendPanel.removeAllViews();

        if (counts.isEmpty()) {
            TextView empty = legendText("LEGENDĂ • fără potriviri afișate", Color.rgb(69, 92, 113));
            legendPanel.addView(empty, legendLp());
            return;
        }

        for (Map.Entry<String, MatchHit> entry : first.entrySet()) {
            MatchHit hit = entry.getValue();
            int count = counts.get(entry.getKey());
            String original = hit.originalText == null ? "" : hit.originalText.trim();
            String label = hit.node.title + (original.isEmpty() ? "" : " · " + original) + (count > 1 ? " ×" + count : "");
            TextView item = legendText(label, hit.node.color);
            item.setOnClickListener(v -> showHitDetails(hit));
            legendPanel.addView(item, legendLp());
        }
    }

    private TextView legendText(String text, int color) {
        TextView view = new TextView(this);
        view.setText("● " + text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(10);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(205, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        return view;
    }

    private void updateHeader() {
        MapProfile active = TopicLibraryStore.getActive(this);
        topicMap = TopicMapStore.load(this);
        int activeNodes = activeNodeCount(topicMap);
        int terms = activeTermCount(topicMap);
        if (mapTitle != null) mapTitle.setText(active.folder + "  ›  " + active.name + "   ▾");
        if (mapMetrics != null) {
            mapMetrics.setText(activeNodes + " noduri active • " + terms + " termeni • atinge pentru schimbare rapidă");
        }
    }

    private void openLibrary() {
        startActivity(new Intent(this, TopicLibraryActivity.class));
    }

    private void applyHits(List<MatchHit> hits, int recognizedWords, int searchableTerms) {
        if (frozen || !ocrEnabled) return;
        mainHandler.removeCallbacks(staleOverlayClear);
        mainHandler.postDelayed(staleOverlayClear, STALE_OVERLAY_MS);

        latestAllHits = hits == null ? Collections.emptyList() : new java.util.ArrayList<>(hits);
        List<MatchHit> visibleHits = filterVisibleHits(latestAllHits);
        overlayView.updateHits(visibleHits, AppPrefs.precision(this), floatingLabelsEnabled);
        updateLegend(visibleHits);

        Set<String> allNodes = new HashSet<>();
        for (MatchHit hit : latestAllHits) allNodes.add(hit.node.path);
        Set<String> shownNodes = new HashSet<>();
        for (MatchHit hit : visibleHits) shownNodes.add(hit.node.path);

        if (searchableTerms <= 0) {
            setStatus("OCR: " + recognizedWords + " cuvinte • adaugă termeni în hartă", Color.rgb(244, 188, 77));
            return;
        }
        if (recognizedWords <= 0) {
            setStatus("OCR live • apropie camera și ține textul clar", Color.rgb(78, 201, 126));
            return;
        }
        if (latestAllHits.isEmpty()) {
            setStatus("OCR: " + recognizedWords + " cuvinte • 0 potriviri", Color.rgb(78, 201, 126));
        } else {
            setStatus(
                    "OCR: " + recognizedWords + " • " + latestAllHits.size() + " găsite • " +
                            visibleHits.size() + " afișate • " + shownNodes.size() + "/" + allNodes.size() + " noduri",
                    Color.rgb(98, 211, 255)
            );
            if (!visibleHits.isEmpty()) maybeFeedback(visibleHits, shownNodes);
        }
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

            Bitmap composite = Bitmap.createBitmap(
                    Math.max(1, previewView.getWidth()),
                    Math.max(1, previewView.getHeight()),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(composite);
            canvas.drawBitmap(bitmap, null, new Rect(0, 0, composite.getWidth(), composite.getHeight()), null);
            overlayView.draw(canvas);

            frozenBitmap = composite;
            frozen = true;
            if (cameraController != null) cameraController.clearImageAnalysisAnalyzer();
            freezeImage.setImageBitmap(frozenBitmap);
            freezeImage.setVisibility(View.VISIBLE);
            overlayView.setVisibility(View.GONE);
            freezeButton.setText("REIA");
            saveButton.setEnabled(true);
            setStatus("Cadru înghețat • nimic nu este salvat automat", Color.rgb(244, 188, 77));
        } else {
            frozen = false;
            if (ocrEnabled) attachAnalyzer();
            overlayView.clearHits();
            latestAllHits = Collections.emptyList();
            updateLegend(Collections.emptyList());
            frozenBitmap = null;
            freezeImage.setImageDrawable(null);
            freezeImage.setVisibility(View.GONE);
            overlayView.setVisibility(View.VISIBLE);
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

    private Button toolbarButton(String text) {
        Button button = actionButton(text);
        button.setMinWidth(dp(78));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private LinearLayout.LayoutParams toolbarLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)
        );
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private Button nodeChip(String text, boolean active) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(
                active ? Color.rgb(42, 128, 94) : Color.rgb(74, 84, 94)
        ));
        return button;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
        );
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams legendLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)
        );
        lp.setMargins(0, 0, dp(4), 0);
        return lp;
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
                frozenBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
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
