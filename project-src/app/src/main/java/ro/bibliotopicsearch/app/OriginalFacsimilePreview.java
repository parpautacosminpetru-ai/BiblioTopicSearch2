package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only facsimile thumbnail. Renders pixels from the archived original
 * PDF/JPEG, never from an OCR transcription. Coordinates are an optional,
 * in-memory, non-destructive navigation overlay.
 */
public final class OriginalFacsimilePreview {
    private OriginalFacsimilePreview() {}

    private static final long PREVIEW_PIXELS = 900_000L;

    public static Bitmap render(Context context, QuestionEvidenceSearch.Passage found)
            throws IOException {
        SourceArchiveStore.Page page = SourceArchiveStore.page(context, found.sourceId, found.page);
        File original = SourceArchiveStore.originalFile(context, page);
        Bitmap raster = page.isPdf()
                ? renderPdf(original, page.pdfPageIndex)
                : OriginalPageActivity.decodeImage(original, PREVIEW_PIXELS);
        try {
            List<SourceArchiveStore.OcrLine> target = new ArrayList<>();
            int[] matched = QuestionEvidenceSearch.matchingLineIndices(context, found);
            if (matched.length > 0) {
                List<SourceArchiveStore.OcrLine> lines =
                        SourceArchiveStore.lines(context, found.sourceId, found.page);
                for (SourceArchiveStore.OcrLine line : lines) {
                    for (int lineIndex : matched) {
                        if (line.index == lineIndex) { target.add(line); break; }
                    }
                }
            }
            return cropOriginalWithOverlay(raster, target);
        } finally {
            raster.recycle();
        }
    }

    private static Bitmap renderPdf(File file, int pageIndex) throws IOException {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdf = new PdfRenderer(descriptor)) {
            if (pageIndex < 0 || pageIndex >= pdf.getPageCount()) {
                throw new IOException("Fila PDF nu mai este disponibilă.");
            }
            try (PdfRenderer.Page page = pdf.openPage(pageIndex)) {
                PdfRenderSizing.Size size = PdfRenderSizing.compute(
                        page.getWidth(), page.getHeight(), 700, PREVIEW_PIXELS, 4f);
                Bitmap bitmap = Bitmap.createBitmap(
                        size.width, size.height, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bitmap;
                } catch (RuntimeException problem) {
                    bitmap.recycle();
                    throw problem;
                }
            }
        }
    }

    private static Bitmap cropOriginalWithOverlay(
            Bitmap raster, List<SourceArchiveStore.OcrLine> matchingLines) {
        int width = raster.getWidth();
        int height = raster.getHeight();
        float minTop = 1f, maxBottom = 0f;
        for (SourceArchiveStore.OcrLine line : matchingLines) {
            minTop = Math.min(minTop, line.top);
            maxBottom = Math.max(maxBottom, line.bottom);
        }
        // Cover the WHOLE detected multi-line passage, without editing the PDF.
        int matchedHeight = matchingLines.isEmpty() ? 0
                : Math.max(0, Math.round((maxBottom - minTop) * height));
        int band = Math.min(height, Math.max(
                Math.round(width * 0.60f), matchedHeight + Math.round(height * 0.04f)));
        band = Math.max(1, band);
        float center = matchingLines.isEmpty() ? 0.5f : (minTop + maxBottom) / 2f;
        int top = Math.max(0, Math.min(height - band, Math.round(center * height - band / 2f)));

        Bitmap preview = Bitmap.createBitmap(width, band, Bitmap.Config.ARGB_8888);
        Canvas surface = new Canvas(preview);
        Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        surface.drawBitmap(raster, new Rect(0, top, width, top + band),
                new Rect(0, 0, width, band), image);

        Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (SourceArchiveStore.OcrLine line : matchingLines) {
            RectF box = new RectF(line.left * width, line.top * height - top,
                    line.right * width, line.bottom * height - top);
            if (box.width() <= 0 || box.height() <= 0) continue;
            marker.setStyle(Paint.Style.FILL);
            marker.setColor(Color.argb(78, 255, 199, 32));
            surface.drawRect(box, marker);
            marker.setColor(Color.rgb(207, 137, 0));
            marker.setStyle(Paint.Style.STROKE);
            marker.setStrokeWidth(Math.max(2f, width / 400f));
            surface.drawRect(box, marker);
        }
        return preview;
    }
}
