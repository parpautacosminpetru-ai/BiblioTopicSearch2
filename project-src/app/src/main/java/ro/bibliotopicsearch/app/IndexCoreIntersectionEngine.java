package ro.bibliotopicsearch.app;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Strict facet intersection: every included criterion must hold on the same occurrence. */
public final class IndexCoreIntersectionEngine {
    private IndexCoreIntersectionEngine() {}

    public static List<IndexCoreDatabase.SearchResult> search(
            IndexCoreDatabase db,
            List<IndexCoreDatabase.FacetFilter> filters,
            String sourceId,
            String pageFrom,
            String pageTo,
            int limit
    ) {
        if (db == null) return Collections.emptyList();
        StringBuilder sql = new StringBuilder(
                "SELECT e.entry_id,e.canonical,e.category,e.validated,COUNT(DISTINCT o.occurrence_id) " +
                "FROM idx_entries e JOIN idx_occurrences o ON o.entry_id=e.entry_id WHERE 1=1"
        );
        List<String> args = new ArrayList<>();
        if (notEmpty(sourceId)) { sql.append(" AND o.source_id=?"); args.add(sourceId.trim()); }
        if (digits(pageFrom)) { sql.append(" AND o.page GLOB '[0-9]*' AND CAST(o.page AS INTEGER)>=?"); args.add(pageFrom.trim()); }
        if (digits(pageTo)) { sql.append(" AND o.page GLOB '[0-9]*' AND CAST(o.page AS INTEGER)<=?"); args.add(pageTo.trim()); }

        if (filters != null) {
            int n = 0;
            for (IndexCoreDatabase.FacetFilter filter : filters) {
                if (filter == null || !notEmpty(filter.dimension) || !notEmpty(filter.value)) continue;
                String alias = "f" + n++;
                if (filter.exclude) {
                    sql.append(" AND NOT EXISTS (SELECT 1 FROM idx_facets ").append(alias)
                            .append(" WHERE ").append(alias).append(".occurrence_id=o.occurrence_id")
                            .append(" AND ").append(alias).append(".dimension=? AND ").append(alias).append(".value=?)");
                } else {
                    sql.append(" AND EXISTS (SELECT 1 FROM idx_facets ").append(alias)
                            .append(" WHERE ").append(alias).append(".occurrence_id=o.occurrence_id")
                            .append(" AND ").append(alias).append(".dimension=? AND ").append(alias).append(".value=?)");
                }
                args.add(filter.dimension); args.add(filter.value);
            }
        }

        sql.append(" GROUP BY e.entry_id,e.canonical,e.category,e.validated")
                .append(" ORDER BY COUNT(DISTINCT o.occurrence_id) DESC,e.canonical COLLATE NOCASE ASC LIMIT ")
                .append(Math.max(1, Math.min(1000, limit)));

        List<IndexCoreDatabase.SearchResult> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                out.add(new IndexCoreDatabase.SearchResult(
                        c.getString(0), c.getString(1), c.getString(2), c.getInt(3) != 0, c.getInt(4)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean notEmpty(String value) { return value != null && !value.trim().isEmpty(); }
    private static boolean digits(String value) { return value != null && value.trim().matches("\\d{1,7}"); }
}