package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.mlkit.vision.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-pinned, all-or-nothing OCR+index capture and manually verified closure.
 *
 * Original PDF/JPEG is immutable; accepted OCR lines/blocks are also immutable.
 * Indexing failures roll back derived data and cannot mark a page completed.
 * Existing v6 cover flags are not proof of v7 capture integrity.
 */
public final class VerifiedCaptureCoordinator {
    private VerifiedCaptureCoordinator() {}

    public static final class Result {
        public final boolean newlyIndexed;
        public final int paragraphs;
        public final int units;
        public final int ambiguous;
        Result(boolean newlyIndexed, int paragraphs, int units, int ambiguous) {
            this.newlyIndexed = newlyIndexed;
            this.paragraphs = paragraphs;
            this.units = units;
            this.ambiguous = ambiguous;
        }
    }

    /** Never capture a source other than the source selected at operation start. */
    private static void requireActiveSource(Context context, String sourceId) throws IOException {
        if (context == null || sourceId == null || sourceId.trim().isEmpty()
                || !sourceId.equals(IndexCoreSourceRegistry.activeSourceId(context))) {
            throw new IOException("Sursa activă s-a schimbat; nu atribui pasajele altei surse.");
        }
    }

    private static int count(SQLiteDatabase sql, String statement, String[] args) {
        try (Cursor c = sql.rawQuery(statement, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static String[] key(String sourceId, String page) {
        return new String[]{sourceId, page};
    }

    private static String indexedDigest(SQLiteDatabase sql, String sourceId, String page) {
        try (Cursor c = sql.rawQuery("SELECT indexed_digest FROM idx_page_processing "
                        + "WHERE source_id=? AND page=?", key(sourceId, page))) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    private static String currentDigest(SQLiteDatabase sql, String sourceId, String page) {
        try (Cursor c = sql.rawQuery("SELECT ocr_digest FROM idx_page_processing "
                        + "WHERE source_id=? AND page=?", key(sourceId, page))) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    private static void ensureDigest(Context context, SQLiteDatabase sql,
                                     String sourceId, String page) throws IOException {
        String digest = SourceArchiveStore.persistedOcrDigest(context, sourceId, page);
        String existing = currentDigest(sql, sourceId, page);
        if (!existing.isEmpty() && !existing.equals(digest)) {
            throw new IOException("Textul OCR arhivat s-a schimbat fără revizie; indexarea a fost oprită.");
        }
        if (!existing.isEmpty()) return;
        ContentValues value = new ContentValues();
        value.put("source_id", sourceId);
        value.put("page", page);
        value.put("ocr_digest", digest);
        sql.insertWithOnConflict("idx_page_processing", null, value, SQLiteDatabase.CONFLICT_ABORT);
    }

    private static void rejectUnprovenLegacyIndex(SQLiteDatabase sql,
                                                   String sourceId, String page) throws IOException {
        if (currentDigest(sql, sourceId, page).isEmpty()
                && count(sql, "SELECT COUNT(*) FROM idx_semantic_units WHERE "
                        + "source_id=? AND page=?", key(sourceId, page)) > 0) {
            throw new IOException("Există o cartografiere veche fără legătură verificabilă "
                    + "cu OCR-ul arhivat. Pentru a nu amesteca dovezi incompatibile, "
                    + "creează o sursă nouă pentru această captură.");
        }
    }

    private static boolean indexed(SQLiteDatabase sql, String sourceId, String page) {
        String digest = currentDigest(sql, sourceId, page);
        return !digest.isEmpty() && digest.equals(indexedDigest(sql, sourceId, page))
                && count(sql, "SELECT COUNT(*) FROM idx_semantic_units WHERE "
                        + "source_id=? AND page=? AND unit_kind='PARAGRAPH'",
                        key(sourceId, page)) > 0;
    }

    public static boolean isIndexed(Context context, String sourceId, String page) {
        return indexed(IndexCoreDatabase.get(context).getReadableDatabase(), sourceId, page);
    }

    private static void setIndexed(SQLiteDatabase sql, String sourceId,
                                   String page) throws IOException {
        String digest = currentDigest(sql, sourceId, page);
        if (digest.isEmpty()) throw new IOException("OCR nu are amprenta înregistrată.");
        ContentValues values = new ContentValues();
        values.put("indexed_digest", digest);
        values.put("indexed_at", System.currentTimeMillis());
        // New index must be reviewed anew, even if an obsolete flag existed.
        values.put("verified_digest", "");
        values.put("verified_at", 0);
        if (sql.update("idx_page_processing", values,
                "source_id=? AND page=?", key(sourceId,page)) != 1) {
            throw new IOException("Indexarea nu a putut fi înregistrată.");
        }
    }

    /** Run all steps on the SAME worker thread and SAME SQLite transaction. */
    public static synchronized Result capture(Context context, String sourceId, String page,
                                               Text recognized, int width, int height,
                                               TopicMap thematicMap) throws IOException {
        requireActiveSource(context, sourceId);
        if (page == null || page.trim().isEmpty()) throw new IOException("Pagina nu este identificată.");
        SourceArchiveStore.Page original = SourceArchiveStore.page(context, sourceId, page);
        // Verifies the actual stored original bytes before declaring a mapping reliable.
        SourceArchiveStore.originalFile(context, original);
        SQLiteDatabase sql = IndexCoreDatabase.get(context).getWritableDatabase();
        sql.beginTransaction();
        try {
            rejectUnprovenLegacyIndex(sql, sourceId, page);
            SourceArchiveStore.saveOcr(context, sourceId, page, recognized, width, height);
            ensureDigest(context, sql, sourceId, page);
            if (indexed(sql, sourceId, page)) {
                sql.setTransactionSuccessful();
                return new Result(false, 0, 0, 0);
            }
            ExactCartographyCapture.Report report = ExactCartographyCapture.capture(
                    context, recognized, thematicMap, page, false, sourceId);
            if (report == null || report.map == null || report.map.paragraphCount() == 0) {
                throw new IOException("OCR nu a produs un index verificabil.");
            }
            setIndexed(sql, sourceId, page);
            sql.setTransactionSuccessful();
            return new Result(true, report.map.paragraphCount(),
                    report.map.totalUnits(), report.map.ambiguousCount());
        } finally {
            sql.endTransaction();
        }
    }

    /**
     * Repair a crash that happened after saving OCR in v6. Reuse the stored blocks
     * (no new photo, no silent OCR replacement) and rebuild the missing index.
     */
    public static synchronized Result recover(Context context, String sourceId,
                                              String page, TopicMap thematicMap) throws IOException {
        requireActiveSource(context, sourceId);
        SourceArchiveStore.Page original = SourceArchiveStore.page(context, sourceId, page);
        SourceArchiveStore.originalFile(context, original);
        SQLiteDatabase sql = IndexCoreDatabase.get(context).getWritableDatabase();
        sql.beginTransaction();
        try {
            rejectUnprovenLegacyIndex(sql, sourceId, page);
            List<String> blocks = SourceArchiveStore.blocks(context, sourceId, page);
            if (blocks.isEmpty()) {
                // Old captures had only OCR lines. Preserve their exact text and
                // treat each line as a separate conservative paragraph.
                List<SourceArchiveStore.OcrLine> lines =
                        SourceArchiveStore.lines(context, sourceId, page);
                blocks = new ArrayList<>();
                for (SourceArchiveStore.OcrLine line : lines) {
                    if (!line.text.trim().isEmpty()) blocks.add(line.text);
                }
                if (blocks.isEmpty()) throw new IOException("Pagina nu are OCR arhivat.");
                for (int i = 0; i < blocks.size(); i++) {
                    ContentValues row = new ContentValues();
                    row.put("source_id", sourceId);
                    row.put("page", page);
                    row.put("block_index", i);
                    row.put("raw_text", blocks.get(i));
                    sql.insertWithOnConflict("idx_ocr_blocks", null, row,
                            SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            ensureDigest(context, sql, sourceId, page);
            if (indexed(sql, sourceId, page)) {
                sql.setTransactionSuccessful();
                return new Result(false, 0, 0, 0);
            }
            List<UniversalParagraphDetector.Detection> detections = new ArrayList<>();
            for (String block : blocks) {
                for (String paragraph : UniversalParagraphDetector.splitParagraphs(block)) {
                    detections.add(UniversalParagraphDetector.detect(paragraph, detections.size()));
                }
            }
            if (detections.isEmpty()) throw new IOException("OCR nu conține paragrafe prelucrabile.");
            HistoricalCartographyEngine.CaptureMap mapped = IndexCoreRuntime.captureExhaustive(
                    context, detections, SemanticGraphBuilder.build(detections),
                    thematicMap, page, false, sourceId);
            if (mapped.paragraphCount() == 0) {
                throw new IOException("Indexarea recuperată nu conține paragrafe.");
            }
            setIndexed(sql, sourceId, page);
            sql.setTransactionSuccessful();
            return new Result(true, mapped.paragraphCount(),
                    mapped.totalUnits(), mapped.ambiguousCount());
        } finally {
            sql.endTransaction();
        }
    }

    /** Never claim exhaustivity on OCR alone or on a stale/partial semantic index. */
    public static synchronized void verifyAndComplete(Context context,
                                                      String sourceId, String page) throws IOException {
        requireActiveSource(context, sourceId);
        SourceArchiveStore.Page original = SourceArchiveStore.page(context, sourceId, page);
        SourceArchiveStore.originalFile(context, original);
        SQLiteDatabase sql = IndexCoreDatabase.get(context).getWritableDatabase();
        sql.beginTransaction();
        try {
            String actual = SourceArchiveStore.persistedOcrDigest(context, sourceId, page);
            String frozen = currentDigest(sql, sourceId, page);
            if (!actual.equals(frozen) || !indexed(sql, sourceId, page)) {
                throw new IOException("Pagina nu este indexată din OCR-ul păstrat integral. "
                        + "Finalizează sau recuperează mai întâi indexarea.");
            }
            ContentValues proof = new ContentValues();
            proof.put("verified_digest", actual);
            proof.put("verified_at", System.currentTimeMillis());
            if (sql.update("idx_page_processing", proof,
                    "source_id=? AND page=?", key(sourceId,page)) != 1) {
                throw new IOException("Confirmarea nu poate fi salvată.");
            }
            IndexCoreDatabase.get(context).recordPageCapture(
                    sourceId, System.currentTimeMillis(), page, true);
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
        }
    }
}
