package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Robust offline PDF ingestion.
 *
 * The selected document is first copied into app cache. This guarantees a seekable
 * local file descriptor for PdfRenderer even when the source document comes from a
 * cloud/provider URI. Every page is then rendered locally, OCR-ed locally and sent
 * through CAPTURĂ EXACTĂ. The untouched original PDF is separately archived,
 * and page bitmaps remain temporary; OCR is stored only as an independent overlay.
 */
public final class DigitalSourceImportActivity extends AppCompatActivity {
    private static final int REQ_OPEN_PDF = 601;
    private static final int OCR_TIMEOUT_SECONDS = 45;
    private static final int NORMAL_TARGET_WIDTH = 1800;
    private static final int RETRY_TARGET_WIDTH = 2400;
    private static final long NORMAL_MAX_PIXELS = 5_500_000L;
    private static final long RETRY_MAX_PIXELS = 8_000_000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Uri selectedUri;
    private File cachedPdf;
    private int pdfPageCount;
    private String selectedName = "";

    private TextView sourceLabel;
    private TextView fileLabel;
    private TextView status;
    private EditText pdfFrom;
    private EditText pdfTo;
    private EditText printedFrom;
    private CheckBox defineScope;
    private ProgressBar progress;
    private Button chooseButton;
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
        deleteCachedPdf();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 21, 28));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text(20f, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setText("IMPORT PDF");
        root.addView(title, full(dp(48)));

        TextView principle = text(11.8f, Color.rgb(135, 207, 176));
        principle.setText(
                "PDF-ul ORIGINAL este arhivat fără modificări (SHA-256). OCR-ul și cartografierea sunt straturi separate. "
                        + "Paginile sunt marcate complete numai după verificarea explicită de către istoric."
        );
        principle.setPadding(0, 0, 0, dp(8));
        root.addView(principle);

        sourceLabel = text(12.5f, Color.rgb(218, 230, 237));
        root.addView(sourceLabel);

        Button sourceButton = button("DEFINEȘTE / SCHIMBĂ SURSA");
        sourceButton.setOnClickListener(v -> openSourceEditor());
        root.addView(sourceButton, full(dp(46)));

        fileLabel = text(12.5f, Color.rgb(205, 219, 228));
        fileLabel.setText("PDF: —");
        fileLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(fileLabel);

        chooseButton = button("ALEGE PDF");
        chooseButton.setOnClickListener(v -> choosePdf());
        root.addView(chooseButton, full(dp(48)));

        Button originals = button("DESCHIDE PAGINILE ORIGINALE / OCR");
        originals.setOnClickListener(v -> {
            Intent open = new Intent(this, OriginalPageActivity.class);
            open.putExtra(OriginalPageActivity.EXTRA_SOURCE_ID,
                    IndexCoreSourceRegistry.activeSourceId(this));
            startActivity(open);
        });
        root.addView(originals, full(dp(44)));

        TextView mapping = text(13f, Color.WHITE);
        mapping.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mapping.setText("Maparea paginilor");
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
                "Exemplu: PDF 12–311, prima pagină tipărită 1 → pagina PDF 12 devine pag. 1. "
                        + "Dacă PDF-ul coincide cu întreg intervalul analizat, bifează domeniul complet."
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
            setStatus(
                    "Oprire cerută • pagina curentă este lăsată să se termine.",
                    Color.rgb(230, 155, 72)
            );
        });
        actions.addView(cancelButton, new LinearLayout.LayoutParams(dp(100), dp(50)));
        root.addView(actions);

        TextView help = text(11.3f, Color.rgb(155, 178, 191));
        help.setText(
                "Dacă o pagină nu poate fi citită, importul continuă cu pagina următoare. "
                        + "Paginile nereușite nu sunt declarate complete și rămân de verificat."
        );
        help.setPadding(0, dp(10), 0, dp(10));
        root.addView(help);

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        root.addView(close, full(dp(46)));

        setContentView(scroll);
    }

    private void refreshSource() {
        if (sourceLabel == null) return;
        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        boolean ready = source != null && !source.title.trim().isEmpty();
        sourceLabel.setText(
                "SURSĂ ACTIVĂ: " + (source == null ? "—" : source.displayName())
                        + (source != null && !source.author.isEmpty() ? " • " + source.author : "")
                        + (ready ? "" : "\n⚠ Completează titlul sursei înainte de import.")
        );
        sourceLabel.setTextColor(
                ready ? Color.rgb(218, 230, 237) : Color.rgb(238, 190, 107)
        );
    }

    private void openSourceEditor() {
        Intent intent = new Intent(this, IndexCoreActivity.class);
        intent.putExtra(IndexCoreActivity.EXTRA_OPEN_PANEL, IndexCoreActivity.PANEL_SOURCE_EDIT);
        startActivity(intent);
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent, REQ_OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_PDF || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (RuntimeException ignored) {
            // Temporary permission is still enough because we immediately materialize the PDF.
        }

        selectedUri = uri;
        selectedName = displayName(uri);
        materializeAndInspect(uri);
    }

    private void materializeAndInspect(Uri uri) {
        cancelled.set(false);
        chooseButton.setEnabled(false);
        importButton.setEnabled(false);
        progress.setProgress(0);
        setStatus("Copiez PDF-ul local pentru procesare sigură…", Color.rgb(230, 190, 80));

        worker.execute(() -> {
            File local = null;
            int pages;
            try {
                local = materializePdf(uri);
                pages = inspectLocalPdf(local);
            } catch (Exception failure) {
                if (local != null) {
                    //noinspection ResultOfMethodCallIgnored
                    local.delete();
                }
                final String message = explainPdfFailure(failure);
                runOnUiThread(() -> {
                    chooseButton.setEnabled(true);
                    importButton.setEnabled(false);
                    fileLabel.setText("PDF: —");
                    setStatus("PDF nereușit: " + message, Color.rgb(230, 92, 92));
                });
                return;
            }

            File previous = cachedPdf;
            cachedPdf = local;
            if (previous != null && !previous.equals(local)) {
                //noinspection ResultOfMethodCallIgnored
                previous.delete();
            }

            final int count = pages;
            runOnUiThread(() -> {
                pdfPageCount = count;
                pdfFrom.setText("1");
                pdfTo.setText(String.valueOf(count));
                printedFrom.setText("1");
                fileLabel.setText(
                        "PDF: " + (selectedName.isEmpty() ? "document selectat" : selectedName)
                                + " • " + count + " pagini"
                );
                chooseButton.setEnabled(true);
                importButton.setEnabled(count > 0);
                setStatus(
                        "PDF pregătit local. Poți porni importul.",
                        Color.rgb(76, 190, 123)
                );
            });
        });
    }

    private File materializePdf(Uri uri) throws Exception {
        File dir = new File(getCacheDir(), "pdf_import");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Nu pot crea spațiul temporar pentru PDF.");
        }

        File outFile = File.createTempFile("source_", ".pdf", dir);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalStateException("Nu pot citi documentul selectat.");
            byte[] buffer = new byte[64 * 1024];
            int read;
            long total = 0L;
            while ((read = in.read(buffer)) >= 0) {
                if (cancelled.get()) throw new IllegalStateException("Operație anulată.");
                if (read == 0) continue;
                out.write(buffer, 0, read);
                total += read;
            }
            out.flush();
            if (total <= 0L) throw new IllegalStateException("Fișierul PDF este gol.");
        } catch (Exception failure) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw failure;
        }
        return outFile;
    }

    private int inspectLocalPdf(File file) throws Exception {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
        )) {
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int count = renderer.getPageCount();
                if (count <= 0) throw new IllegalStateException("PDF-ul nu conține pagini.");
                return count;
            }
        }
    }

    private void prepareImport() {
        if (cachedPdf == null || !cachedPdf.exists() || pdfPageCount <= 0) {
            setStatus("Alege mai întâi un PDF valid.", Color.rgb(230, 155, 72));
            return;
        }

        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        if (source == null || source.title.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Definește sursa")
                    .setMessage(
                            "Înainte de import, completează cel puțin titlul sursei. "
                                    + "Astfel toate paginile rămân legate de proveniența corectă."
                    )
                    .setPositiveButton("DEFINEȘTE SURSA", (d, w) -> openSourceEditor())
                    .setNegativeButton("ANULEAZĂ", null)
                    .show();
            return;
        }

        int from = parseInt(pdfFrom.getText().toString(), 1);
        int to = parseInt(pdfTo.getText().toString(), pdfPageCount);
        int printed = parseInt(printedFrom.getText().toString(), 1);
        DigitalImportPlan plan = new DigitalImportPlan(
                from,
                to,
                printed,
                defineScope.isChecked()
        );
        if (!plan.validForPdf(pdfPageCount)) {
            setStatus(
                    "Interval PDF invalid. Verifică prima și ultima pagină.",
                    Color.rgb(230, 92, 92)
            );
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Import exact PDF")
                .setMessage(
                        source.displayName()
                                + "\n\nPDF: " + plan.pdfPageFrom + "–" + plan.pdfPageTo
                                + " (" + plan.pageCount() + " pagini)"
                                + "\nPagini citabile: " + plan.printedPageFrom
                                + "–" + plan.printedPageTo()
                                + (plan.defineScope
                                    ? "\n\nAcest interval va defini domeniul exhaustivității."
                                    : "\n\nDomeniul exhaustivității rămâne neschimbat.")
                                + "\n\nOriginalul PDF rămâne intact. OCR nu confirmă singur o pagină completă."
                        + "\nImportul continuă chiar dacă o pagină individuală eșuează."
                )
                .setPositiveButton("PORNEȘTE", (d, w) -> runImport(plan))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void runImport(DigitalImportPlan plan) {
        File file = cachedPdf;
        if (file == null || !file.exists()) {
            setStatus("PDF-ul temporar nu mai este disponibil. Selectează-l din nou.", Color.rgb(230, 92, 92));
            return;
        }

        cancelled.set(false);
        chooseButton.setEnabled(false);
        importButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progress.setProgress(0);
        setStatus("Import în curs…", Color.rgb(230, 190, 80));

        final String sourceId = IndexCoreSourceRegistry.activeSourceId(this);
        worker.execute(() -> {
            TextRecognizer recognizer = TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS
            );

            int cartographed = 0;
            int unreadable = 0;
            int failed = 0;
            int ambiguous = 0;
            int units = 0;
            String lastFailure = "";

            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
            ); PdfRenderer renderer = new PdfRenderer(descriptor)) {

                if (!plan.validForPdf(renderer.getPageCount())) {
                    throw new IllegalArgumentException("Intervalul nu mai corespunde PDF-ului.");
                }

                TopicMap map = TopicMapStore.load(getApplicationContext());
                IndexCoreDatabase core = IndexCoreDatabase.get(getApplicationContext());
                int total = plan.pageCount();
                // Persist the selected PDF byte-for-byte BEFORE processing pages.
                // A different PDF cannot replace the original of an existing source.
                SourceArchiveStore.preservePdf(
                        getApplicationContext(), sourceId, file, selectedName, renderer.getPageCount());

                for (int pdfPage = plan.pdfPageFrom; pdfPage <= plan.pdfPageTo; pdfPage++) {
                    if (cancelled.get()) break;

                    int printedPage = plan.printedPageForPdfPage(pdfPage);
                    final int ordinal = pdfPage - plan.pdfPageFrom + 1;
                    final int pdfPageForUi = pdfPage;
                    final int pageForUi = printedPage;
                    runOnUiThread(() -> {
                        progress.setProgress(
                                (int) Math.round((ordinal - 1) * 1000.0 / total)
                        );
                        setStatus(
                                "PDF " + pdfPageForUi + "/" + plan.pdfPageTo
                                        + " → pag. " + pageForUi + " • OCR local…",
                                Color.rgb(230, 190, 80)
                        );
                    });

                    PdfRenderer.Page page = null;
                    Bitmap bitmap = null;
                    try {
                        if (!sourceId.equals(IndexCoreSourceRegistry.activeSourceId(getApplicationContext()))) {
                            throw new IllegalStateException("Sursa activă s-a schimbat în timpul importului.");
                        }
                        SourceArchiveStore.linkPdfPage(
                                getApplicationContext(), sourceId, String.valueOf(printedPage), pdfPage - 1);
                        page = renderer.openPage(pdfPage - 1);
                        bitmap = render(page, false);
                        Text recognized = recognizeBlocking(recognizer, bitmap);

                        if (isEmpty(recognized)) {
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                            bitmap = render(page, true);
                            recognized = recognizeBlocking(recognizer, bitmap);
                        }

                        if (isEmpty(recognized)) {
                            unreadable++;
                            core.recordPageCapture(
                                    sourceId,
                                    System.currentTimeMillis(),
                                    String.valueOf(printedPage),
                                    false
                            );
                            continue;
                        }

                        // The OCR coordinates refer to the exact bitmap used in recognition.
                        // Save them independently of the unmodified source PDF.
                        SourceArchiveStore.saveOcr(getApplicationContext(), sourceId,
                                String.valueOf(printedPage), recognized,
                                bitmap.getWidth(), bitmap.getHeight());
                        ExactCartographyCapture.Report report =
                                ExactCartographyCapture.capture(
                                        getApplicationContext(),
                                        recognized,
                                        map,
                                        String.valueOf(printedPage),
                                        false
                                );
                        // OCR is not automatically equivalent to full visual verification.
                        if (report.map != null && report.map.paragraphCount() > 0) cartographed++;
                        else unreadable++;
                        if (report.map != null) {
                            ambiguous += report.map.ambiguousCount();
                            units += report.map.totalUnits();
                        }
                    } catch (Exception pageFailure) {
                        failed++;
                        lastFailure = explainPdfFailure(pageFailure);
                        core.recordPageCapture(
                                sourceId,
                                System.currentTimeMillis(),
                                String.valueOf(printedPage),
                                false
                        );
                    } finally {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        if (page != null) page.close();
                    }
                }

                if (!cancelled.get() && plan.defineScope) {
                    core.setSourceScope(
                            sourceId,
                            plan.printedPageFrom,
                            plan.printedPageTo()
                    );
                }
            } catch (Exception failure) {
                final String message = explainPdfFailure(failure);
                closeRecognizer(recognizer);
                runOnUiThread(() -> {
                    chooseButton.setEnabled(true);
                    importButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    setStatus(
                            "Import oprit: " + message,
                            Color.rgb(230, 92, 92)
                    );
                });
                return;
            }

            closeRecognizer(recognizer);

            final int cartographedCount = cartographed;
            final int unreadableCount = unreadable;
            final int failedCount = failed;
            final int ambiguousCount = ambiguous;
            final int unitCount = units;
            final boolean wasCancelled = cancelled.get();
            final String finalFailure = lastFailure;

            runOnUiThread(() -> {
                if (!wasCancelled) progress.setProgress(1000);
                chooseButton.setEnabled(true);
                importButton.setEnabled(true);
                cancelButton.setEnabled(false);

                StringBuilder message = new StringBuilder(
                        wasCancelled ? "IMPORT OPRIT" : "IMPORT TERMINAT"
                );
                message.append(" • ").append(cartographedCount).append(" pagini OCR cartografiate");
                message.append(" • de verificat în ORIGINAL înainte de încheiere");
                if (unreadableCount > 0) {
                    message.append(" • ").append(unreadableCount).append(" fără text OCR");
                }
                if (failedCount > 0) {
                    message.append(" • ").append(failedCount).append(" erori de pagină");
                }
                message.append(" • ").append(unitCount).append(" unități");
                if (ambiguousCount > 0) {
                    message.append(" • ").append(ambiguousCount).append(" ambigue");
                }
                if (failedCount > 0 && !finalFailure.isEmpty()) {
                    message.append("\nUltima eroare: ").append(finalFailure);
                }

                setStatus(
                        message.toString(),
                        unreadableCount > 0 || failedCount > 0 || ambiguousCount > 0
                                ? Color.rgb(230, 155, 72)
                                : Color.rgb(76, 190, 123)
                );
            });
        });
    }

    private Bitmap render(PdfRenderer.Page page, boolean retry) {
        PdfRenderSizing.Size size = PdfRenderSizing.compute(
                page.getWidth(),
                page.getHeight(),
                retry ? RETRY_TARGET_WIDTH : NORMAL_TARGET_WIDTH,
                retry ? RETRY_MAX_PIXELS : NORMAL_MAX_PIXELS,
                4.0f
        );
        Bitmap bitmap = Bitmap.createBitmap(
                size.width,
                size.height,
                Bitmap.Config.ARGB_8888
        );
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

        if (!latch.await(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("OCR timeout pe pagină.");
        }
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private boolean isEmpty(Text text) {
        return text == null
                || text.getTextBlocks() == null
                || text.getTextBlocks().isEmpty()
                || text.getText() == null
                || text.getText().trim().isEmpty();
    }

    private String displayName(Uri uri) {
        if (uri == null) return "";
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    return name == null ? "" : name.trim();
                }
            }
        } catch (RuntimeException ignored) {
            // Cosmetic only.
        }
        return "";
    }

    private String explainPdfFailure(Exception failure) {
        String raw = safeMessage(failure);
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("password") || lower.contains("security")) {
            return "PDF-ul pare protejat sau criptat. Deschide o copie fără parolă.";
        }
        if (lower.contains("seek")) {
            return "Furnizorul nu oferă acces direct; selectează din nou fișierul. "
                    + "Versiunea curentă încearcă automat o copie locală.";
        }
        if (lower.contains("space") || lower.contains("enospc")) {
            return "Nu există suficient spațiu temporar pe telefon pentru PDF.";
        }
        return raw;
    }

    private void closeRecognizer(TextRecognizer recognizer) {
        try {
            if (recognizer != null) recognizer.close();
        } catch (RuntimeException ignored) {
            // No research data depends on recognizer close.
        }
    }

    private void deleteCachedPdf() {
        File file = cachedPdf;
        cachedPdf = null;
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
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
        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(53, 92, 125))
        );
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
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeMessage(Exception failure) {
        if (failure == null
                || failure.getMessage() == null
                || failure.getMessage().trim().isEmpty()) {
            return failure == null
                    ? "eroare necunoscută"
                    : failure.getClass().getSimpleName();
        }
        return failure.getMessage().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
