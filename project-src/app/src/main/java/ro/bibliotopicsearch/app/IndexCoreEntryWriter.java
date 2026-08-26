package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Non-destructive v7 entry upsert. Never REPLACE a parent row with occurrence children. */
public final class IndexCoreEntryWriter {
    private IndexCoreEntryWriter() {}

    public static String upsert(IndexCoreDatabase db, LivingIndexStore.Entry entry) {
        if (db == null || entry == null || entry.canonical() == null || entry.canonical().trim().isEmpty()) return "";
        String coreId = IndexCoreDatabase.coreEntryId(entry.canonical());
        SQLiteDatabase sql = db.getWritableDatabase();
        long now = System.currentTimeMillis();

        ContentValues insert = new ContentValues();
        insert.put("entry_id", coreId);
        insert.put("legacy_id", entry.id());
        insert.put("canonical", entry.canonical());
        insert.put("canonical_norm", normalize(entry.canonical()));
        insert.put("category", entry.category().name());
        insert.put("validated", entry.validated() ? 1 : 0);
        insert.put("created_at", entry.createdAt() > 0 ? entry.createdAt() : now);
        insert.put("last_seen_at", entry.lastSeenAt() > 0 ? entry.lastSeenAt() : now);
        sql.insertWithOnConflict("idx_entries", null, insert, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues update = new ContentValues();
        update.put("legacy_id", entry.id());
        update.put("canonical", entry.canonical());
        update.put("canonical_norm", normalize(entry.canonical()));
        update.put("category", entry.category().name());
        update.put("validated", entry.validated() ? 1 : 0);
        update.put("last_seen_at", entry.lastSeenAt() > 0 ? entry.lastSeenAt() : now);
        sql.update("idx_entries", update, "entry_id=?", new String[]{coreId});

        for (String alias : entry.aliases()) db.addAlias(coreId, alias);
        return coreId;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }
}