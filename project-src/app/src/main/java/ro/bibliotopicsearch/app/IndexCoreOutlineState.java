package ro.bibliotopicsearch.app;

import android.database.Cursor;

/** Restores the latest physical-outline chain so scanning can resume on another day. */
public final class IndexCoreOutlineState {
    private IndexCoreOutlineState() {}

    public static final class Restored {
        public final int nextOrder;
        public final long currentId;
        Restored(int nextOrder, long currentId) { this.nextOrder = nextOrder; this.currentId = currentId; }
    }

    public static Restored restore(IndexCoreDatabase db, String sourceId, long[] lastByDepth) {
        if (lastByDepth != null) for (int i = 0; i < lastByDepth.length; i++) lastByDepth[i] = 0L;
        if (db == null || sourceId == null || sourceId.trim().isEmpty()) return new Restored(0, 0L);

        int nextOrder = 0;
        long current = 0L;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT outline_id,COALESCE(parent_id,0),depth,order_index FROM idx_outlines " +
                        "WHERE source_id=? ORDER BY order_index DESC,outline_id DESC LIMIT 1",
                new String[]{sourceId}
        )) {
            if (c.moveToFirst()) {
                current = c.getLong(0);
                nextOrder = c.getInt(3) + 1;
            }
        }
        long node = current;
        int guard = 0;
        while (node > 0 && guard++ < 16) {
            try (Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT outline_id,COALESCE(parent_id,0),depth FROM idx_outlines WHERE outline_id=? LIMIT 1",
                    new String[]{String.valueOf(node)}
            )) {
                if (!c.moveToFirst()) break;
                int depth = c.getInt(2);
                if (lastByDepth != null && depth >= 0 && depth < lastByDepth.length) lastByDepth[depth] = c.getLong(0);
                node = c.getLong(1);
            }
        }
        return new Restored(nextOrder, current);
    }
}