package ro.bibliotopicsearch.app;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only provenance view over the unlimited occurrence ledger. */
public final class IndexCoreOccurrenceReader {
    private IndexCoreOccurrenceReader() {}

    public static final class Location {
        public final long occurrenceId;
        public final String sourceId;
        public final String sourceTitle;
        public final String page;
        public final int paragraphIndex;
        public final String outlineTitle;
        public final String contextCode;

        Location(long occurrenceId, String sourceId, String sourceTitle, String page,
                 int paragraphIndex, String outlineTitle, String contextCode) {
            this.occurrenceId = occurrenceId; this.sourceId = safe(sourceId); this.sourceTitle = safe(sourceTitle);
            this.page = safe(page); this.paragraphIndex = paragraphIndex; this.outlineTitle = safe(outlineTitle);
            this.contextCode = safe(contextCode);
        }
    }

    public static List<Location> list(IndexCoreDatabase db, String entryId, String sourceId, int limit) {
        if (db == null || safe(entryId).isEmpty()) return Collections.emptyList();
        StringBuilder sql = new StringBuilder(
                "SELECT o.occurrence_id,o.source_id,s.title,o.page,o.paragraph_index,COALESCE(x.title,''),o.context_code " +
                "FROM idx_occurrences o JOIN idx_sources s ON s.source_id=o.source_id " +
                "LEFT JOIN idx_outlines x ON x.outline_id=o.outline_id WHERE o.entry_id=?"
        );
        List<String> args = new ArrayList<>(); args.add(entryId);
        if (!safe(sourceId).isEmpty()) { sql.append(" AND o.source_id=?"); args.add(sourceId.trim()); }
        sql.append(" ORDER BY CASE WHEN o.page GLOB '[0-9]*' THEN CAST(o.page AS INTEGER) ELSE 2147483647 END,")
                .append("o.page,o.paragraph_index,o.occurrence_id LIMIT ")
                .append(Math.max(1, Math.min(2000, limit)));

        List<Location> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                out.add(new Location(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getInt(4), c.getString(5), c.getString(6)));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}