package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MapEditorActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_MAP = 301;
    private static final int REQ_EXPORT_MAP = 302;

    private EditText nameEdit;
    private EditText mapEdit;
    private LinearLayout levelContainer;
    private LinearLayout nodeContainer;
    private LinearLayout advancedContainer;
    private Button advancedToggle;
    private final Set<String> collapsedPaths = new HashSet<>();
    private TopicMap currentMap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadCurrent();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(244, 240, 230));
        scroll.addView(root);

        TextView title = heading("HARTĂ DE CĂUTARE", 22);
        root.addView(title);

        TextView help = body(
                "Harta este complet flexibilă: poți crea noduri și subnoduri fără categorii prestabilite. " +
                "Poți adăuga termenii direct cu + TERMEN DE CĂUTAT; în editorul text se pot separa și cu |. " +
                "Dacă un nod nu are încă termeni, titlul lui este folosit temporar ca termen. " +
                "Poți și încărca harta dintr-un document TXT, MD sau DOCX. " +
                "Aplicația nu interpretează sensul; caută strict ce introduci tu."
        );
        help.setPadding(0, dp(4), 0, dp(10));
        root.addView(help);

        nameEdit = new EditText(this);
        nameEdit.setHint("Numele hărții");
        nameEdit.setSingleLine(true);
        root.addView(nameEdit, full(dp(50)));

        MapProfile activeProfile = TopicLibraryStore.getActive(this);
        TextView folderInfo = body("Folder: " + activeProfile.folder + "  •  harta activă");
        folderInfo.setTextSize(12);
        folderInfo.setPadding(0, 0, 0, dp(6));
        root.addView(folderInfo);

        Button addRoot = button("+ NOD PRINCIPAL");
        root.addView(addRoot, full(dp(48)));
        addRoot.setOnClickListener(v -> showAddNodeDialog(null));

        advancedToggle = button("EDITOR TEXT AVANSAT  ▾");
        root.addView(advancedToggle, full(dp(44)));

        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);

        mapEdit = new EditText(this);
        mapEdit.setHint("# Nod principal\ntermen | expresie\n## Subnod\nalt termen");
        mapEdit.setGravity(Gravity.TOP | Gravity.START);
        mapEdit.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mapEdit.setMinLines(10);
        advancedContainer.addView(mapEdit, full(dp(260)));

        LinearLayout actions = row();
        Button save = button("SALVEAZĂ / ANALIZEAZĂ");
        Button importButton = button("ÎNCARCĂ DOCUMENT");
        Button exportButton = button("EXPORTĂ HARTA");
        actions.addView(save, weighted());
        actions.addView(importButton, weighted());
        actions.addView(exportButton, weighted());
        advancedContainer.addView(actions);
        root.addView(advancedContainer);

        advancedToggle.setOnClickListener(v -> {
            boolean opening = advancedContainer.getVisibility() != View.VISIBLE;
            advancedContainer.setVisibility(opening ? View.VISIBLE : View.GONE);
            advancedToggle.setText(opening ? "EDITOR TEXT AVANSAT  ▴" : "EDITOR TEXT AVANSAT  ▾");
        });
        save.setOnClickListener(v -> saveAndRefresh());
        importButton.setOnClickListener(v -> importMap());
        exportButton.setOnClickListener(v -> exportMap());

        TextView nodesTitle = heading("ARBORELE HĂRȚII", 18);
        nodesTitle.setPadding(0, dp(18), 0, dp(6));
        root.addView(nodesTitle);

        LinearLayout bulk = row();
        Button all = button("TOATE");
        Button none = button("NICIUNUL");
        bulk.addView(all, weighted());
        bulk.addView(none, weighted());
        root.addView(bulk);

        all.setOnClickListener(v -> {
            saveRawWithoutRefresh();
            TopicMap map = TopicMapStore.load(this);
            TopicMapStore.setAllEnabled(this, map, true);
            refreshNodes();
        });
        none.setOnClickListener(v -> {
            saveRawWithoutRefresh();
            TopicMap map = TopicMapStore.load(this);
            TopicMapStore.setAllEnabled(this, map, false);
            refreshNodes();
        });

        TextView levelsTitle = heading("FILTRU PE NIVEL", 15);
        levelsTitle.setPadding(0, dp(10), 0, dp(4));
        root.addView(levelsTitle);

        levelContainer = new LinearLayout(this);
        levelContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(levelContainer);

        nodeContainer = new LinearLayout(this);
        nodeContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(nodeContainer);

        TextView footer = body(
                "Culoarea nodului este vizibilă permanent. Atinge săgeata pentru pliere; " +
                "ține apăsat pe un card pentru activare/dezactivare rapidă. " +
                "Editorul cu # rămâne disponibil ca mod avansat pentru import sau editări masive."
        );
        footer.setPadding(0, dp(12), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }


    @Override
    protected void onPause() {
        if (nameEdit != null && mapEdit != null) {
            saveRawWithoutRefresh();
        }
        super.onPause();
    }

    private void loadCurrent() {
        nameEdit.setText(TopicMapStore.getMapName(this));
        mapEdit.setText(TopicMapStore.getRawMap(this));
        refreshNodes();
    }

    private void saveRawWithoutRefresh() {
        TopicMapStore.saveMap(
                this,
                nameEdit.getText().toString(),
                mapEdit.getText().toString()
        );
    }

    private void saveAndRefresh() {
        saveRawWithoutRefresh();
        refreshNodes();
        toast("Harta a fost salvată.");
    }

    private void refreshNodes() {
        currentMap = TopicMapStore.load(this);
        nodeContainer.removeAllViews();
        levelContainer.removeAllViews();

        java.util.Set<Integer> levels = new java.util.TreeSet<>();
        for (TopicNode node : currentMap.nodes) levels.add(node.level);
        for (Integer level : levels) {
            boolean allEnabled = true;
            for (TopicNode node : currentMap.nodes) {
                if (node.level == level && !node.enabled) {
                    allEnabled = false;
                    break;
                }
            }
            CheckBox levelToggle = new CheckBox(this);
            levelToggle.setText("Nivel " + level);
            levelToggle.setChecked(allEnabled);
            levelToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                TopicMapStore.setLevelEnabled(this, currentMap, level, isChecked);
                refreshNodes();
            });
            levelContainer.addView(levelToggle);
        }

        if (currentMap.nodes.isEmpty()) {
            TextView empty = body("Nu există noduri. Adaugă cel puțin o linie care începe cu #.");
            nodeContainer.addView(empty);
            return;
        }

        for (TopicNode node : currentMap.nodes) {
            if (isHiddenByCollapsedAncestor(node)) continue;
            nodeContainer.addView(nodeCard(node));
        }
    }

    private View nodeCard(TopicNode node) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(9));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(2), withAlpha(node.color, node.enabled ? 210 : 85));
        card.setBackground(background);

        LinearLayout.LayoutParams cardLp = full(LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(Math.max(0, node.level - 1) * 14), dp(5), 0, dp(5));
        card.setLayoutParams(cardLp);
        card.setAlpha(node.enabled ? 1f : 0.55f);

        LinearLayout header = row();
        TextView colorMark = new TextView(this);
        colorMark.setText("●");
        colorMark.setTextSize(22);
        colorMark.setTextColor(node.color);
        colorMark.setGravity(Gravity.CENTER);
        header.addView(colorMark, new LinearLayout.LayoutParams(dp(34), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = heading(node.title, 15);
        title.setTextColor(Color.rgb(29, 39, 51));
        TextView path = body("L" + node.level + " • " + node.path);
        path.setTextSize(10.5f);
        path.setMaxLines(1);
        titleBox.addView(title);
        titleBox.addView(path);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, dp(46), 1f));

        if (hasChildren(node)) {
            Button fold = button(collapsedPaths.contains(node.path) ? "▸" : "▾");
            header.addView(fold, new LinearLayout.LayoutParams(dp(48), dp(42)));
            fold.setOnClickListener(v -> {
                if (!collapsedPaths.add(node.path)) collapsedPaths.remove(node.path);
                refreshNodes();
            });
        }
        card.addView(header);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        java.util.List<String> visibleTerms = node.terms.isEmpty()
                ? java.util.Collections.singletonList(node.title + "  (titlu)")
                : node.terms;
        for (String term : visibleTerms) chips.addView(termChip(term, node.color));
        chipScroll.addView(chips);
        card.addView(chipScroll, full(dp(40)));

        LinearLayout line = row();
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Activ");
        enabled.setChecked(node.enabled);
        line.addView(enabled, new LinearLayout.LayoutParams(dp(86), dp(46)));

        Button colorButton = button("CULOARE");
        colorButton.setTextColor(contrastText(node.color));
        colorButton.setBackgroundTintList(ColorStateList.valueOf(node.color));
        line.addView(colorButton, new LinearLayout.LayoutParams(0, dp(46), 1f));

        EditText symbol = new EditText(this);
        symbol.setHint("simbol");
        symbol.setText(node.symbol == null ? "" : node.symbol);
        symbol.setSingleLine(true);
        symbol.setGravity(Gravity.CENTER);
        line.addView(symbol, new LinearLayout.LayoutParams(dp(72), dp(46)));

        Button only = button("DOAR");
        line.addView(only, new LinearLayout.LayoutParams(dp(70), dp(46)));
        card.addView(line);

        LinearLayout addRow = row();
        Button addTerm = button("+ TERMEN");
        Button addChild = button("+ SUBNOD");
        addRow.addView(addTerm, weighted());
        addRow.addView(addChild, weighted());
        card.addView(addRow);
        addTerm.setOnClickListener(v -> showAddTermDialog(node));
        addChild.setOnClickListener(v -> showAddNodeDialog(node));

        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TopicMapStore.setNodeEnabled(this, node.path, isChecked);
            node.enabled = isChecked;
            card.setAlpha(isChecked ? 1f : 0.55f);
        });

        colorButton.setOnClickListener(v ->
                ColorPickerDialog.show(this, node.color, selected -> {
                    TopicMapStore.setNodeColor(this, node.path, selected);
                    node.color = selected;
                    refreshNodes();
                })
        );

        symbol.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                TopicMapStore.setNodeSymbol(MapEditorActivity.this, node.path, s.toString());
                node.symbol = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        only.setOnClickListener(v -> {
            TopicMapStore.setOnlyNode(this, currentMap, node.path);
            refreshNodes();
        });

        card.setOnLongClickListener(v -> {
            boolean next = !node.enabled;
            TopicMapStore.setNodeEnabled(this, node.path, next);
            node.enabled = next;
            refreshNodes();
            toast(next ? "Nod activat." : "Nod dezactivat.");
            return true;
        });
        return card;
    }

    private boolean isHiddenByCollapsedAncestor(TopicNode node) {
        for (String collapsed : collapsedPaths) {
            if (node.path.startsWith(collapsed + " > ")) return true;
        }
        return false;
    }

    private boolean hasChildren(TopicNode node) {
        if (currentMap == null) return false;
        String prefix = node.path + " > ";
        for (TopicNode candidate : currentMap.nodes) {
            if (candidate.level == node.level + 1 && candidate.path.startsWith(prefix)) return true;
        }
        return false;
    }

    private TextView termChip(String term, int color) {
        TextView chip = new TextView(this);
        chip.setText(term);
        chip.setTextSize(11);
        chip.setTextColor(Color.rgb(35, 42, 49));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(withAlpha(color, 36));
        bg.setStroke(dp(1), withAlpha(color, 120));
        bg.setCornerRadius(dp(16));
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        lp.setMargins(0, dp(3), dp(6), dp(3));
        chip.setLayoutParams(lp);
        return chip;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }


    private void showAddTermDialog(TopicNode node) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Termen sau expresie exactă");
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Adaugă termen în „" + node.title + "”")
                .setView(input)
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Adaugă", (dialog, which) -> {
                    String term = input.getText().toString().trim();
                    if (term.isEmpty()) {
                        toast("Scrie termenul de căutat.");
                        return;
                    }
                    addTermToNode(node, term);
                })
                .show();
    }

    private void addTermToNode(TopicNode target, String term) {
        for (String existing : target.terms) {
            if (existing.equalsIgnoreCase(term)) {
                toast("Termenul există deja în acest nod.");
                return;
            }
        }

        String raw = mapEdit.getText().toString().replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = raw.split("\n", -1);
        java.util.TreeMap<Integer, String> hierarchy = new java.util.TreeMap<>();
        int headingIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (!line.startsWith("#")) continue;

            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') level++;
            String headingTitle = line.substring(level).trim();
            if (headingTitle.isEmpty()) headingTitle = "Nod";

            java.util.ArrayList<Integer> remove = new java.util.ArrayList<>();
            for (Integer existingLevel : hierarchy.keySet()) {
                if (existingLevel >= level) remove.add(existingLevel);
            }
            for (Integer removeLevel : remove) hierarchy.remove(removeLevel);
            hierarchy.put(level, headingTitle);

            StringBuilder path = new StringBuilder();
            for (java.util.Map.Entry<Integer, String> entry : hierarchy.entrySet()) {
                if (path.length() > 0) path.append(" > ");
                path.append(entry.getValue());
            }

            if (level == target.level && path.toString().equals(target.path)) {
                headingIndex = i;
                break;
            }
        }

        if (headingIndex < 0) {
            toast("Nu am găsit nodul în hartă.");
            return;
        }

        java.util.ArrayList<String> updatedLines = new java.util.ArrayList<>();
        for (String line : lines) updatedLines.add(line);
        updatedLines.add(headingIndex + 1, term);

        String updated = android.text.TextUtils.join("\n", updatedLines);
        mapEdit.setText(updated);
        saveAndRefresh();
    }

    private void showAddNodeDialog(@Nullable TopicNode parent) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(parent == null ? "Numele nodului" : "Numele subnodului");
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        String dialogTitle = parent == null
                ? "Adaugă nod principal"
                : "Adaugă subnod în „" + parent.title + "”";

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(input)
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Adaugă", (dialog, which) -> {
                    String title = cleanHeadingTitle(input.getText().toString());
                    if (title.isEmpty()) {
                        toast("Scrie numele nodului.");
                        return;
                    }
                    if (parent == null) addRootNode(title);
                    else addSubnode(parent, title);
                })
                .show();
    }

    private String cleanHeadingTitle(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        while (cleaned.startsWith("#")) cleaned = cleaned.substring(1).trim();
        return cleaned;
    }

    private void addRootNode(String title) {
        String raw = mapEdit.getText().toString().replace("\r\n", "\n").replace('\r', '\n');
        String base = raw.replaceFirst("\\s+$", "");
        if (!base.isEmpty()) base += "\n\n";
        String updated = base + "# " + title + "\n";
        mapEdit.setText(updated);
        mapEdit.setSelection(mapEdit.length());
        saveAndRefresh();
    }

    private void addSubnode(TopicNode parent, String title) {
        String raw = mapEdit.getText().toString().replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = raw.split("\\n", -1);
        java.util.TreeMap<Integer, String> hierarchy = new java.util.TreeMap<>();
        int insertionIndex = lines.length;
        boolean foundParent = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (!line.startsWith("#")) continue;

            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') level++;
            String headingTitle = line.substring(level).trim();
            if (headingTitle.isEmpty()) headingTitle = "Nod";

            java.util.ArrayList<Integer> remove = new java.util.ArrayList<>();
            for (Integer existingLevel : hierarchy.keySet()) {
                if (existingLevel >= level) remove.add(existingLevel);
            }
            for (Integer removeLevel : remove) hierarchy.remove(removeLevel);
            hierarchy.put(level, headingTitle);

            StringBuilder path = new StringBuilder();
            for (java.util.Map.Entry<Integer, String> entry : hierarchy.entrySet()) {
                if (path.length() > 0) path.append(" > ");
                path.append(entry.getValue());
            }

            if (foundParent && level <= parent.level) {
                insertionIndex = i;
                break;
            }
            if (!foundParent && level == parent.level && path.toString().equals(parent.path)) {
                foundParent = true;
            }
        }

        if (!foundParent) {
            toast("Nu am găsit nodul în textul hărții. Salvează harta și încearcă din nou.");
            return;
        }

        java.util.ArrayList<String> updatedLines = new java.util.ArrayList<>();
        for (String line : lines) updatedLines.add(line);

        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < parent.level + 1; i++) marks.append('#');
        updatedLines.add(insertionIndex, marks + " " + title);

        String updated = android.text.TextUtils.join("\n", updatedLines);
        mapEdit.setText(updated);
        mapEdit.setSelection(Math.min(mapEdit.length(), offsetForLine(updated, insertionIndex + 1)));
        saveAndRefresh();
    }

    private int offsetForLine(String text, int lineNumber) {
        int line = 0;
        for (int i = 0; i < text.length(); i++) {
            if (line >= lineNumber) return i;
            if (text.charAt(i) == '\n') line++;
        }
        return text.length();
    }

    private void importMap() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain",
                "text/markdown",
                "text/*",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_IMPORT_MAP);
    }

    private void exportMap() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "BiblioTopicSearch_map.txt");
        startActivityForResult(intent, REQ_EXPORT_MAP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_IMPORT_MAP) {
            try {
                mapEdit.setText(readMapDocument(uri));
                saveAndRefresh();
            } catch (Exception e) {
                toast("Import eșuat: " + e.getMessage());
            }
        } else if (requestCode == REQ_EXPORT_MAP) {
            try {
                writeText(uri, mapEdit.getText().toString());
                toast("Harta a fost exportată.");
            } catch (Exception e) {
                toast("Export eșuat: " + e.getMessage());
            }
        }
    }

    private String readMapDocument(Uri uri) throws Exception {
        String name = getDisplayName(uri).toLowerCase(Locale.ROOT);
        String mime = getContentResolver().getType(uri);
        if (name.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(mime)) {
            throw new IllegalArgumentException("PDF nu poate fi folosit direct ca hartă. Exportă documentul ca TXT, MD sau DOCX.");
        }
        if (name.endsWith(".docx") ||
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(mime)) {
            return readDocx(uri);
        }
        return readText(uri);
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null) return value;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "";
    }

    private String readDocx(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalArgumentException("Fișier indisponibil");

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) continue;

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) bytes.write(buffer, 0, read);

                String xml = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                String text = xml
                        .replaceAll("(?i)<w:tab[^>]*/>", "\t")
                        .replaceAll("(?i)<w:br[^>]*/>", "\n")
                        .replaceAll("(?i)</w:p>", "\n")
                        .replaceAll("<[^>]+>", "");
                text = text
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace("&amp;", "&");
                return text.replaceAll("\\n{3,}", "\n\n").trim() + "\n";
            }
        }
        throw new IllegalArgumentException("Documentul DOCX nu conține text citibil.");
    }

    private String readText(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalArgumentException("Fișier indisponibil");
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        String text = out.toString();
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        return text;
    }

    private void writeText(Uri uri, String text) throws Exception {
        OutputStream output = getContentResolver().openOutputStream(uri, "wt");
        if (output == null) throw new IllegalArgumentException("Fișier indisponibil");
        try (OutputStream out = output) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String joinTerms(java.util.List<String> terms) {
        StringBuilder out = new StringBuilder();
        for (String term : terms) {
            if (out.length() > 0) out.append(" • ");
            out.append(term);
        }
        return out.toString();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return button;
    }

    private TextView heading(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(23, 32, 42));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(62, 69, 76));
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int contrastText(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
