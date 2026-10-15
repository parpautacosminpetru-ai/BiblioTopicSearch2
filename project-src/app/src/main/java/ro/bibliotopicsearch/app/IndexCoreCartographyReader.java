package ro.bibliotopicsearch.app;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only research views over the deterministic exhaustive cartography ledger. */
public final class IndexCoreCartographyReader {
    private IndexCoreCartographyReader() {}

    public static final class Stats {
        public final int expectedPages;
        public final int pagesSeen;
        public final int pagesComplete;
        public final int paragraphUnits;
        public final int clauseUnits;
        public final int mappedUnits;
        public final int noIndexUnits;
        public final int ambiguousUnits;
        public final int unparsedUnits;
        public final int intersections;
        public final int distinctTopics;
        public final List<Integer> missingPages;

        Stats(
                int expectedPages,
                int pagesSeen,
                int pagesComplete,
                int paragraphUnits,
                int clauseUnits,
                int mappedUnits,
                int noIndexUnits,
                int ambiguousUnits,
                int unparsedUnits,
                int intersections,
                int distinctTopics,
                List<Integer> missingPages
        ) {
            this.expectedPages = Math.max(0, expectedPages);
            this.pagesSeen = Math.max(0, pagesSeen);
            this.pagesComplete = Math.max(0, pagesComplete);
            this.paragraphUnits = Math.max(0, paragraphUnits);
            this.clauseUnits = Math.max(0, clauseUnits);
            this.mappedUnits = Math.max(0, mappedUnits);
            this.noIndexUnits = Math.max(0, noIndexUnits);
            this.ambiguousUnits = Math.max(0, ambiguousUnits);
            this.unparsedUnits = Math.max(0, unparsedUnits);
            this.intersections = Math.max(0, intersections);
            this.distinctTopics = Math.max(0, distinctTopics);
            this.missingPages = Collections.unmodifiableList(new ArrayList<>(missingPages));
        }

        public boolean scopeDefined() { return expectedPages > 0; }
        public boolean exhaustive() {
            return scopeDefined() && pagesComplete >= expectedPages && missingPages.isEmpty();
        }
        public int coveragePercent() {
            if (expectedPages <= 0) return 0;
            return Math.max(0, Math.min(100, (int) Math.round(pagesComplete * 100.0 / expectedPages)));
        }
    }

    public static final class CoverageRange {
        public final String topicPath;
        public final String topicTitle;
        public final int pageFrom;
        public final int pageTo;
        public final int pages;
        public final int units;

        CoverageRange(String topicPath, String topicTitle, int pageFrom, int pageTo, int pages, int units) {
            this.topicPath = safe(topicPath);
            this.topicTitle = safe(topicTitle);
            this.pageFrom = pageFrom;
            this.pageTo = pageTo;
            this.pages = pages;
            this.units = units;
        }

        public String pageLabel() {
            return pageFrom == pageTo ? String.valueOf(pageFrom) : pageFrom + "–" + pageTo;
        }
    }

    public static final class CoPresenceSummary {
        public final String leftTitle;
        public final String rightTitle;
        public final int occurrences;
        public final int pageFrom;
        public final int pageTo;

        CoPresenceSummary(String leftTitle, String rightTitle, int occurrences, int pageFrom, int pageTo) {
            this.leftTitle = safe(leftTitle);
            this.rightTitle = safe(rightTitle);
            this.occurrences = occurrences;
            this.pageFrom = pageFrom;
            this.pageTo = pageTo;
        }
    }

    public static final class IntersectionSummary {
        public final String leftTitle;
        public final String rightTitle;
        public final String rule;
        public final String relation;
        public final int occurrences;
        public final int pageFrom;
        public final int pageTo;

        IntersectionSummary(
                String leftTitle,
                String rightTitle,
                String rule,
                String relation,
                int occurrences,
                int pageFrom,
                int pageTo
        ) {
            this.leftTitle = safe(leftTitle);
            this.rightTitle = safe(rightTitle);
            this.rule = safe(rule);
            this.relation = safe(relation);
            this.occurrences = occurrences;
            this.pageFrom = pageFrom;
            this.pageTo = pageTo;
        }
    }

    public static final class MatrixRow {
        public final String topicPath;
        public final String topicTitle;
        public final Map<String, Integer> countsBySource;

        MatrixRow(String topicPath, String topicTitle, Map<String, Integer> countsBySource) {
            this.topicPath = safe(topicPath);
            this.topicTitle = safe(topicTitle);
            this.countsBySource = Collections.unmodifiableMap(new LinkedHashMap<>(countsBySource));
        }
    }

    public static Stats stats(IndexCoreDatabase db, String sourceId) {
        if (db == null || safe(sourceId).isEmpty()) {
            return new Stats(0,0,0,0,0,0,0,0,0,0,0,Collections.emptyList());
        }
        IndexCoreDatabase.SourceScope scope = db.sourceScope(sourceId);

        int pagesSeen = scalar(db, "SELECT COUNT(*) FROM idx_page_coverage WHERE source_id=?",
                new String[]{sourceId});
        int pagesComplete = scalar(db,
                "SELECT COUNT(*) FROM idx_page_coverage WHERE source_id=? AND is_complete=1",
                new String[]{sourceId});
        int paragraphs = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND unit_kind='PARAGRAPH'",
                new String[]{sourceId});
        int clauses = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND unit_kind='CLAUSE'",
                new String[]{sourceId});
        int mapped = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND status='MAPPED'",
                new String[]{sourceId});
        int noIndex = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND status='NO_INDEX'",
                new String[]{sourceId});
        int ambiguous = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND status='AMBIGUOUS'",
                new String[]{sourceId});
        int unparsed = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units WHERE source_id=? AND status='UNPARSED'",
                new String[]{sourceId});
        int intersections = scalar(db,
                "SELECT COUNT(*) FROM idx_intersections x JOIN idx_semantic_units u ON u.unit_id=x.unit_id WHERE u.source_id=?",
                new String[]{sourceId});
        int topics = scalar(db,
                "SELECT COUNT(DISTINCT t.topic_path) FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id WHERE u.source_id=?",
                new String[]{sourceId});

        List<Integer> missing = missingPages(db, sourceId, scope);
        return new Stats(
                scope.expectedPages(), pagesSeen, pagesComplete, paragraphs, clauses,
                mapped, noIndex, ambiguous, unparsed, intersections, topics, missing
        );
    }

    public static List<CoverageRange> coverageRanges(IndexCoreDatabase db, String sourceId) {
        if (db == null || safe(sourceId).isEmpty()) return Collections.emptyList();

        class Point {
            final String path, title;
            final int page;
            final int units;
            Point(String path, String title, int page, int units) {
                this.path = path; this.title = title; this.page = page; this.units = units;
            }
        }

        List<Point> points = new ArrayList<>();
        String sql = "SELECT t.topic_path,t.topic_title,CAST(u.page AS INTEGER),COUNT(DISTINCT u.unit_id) "
                + "FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "WHERE u.source_id=? AND u.unit_kind='PARAGRAPH' AND u.page GLOB '[0-9]*' "
                + "GROUP BY t.topic_path,t.topic_title,u.page "
                + "ORDER BY t.topic_path COLLATE NOCASE,CAST(u.page AS INTEGER)";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId})) {
            while (c.moveToNext()) {
                points.add(new Point(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)));
            }
        }

        List<CoverageRange> out = new ArrayList<>();
        String path = null, title = "";
        int start = -1, last = -1, pages = 0, units = 0;
        for (Point point : points) {
            boolean sameTopic = path != null && path.equals(point.path);
            boolean contiguous = sameTopic && point.page <= last + 1;
            if (!sameTopic || !contiguous) {
                if (path != null) out.add(new CoverageRange(path, title, start, last, pages, units));
                path = point.path; title = point.title; start = point.page; last = point.page; pages = 1; units = point.units;
            } else {
                if (point.page != last) pages++;
                last = Math.max(last, point.page);
                units += point.units;
            }
        }
        if (path != null) out.add(new CoverageRange(path, title, start, last, pages, units));
        return Collections.unmodifiableList(out);
    }

    /**
     * Topic pairs sharing the same unit without a proven deterministic crossing.
     * This is the persistent meaning of A + B, kept separate from A × B.
     */
    public static List<CoPresenceSummary> coPresence(IndexCoreDatabase db, String sourceId, int limit) {
        if (db == null || safe(sourceId).isEmpty()) return Collections.emptyList();
        String sql = "SELECT a.topic_title,b.topic_title,COUNT(DISTINCT a.unit_id),"
                + "MIN(CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE NULL END),"
                + "MAX(CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE NULL END) "
                + "FROM idx_unit_topics a "
                + "JOIN idx_unit_topics b ON b.unit_id=a.unit_id AND a.topic_path < b.topic_path "
                + "JOIN idx_semantic_units u ON u.unit_id=a.unit_id "
                + "WHERE u.source_id=? "
                + "AND b.topic_path NOT LIKE a.topic_path || ' > %' "
                + "AND a.topic_path NOT LIKE b.topic_path || ' > %' "
                + "AND NOT EXISTS (SELECT 1 FROM idx_intersections x "
                + "WHERE x.unit_id=a.unit_id AND x.left_topic_path=a.topic_path AND x.right_topic_path=b.topic_path) "
                + "GROUP BY a.topic_path,a.topic_title,b.topic_path,b.topic_title "
                + "ORDER BY COUNT(DISTINCT a.unit_id) DESC,a.topic_title COLLATE NOCASE LIMIT "
                + Math.max(1, Math.min(1000, limit));

        List<CoPresenceSummary> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId})) {
            while (c.moveToNext()) {
                out.add(new CoPresenceSummary(
                        c.getString(0), c.getString(1), c.getInt(2),
                        c.isNull(3) ? 0 : c.getInt(3), c.isNull(4) ? 0 : c.getInt(4)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<IntersectionSummary> intersections(IndexCoreDatabase db, String sourceId, int limit) {
        if (db == null || safe(sourceId).isEmpty()) return Collections.emptyList();
        String sql = "SELECT x.left_title,x.right_title,x.rule,x.relation,COUNT(*),"
                + "MIN(CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE NULL END),"
                + "MAX(CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE NULL END) "
                + "FROM idx_intersections x JOIN idx_semantic_units u ON u.unit_id=x.unit_id "
                + "WHERE u.source_id=? GROUP BY x.left_topic_path,x.right_topic_path,x.rule,x.relation "
                + "ORDER BY COUNT(*) DESC,x.left_title COLLATE NOCASE LIMIT "
                + Math.max(1, Math.min(1000, limit));

        List<IntersectionSummary> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId})) {
            while (c.moveToNext()) {
                out.add(new IntersectionSummary(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getInt(4), c.isNull(5) ? 0 : c.getInt(5), c.isNull(6) ? 0 : c.getInt(6)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<MatrixRow> matrix(IndexCoreDatabase db, int limitTopics) {
        if (db == null) return Collections.emptyList();
        String sql = "SELECT t.topic_path,t.topic_title,u.source_id,COUNT(DISTINCT u.unit_id) "
                + "FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "WHERE u.unit_kind='PARAGRAPH' "
                + "GROUP BY t.topic_path,t.topic_title,u.source_id "
                + "ORDER BY t.topic_path COLLATE NOCASE,u.source_id";
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                String path = c.getString(0);
                titles.put(path, c.getString(1));
                values.computeIfAbsent(path, ignored -> new LinkedHashMap<>())
                        .put(c.getString(2), c.getInt(3));
            }
        }

        List<MatrixRow> out = new ArrayList<>();
        int max = Math.max(1, Math.min(1000, limitTopics));
        for (Map.Entry<String, Map<String, Integer>> entry : values.entrySet()) {
            if (out.size() >= max) break;
            out.add(new MatrixRow(entry.getKey(), titles.get(entry.getKey()), entry.getValue()));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Integer> missingPages(
            IndexCoreDatabase db,
            String sourceId,
            IndexCoreDatabase.SourceScope scope
    ) {
        if (scope == null || !scope.defined()) return Collections.emptyList();
        Set<Integer> complete = new LinkedHashSet<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT page FROM idx_page_coverage WHERE source_id=? AND is_complete=1 AND page GLOB '[0-9]*'",
                new String[]{sourceId}
        )) {
            while (c.moveToNext()) complete.add(c.getInt(0));
        }
        List<Integer> out = new ArrayList<>();
        for (int page = scope.pageFrom; page <= scope.pageTo; page++) {
            if (!complete.contains(page)) out.add(page);
        }
        return out;
    }

    private static int scalar(IndexCoreDatabase db, String sql, String[] args) {
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
