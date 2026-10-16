package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Original-page viewer. The OCR layer is painted over a read-only rendering,
 * NEVER composited into or saved back to the original PDF/JPEG.
 */
public final class OriginalPageActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_ORIGINAL = 971;
    public static final String EXTRA_SOURCE_ID = "source_id";
    public static final String EXTRA_PAGE = "printed_page";
    public static final String EXTRA_LINE_INDEX = "ocr_line_index";

    private static final long MAX_RENDER_PIXELS = 6_000_000L;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();

    private String sourceId = "";
    private String printedPage = "";
    private int selectedLine = -1;
    private List<SourceArchiveStore.Page> pages = new ArrayList<>();
    private Bitmap currentBitmap;
    private List<SourceArchiveStore.OcrLine> ocrLines = new ArrayList<>();
    private float zoom = 1f;
    private boolean stopped;
    private SourceArchiveStore.Page exportSelection;

    private FacsimileView facsimile;
    private EditText search;
    private TextView pageTitle, status, ocrText;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        sourceId = getIntent().getStringExtra(EXTRA_SOURCE_ID);
        if (sourceId == null || sourceId.trim().isEmpty()) {
            sourceId = IndexCoreSourceRegistry.activeSourceId(this);
        }
        printedPage = getIntent().getStringExtra(EXTRA_PAGE);
        if (printedPage == null) printedPage = "";
        selectedLine = getIntent().getIntExtra(EXTRA_LINE_INDEX, -1);
        pages = SourceArchiveStore.pages(this, sourceId);
        if (printedPage.isEmpty() && !pages.isEmpty()) printedPage = pages.get(0).number;
        buildUi();
        if (pages.isEmpty()) {
            status.setText("Nu există încă facsimile pentru sursa activă. Importă un PDF sau fotografiază o pagină.");
        } else {
            loadPage();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 19, 26));
        root.setPadding(dp(10), dp(10), dp(10), dp(24));
        scroll.addView(root);

        TextView headline = text("ORIGINALUL SURSEI • NEMODIFICAT", 17);
        headline.setTextColor(Color.rgb(169, 229, 194));
        root.addView(headline);

        pageTitle = text("Pagina —", 13);
        root.addView(pageTitle);

        LinearLayout nav = row();
        Button back = button("◀"); back.setOnClickListener(v -> movePage(-1));
        Button choose = button("PAGINI"); choose.setOnClickListener(v -> choosePage());
        Button next = button("▶"); next.setOnClickListener(v -> movePage(1));
        Button minus = button("−"); minus.setOnClickListener(v -> scaleZoom(1 / 1.5f));
        Button plus = button("+"); plus.setOnClickListener(v -> scaleZoom(1.5f));
        nav.addView(back, new LinearLayout.LayoutParams(0, dp(45), 1));
        nav.addView(choose, new LinearLayout.LayoutParams(0, dp(45), 2));
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(45), 1));
        nav.addView(minus, new LinearLayout.LayoutParams(0, dp(45), 1));
        nav.addView(plus, new LinearLayout.LayoutParams(0, dp(45), 1));
        root.addView(nav);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        facsimile = new FacsimileView();
        horizontal.addView(facsimile, new ViewGroup.LayoutParams(dp(280), dp(400)));
        root.addView(horizontal);

        status = text("Deschid originalul…", 12);
        status.setPadding(0, dp(8), 0, dp(10));
        root.addView(status);

        LinearLayout find = row();
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setHint("Cuvinte explicite în OCR");
        find.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button go = button("CAUTĂ");
        go.setOnClickListener(v -> searchLines());
        find.addView(go, new LinearLayout.LayoutParams(dp(91), dp(52)));
        root.addView(find);

        Button recover = button("RECUPEREAZĂ INDEXAREA DIN OCR ARHIVAT");
        recover.setOnClickListener(v -> recoverMissingIndex());
        root.addView(recover, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        Button confirm = button("AM VERIFICAT PAGINA • ÎNCHEIE ✓");
        confirm.setOnClickListener(v -> confirmVerifiedPage());
        root.addView(confirm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button export = button("EXPORTĂ ORIGINALUL NEMODIFICAT");
        export.setOnClickListener(v -> exportOriginal());
        root.addView(export, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView disclaimer = text(
                "Rezultatele sunt potriviri lexicale în transcrierea OCR, nu interpretări ale autorului. "
                        + "Originalul nu se modifică. Verifică vizual pasajul și textul înainte de a încheia pagina.", 11);
        disclaimer.setTextColor(Color.rgb(175, 193, 208));
        disclaimer.setPadding(0, dp(8), 0, dp(8));
        root.addView(disclaimer);

        ocrText = text("", 12);
        ocrText.setTextIsSelectable(true);
        root.addView(ocrText);

        Button finish = button("ÎNAPOI");
        finish.setOnClickListener(v -> finish());
        root.addView(finish, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        setContentView(scroll);
    }

    private void loadPage() {
        if (printedPage == null || printedPage.isEmpty()) return;
        final String requested = printedPage;
        final int requestedLine = selectedLine;
        int stamp = generation.incrementAndGet();
        status.setText("Verific amprenta și deschid originalul paginii " + requested + "…");
        pageTitle.setText("Pagina " + requested);
        // Prevent confirming the NEW page with OCR left from the previous page.
        ocrLines = Collections.emptyList();
        facsimile.setContent(null, ocrLines, -1);
        ocrText.setText("");
        worker.execute(() -> {
            Bitmap bitmap = null;
            try {
                SourceArchiveStore.Page page = SourceArchiveStore.page(this, sourceId, requested);
                File original = SourceArchiveStore.originalFile(this, page);
                bitmap = page.isPdf() ? renderPdf(original, page.pdfPageIndex) : decodeImage(original);
                if (bitmap == null) throw new IOException("Originalul nu a putut fi afișat.");
                List<SourceArchiveStore.OcrLine> lines = SourceArchiveStore.lines(this, sourceId, requested);
                final Bitmap loaded = bitmap;
                runOnUiThread(() -> {
                    if (stopped || stamp != generation.get()) {
                        loaded.recycle();
                        return;
                    }
                    Bitmap previous = currentBitmap;
                    currentBitmap = loaded;
                    ocrLines = lines;
                    facsimile.setContent(loaded, lines, requestedLine);
                    setZoom(zoom);
                    if (previous != null && previous != loaded) previous.recycle();
                    StringBuilder text = new StringBuilder("OCR (strat separat, neevaluat semantic):\n\n");
                    for (SourceArchiveStore.OcrLine line : lines) {
                        text.append(line.text).append('\n');
                    }
                    ocrText.setText(text.toString());
                    status.setText("Original verificat SHA-256 • "
                            + (page.isPdf() ? "PDF, fila " + (page.pdfPageIndex + 1) : "fotografie originală")
                            + " • " + lines.size() + " linii OCR");
                });
            } catch (Exception failure) {
                if (bitmap != null) bitmap.recycle();
                final String message = failure.getMessage();
                runOnUiThread(() -> {
                    if (!stopped && stamp == generation.get()) {
                        status.setText("Nu pot deschide pagina: " + message);
                        facsimile.setContent(null, new ArrayList<>(), -1);
                    }
                });
            }
        });
    }

    private Bitmap renderPdf(File original, int index) throws IOException {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                original, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            if (index < 0 || index >= renderer.getPageCount()) throw new IOException("Index PDF invalid.");
            try (PdfRenderer.Page page = renderer.openPage(index)) {
                PdfRenderSizing.Size size = PdfRenderSizing.compute(
                        page.getWidth(), page.getHeight(), 2100, MAX_RENDER_PIXELS, 4f);
                Bitmap bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bitmap;
                } catch (RuntimeException error) {
                    bitmap.recycle();
                    throw error;
                }
            }
        }
    }

    /**
     * Decode and orient a DISPLAY COPY only. The JPEG file and EXIF bytes stay
     * unchanged; OCR uses the same upright display orientation.
     */
    public static Bitmap decodeImage(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1) throw new IOException("JPEG invalid.");
        int sample = 1;
        while ((long) (bounds.outWidth / sample) * (bounds.outHeight / sample) > MAX_RENDER_PIXELS) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null) throw new IOException("Nu pot decoda fotografia.");
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postScale(-1, 1); matrix.postRotate(270); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postScale(-1, 1); matrix.postRotate(90); break;
            default: return decoded;
        }
        Bitmap oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(),
                decoded.getHeight(), matrix, true);
        if (oriented != decoded) decoded.recycle();
        return oriented;
    }

    private void searchLines() {
        String terms = search.getText().toString().trim();
        if (terms.isEmpty()) {
            status.setText("Introdu unul sau mai multe cuvinte exacte.");
            return;
        }
        status.setText("Caut potrivirile lexicale, fără interpretare…");
        worker.execute(() -> {
            SourceArchiveStore.SearchResult results =
                    SourceArchiveStore.search(this, sourceId, terms, 1000);
            runOnUiThread(() -> {
                if (stopped) return;
                if (results.matches.isEmpty()) {
                    status.setText("Nu am identificat potriviri în OCR-ul arhivat. Aceasta NU dovedește absența în sursă.");
                    return;
                }
                List<SourceArchiveStore.OcrLine> matches = results.matches;
                String[] labels = new String[matches.size()];
                for (int i = 0; i < matches.size(); i++) {
                    SourceArchiveStore.OcrLine hit = matches.get(i);
                    String excerpt = hit.text.replace('\n', ' ');
                    if (excerpt.length() > 135) excerpt = excerpt.substring(0, 135) + "…";
                    labels[i] = "p. " + hit.page + " • " + excerpt;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Pasaje OCR: " + matches.size()
                                + (results.truncated ? " (primele 1000)" : ""))
                        .setItems(labels, (dialog, chosen) -> {
                            SourceArchiveStore.OcrLine selected = matches.get(chosen);
                            printedPage = selected.page;
                            selectedLine = selected.index;
                            loadPage();
                        })
                        .setNegativeButton("ÎNCHIDE", null).show();
            });
        });
    }

    private void exportOriginal() {
        SourceArchiveStore.Page chosen = SourceArchiveStore.page(this, sourceId, printedPage);
        if (chosen == null) {
            status.setText("Nu există un original asociat paginii.");
            return;
        }
        exportSelection = chosen;
        Intent destination = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        destination.addCategory(Intent.CATEGORY_OPENABLE);
        destination.setType(chosen.isPdf() ? "application/pdf" : "image/jpeg");
        String name = chosen.isPdf() ? "sursa_originala.pdf"
                : "scanare_originala_pagina_" + chosen.number + ".jpg";
        destination.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(destination, REQ_EXPORT_ORIGINAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT_ORIGINAL || resultCode != RESULT_OK
                || data == null || data.getData() == null || exportSelection == null) return;
        final Uri destination = data.getData();
        final SourceArchiveStore.Page selected = exportSelection;
        exportSelection = null;
        status.setText("Exportez copia octet-cu-octet a originalului…");
        worker.execute(() -> {
            String message;
            try {
                File original = SourceArchiveStore.originalFile(this, selected);
                try (FileInputStream input = new FileInputStream(original);
                     OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("Destinația nu poate fi scrisă.");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                }
                message = "Original exportat fără modificarea conținutului.";
            } catch (Exception failure) {
                message = "Exportul originalului a eșuat: " + failure.getMessage();
            }
            final String outcome = message;
            runOnUiThread(() -> {
                if (!stopped) status.setText(outcome);
            });
        });
    }

    private void recoverMissingIndex() {
        if (printedPage == null || printedPage.isEmpty()) return;
        final String target = printedPage;
        status.setText("Verific originalul și reiau indexarea fără a schimba OCR-ul…");
        worker.execute(() -> {
            String outcome;
            try {
                VerifiedCaptureCoordinator.Result result = VerifiedCaptureCoordinator.recover(
                        this, sourceId, target, TopicMapStore.load(this));
                outcome = result.newlyIndexed
                        ? "Index recuperat: " + result.paragraphs + " paragrafe. Verifică pagina."
                        : "Indexul era deja salvat și corespunde OCR-ului arhivat.";
            } catch (Exception failure) {
                outcome = "Indexare nereușită: " + failure.getMessage();
            }
            String reply = outcome;
            runOnUiThread(() -> {
                if (!stopped) status.setText(reply);
            });
        });
    }

    private void confirmVerifiedPage() {
        if (printedPage == null || printedPage.isEmpty()) return;
        if (ocrLines.isEmpty()) {
            status.setText("Nu se poate încheia: nu există transcriere OCR de verificat.");
            return;
        }
        final String pageToConfirm = printedPage;
        new AlertDialog.Builder(this)
                .setTitle("Confirmare explicită")
                .setMessage("Ai comparat textul OCR cu pagina originală și ai verificat că nu lipsesc zone? "
                        + "Această confirmare este a cercetătorului, nu o presupunere automată.")
                .setPositiveButton("AM VERIFICAT • ÎNCHEIE", (d, w) -> worker.execute(() -> {
                    String outcome;
                    try {
                        VerifiedCaptureCoordinator.verifyAndComplete(
                                this, sourceId, pageToConfirm);
                        outcome = "Pagina " + pageToConfirm
                                + " este verificată: original + OCR + index semantic.";
                    } catch (Exception failure) {
                        outcome = "Pagina nu poate fi încheiată: " + failure.getMessage();
                    }
                    final String result = outcome;
                    runOnUiThread(() -> {
                        if (!stopped) status.setText(result);
                    });
                }))
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void movePage(int direction) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).number.equals(printedPage)) {
                int next = i + direction;
                if (next >= 0 && next < pages.size()) {
                    printedPage = pages.get(next).number;
                    selectedLine = -1;
                    loadPage();
                }
                return;
            }
        }
    }

    private void choosePage() {
        if (pages.isEmpty()) return;
        String[] numbers = new String[pages.size()];
        for (int i = 0; i < pages.size(); i++) numbers[i] = pages.get(i).number;
        new AlertDialog.Builder(this).setTitle("Pagini cu original arhivat")
                .setItems(numbers, (d, choice) -> {
                    printedPage = numbers[choice];
                    selectedLine = -1;
                    loadPage();
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void scaleZoom(float factor) {
        setZoom(Math.max(0.75f, Math.min(3.5f, zoom * factor)));
    }

    private void setZoom(float next) {
        zoom = next;
        if (currentBitmap == null) return;
        int base = getResources().getDisplayMetrics().widthPixels - dp(20);
        int width = Math.max(100, Math.round(base * zoom));
        int height = Math.max(1, Math.round(
                width * currentBitmap.getHeight() / (float) currentBitmap.getWidth()));
        facsimile.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        facsimile.requestLayout();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String string, int size) {
        TextView view = new TextView(this);
        view.setText(string);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(dp(4), dp(6), dp(4), dp(6));
        return view;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11);
        return b;
    }

    private int dp(int px) {
        return Math.round(px * getResources().getDisplayMetrics().density);
    }

    private final class FacsimileView extends View {
        private Bitmap bitmap;
        private List<SourceArchiveStore.OcrLine> lines = Collections.emptyList();
        private int activeIndex = -1;
        private final Paint picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint highlighter = new Paint(Paint.ANTI_ALIAS_FLAG);

        FacsimileView() { super(OriginalPageActivity.this); }

        void setContent(Bitmap value, List<SourceArchiveStore.OcrLine> positions, int line) {
            bitmap = value;
            lines = positions;
            activeIndex = line;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            canvas.drawBitmap(bitmap, null,
                    new Rect(0, 0, getWidth(), getHeight()), picture);
            if (activeIndex < 0) return;
            highlighter.setColor(Color.argb(100, 255, 205, 35));
            for (SourceArchiveStore.OcrLine ocr : lines) {
                if (ocr.index != activeIndex) continue;
                canvas.drawRect(new RectF(ocr.left * getWidth(),
                        ocr.top * getHeight(), ocr.right * getWidth(),
                        ocr.bottom * getHeight()), highlighter);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        generation.incrementAndGet();
        worker.shutdownNow();
        facsimile.setContent(null, new ArrayList<>(), -1);
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        super.onDestroy();
    }
}
