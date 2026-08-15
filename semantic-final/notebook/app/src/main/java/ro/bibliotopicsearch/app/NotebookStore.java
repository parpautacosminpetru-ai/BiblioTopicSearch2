package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OrtException;
import ro.bibliotopicsearch.app.semantic.OnDeviceSentenceEmbedder;

/**
 * Fully local source notebook. Stores original source text plus embeddings for exact
 * text spans. Search returns only verbatim substrings from a stored source.
 */
public final class NotebookStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "semantic_notebook.db";
    private static final int DB_VERSION = 1;
    private static final float DIRECT_THRESHOLD = 0.50f;
    private static final float CONTINUATION_FLOOR = 0.40f;
    private static final float COHERENCE_THRESHOLD = 0.74f;
    private static final int MAX_SPAN_CHUNKS = 8;

    public static final class SourcePage {
        public final long id;
        public final String sourceName;
        public final String pageLabel;
        public final String text;
        public final long createdAt;
        public final int chunks;

        SourcePage(long id, String sourceName, String pageLabel, String text, long createdAt, int chunks) {
            this.id = id;
            this.sourceName = sourceName;
            this.pageLabel = pageLabel;
            this.text = text;
            this.createdAt = createdAt;
            this.chunks = chunks;
        }
    }

    public static final class SearchResult {
        public final long pageId;
        public final String sourceName;
        public final String pageLabel;
        public final String exactText;
        public final int startChar;
        public final int endChar;
        public final float similarity;

        SearchResult(long pageId, String sourceName, String pageLabel, String exactText,
                     int startChar, int endChar, float similarity) {
            this.pageId = pageId;
            this.sourceName = sourceName;
            this.pageLabel = pageLabel;
            this.exactText = exactText;
            this.startChar = startChar;
            this.endChar = endChar;
            this.similarity = similarity;
        }
    }

    private static final class Chunk {
        long id;
        long pageId;
        int ordinal;
        int startChar;
        int endChar;
        String text;
        float[] embedding;
        float score;
    }

    private static final class RawChunk {
        final int ordinal;
        final int start;
        final int end;
        final String text;

        RawChunk(int ordinal, int start, int end, String text) {
            this.ordinal = ordinal;
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    public NotebookStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pages (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_name TEXT NOT NULL," +
                "page_label TEXT NOT NULL," +
                "full_text TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE chunks (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "page_id INTEGER NOT NULL," +
                "ordinal INTEGER NOT NULL," +
                "start_char INTEGER NOT NULL," +
                "end_char INTEGER NOT NULL," +
                "exact_text TEXT NOT NULL," +
                "embedding BLOB NOT NULL," +
                "FOREIGN KEY(page_id) REFERENCES pages(_id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX chunks_page_ordinal ON chunks(page_id, ordinal)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS chunks");
        db.execSQL("DROP TABLE IF EXISTS pages");
        onCreate(db);
    }

    public long addPage(String sourceName, String pageLabel, String fullText,
                        OnDeviceSentenceEmbedder embedder) throws OrtException {
        String text = fullText == null ? "" : fullText;
        if (text.trim().isEmpty()) throw new IllegalArgumentException("Sursa nu conține text.");
        String source = sourceName == null || sourceName.trim().isEmpty() ? "Sursă locală" : sourceName.trim();
        String page = pageLabel == null || pageLabel.trim().isEmpty() ? "Pagină" : pageLabel.trim();

        List<RawChunk> raw = chunkExact(text);
        if (raw.isEmpty()) throw new IllegalArgumentException("Nu am găsit fragmente indexabile.");
        List<String> texts = new ArrayList<>(raw.size());
        for (RawChunk chunk : raw) texts.add(chunk.text);
        List<float[]> vectors = embedder.embedAll(texts);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues pageValues = new ContentValues();
            pageValues.put("source_name", source);
            pageValues.put("page_label", page);
            pageValues.put("full_text", text);
            pageValues.put("created_at", System.currentTimeMillis());
            long pageId = db.insertOrThrow("pages", null, pageValues);

            for (int i = 0; i < raw.size(); i++) {
                RawChunk chunk = raw.get(i);
                ContentValues values = new ContentValues();
                values.put("page_id", pageId);
                values.put("ordinal", chunk.ordinal);
                values.put("start_char", chunk.start);
                values.put("end_char", chunk.end);
                values.put("exact_text", chunk.text);
                values.put("embedding", encodeVector(vectors.get(i)));
                db.insertOrThrow("chunks", null, values);
            }
            db.setTransactionSuccessful();
            return pageId;
        } finally {
            db.endTransaction();
        }
    }

    public List<SourcePage> listPages() {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT p._id,p.source_name,p.page_label,p.full_text,p.created_at," +
                "COUNT(c._id) AS chunks FROM pages p LEFT JOIN chunks c ON c.page_id=p._id " +
                "GROUP BY p._id ORDER BY p.created_at DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            List<SourcePage> out = new ArrayList<>();
            while (cursor.moveToNext()) {
                out.add(new SourcePage(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getLong(4), cursor.getInt(5)
                ));
            }
            return out;
        }
    }

    public void deletePage(long pageId) {
        getWritableDatabase().delete("pages", "_id=?", new String[]{String.valueOf(pageId)});
    }

    public List<SearchResult> search(String query, OnDeviceSentenceEmbedder embedder, int limit)
            throws OrtException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || limit <= 0) return Collections.emptyList();
        float[] queryVector = embedder.embed(q);

        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT c._id,c.page_id,c.ordinal,c.start_char,c.end_char,c.exact_text,c.embedding," +
                "p.source_name,p.page_label,p.full_text FROM chunks c JOIN pages p ON p._id=c.page_id " +
                "ORDER BY c.page_id,c.ordinal";
        List<Chunk> chunks = new ArrayList<>();
        Map<Long, String> sourceNames = new HashMap<>();
        Map<Long, String> pageLabels = new HashMap<>();
        Map<Long, String> fullTexts = new HashMap<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                Chunk chunk = new Chunk();
                chunk.id = cursor.getLong(0);
                chunk.pageId = cursor.getLong(1);
                chunk.ordinal = cursor.getInt(2);
                chunk.startChar = cursor.getInt(3);
                chunk.endChar = cursor.getInt(4);
                chunk.text = cursor.getString(5);
                chunk.embedding = decodeVector(cursor.getBlob(6));
                chunk.score = OnDeviceSentenceEmbedder.cosine(queryVector, chunk.embedding);
                chunks.add(chunk);
                sourceNames.put(chunk.pageId, cursor.getString(7));
                pageLabels.put(chunk.pageId, cursor.getString(8));
                fullTexts.put(chunk.pageId, cursor.getString(9));
            }
        }
        if (chunks.isEmpty()) return Collections.emptyList();

        List<Integer> nuclei = new ArrayList<>();
        float best = -1f;
        for (int i = 0; i < chunks.size(); i++) {
            best = Math.max(best, chunks.get(i).score);
            if (chunks.get(i).score >= DIRECT_THRESHOLD) nuclei.add(i);
        }
        if (nuclei.isEmpty() && best >= DIRECT_THRESHOLD - 0.02f) {
            int bestIndex = 0;
            for (int i = 1; i < chunks.size(); i++) {
                if (chunks.get(i).score > chunks.get(bestIndex).score) bestIndex = i;
            }
            nuclei.add(bestIndex);
        }
        nuclei.sort((a, b) -> Float.compare(chunks.get(b).score, chunks.get(a).score));

        boolean[] consumed = new boolean[chunks.size()];
        List<SearchResult> results = new ArrayList<>();
        for (int nucleus : nuclei) {
            if (consumed[nucleus]) continue;
            Chunk center = chunks.get(nucleus);
            int start = nucleus;
            int end = nucleus;
            while (start > 0 && end - start + 1 < MAX_SPAN_CHUNKS) {
                Chunk left = chunks.get(start - 1);
                Chunk current = chunks.get(start);
                if (left.pageId != center.pageId || left.ordinal + 1 != current.ordinal) break;
                float coherence = OnDeviceSentenceEmbedder.cosine(left.embedding, current.embedding);
                if (left.score < CONTINUATION_FLOOR || coherence < COHERENCE_THRESHOLD) break;
                start--;
            }
            while (end + 1 < chunks.size() && end - start + 1 < MAX_SPAN_CHUNKS) {
                Chunk current = chunks.get(end);
                Chunk right = chunks.get(end + 1);
                if (right.pageId != center.pageId || current.ordinal + 1 != right.ordinal) break;
                float coherence = OnDeviceSentenceEmbedder.cosine(current.embedding, right.embedding);
                if (right.score < CONTINUATION_FLOOR || coherence < COHERENCE_THRESHOLD) break;
                end++;
            }

            int startChar = chunks.get(start).startChar;
            int endChar = chunks.get(end).endChar;
            String full = fullTexts.get(center.pageId);
            if (full == null) continue;
            startChar = Math.max(0, Math.min(startChar, full.length()));
            endChar = Math.max(startChar, Math.min(endChar, full.length()));
            String exact = full.substring(startChar, endChar);
            float score = center.score;
            results.add(new SearchResult(
                    center.pageId,
                    sourceNames.get(center.pageId),
                    pageLabels.get(center.pageId),
                    exact,
                    startChar,
                    endChar,
                    score
            ));
            for (int i = start; i <= end; i++) consumed[i] = true;
            if (results.size() >= limit * 2) break;
        }

        results.sort(Comparator.comparingDouble((SearchResult value) -> value.similarity).reversed());
        if (results.size() > limit) return new ArrayList<>(results.subList(0, limit));
        return results;
    }

    private static List<RawChunk> chunkExact(String text) {
        List<RawChunk> out = new ArrayList<>();
        int ordinal = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int lineEnd = text.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = text.length();
            int start = cursor;
            int end = lineEnd;
            while (start < end && Character.isWhitespace(text.charAt(start))) start++;
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (end > start) {
                String line = text.substring(start, end);
                if (line.length() <= 420) {
                    out.add(new RawChunk(ordinal++, start, end, line));
                } else {
                    BreakIterator breaker = BreakIterator.getSentenceInstance(Locale.ROOT);
                    breaker.setText(line);
                    int localStart = breaker.first();
                    int localEnd;
                    while ((localEnd = breaker.next()) != BreakIterator.DONE) {
                        int s = localStart;
                        int e = localEnd;
                        while (s < e && Character.isWhitespace(line.charAt(s))) s++;
                        while (e > s && Character.isWhitespace(line.charAt(e - 1))) e--;
                        if (e > s) out.add(new RawChunk(ordinal++, start + s, start + e, line.substring(s, e)));
                        localStart = localEnd;
                    }
                }
            }
            cursor = lineEnd < text.length() ? lineEnd + 1 : text.length();
        }
        return out;
    }

    private static byte[] encodeVector(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) buffer.putFloat(value);
        return buffer.array();
    }

    private static float[] decodeVector(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) return new float[0];
        float[] out = new float[bytes.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < out.length; i++) out[i] = buffer.getFloat();
        return out;
    }
}
