package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** End-to-end SQLite search across separately archived books. */
@RunWith(AndroidJUnit4.class)
public final class QuestionEvidenceSearchAndroidTest {
    @Test
    public void returnsEvidenceFromEveryArchivedSourceAndOpensCorrectLine() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        String suffix = String.valueOf(System.nanoTime());
        String first = "query-book-one-" + suffix;
        String second = "query-book-two-" + suffix;
        String third = "query-book-topic-only-" + suffix;
        archivePage(context, db, first, "Carte unu", "41",
                "Inflația crește deoarece cererea depășește oferta.");
        archivePage(context, db, second, "Carte doi", "76",
                "Inflația crește deoarece producția scade.");
        archivePage(context, db, third, "Carte trei", "19",
                "Inflația crește în ultimii ani.");

        QuestionEvidenceSearch.Report report = QuestionEvidenceSearch.search(
                context, "De ce crește inflația?");
        assertTrue(report.queryUsable);
        assertTrue(report.sourcesSearched >= 3);

        List<QuestionEvidenceSearch.Passage> evidence = new ArrayList<>();
        for (QuestionEvidenceSearch.Passage item : report.passages) {
            if (first.equals(item.sourceId) || second.equals(item.sourceId)) {
                if (item.directCandidate) evidence.add(item);
            }
            if (third.equals(item.sourceId)) {
                assertTrue("A topical mention cannot be a direct causal answer",
                        !item.directCandidate);
            }
        }

        boolean firstFound = false, secondFound = false;
        for (QuestionEvidenceSearch.Passage item : evidence) {
            assertTrue(item.excerpt.contains("deoarece"));
            if (first.equals(item.sourceId)) {
                firstFound = true;
                assertEquals("41", item.page);
                assertEquals(0, QuestionEvidenceSearch.closestLine(context, item));
            }
            if (second.equals(item.sourceId)) {
                secondFound = true;
                assertEquals("76", item.page);
                assertEquals(0, QuestionEvidenceSearch.closestLine(context, item));
            }
        }
        assertTrue("The first archived book must be searched", firstFound);
        assertTrue("The second archived book must be searched", secondFound);
    }

    @Test
    public void visualResultIsRenderedFromUnmodifiedOriginalNotTranscribedText()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        String id = "facsimile-preview-" + System.nanoTime();
        String page = "32";
        archivePage(context, db, id, "Sursă primară", page,
                "Inflația crește deoarece cererea depășește oferta.");

        QuestionEvidenceSearch.Report report = QuestionEvidenceSearch.search(
                context, "De ce crește inflația?");
        QuestionEvidenceSearch.Passage chosen = null;
        for (QuestionEvidenceSearch.Passage candidate : report.passages) {
            if (id.equals(candidate.sourceId) && candidate.directCandidate) {
                chosen = candidate;
                break;
            }
        }
        assertNotNull(chosen);

        SourceArchiveStore.Page originalPage = SourceArchiveStore.page(context, id, page);
        File original = SourceArchiveStore.originalFile(context, originalPage);
        byte[] untouchedBytes = Files.readAllBytes(original.toPath());
        Bitmap fromOriginal = OriginalPageActivity.decodeImage(original, 900_000L);
        Bitmap preview = OriginalFacsimilePreview.render(context, chosen);
        try {
            assertTrue(preview.getWidth() > 0 && preview.getHeight() > 0);
            // Test fixture has a 32x32 source, so the preview contains the
            // original full page; the top-left pixel is outside the overlay.
            assertEquals(fromOriginal.getPixel(0, 0), preview.getPixel(0, 0));
        } finally {
            preview.recycle();
            fromOriginal.recycle();
        }
        assertArrayEquals("Preview must never rewrite PDF/JPEG original",
                untouchedBytes, Files.readAllBytes(original.toPath()));
    }

    private static void archivePage(Context context, IndexCoreDatabase db,
                                    String sourceId, String title, String page,
                                    String sourceText) throws Exception {
        db.updateSource(sourceId, title, "", "", "", "");
        File jpeg = File.createTempFile("question-original-", ".jpg", context.getCacheDir());
        Bitmap image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.WHITE);
        try (FileOutputStream out = new FileOutputStream(jpeg)) {
            assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 94, out));
        } finally {
            image.recycle();
        }
        SourceArchiveStore.preserveScan(context, sourceId, page, jpeg);
        jpeg.delete();

        ContentValues paragraph = new ContentValues();
        paragraph.put("source_id", sourceId);
        paragraph.put("page", page);
        paragraph.put("block_index", 0);
        paragraph.put("raw_text", sourceText);
        db.getWritableDatabase().insertOrThrow("idx_ocr_blocks", null, paragraph);

        ContentValues line = new ContentValues();
        line.put("source_id", sourceId);
        line.put("page", page);
        line.put("line_index", 0);
        line.put("raw_text", sourceText);
        line.put("left_frac", 0.1);
        line.put("top_frac", 0.1);
        line.put("right_frac", 0.9);
        line.put("bottom_frac", 0.3);
        db.getWritableDatabase().insertOrThrow("idx_original_ocr_lines", null, line);
    }
}
