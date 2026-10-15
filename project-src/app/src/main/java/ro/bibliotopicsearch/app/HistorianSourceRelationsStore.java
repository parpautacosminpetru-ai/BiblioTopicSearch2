package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Human-declared relationships among sources, editions and textual witnesses. */
public final class HistorianSourceRelationsStore {
    private HistorianSourceRelationsStore() {}

    public enum RelationType {
        SAME_WORK,
        EDITION_OF,
        TRANSLATION_OF,
        MANUSCRIPT_OF,
        COPY_OF,
        DERIVED_FROM,
        CITES,
        RESPONDS_TO,
        CONTEMPORARY_WITH,
        OTHER
    }

    public static final class Relation {
        public final String leftSourceId;
        public final String leftTitle;
        public final String rightSourceId;
        public final String rightTitle;
        public final RelationType type;
        public final String note;
        public final long updatedAt;

        Relation(
                String leftSourceId,
                String leftTitle,
                String rightSourceId,
                String rightTitle,
                RelationType type,
                String note,
                long updatedAt
        ) {
            this.leftSourceId = safe(leftSourceId);
            this.leftTitle = safe(leftTitle);
            this.rightSourceId = safe(rightSourceId);
            this.rightTitle = safe(rightTitle);
            this.type = type == null ? RelationType.OTHER : type;
            this.note = safe(note);
            this.updatedAt = updatedAt;
        }
    }

    public static boolean save(
            IndexCoreDatabase db,
            String leftSourceId,
            String rightSourceId,
            RelationType type,
            String note
    ) {
        if (db == null || safe(leftSourceId).isEmpty() || safe(rightSourceId).isEmpty()
                || leftSourceId.equals(rightSourceId) || type == null) return false;
        db.ensureSource(leftSourceId);
        db.ensureSource(rightSourceId);
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("left_source_id", leftSourceId);
        row.put("right_source_id", rightSourceId);
        row.put("relation", type.name());
        row.put("note", safe(note));
        row.put("created_at", now);
        row.put("updated_at", now);
        long result = db.getWritableDatabase().insertWithOnConflict(
                "idx_source_relations", null, row, SQLiteDatabase.CONFLICT_REPLACE
        );
        if (result >= 0) {
            audit(db, leftSourceId, "SOURCE_RELATION_" + type.name(), rightSourceId);
            return true;
        }
        return false;
    }

    public static boolean remove(
            IndexCoreDatabase db,
            String leftSourceId,
            String rightSourceId,
            RelationType type
    ) {
        if (db == null || type == null) return false;
        int changed = db.getWritableDatabase().delete(
                "idx_source_relations",
                "left_source_id=? AND right_source_id=? AND relation=?",
                new String[]{safe(leftSourceId), safe(rightSourceId), type.name()}
        );
        if (changed > 0) {
            audit(db, leftSourceId, "SOURCE_RELATION_REMOVED", rightSourceId + "|" + type.name());
        }
        return changed > 0;
    }

    public static List<Relation> list(IndexCoreDatabase db) {
        if (db == null) return Collections.emptyList();
        String sql = "SELECT r.left_source_id,COALESCE(l.title,''),"
                + "r.right_source_id,COALESCE(rr.title,''),r.relation,r.note,r.updated_at "
                + "FROM idx_source_relations r "
                + "LEFT JOIN idx_sources l ON l.source_id=r.left_source_id "
                + "LEFT JOIN idx_sources rr ON rr.source_id=r.right_source_id "
                + "ORDER BY COALESCE(l.title,'') COLLATE NOCASE,r.relation,"
                + "COALESCE(rr.title,'') COLLATE NOCASE";
        List<Relation> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new Relation(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                        parse(c.getString(4)), c.getString(5), c.getLong(6)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static RelationType parse(String value) {
        try { return RelationType.valueOf(safe(value).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return RelationType.OTHER; }
    }

    private static void audit(
            IndexCoreDatabase db,
            String sourceId,
            String action,
            String detail
    ) {
        try {
            ContentValues row = new ContentValues();
            row.put("entity_type", "SOURCE");
            row.put("entity_id", safe(sourceId));
            row.put("action", safe(action));
            row.put("detail", safe(detail));
            row.put("created_at", System.currentTimeMillis());
            db.getWritableDatabase().insert("idx_research_audit", null, row);
        } catch (RuntimeException ignored) {
            // Relations remain valid even if the audit sidecar cannot be written.
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
