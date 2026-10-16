package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable original files + a separate, replaceable OCR overlay.
 *
 * Nothing in this store rewrites a PDF or captured JPEG. A changed document must
 * be imported as a NEW source; a page's facsimile may not be silently replaced.
 * The OCR lines are derived evidence, never a substitute for the original.
 */
public final class SourceArchiveStore {
    private SourceArchiveStore() {}

    private static final String ROOT = "source_originals";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    public static final class Document {
        public final String sourceId, sha256, relativePath, displayName;
        public final int pageCount;
        Document(String s, String h, String p, String n, int c) {
            sourceId=s; sha256=h; relativePath=p; displayName=n; pageCount=c;
        }
    }

    public static final class Page {
        public final String sourceId, number, mediaKind, sha256, relativePath;
        public final int pdfPageIndex;
        Page(String s, String p, String m, String h, String r, int i) {
            sourceId=s; number=p; mediaKind=m; sha256=h; relativePath=r; pdfPageIndex=i;
        }
        public boolean isPdf() { return "PDF".equals(mediaKind); }
    }

    public static final class OcrLine {
        public final String page, text;
        public final int index;
        public final float left, top, right, bottom;
        OcrLine(String p, int i, String t, float l, float y, float r, float b) {
            page=p; index=i; text=t; left=l; top=y; right=r; bottom=b;
        }
    }

    public static final class SearchResult {
        public final List<OcrLine> matches;
        public final boolean truncated;
        SearchResult(List<OcrLine> matches, boolean truncated) {
            this.matches = Collections.unmodifiableList(matches);
            this.truncated = truncated;
        }
    }

    public static synchronized Document preservePdf(Context context, String sourceId,
                                                      File selectedPdf, String displayName,
                                                      int pdfPageCount) throws IOException {
        if (context == null || sourceId == null || sourceId.trim().isEmpty()
                || selectedPdf == null || !selectedPdf.isFile() || pdfPageCount < 1) {
            throw new IOException("Sursă PDF invalidă.");
        }
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        db.ensureSource(sourceId);
        String hash = digestFile(selectedPdf);
        Document existing = document(db, sourceId);
        if (existing != null && !hash.equals(existing.sha256)) {
            throw new IOException("Sursa are deja un PDF original diferit. Creează o sursă nouă; originalul nu se suprascrie.");
        }
        String rel = sourceKey(sourceId) + "/" + hash + ".pdf";
        File original = resolve(context, rel);
        copyNeverOverwrite(selectedPdf, original, hash);
        if (existing == null) {
            ContentValues v = new ContentValues();
            v.put("source_id", sourceId);
            v.put("file_hash", hash);
            v.put("relative_path", rel);
            v.put("display_name", displayName == null ? "" : displayName);
            v.put("pdf_page_count", pdfPageCount);
            v.put("created_at", System.currentTimeMillis());
            db.getWritableDatabase().insertWithOnConflict(
                    "idx_original_documents", null, v, SQLiteDatabase.CONFLICT_ABORT);
        } else if (!existing.relativePath.equals(rel)) {
            throw new IOException("Calea originalului nu corespunde amprentei înregistrate.");
        }
        return document(db, sourceId);
    }

    public static synchronized Page preserveScan(Context context, String sourceId,
                                                   String page, File originalJpeg) throws IOException {
        if (context == null || sourceId == null || sourceId.trim().isEmpty()
                || page == null || page.trim().isEmpty() || originalJpeg == null
                || !originalJpeg.isFile()) throw new IOException("Scanare sau pagină invalidă.");
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        db.ensureSource(sourceId);
        String hash = digestFile(originalJpeg);
        Page existing = page(db, sourceId, page);
        if (existing != null) {
            if (existing.mediaKind.equals("IMAGE") && existing.sha256.equals(hash)) return existing;
            throw new IOException("Pagina " + page + " are deja un original. Alege o sursă nouă pentru o scanare diferită.");
        }
        String rel = sourceKey(sourceId) + "/" + hash + ".jpg";
        copyNeverOverwrite(originalJpeg, resolve(context, rel), hash);
        insertPage(db, sourceId, page, "IMAGE", hash, rel, -1);
        return page(db, sourceId, page);
    }

    /** One logical printed page points to one unaltered page inside its original PDF. */
    public static synchronized Page linkPdfPage(Context context, String sourceId,
                                                 String printedPage, int zeroBasedPdfIndex)
            throws IOException {
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        Document doc = document(db, sourceId);
        if (doc == null || zeroBasedPdfIndex < 0 || zeroBasedPdfIndex >= doc.pageCount
                || printedPage == null || printedPage.trim().isEmpty()) {
            throw new IOException("Pagina PDF nu are un original valid.");
        }
        Page existing = page(db, sourceId, printedPage);
        if (existing != null) {
            if (existing.isPdf() && existing.sha256.equals(doc.sha256)
                    && existing.pdfPageIndex == zeroBasedPdfIndex) return existing;
            throw new IOException("Pagina " + printedPage + " indică deja alt facsimil. Nu îl suprascriu.");
        }
        insertPage(db, sourceId, printedPage, "PDF", doc.sha256,
                doc.relativePath, zeroBasedPdfIndex);
        return page(db, sourceId, printedPage);
    }

    private static void insertPage(IndexCoreDatabase db, String sourceId, String page,
                                   String kind, String hash, String rel, int index) {
        ContentValues values = new ContentValues();
        values.put("source_id", sourceId);
        values.put("page", page.trim());
        values.put("media_kind", kind);
        values.put("file_hash", hash);
        values.put("relative_path", rel);
        values.put("pdf_page_index", index);
        values.put("saved_at", System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict(
                "idx_original_pages", null, values, SQLiteDatabase.CONFLICT_ABORT);
    }

    /**
     * OCR is a spatial layer separate from the immutable original bytes.
     * Recognizer coordinates must refer to exactly the raster used for OCR.
     * A transaction prevents half-written overlays if the scan is interrupted.
     */
    public static void saveOcr(Context context, String sourceId, String printedPage,
                               Text recognized, int imageWidth, int imageHeight) throws IOException {
        if (recognized == null || imageWidth < 1 || imageHeight < 1
                || page(context, sourceId, printedPage) == null) {
            throw new IOException("OCR fără pagină originală sau coordonate valide.");
        }
        List<OcrLine> lines = new ArrayList<>();
        for (Text.TextBlock block : recognized.getTextBlocks()) {
            if (block == null) continue;
            List<Text.Line> blockLines = block.getLines();
            if (blockLines == null || blockLines.isEmpty()) {
                addLine(lines, printedPage, block.getText(), block.getBoundingBox(), imageWidth, imageHeight);
            } else {
                for (Text.Line line : blockLines) {
                    if (line != null) addLine(lines, printedPage, line.getText(),
                            line.getBoundingBox(), imageWidth, imageHeight);
                }
            }
        }
        if (lines.isEmpty()) throw new IOException("Nu există text OCR de păstrat; pagina rămâne de verificat.");
        SQLiteDatabase sql = IndexCoreDatabase.get(context).getWritableDatabase();
        sql.beginTransaction();
        try {
            sql.delete("idx_original_ocr_lines", "source_id=? AND page=?",
                    new String[]{sourceId, printedPage});
            for (OcrLine line : lines) {
                ContentValues values = new ContentValues();
                values.put("source_id", sourceId);
                values.put("page", printedPage);
                values.put("line_index", line.index);
                values.put("raw_text", line.text);
                values.put("left_frac", line.left);
                values.put("top_frac", line.top);
                values.put("right_frac", line.right);
                values.put("bottom_frac", line.bottom);
                sql.insertWithOnConflict("idx_original_ocr_lines", null, values,
                        SQLiteDatabase.CONFLICT_ABORT);
            }
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
        }
    }

    private static void addLine(List<OcrLine> lines, String page, String text, Rect rect,
                                int w, int h) {
        if (text == null || text.trim().isEmpty() || rect == null) return;
        lines.add(new OcrLine(page, lines.size(), text,
                frac(rect.left, w), frac(rect.top, h),
                frac(rect.right, w), frac(rect.bottom, h)));
    }

    private static float frac(int px, int extent) {
        return Math.max(0f, Math.min(1f, px / (float) extent));
    }

    public static Document document(Context context, String sourceId) {
        return document(IndexCoreDatabase.get(context), sourceId);
    }

    private static Document document(IndexCoreDatabase db, String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return null;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT file_hash,relative_path,display_name,pdf_page_count "
                        + "FROM idx_original_documents WHERE source_id=?",
                new String[]{sourceId})) {
            return c.moveToFirst() ? new Document(sourceId, c.getString(0),
                    c.getString(1), c.getString(2), c.getInt(3)) : null;
        }
    }

    public static Page page(Context context, String sourceId, String page) {
        return page(IndexCoreDatabase.get(context), sourceId, page);
    }

    private static Page page(IndexCoreDatabase db, String sourceId, String page) {
        if (sourceId == null || page == null) return null;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT media_kind,file_hash,relative_path,pdf_page_index "
                        + "FROM idx_original_pages WHERE source_id=? AND page=?",
                new String[]{sourceId, page.trim()})) {
            return c.moveToFirst() ? new Page(sourceId, page.trim(), c.getString(0),
                    c.getString(1), c.getString(2), c.getInt(3)) : null;
        }
    }

    public static List<Page> pages(Context context, String sourceId) {
        List<Page> out = new ArrayList<>();
        try (Cursor c = IndexCoreDatabase.get(context).getReadableDatabase().rawQuery(
                "SELECT page,media_kind,file_hash,relative_path,pdf_page_index "
                        + "FROM idx_original_pages WHERE source_id=? "
                        + "ORDER BY CAST(page AS INTEGER),page",
                new String[]{sourceId})) {
            while (c.moveToNext()) out.add(new Page(sourceId, c.getString(0), c.getString(1),
                    c.getString(2), c.getString(3), c.getInt(4)));
        }
        return out;
    }

    public static List<OcrLine> lines(Context context, String sourceId, String page) {
        List<OcrLine> out = new ArrayList<>();
        try (Cursor c = IndexCoreDatabase.get(context).getReadableDatabase().rawQuery(
                "SELECT line_index,raw_text,left_frac,top_frac,right_frac,bottom_frac "
                        + "FROM idx_original_ocr_lines WHERE source_id=? AND page=? ORDER BY line_index",
                new String[]{sourceId, page})) {
            while (c.moveToNext()) out.add(new OcrLine(page, c.getInt(0), c.getString(1),
                    c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5)));
        }
        return out;
    }

    /** Lexical retrieval, not a generative answer or an assertion of completeness. */
    public static SearchResult search(Context context, String sourceId, String terms, int limit) {
        int max = Math.max(1, Math.min(5000, limit));
        List<OcrLine> out = new ArrayList<>();
        boolean truncated = false;
        try (Cursor c = IndexCoreDatabase.get(context).getReadableDatabase().rawQuery(
                "SELECT page,line_index,raw_text,left_frac,top_frac,right_frac,bottom_frac "
                        + "FROM idx_original_ocr_lines WHERE source_id=? "
                        + "ORDER BY CAST(page AS INTEGER),page,line_index",
                new String[]{sourceId})) {
            while (c.moveToNext()) {
                String raw = c.getString(2);
                if (!matchesAllWords(raw, terms)) continue;
                if (out.size() == max) { truncated = true; break; }
                out.add(new OcrLine(c.getString(0), c.getInt(1), raw, c.getFloat(3),
                        c.getFloat(4), c.getFloat(5), c.getFloat(6)));
            }
        }
        return new SearchResult(out, truncated);
    }

    static boolean matchesAllWords(String text, String query) {
        if (text == null || query == null || query.trim().isEmpty()) return false;
        String hay = paddedTokens(text);
        Matcher matcher = WORD.matcher(RomanianLanguagePack.fold(query));
        boolean any = false;
        while (matcher.find()) {
            any = true;
            if (!hay.contains(" " + matcher.group() + " ")) return false;
        }
        return any;
    }

    private static String paddedTokens(String text) {
        Matcher matcher = WORD.matcher(RomanianLanguagePack.fold(text));
        StringBuilder tokens = new StringBuilder(" ");
        while (matcher.find()) tokens.append(matcher.group()).append(' ');
        return tokens.toString();
    }

    public static File originalFile(Context context, Page page) throws IOException {
        if (page == null) throw new IOException("Pagina originală lipsește.");
        File file = resolve(context, page.relativePath);
        if (!file.isFile() || !page.sha256.equals(digestFile(file))) {
            throw new IOException("Originalul lipsește sau nu mai corespunde amprentei SHA-256.");
        }
        return file;
    }

    public static String sourceKey(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) throw new IllegalArgumentException("Sursă fără ID");
        return digest(sourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 32);
    }

    private static File root(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), ROOT);
    }

    private static File resolve(Context context, String relative) throws IOException {
        File dir = root(context).getCanonicalFile();
        File target = new File(dir, relative).getCanonicalFile();
        if (!target.getPath().startsWith(dir.getPath() + File.separator)) {
            throw new IOException("Cale de arhivă invalidă.");
        }
        return target;
    }

    private static void copyNeverOverwrite(File from, File target, String expected) throws IOException {
        if (target.isFile()) {
            if (!expected.equals(digestFile(target))) throw new IOException("Original arhivat deteriorat.");
            return;
        }
        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Nu pot crea arhiva.");
        File temporary = File.createTempFile("original-", ".partial", dir);
        try {
            try (FileInputStream in = new FileInputStream(from);
                 FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.getFD().sync();
            }
            if (!expected.equals(digestFile(temporary))) throw new IOException("Copia originalului nu se verifică.");
            if (!temporary.renameTo(target)) throw new IOException("Nu pot fixa originalul în arhivă.");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }
    }

    private static String digestFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) sha.update(buffer, 0, read);
            return hex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String digest(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
}
