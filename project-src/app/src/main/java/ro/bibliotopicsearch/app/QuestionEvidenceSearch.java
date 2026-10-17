package ro.bibliotopicsearch.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Searches the immutable OCR evidence of EVERY archived source, not just the
 * active book or the terms of the active topic map. No generated answer is made.
 * Each hit is a source clause, linked to an original PDF/JPEG page.
 */
public final class QuestionEvidenceSearch {
    private QuestionEvidenceSearch() {}

    public static final class Passage {
        public final String sourceId;
        public final String sourceTitle;
        public final String page;
        public final int blockIndex;
        public final String excerpt;
        public final boolean manuallyVerified;
        public final boolean directCandidate;
        public final double score;
        public final double lexicalCoverage;
        public final ResearchSemanticEngine.Intent intent;

        Passage(String sourceId, String sourceTitle, String page, int blockIndex,
                String excerpt, boolean manuallyVerified, boolean directCandidate,
                double score, double lexicalCoverage, ResearchSemanticEngine.Intent intent) {
            this.sourceId = sourceId;
            this.sourceTitle = sourceTitle;
            this.page = page;
            this.blockIndex = blockIndex;
            this.excerpt = excerpt;
            this.manuallyVerified = manuallyVerified;
            this.directCandidate = directCandidate;
            this.score = score;
            this.lexicalCoverage = lexicalCoverage;
            this.intent = intent;
        }
    }

    public static final class Report {
        public final List<Passage> passages;
        public final int sourcesSearched;
        public final int blocksSearched;
        public final int directCandidates;
        public final ResearchSemanticEngine.Intent questionType;
        public final boolean queryUsable;

        Report(List<Passage> passages, int sourcesSearched, int blocksSearched,
               int directCandidates, ResearchSemanticEngine.Intent questionType,
               boolean queryUsable) {
            this.passages = Collections.unmodifiableList(new ArrayList<>(passages));
            this.sourcesSearched = sourcesSearched;
            this.blocksSearched = blocksSearched;
            this.directCandidates = directCandidates;
            this.questionType = questionType;
            this.queryUsable = queryUsable;
        }
    }

    private static final String PROOF =
            "CASE WHEN COALESCE(c.is_complete,0)=1 AND COALESCE(p.ocr_digest,'')<>'' "
                    + "AND p.ocr_digest=p.indexed_digest "
                    + "AND p.ocr_digest=p.verified_digest THEN 1 ELSE 0 END";

    private static final String JOINS =
            " JOIN idx_original_pages o ON o.source_id=e.source_id AND o.page=e.page "
                    + "JOIN idx_sources s ON s.source_id=e.source_id "
                    + "LEFT JOIN idx_page_processing p ON p.source_id=e.source_id AND p.page=e.page "
                    + "LEFT JOIN idx_page_coverage c ON c.source_id=e.source_id AND c.page=e.page ";

    public static Report search(Context context, String question) {
        // Deliberately do not expand an explicit question with the active map:
        // unrelated configured topics must never silently widen its scope.
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(question, null);
        if (!profile.enabled()) {
            return new Report(Collections.emptyList(), 0, 0, 0, profile.intent(), false);
        }

        SQLiteDatabase db = IndexCoreDatabase.get(context).getReadableDatabase();
        List<Passage> found = new ArrayList<>();
        Set<String> sourceIds = new HashSet<>();
        Set<String> distinct = new HashSet<>();
        int scanned = 0;

        // OCR blocks preserve coherent paragraphs, including answers spanning
        // multiple OCR lines. Every archived source is included in this query.
        String blockSql = "SELECT e.source_id,s.title,e.page,e.block_index,e.raw_text,"
                + PROOF + " FROM idx_ocr_blocks e " + JOINS
                + " ORDER BY e.source_id,CAST(e.page AS INTEGER),e.page,e.block_index";
        try (Cursor cursor = db.rawQuery(blockSql, null)) {
            while (cursor.moveToNext()) {
                String sourceId = cursor.getString(0);
                sourceIds.add(sourceId);
                scanned++;
                appendAnswers(profile, found, distinct, sourceId, cursor.getString(1),
                        cursor.getString(2), cursor.getInt(3), cursor.getString(4),
                        cursor.getInt(5) != 0);
            }
        }

        // Migration fallback: version 6 can have spatial OCR lines but no
        // immutable paragraph blocks. Read each legacy page in line order.
        String legacySql = "SELECT e.source_id,s.title,e.page,e.line_index,e.raw_text,"
                + PROOF + " FROM idx_original_ocr_lines e " + JOINS
                + " WHERE NOT EXISTS (SELECT 1 FROM idx_ocr_blocks b "
                + "WHERE b.source_id=e.source_id AND b.page=e.page) "
                + "ORDER BY e.source_id,CAST(e.page AS INTEGER),e.page,e.line_index";
        try (Cursor cursor = db.rawQuery(legacySql, null)) {
            String previousSource = null;
            String previousPage = null;
            String previousTitle = "";
            boolean previousVerified = false;
            StringBuilder text = new StringBuilder();
            while (cursor.moveToNext()) {
                String source = cursor.getString(0);
                String page = cursor.getString(2);
                if (previousSource != null
                        && (!previousSource.equals(source) || !previousPage.equals(page))) {
                    appendAnswers(profile, found, distinct, previousSource, previousTitle,
                            previousPage, -1, text.toString(), previousVerified);
                    scanned++;
                    text.setLength(0);
                }
                sourceIds.add(source);
                previousSource = source;
                previousTitle = cursor.getString(1);
                previousPage = page;
                previousVerified = cursor.getInt(5) != 0;
                if (text.length() > 0) text.append('\n');
                text.append(cursor.getString(4));
            }
            if (previousSource != null) {
                appendAnswers(profile, found, distinct, previousSource, previousTitle,
                        previousPage, -1, text.toString(), previousVerified);
                scanned++;
            }
        }

        found.sort(Comparator
                .comparingInt((Passage p) -> p.directCandidate ? 0 : 1)
                .thenComparing(Comparator.comparingDouble((Passage p) -> p.score).reversed())
                .thenComparing(p -> p.sourceTitle.toLowerCase(Locale.ROOT))
                .thenComparing(p -> p.page)
                .thenComparingInt(p -> p.blockIndex));
        int direct = 0;
        for (Passage passage : found) if (passage.directCandidate) direct++;
        return new Report(found, sourceIds.size(), scanned, direct, profile.intent(), true);
    }

    private static void appendAnswers(
            ResearchSemanticEngine.Profile profile, List<Passage> output,
            Set<String> distinct, String sourceId, String sourceTitle, String page,
            int block, String raw, boolean verified) {
        if (raw == null || raw.trim().isEmpty()) return;
        List<UniversalParagraphDetector.Detection> detections = new ArrayList<>();
        for (String paragraph : UniversalParagraphDetector.splitParagraphs(raw)) {
            detections.add(UniversalParagraphDetector.detect(paragraph, detections.size()));
        }
        if (detections.isEmpty()) return;
        List<ResearchSemanticEngine.Answer> answers = ResearchSemanticEngine.findAll(
                profile, detections, null, Integer.MAX_VALUE);
        for (ResearchSemanticEngine.Answer answer : answers) {
            String segment = answer.segment();
            if (segment.isEmpty()) continue;
            // Exact source text, not a generated answer or a topic-only summary.
            String identity = sourceId + "\u0000" + page + "\u0000" + block
                    + "\u0000" + answer.paragraphIndex() + "\u0000" + segment;
            if (!distinct.add(identity)) continue;
            boolean direct = isDirectCandidate(profile, answer);
            output.add(new Passage(sourceId, sourceTitle == null || sourceTitle.trim().isEmpty()
                    ? "Sursă fără titlu" : sourceTitle, page, block, segment,
                    verified, direct, answer.score(), answer.directCoverage(), answer.intent()));
        }
    }

    // A relation marker + aligned lexical evidence makes a strong CANDIDATE,
    // not a certainty that OCR captured the entire source or that a historian
    // has confirmed the interpretation.
    static boolean isDirectCandidate(
            ResearchSemanticEngine.Profile profile, ResearchSemanticEngine.Answer answer) {
        if (answer.directCoverage() < 0.66) return false;
        if (!profile.explicitQuestion()) return false;
        switch (answer.intent()) {
            case WHEN:
            case WHERE:
            case WHY:
            case HOW:
            case DEFINITION:
            case EFFECT:
            case CONDITION:
            case PURPOSE:
            case COMPARISON:
            case EVIDENCE:
            case PROBLEM:
            case SOLUTION:
                return answer.relationEvidence() >= 0.62;
            case WHO:
                return !answer.subject().isEmpty()
                        && answer.relationEvidence() >= 0.60;
            case TOPIC:
                // A literal "what" or "how many" question has no specialized
                // ResearchSemanticEngine intent. Never silently classify a
                // topical keyword search as an answered question.
                String query = " " + RomanianLanguagePack.fold(profile.rawQuery()) + " ";
                boolean quantity = query.contains(" cati ") || query.contains(" cate ")
                        || query.contains(" cat ") || query.contains(" ce numar ");
                if (quantity) {
                    return answer.segment().matches("(?s).*\\b\\d+(?:[.,]\\d+)?\\b.*");
                }
                boolean what = query.startsWith(" ce ") || query.startsWith(" care ");
                return what && answer.directCoverage() >= 0.75
                        && !answer.subject().isEmpty()
                        && (!answer.predicate().isEmpty() || !answer.object().isEmpty());
            default:
                return false;
        }
    }

    /** Highlight the line best aligned with the source clause on the facsimile. */
    public static int closestLine(Context context, Passage passage) {
        List<SourceArchiveStore.OcrLine> lines =
                SourceArchiveStore.lines(context, passage.sourceId, passage.page);
        if (lines.isEmpty()) return -1;
        Set<String> tokens = meaningfulTokens(passage.excerpt);
        int selected = -1, best = 0;
        for (SourceArchiveStore.OcrLine line : lines) {
            Set<String> lineTokens = meaningfulTokens(line.text);
            int common = 0;
            for (String token : tokens) {
                for (String lineToken : lineTokens) {
                    if (token.equals(lineToken) || RomanianMorphology.sameFamily(token, lineToken)) {
                        common++;
                        break;
                    }
                }
            }
            if (common > best) {
                best = common;
                selected = line.index;
            }
        }
        return selected;
    }

    private static Set<String> meaningfulTokens(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String folded = RomanianLanguagePack.fold(text);
        for (String word : folded.split(" ")) {
            if (word.length() >= 3 && !RomanianLanguagePack.isFunctionWord(word)) {
                terms.add(word);
            }
        }
        return terms;
    }
}
