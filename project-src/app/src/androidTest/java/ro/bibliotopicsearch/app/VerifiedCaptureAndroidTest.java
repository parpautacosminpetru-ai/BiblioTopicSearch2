package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Uses real Android SQLite, app-private files and the real source/index APIs. */
@RunWith(AndroidJUnit4.class)
public final class VerifiedCaptureAndroidTest {
    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private String newSource() {
        return IndexCoreSourceRegistry.newSource(
                app(), "Testare integritate", "", "", "", "");
    }

    private SourceArchiveStore.Page archived(String source, String page) throws Exception {
        File snapshot = File.createTempFile("test-original-", ".jpg", app().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(snapshot)) {
            out.write(("ORIGINAL-TEST:" + source + ":" + page).getBytes(StandardCharsets.UTF_8));
        }
        try {
            return SourceArchiveStore.preserveScan(app(), source, page, snapshot);
        } finally {
            snapshot.delete();
        }
    }

    private void storeOcr(String source, String page, String text) {
        SQLiteDatabase sql = IndexCoreDatabase.get(app()).getWritableDatabase();
        ContentValues line = new ContentValues();
        line.put("source_id", source);
        line.put("page", page);
        line.put("line_index", 0);
        line.put("raw_text", text);
        line.put("left_frac", 0.1f);
        line.put("top_frac", 0.1f);
        line.put("right_frac", 0.8f);
        line.put("bottom_frac", 0.2f);
        sql.insertOrThrow("idx_original_ocr_lines", null, line);
        ContentValues block = new ContentValues();
        block.put("source_id", source);
        block.put("page", page);
        block.put("block_index", 0);
        block.put("raw_text", text);
        sql.insertOrThrow("idx_ocr_blocks", null, block);
    }

    @Test
    public void oldCompleteFlagAndOcrAloneAreNotExhaustive() throws Exception {
        String source = newSource();
        archived(source, "1");
        IndexCoreDatabase db = IndexCoreDatabase.get(app());
        db.setSourceScope(source, 1, 1);
        db.recordPageCapture(source, 100L, "1", true);

        assertFalse(IndexCoreCartographyReader.stats(db, source).exhaustive());
        try {
            VerifiedCaptureCoordinator.verifyAndComplete(app(), source, "1");
            fail("Must never close a page without OCR + a matching index");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }

        storeOcr(source, "1", "Împăratul controlează armata.");
        assertFalse(IndexCoreCartographyReader.stats(db, source).exhaustive());
    }

    @Test
    public void recoversIndexedEvidenceAndRequiresManualVerification() throws Exception {
        String source = newSource();
        SourceArchiveStore.Page original = archived(source, "2");
        File originalFile = SourceArchiveStore.originalFile(app(), original);
        assertTrue(originalFile.isFile());
        storeOcr(source, "2", "Împăratul controlează armata.");
        IndexCoreDatabase db = IndexCoreDatabase.get(app());
        db.setSourceScope(source, 2, 2);

        TopicNode node = new TopicNode("Imperiu", "Imperiu", 1);
        node.enabled = true;
        node.terms.add("împărat");
        TopicMap map = new TopicMap("Test", "", Collections.singletonList(node));
        VerifiedCaptureCoordinator.Result rebuilt = VerifiedCaptureCoordinator.recover(
                app(), source, "2", map);
        assertTrue(rebuilt.newlyIndexed);
        assertEquals(1, rebuilt.paragraphs);
        assertTrue(VerifiedCaptureCoordinator.isIndexed(app(), source, "2"));
        assertFalse(IndexCoreCartographyReader.stats(db, source).exhaustive());

        VerifiedCaptureCoordinator.Result duplicate = VerifiedCaptureCoordinator.recover(
                app(), source, "2", map);
        assertFalse(duplicate.newlyIndexed);
        VerifiedCaptureCoordinator.verifyAndComplete(app(), source, "2");
        IndexCoreCartographyReader.Stats stats =
                IndexCoreCartographyReader.stats(db, source);
        assertEquals(1, stats.pagesComplete);
        assertTrue(stats.exhaustive());
    }

    @Test
    public void sourceSwitchCannotCertifyWrongBook() throws Exception {
        String first = newSource();
        archived(first, "1");
        newSource(); // switches active source
        try {
            VerifiedCaptureCoordinator.verifyAndComplete(app(), first, "1");
            fail("Cross-source verification must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Sursa"));
        }
    }

    @Test
    public void archivedOriginalCannotBeSilentlyReplacedOrCorrupted() throws Exception {
        String source = newSource();
        SourceArchiveStore.Page page = archived(source, "1");
        File first = SourceArchiveStore.originalFile(app(), page);
        File second = File.createTempFile("changed-", ".jpg", app().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(second)) {
            out.write("different".getBytes(StandardCharsets.UTF_8));
        }
        try {
            SourceArchiveStore.preserveScan(app(), source, "1", second);
            fail("Different source bytes must never overwrite an original");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        } finally {
            second.delete();
        }
        assertEquals(first.getAbsolutePath(),
                SourceArchiveStore.originalFile(app(), page).getAbsolutePath());
    }
    @Test
    public void actualPdfRendererCanOpenByteIdenticalArchivedDocument() throws Exception {
        String source = newSource();
        File pdfFile = File.createTempFile("source-real-", ".pdf", app().getCacheDir());
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(420, 600, 1).create();
        PdfDocument.Page printed = pdf.startPage(info);
        Paint ink = new Paint();
        ink.setTextSize(24f);
        printed.getCanvas().drawText("Anul 1054 in sursa originala", 18f, 80f, ink);
        pdf.finishPage(printed);
        try (FileOutputStream out = new FileOutputStream(pdfFile)) {
            pdf.writeTo(out);
        } finally {
            pdf.close();
        }
        byte[] inputBytes = Files.readAllBytes(pdfFile.toPath());
        SourceArchiveStore.Document document = SourceArchiveStore.preservePdf(
                app(), source, pdfFile, "Test PDF", 1);
        SourceArchiveStore.Page linked = SourceArchiveStore.linkPdfPage(
                app(), source, "19", 0);
        File stored = SourceArchiveStore.originalFile(app(), linked);
        assertArrayEquals(inputBytes, Files.readAllBytes(stored.toPath()));
        assertEquals(document.sha256, linked.sha256);
        SourceArchiveStore.linkPdfPage(app(), source, "19", 0);

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                stored, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            assertEquals(1, renderer.getPageCount());
        }
        // Reopening the same original cannot alter the archived bytes.
        SourceArchiveStore.preservePdf(app(), source, pdfFile, "Repeated import", 1);
        assertArrayEquals(inputBytes, Files.readAllBytes(stored.toPath()));
        pdfFile.delete();
    }

}
