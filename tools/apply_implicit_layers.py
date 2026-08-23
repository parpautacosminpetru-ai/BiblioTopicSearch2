from pathlib import Path

ROOT = Path("project-src")
MAIN = ROOT / "app/src/main/java/ro/bibliotopicsearch/app/MainActivity.java"
GRADLE = ROOT / "app/build.gradle.kts"

text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f"Patch point missing: {label}")
    text = text.replace(old, new, 1)

replace_once(
'''    private Button nodesButton;\n    private Button labelsButton;\n    private LinearLayout nodePanel;''',
'''    private Button nodesButton;\n    private Button labelsButton;\n    private Button textualButton;\n    private Button semanticButton;\n    private Button themeLayerButton;\n    private Button zoomButton;\n    private LinearLayout nodePanel;''',
"toolbar fields"
)

replace_once(
'''    private TopicMap topicMap;\n    private boolean frozen = false;\n    private boolean torchEnabled = false;\n    private boolean ocrEnabled = true;''',
'''    private TopicMap topicMap;\n    private TopicMap userTopicMap;\n    private boolean frozen = false;\n    private boolean torchEnabled = false;\n    private boolean ocrEnabled = true;\n    private boolean textualLayerEnabled = false;\n    private boolean semanticLayerEnabled = false;\n    private boolean themeLayerEnabled = true;\n    private int zoomLevel = 0;''',
"layer state fields"
)

replace_once(
'''        ocrEnabled = AppPrefs.ocrEnabled(this);\n        floatingLabelsEnabled = AppPrefs.floatingLabels(this);\n        buildUi();''',
'''        ocrEnabled = AppPrefs.ocrEnabled(this);\n        floatingLabelsEnabled = AppPrefs.floatingLabels(this);\n        textualLayerEnabled = AppPrefs.textualLayer(this);\n        semanticLayerEnabled = AppPrefs.semanticLayer(this);\n        themeLayerEnabled = AppPrefs.themeLayer(this);\n        zoomLevel = AppPrefs.zoomLevel(this);\n        buildUi();''',
"onCreate preferences"
)

replace_once(
'''        MapProfile activeProfile = TopicLibraryStore.getActive(this);\n        topicMap = TopicMapStore.load(this);\n        searchPlan = TopicMatcher.compile(this, topicMap);\n        syncVisibleNodes(activeProfile.id);\n        updateHeader();\n        updateOcrButton();\n        updateLabelsButton();\n        rebuildNodeChips();\n        redrawVisibleHits();''',
'''        reloadSearchLayers();\n        updateOcrButton();\n        updateLabelsButton();\n        updateLayerButtons();\n        updateZoomButton();''',
"onResume reload"
)

replace_once(
'''        ocrButton = toolbarButton(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR PAUZĂ");\n        nodesButton = toolbarButton("NODURI");\n        labelsButton = toolbarButton("ETICHETE");\n        Button libraryButton = toolbarButton("TEME");\n        freezeButton = toolbarButton("CADRU");''',
'''        ocrButton = toolbarButton(ocrEnabled ? "● OCR LIVE" : "Ⅱ OCR PAUZĂ");\n        textualButton = toolbarButton("TEXT");\n        semanticButton = toolbarButton("SEM");\n        themeLayerButton = toolbarButton("ȚINTĂ");\n        zoomButton = toolbarButton("LUPĂ");\n        nodesButton = toolbarButton("NODURI");\n        labelsButton = toolbarButton("ETICHETE");\n        Button libraryButton = toolbarButton("TEME");\n        freezeButton = toolbarButton("CADRU");''',
"toolbar declarations"
)

replace_once(
'''        toolbar.addView(ocrButton, toolbarLp());\n        toolbar.addView(nodesButton, toolbarLp());\n        toolbar.addView(labelsButton, toolbarLp());''',
'''        toolbar.addView(ocrButton, toolbarLp());\n        toolbar.addView(textualButton, toolbarLp());\n        toolbar.addView(semanticButton, toolbarLp());\n        toolbar.addView(themeLayerButton, toolbarLp());\n        toolbar.addView(zoomButton, toolbarLp());\n        toolbar.addView(nodesButton, toolbarLp());\n        toolbar.addView(labelsButton, toolbarLp());''',
"toolbar views"
)

replace_once(
'''        ocrButton.setOnClickListener(v -> toggleOcr());\n        nodesButton.setOnClickListener(v -> {''',
'''        ocrButton.setOnClickListener(v -> toggleOcr());\n        textualButton.setOnClickListener(v -> toggleTextualLayer());\n        semanticButton.setOnClickListener(v -> toggleSemanticLayer());\n        themeLayerButton.setOnClickListener(v -> toggleThemeLayer());\n        zoomButton.setOnClickListener(v -> toggleZoom());\n        nodesButton.setOnClickListener(v -> {''',
"toolbar listeners"
)

replace_once(
'''        updateOcrButton();\n        updateLabelsButton();\n        setStatus(''',
'''        updateOcrButton();\n        updateLabelsButton();\n        updateLayerButtons();\n        updateZoomButton();\n        setStatus(''',
"initial button state"
)

replace_once(
'''        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);\n        previewView.setController(cameraController);''',
'''        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);\n        cameraController.setPinchToZoomEnabled(true);\n        previewView.setController(cameraController);\n        applyZoom();''',
"camera zoom setup"
)

replace_once(
'''                        TopicMap activeMap = topicMap == null ? TopicMapStore.load(MainActivity.this) : topicMap;''',
'''                        TopicMap activeMap = topicMap == null ? buildActiveSearchMap() : topicMap;''',
"analyzer fallback map"
)

marker = '''    private void syncVisibleNodes(String profileId) {'''
insert = r'''    private TopicMap buildActiveSearchMap() {
        java.util.List<TopicMap> layers = new java.util.ArrayList<>();
        if (themeLayerEnabled) {
            if (userTopicMap == null) userTopicMap = TopicMapStore.load(this);
            layers.add(userTopicMap);
        }
        if (textualLayerEnabled) layers.add(BuiltInMaps.textual(this));
        if (semanticLayerEnabled) layers.add(BuiltInMaps.semantic(this));
        return TopicMapMerger.merge("Straturi active", layers.toArray(new TopicMap[0]));
    }

    private void reloadSearchLayers() {
        MapProfile activeProfile = TopicLibraryStore.getActive(this);
        userTopicMap = TopicMapStore.load(this);
        topicMap = buildActiveSearchMap();
        searchPlan = TopicMatcher.compile(this, topicMap);
        syncVisibleNodes(activeProfile.id);
        latestAllHits = Collections.emptyList();
        if (overlayView != null) overlayView.clearHits();
        updateLegend(Collections.emptyList());
        updateHeader();
        rebuildNodeChips();
        redrawVisibleHits();
    }

    private void toggleTextualLayer() {
        textualLayerEnabled = !textualLayerEnabled;
        AppPrefs.setTextualLayer(this, textualLayerEnabled);
        reloadSearchLayers();
        updateLayerButtons();
        if (textualLayerEnabled) openNodePanelForBuiltIns("TEXTUAL activ • selectează categoria din NODURI");
        else setStatus("TEXTUAL oprit", Color.rgb(170, 178, 186));
    }

    private void toggleSemanticLayer() {
        semanticLayerEnabled = !semanticLayerEnabled;
        AppPrefs.setSemanticLayer(this, semanticLayerEnabled);
        reloadSearchLayers();
        updateLayerButtons();
        if (semanticLayerEnabled) openNodePanelForBuiltIns("SEMANTIC activ • selectează categoria din NODURI");
        else setStatus("SEMANTIC oprit", Color.rgb(170, 178, 186));
    }

    private void toggleThemeLayer() {
        themeLayerEnabled = !themeLayerEnabled;
        AppPrefs.setThemeLayer(this, themeLayerEnabled);
        reloadSearchLayers();
        updateLayerButtons();
        setStatus(themeLayerEnabled ? "Ținta tematică activă" : "Ținta tematică oprită • poți lucra doar textual/semantic",
                themeLayerEnabled ? Color.rgb(78, 201, 126) : Color.rgb(244, 188, 77));
    }

    private void openNodePanelForBuiltIns(String message) {
        if (nodePanel != null) nodePanel.setVisibility(View.VISIBLE);
        if (nodesButton != null) nodesButton.setText("ÎNCHIDE NODURI");
        setStatus(message, Color.rgb(98, 211, 255));
    }

    private void updateLayerButtons() {
        if (textualButton != null) {
            textualButton.setText(textualLayerEnabled ? "TEXT ON" : "TEXT OFF");
            textualButton.setBackgroundTintList(ColorStateList.valueOf(
                    textualLayerEnabled ? Color.rgb(40, 146, 177) : Color.rgb(74, 84, 94)
            ));
        }
        if (semanticButton != null) {
            semanticButton.setText(semanticLayerEnabled ? "SEM ON" : "SEM OFF");
            semanticButton.setBackgroundTintList(ColorStateList.valueOf(
                    semanticLayerEnabled ? Color.rgb(210, 126, 49) : Color.rgb(74, 84, 94)
            ));
        }
        if (themeLayerButton != null) {
            themeLayerButton.setText(themeLayerEnabled ? "ȚINTĂ ON" : "ȚINTĂ OFF");
            themeLayerButton.setBackgroundTintList(ColorStateList.valueOf(
                    themeLayerEnabled ? Color.rgb(42, 128, 94) : Color.rgb(74, 84, 94)
            ));
        }
    }

    private void toggleZoom() {
        if (frozen) {
            toast("Reia camera pentru a schimba lupa.");
            return;
        }
        zoomLevel = (zoomLevel + 1) % 4;
        AppPrefs.setZoomLevel(this, zoomLevel);
        applyZoom();
    }

    private void applyZoom() {
        updateZoomButton();
        if (cameraController == null) return;
        float[] levels = {0f, 0.20f, 0.42f, 0.68f};
        try {
            cameraController.setLinearZoom(levels[Math.max(0, Math.min(3, zoomLevel))]);
        } catch (Exception ignored) {
            // Some camera devices expose a restricted zoom range; keep OCR usable.
        }
    }

    private void updateZoomButton() {
        if (zoomButton == null) return;
        zoomButton.setText("LUPĂ " + (zoomLevel + 1));
        zoomButton.setBackgroundTintList(ColorStateList.valueOf(
                zoomLevel > 0 ? Color.rgb(123, 88, 154) : Color.rgb(53, 92, 125)
        ));
    }

'''
if marker not in text:
    raise SystemExit("Patch point missing: layer methods insertion")
text = text.replace(marker, insert + marker, 1)

replace_once(
'''        if (profileChanged || knownNodePaths.isEmpty()) {\n            visibleNodePaths.clear();\n            visibleNodePaths.addAll(current);\n        } else {\n            visibleNodePaths.retainAll(current);\n            for (String path : current) {\n                if (!knownNodePaths.contains(path)) visibleNodePaths.add(path);\n            }\n        }''',
'''        if (profileChanged || knownNodePaths.isEmpty()) {\n            visibleNodePaths.clear();\n            for (String path : current) {\n                // Built-in libraries are available immediately, but start visually quiet.\n                // The user chooses one or more categories from NODURI.\n                if (!BuiltInMaps.isBuiltInPath(path)) visibleNodePaths.add(path);\n            }\n        } else {\n            visibleNodePaths.retainAll(current);\n            for (String path : current) {\n                if (!knownNodePaths.contains(path) && !BuiltInMaps.isBuiltInPath(path)) {\n                    visibleNodePaths.add(path);\n                }\n            }\n        }''',
"built-in visibility policy"
)

replace_once(
'''    private void updateHeader() {\n        MapProfile active = TopicLibraryStore.getActive(this);\n        topicMap = TopicMapStore.load(this);\n        int activeNodes = activeNodeCount(topicMap);\n        int terms = activeTermCount(topicMap);\n        if (mapTitle != null) mapTitle.setText(active.folder + "  ›  " + active.name + "   ▾");\n        if (mapMetrics != null) {\n            mapMetrics.setText(activeNodes + " noduri active • " + terms + " termeni • atinge pentru schimbare rapidă");\n        }\n    }''',
'''    private void updateHeader() {\n        MapProfile active = TopicLibraryStore.getActive(this);\n        int activeNodes = activeNodeCount(topicMap);\n        int terms = activeTermCount(topicMap);\n        if (mapTitle != null) mapTitle.setText(active.folder + "  ›  " + active.name + "   ▾");\n        if (mapMetrics != null) {\n            StringBuilder layers = new StringBuilder();\n            if (themeLayerEnabled) layers.append("ȚINTĂ ");\n            if (textualLayerEnabled) layers.append("TEXT ");\n            if (semanticLayerEnabled) layers.append("SEM ");\n            if (layers.length() == 0) layers.append("NICIUN STRAT");\n            mapMetrics.setText(activeNodes + " noduri • " + terms + " termeni • " + layers.toString().trim());\n        }\n    }''',
"header layers"
)

replace_once(
'''            setStatus("OCR: " + recognizedWords + " cuvinte • adaugă termeni în hartă", Color.rgb(244, 188, 77));''',
'''            setStatus("OCR: " + recognizedWords + " cuvinte • activează TEXT, SEM sau ȚINTĂ", Color.rgb(244, 188, 77));''',
"empty search status"
)

MAIN.write_text(text, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = gradle.replace('versionCode = 3', 'versionCode = 4', 1)
gradle = gradle.replace('versionName = "1.1.1"', 'versionName = "1.2.0"', 1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Implicit textual/semantic layers, theme toggle and magnifier patched successfully.")
