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
        public final int unresolvedUnits;
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
                int unresolvedUnits,
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
            this.unresolvedUnits = Math.max(0, unresolvedUnits);
            this.intersections = Math.max(0, intersections);
            this.distinctTopics = Math.max(0, distinctTopics);
            this.missingPages = Collections.unmodifiableList(new ArrayList<>(missingPages));
        }

        public boolean scopeDefined() { return expectedPages > 0; }
        public boolean exhaustive() {
            return scopeDefined() && pagesComplete >= expectedPages && missingPages.isEmpty()
                    && unresolvedUnits == 0;
        }
        public int coveragePercent() {
            if (expectedPages <= 0) return 0;
            return Math.max(0, Math.min(100, (int) Math.round(pagesComplete * 100.0 / expectedPages)));
        }
    }

    public static final class ReviewItem {
        public final String unitId;
        public final String page;
        public final int paragraphIndex;
        public final String unitKind;
        public final String status;
        public final String function;
        public final String relation;
        public final String outlineTitle;
        public final String evidence;

        ReviewItem(
                String unitId,
                String page,
                int paragraphIndex,
                String unitKind,
                String status,
                String function,
                String relation,
                String outlineTitle,
                String evidence
        ) {
            this.unitId = safe(unitId);
            this.page = safe(page);
            this.paragraphIndex = paragraphIndex;
            this.unitKind = safe(unitKind);
            this.status = safe(status);
            this.function = safe(function);
            this.relation = safe(relation);
            this.outlineTitle = safe(outlineTitle);
            this.evidence = safe(evidence);
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

    public static final class TopicSummary {
        public final String topicPath;
        public final String topicTitle;
        public final int units;
        public final int pages;

        TopicSummary(String topicPath, String topicTitle, int units, int pages) {
            this.topicPath = safe(topicPath);
            this.topicTitle = safe(topicTitle);
            this.units = units;
            this.pages = pages;
        }
    }

    public static final class ExtractionItem {
        public final String unitId;
        public final String page;
        public final int paragraphIndex;
        public final int sentenceIndex;
        public final int clauseIndex;
        public final String unitKind;
        public final String outlineTitle;
        public final String function;
        public final String relation;
        public final String surface;
        public final String matchKind;
        public final String crossings;

        ExtractionItem(
                String unitId,
                String page,
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String unitKind,
                String outlineTitle,
                String function,
                String relation,
                String surface,
                String matchKind,
                String crossings
        ) {
            this.unitId = safe(unitId);
            this.page = safe(page);
            this.paragraphIndex = paragraphIndex;
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.unitKind = safe(unitKind);
            this.outlineTitle = safe(outlineTitle);
            this.function = safe(function);
            this.relation = safe(relation);
            this.surface = safe(surface);
            this.matchKind = safe(matchKind);
            this.crossings = safe(crossings);
        }
    }

    public static final class CorpusExtractionItem {
        public final String unitId;
        public final String sourceId;
        public final String sourceTitle;
        public final String page;
        public final int paragraphIndex;
        public final int sentenceIndex;
        public final int clauseIndex;
        public final String outlineTitle;
        public final String function;
        public final String relation;
        public final String surface;
        public final String crossings;

        CorpusExtractionItem(
                String unitId,
                String sourceId,
                String sourceTitle,
                String page,
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String outlineTitle,
                String function,
                String relation,
                String surface,
                String crossings
        ) {
            this.unitId = safe(unitId);
            this.sourceId = safe(sourceId);
            this.sourceTitle = safe(sourceTitle);
            this.page = safe(page);
            this.paragraphIndex = paragraphIndex;
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.outlineTitle = safe(outlineTitle);
            this.function = safe(function);
            this.relation = safe(relation);
            this.surface = safe(surface);
            this.crossings = safe(crossings);
        }
    }

    public static final class FacetSummary {
        public final String dimension;
        public final String value;
        public final int units;
        public final int pages;

        FacetSummary(String dimension, String value, int units, int pages) {
            this.dimension = safe(dimension);
            this.value = safe(value);
            this.units = Math.max(0, units);
            this.pages = Math.max(0, pages);
        }
    }

    public static final class FacetLocation {
        public final String page;
        public final int paragraphIndex;
        public final int sentenceIndex;
        public final int clauseIndex;
        public final String outlineTitle;
        public final String function;
        public final String relation;
        public final String topics;

        FacetLocation(
                String page,
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String outlineTitle,
                String function,
                String relation,
                String topics
        ) {
            this.page = safe(page);
            this.paragraphIndex = paragraphIndex;
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.outlineTitle = safe(outlineTitle);
            this.function = safe(function);
            this.relation = safe(relation);
            this.topics = safe(topics);
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
            return new Stats(0,0,0,0,0,0,0,0,0,0,0,0,Collections.emptyList());
        }
        IndexCoreDatabase.SourceScope scope = db.sourceScope(sourceId);

        int pagesSeen = scalar(db, "SELECT COUNT(*) FROM idx_page_coverage WHERE source_id=?",
                new String[]{sourceId});
        // Completeness is defined by the declared scope, not by unrelated pages.
        // COUNT(DISTINCT ...) also treats "7" and "007" as the same physical page.
        // A historical is_complete flag alone is NOT sufficient: it might have
        // been set by a pre-v7 OCR-only scan or without a committed semantic index.
        final String completeProofJoin = " FROM idx_page_coverage c "
                + "JOIN idx_page_processing p ON p.source_id=c.source_id AND p.page=c.page "
                + "JOIN idx_original_pages o ON o.source_id=c.source_id AND o.page=c.page "
                + "WHERE c.source_id=? AND c.is_complete=1 AND p.ocr_digest<>'' "
                + "AND p.ocr_digest=p.indexed_digest "
                + "AND p.ocr_digest=p.verified_digest ";
        int pagesComplete = scope.defined()
                ? scalar(db, "SELECT COUNT(DISTINCT CAST(c.page AS INTEGER))" + completeProofJoin
                        + "AND c.page<>'' AND c.page NOT GLOB '*[^0-9]*' "
                        + "AND CAST(c.page AS INTEGER) BETWEEN ? AND ?",
                        new String[]{sourceId, String.valueOf(scope.pageFrom), String.valueOf(scope.pageTo)})
                : scalar(db, "SELECT COUNT(*)" + completeProofJoin, new String[]{sourceId});
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
        int unresolved = scalar(db,
                "SELECT COUNT(*) FROM idx_semantic_units u "
                        + "WHERE u.source_id=? AND u.status IN ('AMBIGUOUS','UNPARSED') "
                        + "AND NOT EXISTS (SELECT 1 FROM idx_unit_reviews r WHERE r.unit_id=u.unit_id)",
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
                mapped, noIndex, ambiguous, unparsed, unresolved, intersections, topics, missing
        );
    }

    public static List<ReviewItem> reviewQueue(IndexCoreDatabase db, String sourceId, int limit) {
        if (db == null || safe(sourceId).isEmpty()) return Collections.emptyList();
        String sql = "SELECT u.unit_id,u.page,u.paragraph_index,u.unit_kind,u.status,u.function,u.relation,"
                + "COALESCE(o.title,''),COALESCE(GROUP_CONCAT(t.topic_title || ': ' || t.surface,' | '),'') "
                + "FROM idx_semantic_units u "
                + "LEFT JOIN idx_outlines o ON o.outline_id=u.outline_id "
                + "LEFT JOIN idx_unit_topics t ON t.unit_id=u.unit_id "
                + "LEFT JOIN idx_unit_reviews r ON r.unit_id=u.unit_id "
                + "WHERE u.source_id=? AND u.status IN ('AMBIGUOUS','UNPARSED') AND r.unit_id IS NULL "
                + "GROUP BY u.unit_id,u.page,u.paragraph_index,u.unit_kind,u.status,u.function,u.relation,o.title "
                + "ORDER BY CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE 2147483647 END,"
                + "u.page,u.paragraph_index,u.unit_kind LIMIT "
                + Math.max(1, Math.min(2000, limit));
        List<ReviewItem> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId})) {
            while (c.moveToNext()) {
                out.add(new ReviewItem(
                        c.getString(0), c.getString(1), c.getInt(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8)
                ));
            }
        }
        return Collections.unmodifiableList(out);
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
        String sql = "SELECT t.topic_path,t.topic_title,CAST(u.page AS INTEGER),"
                + "COUNT(DISTINCT u.unit_id) "
                + "FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "WHERE u.source_id=? AND u.page GLOB '[0-9]*' "
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
                + "WHERE x.unit_id=a.unit_id AND ("
                + "(x.left_topic_path=a.topic_path AND x.right_topic_path=b.topic_path) OR "
                + "(x.left_topic_path=b.topic_path AND x.right_topic_path=a.topic_path))) "
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

    public static List<TopicSummary> topicsForSource(IndexCoreDatabase db, String sourceId, int limit) {
        if (db == null || safe(sourceId).isEmpty()) return Collections.emptyList();
        String sql = "SELECT t.topic_path,MAX(t.topic_title),"
                + "COUNT(DISTINCT u.unit_id),"
                + "COUNT(DISTINCT CASE WHEN u.page GLOB '[0-9]*' THEN u.page ELSE NULL END) "
                + "FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "WHERE u.source_id=? GROUP BY t.topic_path "
                + "ORDER BY COUNT(DISTINCT t.unit_id) DESC,MAX(t.topic_title) COLLATE NOCASE LIMIT "
                + Math.max(1, Math.min(1000, limit));
        List<TopicSummary> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId})) {
            while (c.moveToNext()) {
                out.add(new TopicSummary(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<ExtractionItem> extractionQueue(
            IndexCoreDatabase db,
            String sourceId,
            String topicPath,
            int limit
    ) {
        if (db == null || safe(sourceId).isEmpty() || safe(topicPath).isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT u.unit_id,u.page,u.paragraph_index,u.sentence_index,u.clause_index,u.unit_kind,"
                + "COALESCE(o.title,''),u.function,u.relation,t.surface,t.match_kind,"
                + "COALESCE(GROUP_CONCAT(DISTINCT "
                + "CASE WHEN x.left_topic_path=t.topic_path THEN x.right_title || ' [' || x.rule || ']' "
                + "WHEN x.right_topic_path=t.topic_path THEN x.left_title || ' [' || x.rule || ']' ELSE NULL END),'') "
                + "FROM idx_unit_topics t "
                + "JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "LEFT JOIN idx_outlines o ON o.outline_id=u.outline_id "
                + "LEFT JOIN idx_intersections x ON x.unit_id=u.unit_id "
                + "WHERE u.source_id=? AND t.topic_path=? "
                + "GROUP BY u.unit_id,u.page,u.paragraph_index,u.sentence_index,u.clause_index,u.unit_kind,"
                + "o.title,u.function,u.relation,t.surface,t.match_kind "
                + "ORDER BY CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE 2147483647 END,"
                + "u.page,u.paragraph_index,u.sentence_index,u.clause_index LIMIT "
                + Math.max(1, Math.min(5000, limit));
        List<ExtractionItem> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId, topicPath})) {
            while (c.moveToNext()) {
                out.add(new ExtractionItem(
                        c.getString(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getString(5),
                        c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                        c.getString(10), c.getString(11)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<CorpusExtractionItem> corpusExtractionQueue(
            IndexCoreDatabase db,
            String topicPath,
            int limit
    ) {
        if (db == null || safe(topicPath).isEmpty()) return Collections.emptyList();
        String sql = "SELECT u.unit_id,u.source_id,COALESCE(s.title,''),u.page,u.paragraph_index,u.sentence_index,u.clause_index,"
                + "COALESCE(o.title,''),u.function,u.relation,t.surface,"
                + "COALESCE(GROUP_CONCAT(DISTINCT "
                + "CASE WHEN x.left_topic_path=t.topic_path THEN x.right_title || ' [' || x.rule || ']' "
                + "WHEN x.right_topic_path=t.topic_path THEN x.left_title || ' [' || x.rule || ']' ELSE NULL END),'') "
                + "FROM idx_unit_topics t "
                + "JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
                + "JOIN idx_sources s ON s.source_id=u.source_id "
                + "LEFT JOIN idx_outlines o ON o.outline_id=u.outline_id "
                + "LEFT JOIN idx_intersections x ON x.unit_id=u.unit_id "
                + "WHERE t.topic_path=? "
                + "GROUP BY u.unit_id,u.source_id,s.title,u.page,u.paragraph_index,u.sentence_index,u.clause_index,"
                + "o.title,u.function,u.relation,t.surface "
                + "ORDER BY s.title COLLATE NOCASE,"
                + "CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE 2147483647 END,"
                + "u.page,u.paragraph_index,u.sentence_index,u.clause_index LIMIT "
                + Math.max(1, Math.min(10000, limit));
        List<CorpusExtractionItem> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{topicPath})) {
            while (c.moveToNext()) {
                out.add(new CorpusExtractionItem(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4),
                        c.getInt(5), c.getInt(6), c.getString(7), c.getString(8),
                        c.getString(9), c.getString(10), c.getString(11)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<FacetSummary> facetSummary(
            IndexCoreDatabase db,
            String sourceId,
            String dimension,
            int limit
    ) {
        if (db == null || safe(sourceId).isEmpty() || safe(dimension).isEmpty()) {
            return Collections.emptyList();
        }
        String dim = safe(dimension).toUpperCase(java.util.Locale.ROOT);
        String order = "YEAR".equals(dim)
                ? "CASE WHEN f.value GLOB '[0-9]*' THEN CAST(f.value AS INTEGER) ELSE 2147483647 END,f.value"
                : "f.value COLLATE NOCASE";
        String sql = "SELECT f.dimension,f.value,COUNT(DISTINCT f.unit_id),"
                + "COUNT(DISTINCT CASE WHEN u.page GLOB '[0-9]*' THEN u.page ELSE NULL END) "
                + "FROM idx_unit_facets f JOIN idx_semantic_units u ON u.unit_id=f.unit_id "
                + "WHERE u.source_id=? AND f.dimension=? "
                + "GROUP BY f.dimension,f.value,f.value_norm ORDER BY " + order + " LIMIT "
                + Math.max(1, Math.min(3000, limit));
        List<FacetSummary> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{sourceId, dim})) {
            while (c.moveToNext()) {
                out.add(new FacetSummary(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<FacetLocation> facetLocations(
            IndexCoreDatabase db,
            String sourceId,
            String dimension,
            String value,
            int limit
    ) {
        if (db == null || safe(sourceId).isEmpty() || safe(dimension).isEmpty() || safe(value).isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT u.page,u.paragraph_index,u.sentence_index,u.clause_index,"
                + "COALESCE(o.title,''),u.function,u.relation,"
                + "COALESCE(GROUP_CONCAT(DISTINCT t.topic_title),'') "
                + "FROM idx_unit_facets f "
                + "JOIN idx_semantic_units u ON u.unit_id=f.unit_id "
                + "LEFT JOIN idx_outlines o ON o.outline_id=u.outline_id "
                + "LEFT JOIN idx_unit_topics t ON t.unit_id=u.unit_id "
                + "WHERE u.source_id=? AND f.dimension=? AND f.value_norm=? "
                + "GROUP BY u.unit_id,u.page,u.paragraph_index,u.sentence_index,u.clause_index,"
                + "o.title,u.function,u.relation "
                + "ORDER BY CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE 2147483647 END,"
                + "u.page,u.paragraph_index,u.sentence_index,u.clause_index LIMIT "
                + Math.max(1, Math.min(5000, limit));
        List<FacetLocation> out = new ArrayList<>();
        String normalized = foldForFacet(value);
        try (Cursor c = db.getReadableDatabase().rawQuery(
                sql,
                new String[]{sourceId, safe(dimension).toUpperCase(java.util.Locale.ROOT), normalized}
        )) {
            while (c.moveToNext()) {
                out.add(new FacetLocation(
                        c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<MatrixRow> matrix(IndexCoreDatabase db, int limitTopics) {
        if (db == null) return Collections.emptyList();
        String sql = "SELECT t.topic_path,t.topic_title,u.source_id,"
                + "COUNT(DISTINCT u.unit_id) "
                + "FROM idx_unit_topics t JOIN idx_semantic_units u ON u.unit_id=t.unit_id "
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
                "SELECT c.page FROM idx_page_coverage c "
                        + "JOIN idx_page_processing p ON p.source_id=c.source_id AND p.page=c.page "
                        + "JOIN idx_original_pages o ON o.source_id=c.source_id AND o.page=c.page "
                        + "WHERE c.source_id=? AND c.is_complete=1 "
                        + "AND p.ocr_digest<>'' AND p.ocr_digest=p.indexed_digest "
                        + "AND p.ocr_digest=p.verified_digest "
                        + "AND c.page<>'' AND c.page NOT GLOB '*[^0-9]*' "
                        + "AND CAST(c.page AS INTEGER) BETWEEN ? AND ?",
                new String[]{sourceId, String.valueOf(scope.pageFrom), String.valueOf(scope.pageTo)}
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

    private static String foldForFacet(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
