package ro.bibliotopicsearch.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ro.bibliotopicsearch.app.semantic.OnDeviceSentenceEmbedder;

/**
 * NotebookLM-like local source browser, deliberately without synthesis:
 * semantic retrieval in stored sources, exact evidence only, no paraphrase or summary.
 */
public final class NotebookActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_TEXT = 801;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private NotebookStore store;
    private OnDeviceSentenceEmbedder embedder;
    private EditText searchInput;
    private TextView status;
    private LinearLayout content;
    private String incomingOcr = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        incomingOcr = getIntent().getStringExtra("ocr_text");
        if (incomingOcr == null) incomingOcr = "";
        buildUi();
        showSources();
        executor.execute(this::ensureEmbedder);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        if (embedder != null) {
            try { embedder.close(); } catch (Exception ignored) {}
        }
        if (store != null) store.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(12, 20, 27));

        TextView title = text("NOTEBOOK LOCAL • VERBATIM", 19, Color.WHITE, true);
        root.addView(title);
        TextView rule = text("Căutare semantică în surse locale. Fără răspuns generat, fără rezumat, fără parafrază.",
                12, Color.rgb(186, 205, 218), false);
        rule.setPadding(0, dp(3), 0, dp(9));
        root.addView(rule);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button addOcr = button("+ OCR CURENT");
        Button importText = button("+ TEXT");
        Button showSources = button("SURSE");
        actions.addView(addOcr, weighted());
        actions.addView(importText, weighted());
        actions.addView(showSources, weighted());
        root.addView(actions);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(0, dp(10), 0, dp(6));
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.rgb(135, 157, 172));
        searchInput.setHint("Caută semantic în toate sursele…");
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button search = button("CAUTĂ");
        searchRow.addView(search, new LinearLayout.LayoutParams(dp(86), dp(44)));
        root.addView(searchRow);

        status = text("", 11, Color.rgb(98, 211, 255), false);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addOcr.setOnClickListener(v -> addIncomingOcr());
        importText.setOnClickListener(v -> importText());
        showSources.setOnClickListener(v -> showSources());
        search.setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        setContentView(root);
    }

    private void ensureEmbedder() {
        if (embedder != null) return;
        try {
            embedder = new OnDeviceSentenceEmbedder(getApplicationContext());
            runOnUiThread(() -> status.setText("Motor semantic offline pregătit."));
        } catch (Throwable error) {
            runOnUiThread(() -> status.setText("Model semantic indisponibil: " + error.getClass().getSimpleName()));
        }
    }

    private void addIncomingOcr() {
        if (incomingOcr.trim().isEmpty()) {
            toast("Nu există încă text OCR curent. Revino la cameră și scanează pagina.");
            return;
        }
        promptAndIndex("Scanare OCR", "Pagină scanată", incomingOcr);
    }

    private void importText() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQ_IMPORT_TEXT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_TEXT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            String imported = readText(uri);
            String name = displayName(uri);
            promptAndIndex(name == null ? "Text importat" : name, "Document", imported);
        } catch (Exception error) {
            toast("Nu am putut citi fișierul text.");
        }
    }

    private void promptAndIndex(String defaultSource, String defaultPage, String text) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18), dp(6), dp(18), 0);
        EditText source = new EditText(this);
        source.setHint("Numele sursei");
        source.setText(defaultSource);
        EditText page = new EditText(this);
        page.setHint("Pagină / secțiune");
        page.setText(defaultPage);
        fields.addView(source);
        fields.addView(page);

        new AlertDialog.Builder(this)
                .setTitle("Adaugă sursă locală")
                .setView(fields)
                .setMessage("Textul va fi păstrat local și indexat semantic. Rezultatele rămân verbatim.")
                .setNegativeButton("Anulează", null)
                .setPositiveButton("Indexează", (dialog, which) -> indexSource(
                        source.getText().toString(), page.getText().toString(), text))
                .show();
    }

    private void indexSource(String source, String page, String text) {
        status.setText("Indexez local sursa…");
        executor.execute(() -> {
            try {
                ensureEmbedder();
                if (embedder == null) throw new IllegalStateException("Modelul semantic nu este disponibil");
                long id = store.addPage(source, page, text, embedder);
                runOnUiThread(() -> {
                    incomingOcr = "";
                    status.setText("Sursă indexată local • ID " + id);
                    showSources();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> status.setText("Indexare eșuată: " + error.getMessage()));
            }
        });
    }

    private void runSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            showSources();
            return;
        }
        status.setText("Caut semantic în surse locale…");
        content.removeAllViews();
        executor.execute(() -> {
            try {
                ensureEmbedder();
                if (embedder == null) throw new IllegalStateException("Model indisponibil");
                List<NotebookStore.SearchResult> results = store.search(query, embedder, 20);
                runOnUiThread(() -> showResults(query, results));
            } catch (Throwable error) {
                runOnUiThread(() -> status.setText("Căutare eșuată: " + error.getMessage()));
            }
        });
    }

    private void showResults(String query, List<NotebookStore.SearchResult> results) {
        content.removeAllViews();
        status.setText(results.size() + " fragmente verbatim pentru: " + query);
        if (results.isEmpty()) {
            content.addView(text("Nicio dovadă suficient de apropiată semantic.", 13,
                    Color.rgb(190, 202, 211), false));
            return;
        }
        for (NotebookStore.SearchResult result : results) {
            LinearLayout card = card();
            TextView head = text(result.sourceName + " • " + result.pageLabel + " • " +
                    Math.round(result.similarity * 100f) + "%", 12, Color.rgb(98, 211, 255), true);
            card.addView(head);
            TextView quote = text(result.exactText, 15, Color.WHITE, false);
            quote.setPadding(0, dp(5), 0, dp(2));
            card.addView(quote);
            TextView footer = text("DOVADĂ EXACTĂ • " + result.startChar + "→" + result.endChar,
                    10, Color.rgb(143, 160, 173), false);
            card.addView(footer);
            card.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(result.sourceName + " • " + result.pageLabel)
                    .setMessage(result.exactText)
                    .setPositiveButton("Închide", null)
                    .show());
            content.addView(card);
        }
    }

    private void showSources() {
        List<NotebookStore.SourcePage> pages = store.listPages();
        content.removeAllViews();
        status.setText(pages.size() + " surse/pagini locale • fără cloud");
        if (pages.isEmpty()) {
            content.addView(text("Adaugă OCR-ul curent sau importă un fișier text. Notebook-ul nu generează rezumate.",
                    13, Color.rgb(190, 202, 211), false));
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
        for (NotebookStore.SourcePage page : pages) {
            LinearLayout card = card();
            card.addView(text(page.sourceName, 15, Color.WHITE, true));
            card.addView(text(page.pageLabel + " • " + page.chunks + " fragmente indexate • " +
                    format.format(new Date(page.createdAt)), 11, Color.rgb(155, 177, 191), false));
            String preview = page.text.trim();
            if (preview.length() > 220) preview = preview.substring(0, 220) + "…";
            card.addView(text(preview, 13, Color.rgb(215, 222, 227), false));
            card.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(page.sourceName + " • " + page.pageLabel)
                    .setMessage(page.text)
                    .setNegativeButton("Șterge", (dialog, which) -> confirmDelete(page))
                    .setPositiveButton("Închide", null)
                    .show());
            content.addView(card);
        }
    }

    private void confirmDelete(NotebookStore.SourcePage page) {
        new AlertDialog.Builder(this)
                .setTitle("Ștergi sursa?")
                .setMessage(page.sourceName + " • " + page.pageLabel)
                .setNegativeButton("Anulează", null)
                .setPositiveButton("Șterge", (dialog, which) -> {
                    store.deletePage(page.id);
                    showSources();
                })
                .show();
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Fișier indisponibil");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (out.size() > 10 * 1024 * 1024) throw new IllegalArgumentException("Fișierul este prea mare");
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(Color.rgb(27, 40, 50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return text;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
