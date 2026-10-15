package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Offline bulk ingestion of PDF sources.
 *
 * Each PDF page is rendered locally, OCR-ed locally, and passed through the same
 * exact cartography pipeline used by the camera. Page images are never persisted.
 */
public final class DigitalSourceImportActivity extends AppCompatActivity {
    private static final int REQ_OPEN_PDF = 601;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Uri selectedUri;
    private int pdfPageCount;

    private TextView sourceLabel;
    private TextView fileLabel;
    private TextView status;
    private EditText pdfFrom;
    private EditText pdfTo;
    private EditText printedFrom;
    private CheckBox defineScope;
    private ProgressBar progress;
    private Button importButton;
    private Button cancelButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshSource();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSource();
    }

    @Override
    protected void onDestroy() {
        cancelled.set(true);
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(18));
        root.setBackgroundColor(Color.rgb(13, 21, 28));

        TextView title = text(20f, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setText("IMPORT SURSĂ DIGITALĂ");
        root.addView(title, full(dp(48)));

        TextView principle = text(11.8f, Color.rgb(135, 207, 176));
        principle.setText(
                "PDF local → randare pagină → OCR local → CAPTURĂ EXACTĂ → registru exhaustiv. "
                        + "Imaginea paginii nu este salvată."
        );
        principle.setPadding(0, 0, 0, dp(8));
        root.addView(principle);

        sourceLabel = text(12.5f, Color.rgb(218, 230, 237));
        root.addView(sourceLabel);

        Button sourceButton = button("DESCHIDE REGISTRUL DE SURSE");
        sourceButton.setOnClickListener(v ->
                startActivity(new Intent(this, IndexCoreActivity.class)));
        root.addView(sourceButton, full(dp(46)));

        fileLabel = text(12.5f, Color.rgb(205, 219, 228));
        fileLabel.setText("PDF: —");
        fileLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(fileLabel);

        Button choose = button("ALEGE PDF");
        choose.setOnClickListener(v -> choosePdf());
        root.addView(choose, full(dp(48)));

        TextView mapping = text(13f, Color.WHITE);
        mapping.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mapping.setText("Mapare pagini");
        mapping.setPadding(0, dp(14), 0, dp(4));
        root.addView(mapping);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        pdfFrom = field("PDF de la", "1");
        row.addView(pdfFrom, weighted());
        pdfTo = field("PDF până la", "");
        row.addView(pdfTo, weighted());
        printedFrom = field("Prima pagină tipărită", "1");
        row.addView(printedFrom, weighted());
        root.addView(row);

        defineScope = new CheckBox(this);
        defineScope.setText("Intervalul importat este întregul domeniu de analiză al sursei");
        defineScope.setTextColor(Color.rgb(220, 230, 237));
        defineScope.setChecked(false);
        root.addView(defineScope);

        TextView explanation = text(11.5f, Color.rgb(169, 190, 203));
        explanation.setText(
                "Exemplu: PDF 12–311, prima pagină tipărită 1 → PDF 12 devine pag. 1. "
                        + "Dacă bifezi domeniul complet, exhaustivitatea va fi măsurată pe 1–300."
        );
        explanation.setPadding(0, dp(2), 0, dp(10));
        root.addView(explanation);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        root.addView(progress, full(dp(26)));

        status = text(12.2f, Color.rgb(205, 219, 228));
        status.setText("Alege un PDF.");
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        importButton = button("IMPORTĂ ȘI CARTOGRAFIAZĂ");
        importButton.setEnabled(false);
        importButton.setOnClickListener(v -> prepareImport());
        actions.addView(importButton, new LinearLayout.LayoutParams(0, dp(50), 1f));

        cancelButton = button("OPREȘTE");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> {
            cancelled.set(true);
            setStatus("Oprire cerută • pagina curentă este lăsată să se termine.", Color.rgb(230, 155, 72));
        });
        actions.addView(cancelButton, new LinearLayout.LayoutParams(dp(100), dp(50)));
        root.addView(actions);

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        root.addView(close, full(dp(46)));

        setContentView(root);
    }

    private void refreshSource() {
        if (sourceLabel == null) return;
        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        sourceLabel.setText(
                "SURSĂ ACTIVĂ: " + (source == null ? "—" : source.displayName())
                        + (source != null && !source.author.isEmpty() ? " • " + source.author : "")
        );
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_PDF || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            final int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (RuntimeException ignored) {
            // Some providers grant temporary access only; the current import still works.
        }
        selectedUri = uri;
        inspectPdf(uri);
    }

    private void inspectPdf(Uri uri) {
        importButton.setEnabled(false);
        setStatus("Inspectez PDF-ul…", Color.rgb(230, 190, 80));
        worker.execute(() -> {
            int count = 0;
            try (ParcelFileDescriptor descriptor =
                         getContentResolver().openFileDescriptor(uri, "r")) {
                if (descriptor == null) throw new IllegalStateException("PDF-ul nu poate fi deschis.");
                try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                    count = renderer.getPageCount();
                }
            } catch (Exception failure) {
                final String message = safeMessage(failure);
                runOnUiThread(() ->
                        setStatus("PDF nereușit: " + message, Color.rgb(230, 92, 92)));
                return;
            }

            final int pages = count;
            runOnUiThread(() -> {
                pdfPageCount = pages;
                pdfFrom.setText("1");
                pdfTo.setText(String.valueOf(pages));
                printedFrom.setText("1");
                fileLabel.setText("PDF selectat • " + pages + " pagini");
                importButton.setEnabled(pages > 0);
                setStatus("Pregătit pentru import.", Color.rgb(76, 190, 123));
            });
        });
    }

    private void prepareImport() {
        if (selectedUri == null || pdfPageCount <= 0) {
            setStatus("Alege mai întâi un PDF.", Color.rgb(230, 155, 72));
            return;
        }

        int from = parseInt(pdfFrom.getText().toString(), 1);
        int to = parseInt(pdfTo.getText().toString(), pdfPageCount);
        int printed = parseInt(printedFrom.getText().toString(), 1);
        DigitalImportPlan plan = new DigitalImportPlan(
                from, to, printed, defineScope.isChecked()
        );
        if (!plan.validForPdf(pdfPageCount)) {
            setStatus("Interval PDF invalid.", Color.rgb(230, 92, 92));
            return;
        }

        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        String sourceName = source == null ? "sursa activă" : source.displayName();
        new AlertDialog.Builder(this)
                .setTitle("Import exact PDF")
                .setMessage(
                        sourceName
                                + "\nPDF: " + plan.pdfPageFrom + "–" + plan.pdfPageTo
                                + " (" + plan.pageCount() + " pagini)"
                                + "\nPagini cartografice: " + plan.printedPageFrom
                                + "–" + plan.printedPageTo()
                                + (plan.defineScope
                                    ? "\n\nDomeniul exhaustivității va fi setat pe acest interval."
                                    : "\n\nDomeniul exhaustivității rămâne neschimbat.")
                                + "\n\nFiecare pagină lizibilă va fi închisă automat deoarece PDF-ul oferă pagina întreagă."
                )
                .setPositiveButton("PORNEȘTE", (d, w) -> runImport(plan))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void runImport(DigitalImportPlan plan) {
        if (selectedUri == null) return;
        cancelled.set(false);
        importButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progress.setProgress(0);
        setStatus("Import în curs…", Color.rgb(230, 190, 80));

        final Uri uri = selectedUri;
        final String sourceId = IndexCoreSourceRegistry.activeSourceId(this);
        worker.execute(() -> {
            TextRecognizer recognizer = TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS
            );
            int complete = 0;
            int unreadable = 0;
            int ambiguous = 0;
            int units = 0;
            try (ParcelFileDescriptor descriptor =
                         getContentResolver().openFileDescriptor(uri, "r")) {
                if (descriptor == null) throw new IllegalStateException("PDF-ul nu poate fi deschis.");
                try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                    if (!plan.validForPdf(renderer.getPageCount())) {
                        throw new IllegalArgumentException("Intervalul nu mai corespunde PDF-ului.");
                    }

                    TopicMap map = TopicMapStore.load(getApplicationContext());
                    int total = plan.pageCount();

                    for (int pdfPage = plan.pdfPageFrom; pdfPage <= plan.pdfPageTo; pdfPage++) {
                        if (cancelled.get()) break;
                        int printedPage = plan.printedPageForPdfPage(pdfPage);
                        final int ordinal = pdfPage - plan.pdfPageFrom + 1;
                        final int pdfPageForUi = pdfPage;
                        final int pageForUi = printedPage;
                        runOnUiThread(() -> {
                            progress.setProgress((int) Math.round((ordinal - 1) * 1000.0 / total));
                            setStatus(
                                    "PDF " + pdfPageForUi + "/" + plan.pdfPageTo
                                            + " → pag. " + pageForUi + " • OCR local…",
                                    Color.rgb(230, 190, 80)
                            );
                        });

                        PdfRenderer.Page page = renderer.openPage(pdfPage - 1);
                        Bitmap bitmap = null;
                        try {
                            bitmap = render(page);
                            Text recognized = recognizeBlocking(recognizer, bitmap);
                            if (recognized == null || recognized.getTextBlocks() == null
                                    || recognized.getTextBlocks().isEmpty()) {
                                unreadable++;
                                IndexCoreDatabase.get(getApplicationContext()).recordPageCapture(
                                        sourceId,
                                        System.currentTimeMillis(),
                                        String.valueOf(printedPage),
                                        false
                                );
                                continue;
                            }

                            ExactCartographyCapture.Report report =
                                    ExactCartographyCapture.capture(
                                            getApplicationContext(),
                                            recognized,
                                            map,
                                            String.valueOf(printedPage),
                                            true
                                    );
                            if (report.pageComplete) complete++;
                            else unreadable++;
                            if (report.map != null) {
                                ambiguous += report.map.ambiguousCount();
                                units += report.map.totalUnits();
                            }
                        } finally {
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                            page.close();
                        }
                    }

                    if (!cancelled.get() && plan.defineScope) {
                        IndexCoreDatabase.get(getApplicationContext()).setSourceScope(
                                sourceId,
                                plan.printedPageFrom,
                                plan.printedPageTo()
                        );
                    }
                }
            } catch (Exception failure) {
                final String message = safeMessage(failure);
                runOnUiThread(() -> {
                    importButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    setStatus("Import oprit de eroare: " + message, Color.rgb(230, 92, 92));
                });
                try { recognizer.close(); } catch (RuntimeException ignored) {}
                IndexCoreRuntime.stop();
                return;
            }

            try { recognizer.close(); } catch (RuntimeException ignored) {}
            IndexCoreRuntime.stop();

            final int completeCount = complete;
            final int unreadableCount = unreadable;
            final int ambiguousCount = ambiguous;
            final int unitCount = units;
            final boolean wasCancelled = cancelled.get();

            runOnUiThread(() -> {
                progress.setProgress(wasCancelled ? progress.getProgress() : 1000);
                importButton.setEnabled(true);
                cancelButton.setEnabled(false);
                setStatus(
                        (wasCancelled ? "IMPORT OPRIT" : "IMPORT TERMINAT")
                                + " • " + completeCount + " pagini închise"
                                + (unreadableCount > 0 ? " • " + unreadableCount + " de verificat" : "")
                                + " • " + unitCount + " unități"
                                + (ambiguousCount > 0 ? " • " + ambiguousCount + " ambigue" : ""),
                        unreadableCount > 0 || ambiguousCount > 0
                                ? Color.rgb(230, 155, 72)
                                : Color.rgb(76, 190, 123)
                );
            });
        });
    }

    private Bitmap render(PdfRenderer.Page page) {
        int sourceWidth = Math.max(1, page.getWidth());
        int sourceHeight = Math.max(1, page.getHeight());
        float scale = Math.max(1.4f, 1800f / sourceWidth);
        scale = Math.min(scale, 3.0f);
        int width = Math.max(1, Math.round(sourceWidth * scale));
        int height = Math.max(1, Math.round(sourceHeight * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private Text recognizeBlocking(TextRecognizer recognizer, Bitmap bitmap) throws Exception {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Text> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        recognizer.process(image)
                .addOnSuccessListener(value -> {
                    result.set(value);
                    latch.countDown();
                })
                .addOnFailureListener(failure -> {
                    error.set(failure);
                    latch.countDown();
                });

        if (!latch.await(45, TimeUnit.SECONDS)) {
            throw new IllegalStateException("OCR timeout.");
        }
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private void setStatus(String value, int color) {
        if (status == null) return;
        status.setText(value);
        status.setTextColor(color);
    }

    private EditText field(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(145, 165, 178));
        return input;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10.5f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return button;
    }

    private TextView text(float size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams value =
                new LinearLayout.LayoutParams(0, dp(54), 1f);
        value.setMargins(dp(2), 0, dp(2), 0);
        return value;
    }

    private LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private String safeMessage(Exception failure) {
        if (failure == null || failure.getMessage() == null || failure.getMessage().trim().isEmpty()) {
            return "eroare necunoscută";
        }
        return failure.getMessage().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
