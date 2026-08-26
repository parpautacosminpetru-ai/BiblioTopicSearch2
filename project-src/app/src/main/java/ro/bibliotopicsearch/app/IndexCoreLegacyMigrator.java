package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Imports v5/v6 JSON refs once without using destructive parent-row REPLACE semantics. */
public final class IndexCoreLegacyMigrator {
    private static final String KEY = "legacy_migrated_safe_v7";
    private IndexCoreLegacyMigrator() {}

    public static void migrate(IndexCoreDatabase db, LivingIndexStore.State state) {
        if (db == null || state == null || done(db)) return;
        SQLiteDatabase sql = db.getWritableDatabase();
        for (LivingIndexStore.Entry entry : state.entries()) {
            String coreId = IndexCoreEntryWriter.upsert(db, entry);
            if (coreId.isEmpty()) continue;
            for (LivingIndexStore.Ref ref : entry.refs()) {
                String source = "legacy-" + ref.sourceId();
                db.ensureSource(source);
                List<String> facets = new ArrayList<>(ref.axes());
                String primary = "PRIMARY=" + entry.category().name();
                if (!facets.contains(primary)) facets.add(primary);
                db.addOccurrence(coreId, source, ref.sourceId(), ref.page(), ref.paragraphIndex(), 0L,
                        ref.contextCode(), ref.seenAt(), facets);
            }
        }
        ContentValues v = new ContentValues(); v.put("k", KEY); v.put("v", "1");
        sql.insertWithOnConflict("idx_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static boolean done(IndexCoreDatabase db) {
        try (Cursor c = db.getReadableDatabase().query("idx_meta", new String[]{"v"}, "k=?", new String[]{KEY}, null, null, null, "1")) {
            return c.moveToFirst() && "1".equals(c.getString(0));
        }
    }
}