package ro.bibliotopicsearch.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live, low-cost bridge from existing OCR/index detections into the v7 SQLite core. */
public final class IndexCoreRuntime {
    private IndexCoreRuntime() {}

    private static final Object LOCK = new Object();
    private static Context appContext;
    private static String sourceId = "";
    private static long sessionId;
    private static final long[] lastOutlineByDepth = new long[8];
    private static long currentOutlineId;
    private static int outlineOrder;

    public static void start(Context context, long session) {
        if (context == null) return;
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            sourceId = IndexCoreSourceRegistry.activeSourceId(appContext);
            sessionId = session > 0 ? session : System.currentTimeMillis();
            currentOutlineId = 0L;
            outlineOrder = 0;
            for (int i = 0; i < lastOutlineByDepth.length; i++) lastOutlineByDepth[i] = 0L;
            IndexCoreDatabase db = IndexCoreDatabase.get(appContext);
            db.ensureSource(sourceId);
            db.migrateLegacyOnce(LivingIndexRuntime.state());
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            currentOutlineId = 0L;
        }
    }

    /**
     * Persist entries/occurrences after the legacy in-memory state has already merged
     * this batch. No OCR or semantic parsing is repeated here.
     */
    public static void observeBatch(
            List<UniversalParagraphDetector.Detection> detections,
            List<LivingIndexEngine.Candidate> candidates,
            LivingIndexStore.State state,
            String page
    ) {
        if (detections == null || detections.isEmpty() || candidates == null || candidates.isEmpty() || state == null) return;
        synchronized (LOCK) {
            if (appContext == null || sourceId.isEmpty()) return;
            IndexCoreDatabase db = IndexCoreDatabase.get(appContext);
            Map<Integer, Long> outlineByParagraph = updateOutlines(db, detections, page);

            for (LivingIndexEngine.Candidate candidate : candidates) {
                if (candidate == null || candidate.surface().isEmpty()) continue;
                LivingIndexStore.Entry entry = state.findCanonical(candidate.surface());
                if (entry == null) continue; // research mode can deliberately skip unknowns
                String coreId = db.upsertEntry(entry);
                if (coreId.isEmpty()) continue;
                long outlineId = outlineForParagraph(candidate.paragraphIndex(), outlineByParagraph);
                db.addOccurrence(
                        coreId,
                        sourceId,
                        sessionId,
                        page,
                        candidate.paragraphIndex(),
                        outlineId,
                        candidate.contextCode(),
                        System.currentTimeMillis(),
                        candidate.axes()
                );
            }
        }
    }

    public static void syncEntry(LivingIndexStore.Entry entry) {
        synchronized (LOCK) {
            if (appContext != null && entry != null) IndexCoreDatabase.get(appContext).upsertEntry(entry);
        }
    }

    public static String sourceId() {
        synchronized (LOCK) { return sourceId; }
    }

    public static long legacySourceId() {
        synchronized (LOCK) { return IndexCoreSourceRegistry.legacyNumericId(sourceId); }
    }

    public static long sessionId() {
        synchronized (LOCK) { return sessionId; }
    }

    private static Map<Integer, Long> updateOutlines(
            IndexCoreDatabase db,
            List<UniversalParagraphDetector.Detection> detections,
            String page
    ) {
        List<SourceOutlineDetector.Heading> headings = SourceOutlineDetector.detect(detections);
        Map<Integer, Long> changedAt = new HashMap<>();
        for (SourceOutlineDetector.Heading heading : headings) {
            int depth = Math.max(0, Math.min(lastOutlineByDepth.length - 1, heading.depth));
            long parentId = 0L;
            for (int d = depth - 1; d >= 0; d--) {
                if (lastOutlineByDepth[d] > 0) { parentId = lastOutlineByDepth[d]; break; }
            }
            long id = db.upsertOutline(
                    sourceId,
                    parentId,
                    heading.kind,
                    heading.title,
                    page,
                    depth,
                    outlineOrder++,
                    heading.confidence
            );
            if (id <= 0) continue;
            lastOutlineByDepth[depth] = id;
            for (int d = depth + 1; d < lastOutlineByDepth.length; d++) lastOutlineByDepth[d] = 0L;
            currentOutlineId = id;
            changedAt.put(heading.paragraphIndex, id);
        }
        return changedAt;
    }

    /** Nearest heading at/before paragraph, otherwise continue the last known outline. */
    private static long outlineForParagraph(int paragraphIndex, Map<Integer, Long> changedAt) {
        long value = currentOutlineId;
        int best = -1;
        for (Map.Entry<Integer, Long> entry : changedAt.entrySet()) {
            int p = entry.getKey();
            if (p <= paragraphIndex && p >= best) { best = p; value = entry.getValue(); }
        }
        return value;
    }
}