package ro.bibliotopicsearch.app;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
            IndexCoreLegacyMigrator.migrate(db, LivingIndexRuntime.state());
        }
    }

    public static void stop() {
        synchronized (LOCK) { currentOutlineId = 0L; }
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
            long previousOutline = currentOutlineId;
            Map<Integer, Long> outlineByParagraph = updateOutlines(db, detections, page);
            String batchToken = (page == null || page.trim().isEmpty()) ? batchFingerprint(candidates) : "";

            for (LivingIndexEngine.Candidate candidate : candidates) {
                if (candidate == null || candidate.surface().isEmpty()) continue;
                LivingIndexStore.Entry entry = state.findCanonical(candidate.surface());
                if (entry == null) continue;
                String coreId = IndexCoreEntryWriter.upsert(db, entry);
                if (coreId.isEmpty()) continue;
                long outlineId = outlineForParagraph(candidate.paragraphIndex(), outlineByParagraph, previousOutline);
                String dbContext = candidate.contextCode();
                if (!batchToken.isEmpty()) dbContext += "|B:" + batchToken;
                db.addOccurrence(
                        coreId,
                        sourceId,
                        sessionId,
                        page,
                        candidate.paragraphIndex(),
                        outlineId,
                        dbContext,
                        System.currentTimeMillis(),
                        candidate.axes()
                );
            }
        }
    }

    public static void syncEntry(LivingIndexStore.Entry entry) {
        synchronized (LOCK) {
            if (appContext != null && entry != null) IndexCoreEntryWriter.upsert(IndexCoreDatabase.get(appContext), entry);
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

    private static long outlineForParagraph(int paragraphIndex, Map<Integer, Long> changedAt, long previousOutline) {
        long value = previousOutline;
        int best = -1;
        for (Map.Entry<Integer, Long> entry : changedAt.entrySet()) {
            int p = entry.getKey();
            if (p <= paragraphIndex && p >= best) { best = p; value = entry.getValue(); }
        }
        return value;
    }

    /** Only a hash is persisted for unknown-page disambiguation; no body text is stored. */
    private static String batchFingerprint(List<LivingIndexEngine.Candidate> candidates) {
        StringBuilder raw = new StringBuilder();
        for (LivingIndexEngine.Candidate candidate : candidates) {
            if (candidate == null) continue;
            raw.append(candidate.paragraphIndex()).append(':')
                    .append(candidate.surface().toLowerCase(Locale.ROOT).trim()).append('|');
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(8, bytes.length); i++) out.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(raw.toString().hashCode());
        }
    }
}