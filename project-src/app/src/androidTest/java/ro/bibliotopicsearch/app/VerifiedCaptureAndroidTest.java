package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
