package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scalable v7 storage. The legacy JSON index remains as a compatibility/cache layer;
 * this database is the authoritative unlimited occurrence ledger and facet store.
 */
public final class IndexCoreDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "index_core_v7.db";
    private static final int DB_VERSION = 1;
    private static volatile IndexCoreDatabase INSTANCE;

    public static IndexCoreDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (IndexCoreDatabase.class) {
                if (INSTANCE == null) INSTANCE = new IndexCoreDatabase(context.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    private IndexCoreDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE idx_meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE idx_sources (" +
                "source_id TEXT PRIMARY KEY," +
                "title TEXT NOT NULL DEFAULT ''," +
                "author TEXT NOT NULL DEFAULT ''," +
                "edition TEXT NOT NULL DEFAULT ''," +
                "isbn TEXT NOT NULL DEFAULT ''," +
                "locator TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE idx_entries (" +
                "entry_id TEXT PRIMARY KEY," +
                "legacy_id TEXT NOT NULL DEFAULT ''," +
                "canonical TEXT NOT NULL," +
                "canonical_norm TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "validated INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "last_seen_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX ux_idx_entries_norm ON idx_entries(canonical_norm)");
        db.execSQL("CREATE TABLE idx_aliases (" +
                "alias_norm TEXT PRIMARY KEY," +
                "entry_id TEXT NOT NULL REFERENCES idx_entries(entry_id) ON DELETE CASCADE," +
                "surface TEXT NOT NULL)");
        db.execSQL("CREATE INDEX ix_idx_aliases_entry ON idx_aliases(entry_id)");
        db.execSQL("CREATE TABLE idx_outlines (" +
                "outline_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_id TEXT NOT NULL REFERENCES idx_sources(source_id) ON DELETE CASCADE," +
                "parent_id INTEGER REFERENCES idx_outlines(outline_id) ON DELETE SET NULL," +
                "kind TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "title_norm TEXT NOT NULL," +
                "page_start TEXT NOT NULL DEFAULT ''," +
                "page_end TEXT NOT NULL DEFAULT ''," +
                "depth INTEGER NOT NULL," +
                "order_index INTEGER NOT NULL," +
                "confidence REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX ux_idx_outline_identity ON idx_outlines(source_id,kind,title_norm,page_start,depth)");
        db.execSQL("CREATE INDEX ix_idx_outline_source_order ON idx_outlines(source_id,order_index)");
        db.execSQL("CREATE TABLE idx_occurrences (" +
                "occurrence_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "occurrence_key TEXT NOT NULL UNIQUE," +
                "entry_id TEXT NOT NULL REFERENCES idx_entries(entry_id) ON DELETE CASCADE," +
                "source_id TEXT NOT NULL REFERENCES idx_sources(source_id) ON DELETE CASCADE," +
                "session_id INTEGER NOT NULL," +
                "page TEXT NOT NULL DEFAULT ''," +
                "paragraph_index INTEGER NOT NULL," +
                "outline_id INTEGER REFERENCES idx_outlines(outline_id) ON DELETE SET NULL," +
                "context_code TEXT NOT NULL DEFAULT ''," +
                "seen_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX ix_idx_occ_entry ON idx_occurrences(entry_id)");
        db.execSQL("CREATE INDEX ix_idx_occ_source_page ON idx_occurrences(source_id,page,paragraph_index)");
        db.execSQL("CREATE INDEX ix_idx_occ_outline ON idx_occurrences(outline_id)");
        db.execSQL("CREATE TABLE idx_facets (" +
                "occurrence_id INTEGER NOT NULL REFERENCES idx_occurrences(occurrence_id) ON DELETE CASCADE," +
                "dimension TEXT NOT NULL," +
                "value TEXT NOT NULL," +
                "PRIMARY KEY(occurrence_id,dimension,value))");
        db.execSQL("CREATE INDEX ix_idx_facets_dim_value ON idx_facets(dimension,value,occurrence_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v7 begins at schema 1. Future upgrades must be additive/migrating, never destructive.
    }

    public static final class SourceRecord {
        public final String id;
        public final String title;
        public final String author;
        public final String edition;
        public final String isbn;
        public final String locator;

        SourceRecord(String id, String title, String author, String edition, String isbn, String locator) {
            this.id = safe(id); this.title = safe(title); this.author = safe(author);
            this.edition = safe(edition); this.isbn = safe(isbn); this.locator = safe(locator);
        }
        public String displayName() { return title.isEmpty() ? "Sursă fără titlu" : title; }
    }

    public static final class OutlineRecord {
        public final long id;
        public final long parentId;
        public final String kind;
        public final String title;
        public final String pageStart;
        public final int depth;
        public final int orderIndex;

        OutlineRecord(long id, long parentId, String kind, String title, String pageStart, int depth, int orderIndex) {
            this.id = id; this.parentId = parentId; this.kind = safe(kind); this.title = safe(title);
            this.pageStart = safe(pageStart); this.depth = depth; this.orderIndex = orderIndex;
        }
    }

    public static final class FacetFilter {
        public final String dimension;
        public final String value;
        public final boolean exclude;

        public FacetFilter(String dimension, String value, boolean exclude) {
            this.dimension = upper(dimension);
            this.value = upper(value);
            this.exclude = exclude;
        }
    }

    public static final class SearchResult {
        public final String entryId;
        public final String canonical;
        public final String category;
        public final boolean validated;
        public final int occurrences;

        SearchResult(String entryId, String canonical, String category, boolean validated, int occurrences) {
            this.entryId = entryId; this.canonical = canonical; this.category = category;
            this.validated = validated; this.occurrences = occurrences;
        }
    }

    public static final class Stats {
        public final long sources;
        public final long entries;
        public final long occurrences;
        public final long facets;
        public final long outlines;

        Stats(long sources, long entries, long occurrences, long facets, long outlines) {
            this.sources = sources; this.entries = entries; this.occurrences = occurrences;
            this.facets = facets; this.outlines = outlines;
        }
    }

    public void ensureSource(String sourceId) {
        String id = safe(sourceId);
        if (id.isEmpty()) return;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("source_id", id);
        values.put("created_at", now);
        values.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("idx_sources", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateSource(String sourceId, String title, String author, String edition, String isbn, String locator) {
        ensureSource(sourceId);
        ContentValues values = new ContentValues();
        values.put("title", safe(title)); values.put("author", safe(author));
        values.put("edition", safe(edition)); values.put("isbn", safe(isbn));
        values.put("locator", safe(locator)); values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("idx_sources", values, "source_id=?", new String[]{safe(sourceId)});
    }

    public SourceRecord source(String sourceId) {
        try (Cursor c = getReadableDatabase().query("idx_sources",
                new String[]{"source_id","title","author","edition","isbn","locator"},
                "source_id=?", new String[]{safe(sourceId)}, null, null, null, "1")) {
            if (!c.moveToFirst()) return null;
            return new SourceRecord(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5));
        }
    }

    public List<SourceRecord> listSources() {
        List<SourceRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("idx_sources",
                new String[]{"source_id","title","author","edition","isbn","locator"},
                null, null, null, null, "updated_at DESC")) {
            while (c.moveToNext()) out.add(new SourceRecord(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Returns the collision-resistant v7 entry id. */
    public String upsertEntry(LivingIndexStore.Entry entry) {
        if (entry == null || safe(entry.canonical()).isEmpty()) return "";
        String coreId = coreEntryId(entry.canonical());
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("entry_id", coreId);
        values.put("legacy_id", safe(entry.id()));
        values.put("canonical", entry.canonical());
        values.put("canonical_norm", fold(entry.canonical()));
        values.put("category", entry.category().name());
        values.put("validated", entry.validated() ? 1 : 0);
        values.put("created_at", entry.createdAt() > 0 ? entry.createdAt() : now);
        values.put("last_seen_at", entry.lastSeenAt() > 0 ? entry.lastSeenAt() : now);
        getWritableDatabase().insertWithOnConflict("idx_entries", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        for (String alias : entry.aliases()) addAlias(coreId, alias);
        return coreId;
    }

    public void addAlias(String entryId, String surface) {
        String norm = fold(surface);
        if (safe(entryId).isEmpty() || norm.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("alias_norm", norm); values.put("entry_id", entryId); values.put("surface", safe(surface));
        getWritableDatabase().insertWithOnConflict("idx_aliases", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long upsertOutline(
            String sourceId, long parentId, String kind, String title, String page,
            int depth, int orderIndex, double confidence
    ) {
        ensureSource(sourceId);
        SQLiteDatabase db = getWritableDatabase();
        String norm = fold(title);
        try (Cursor c = db.query("idx_outlines", new String[]{"outline_id"},
                "source_id=? AND kind=? AND title_norm=? AND page_start=? AND depth=?",
                new String[]{safe(sourceId), upper(kind), norm, safe(page), String.valueOf(Math.max(0, depth))},
                null, null, null, "1")) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        ContentValues values = new ContentValues();
        values.put("source_id", safe(sourceId));
        if (parentId > 0) values.put("parent_id", parentId); else values.putNull("parent_id");
        values.put("kind", upper(kind)); values.put("title", safe(title)); values.put("title_norm", norm);
        values.put("page_start", safe(page)); values.put("page_end", "");
        values.put("depth", Math.max(0, depth)); values.put("order_index", Math.max(0, orderIndex));
        values.put("confidence", Math.max(0.0, Math.min(1.0, confidence)));
        return db.insert("idx_outlines", null, values);
    }

    public List<OutlineRecord> outlinesForSource(String sourceId) {
        List<OutlineRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("idx_outlines",
                new String[]{"outline_id","COALESCE(parent_id,0)","kind","title","page_start","depth","order_index"},
                "source_id=?", new String[]{safe(sourceId)}, null, null, "order_index ASC, outline_id ASC")) {
            while (c.moveToNext()) out.add(new OutlineRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getInt(6)));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Insert one logical occurrence. Repeated camera frames collapse on occurrence_key;
     * later scans with a known page remain the same occurrence even in a new session.
     */
    public long addOccurrence(
            String coreEntryId,
            String sourceId,
            long sessionId,
            String page,
            int paragraphIndex,
            long outlineId,
            String contextCode,
            long seenAt,
            List<String> facets
    ) {
        if (safe(coreEntryId).isEmpty() || safe(sourceId).isEmpty()) return -1L;
        ensureSource(sourceId);
        String key = occurrenceKey(coreEntryId, sourceId, sessionId, page, paragraphIndex, outlineId, contextCode);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("occurrence_key", key); values.put("entry_id", coreEntryId); values.put("source_id", sourceId);
            values.put("session_id", sessionId); values.put("page", safe(page)); values.put("paragraph_index", Math.max(0, paragraphIndex));
            if (outlineId > 0) values.put("outline_id", outlineId); else values.putNull("outline_id");
            values.put("context_code", safe(contextCode)); values.put("seen_at", seenAt > 0 ? seenAt : System.currentTimeMillis());
            long id = db.insertWithOnConflict("idx_occurrences", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            if (id < 0) {
                try (Cursor c = db.query("idx_occurrences", new String[]{"occurrence_id"}, "occurrence_key=?", new String[]{key}, null, null, null, "1")) {
                    if (c.moveToFirst()) id = c.getLong(0);
                }
            }
            if (id > 0 && facets != null) {
                for (String facet : facets) {
                    ParsedFacet parsed = ParsedFacet.parse(facet);
                    if (parsed == null) continue;
                    ContentValues f = new ContentValues();
                    f.put("occurrence_id", id); f.put("dimension", parsed.dimension); f.put("value", parsed.value);
                    db.insertWithOnConflict("idx_facets", null, f, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public long countOccurrences(String coreEntryId) {
        return scalar("SELECT COUNT(*) FROM idx_occurrences WHERE entry_id=?", new String[]{safe(coreEntryId)});
    }

    public Stats stats() {
        return new Stats(
                scalar("SELECT COUNT(*) FROM idx_sources", null),
                scalar("SELECT COUNT(*) FROM idx_entries", null),
                scalar("SELECT COUNT(*) FROM idx_occurrences", null),
                scalar("SELECT COUNT(*) FROM idx_facets", null),
                scalar("SELECT COUNT(*) FROM idx_outlines", null)
        );
    }

    /** AND intersection across included facets; excluded facets use NOT EXISTS. */
    public List<SearchResult> search(
            List<FacetFilter> filters,
            String sourceId,
            String pageFrom,
            String pageTo,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.entry_id,e.canonical,e.category,e.validated,COUNT(DISTINCT o.occurrence_id) " +
                "FROM idx_entries e JOIN idx_occurrences o ON o.entry_id=e.entry_id WHERE 1=1"
        );
        List<String> args = new ArrayList<>();
        if (!safe(sourceId).isEmpty()) { sql.append(" AND o.source_id=?"); args.add(sourceId); }
        if (isDigits(pageFrom)) { sql.append(" AND o.page GLOB '[0-9]*' AND CAST(o.page AS INTEGER)>=?"); args.add(pageFrom); }
        if (isDigits(pageTo)) { sql.append(" AND o.page GLOB '[0-9]*' AND CAST(o.page AS INTEGER)<=?"); args.add(pageTo); }
        if (filters != null) {
            int n = 0;
            for (FacetFilter filter : filters) {
                if (filter == null || filter.dimension.isEmpty() || filter.value.isEmpty()) continue;
                String alias = "f" + (n++);
                if (filter.exclude) {
                    sql.append(" AND NOT EXISTS (SELECT 1 FROM idx_facets ").append(alias)
                            .append(" JOIN idx_occurrences ox ON ox.occurrence_id=").append(alias).append(".occurrence_id")
                            .append(" WHERE ox.entry_id=e.entry_id AND ").append(alias).append(".dimension=? AND ").append(alias).append(".value=?)");
                } else {
                    sql.append(" AND EXISTS (SELECT 1 FROM idx_facets ").append(alias)
                            .append(" JOIN idx_occurrences ix ON ix.occurrence_id=").append(alias).append(".occurrence_id")
                            .append(" WHERE ix.entry_id=e.entry_id AND ").append(alias).append(".dimension=? AND ").append(alias).append(".value=?)");
                }
                args.add(filter.dimension); args.add(filter.value);
            }
        }
        sql.append(" GROUP BY e.entry_id ORDER BY COUNT(DISTINCT o.occurrence_id) DESC,e.canonical COLLATE NOCASE ASC LIMIT ")
                .append(Math.max(1, Math.min(1000, limit)));

        List<SearchResult> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) out.add(new SearchResult(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) != 0, c.getInt(4)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Import legacy v5/v6 entries and their capped historical refs once. New refs are unlimited here. */
    public void migrateLegacyOnce(LivingIndexStore.State state) {
        if (state == null || "1".equals(meta("legacy_migrated"))) return;
        SQLiteDatabase db = getWritableDatabase();
        for (LivingIndexStore.Entry entry : state.entries()) {
            String coreId = upsertEntry(entry);
            for (LivingIndexStore.Ref ref : entry.refs()) {
                String legacySource = "legacy-" + ref.sourceId();
                ensureSource(legacySource);
                addOccurrence(coreId, legacySource, ref.sourceId(), ref.page(), ref.paragraphIndex(), 0,
                        ref.contextCode(), ref.seenAt(), ref.axes());
            }
        }
        ContentValues value = new ContentValues(); value.put("k", "legacy_migrated"); value.put("v", "1");
        db.insertWithOnConflict("idx_meta", null, value, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private String meta(String key) {
        try (Cursor c = getReadableDatabase().query("idx_meta", new String[]{"v"}, "k=?", new String[]{key}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    private long scalar(String sql, String[] args) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) { return c.moveToFirst() ? c.getLong(0) : 0L; }
    }

    public static String coreEntryId(String canonical) {
        return "e-" + sha256(fold(canonical)).substring(0, 32);
    }

    public static String occurrenceKey(
            String entryId, String sourceId, long sessionId, String page,
            int paragraphIndex, long outlineId, String contextCode
    ) {
        String p = safe(page);
        // Known page => stable across sessions. Unknown page => session keeps separate pages from colliding.
        String sessionPart = p.isEmpty() ? String.valueOf(sessionId) : "page";
        String raw = safe(entryId) + "|" + safe(sourceId) + "|" + sessionPart + "|" + p + "|" +
                Math.max(0, paragraphIndex) + "|" + Math.max(0, outlineId) + "|" + safe(contextCode);
        return "o-" + sha256(raw).substring(0, 40);
    }

    private static final class ParsedFacet {
        final String dimension; final String value;
        ParsedFacet(String dimension, String value) { this.dimension = dimension; this.value = value; }
        static ParsedFacet parse(String raw) {
            if (raw == null) return null;
            int i = raw.indexOf('=');
            if (i <= 0 || i >= raw.length() - 1) return null;
            String d = upper(raw.substring(0, i)); String v = upper(raw.substring(i + 1));
            return d.isEmpty() || v.isEmpty() ? null : new ParsedFacet(d, v);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception impossible) {
            return String.format(Locale.ROOT, "%064x", safe(value).hashCode());
        }
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String upper(String value) { return safe(value).toUpperCase(Locale.ROOT); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static boolean isDigits(String value) { return safe(value).matches("\\d{1,7}"); }
}