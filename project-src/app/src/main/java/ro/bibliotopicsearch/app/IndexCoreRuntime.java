package ro.bibliotopicsearch.app;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
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

    /** Compatibility entry point: reads legacy JSON directly, never through LivingIndexRuntime locks. */
    public static void start(Context context, long session) {
        LivingIndexStore.State legacy = context == null ? null : LivingIndexStore.load(context.getApplicationContext());
        start(context, session, legacy);
    }

    public static void start(Context context, long session, LivingIndexStore.State legacyState) {
        if (context == null) return;
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            sourceId = IndexCoreSourceRegistry.activeSourceId(appContext);
            sessionId = session > 0 ? session : System.currentTimeMillis();
            IndexCoreDatabase db = IndexCoreDatabase.get(appContext);
            db.ensureSource(sourceId);
            IndexCoreLegacyMigrator.migrate(db, legacyState);
            Arrays.fill(lastOutlineByDepth, 0L);
            IndexCoreOutlineState.Restored restored = IndexCoreOutlineState.restore(db, sourceId, lastOutlineByDepth);
            currentOutlineId = restored.currentId;
            outlineOrder = restored.nextOrder;
        }
    }

    public static void stop() {
        synchronized (LOCK) { currentOutlineId = 0L; }
    }

    public static void observeBatch(
            List<UniversalParagraphDetector.Detection> detections,
            List<LivingIndexEngine.Candidate> candidates,
            LivingIndexStore.State state,
            String page
    ) {
        if (detections == null || detections.isEmpty() || candidates == null || candidates.isEmpty() || state == null) return;
        synchronized (LOCK) {
            if (appContext == null) return;
            ensureStartedLocked(appContext);
            if (sourceId.isEmpty()) return;
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

    /**
     * Explicit exhaustive capture. Unlike the sampled live sidecar, this path never
     * drops a unit from the accepted frame: every OCR paragraph is ledgered, including
     * NO_INDEX and AMBIGUOUS units.
     */
    public static HistoricalCartographyEngine.CaptureMap captureExhaustive(
            Context context,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            TopicMap thematicMap,
            String page,
            boolean markPageComplete
    ) {
        if (detections == null || detections.isEmpty()) {
            return HistoricalCartographyEngine.analyze(detections, graph, thematicMap);
        }
        synchronized (LOCK) {
            ensureStartedLocked(context);
            HistoricalCartographyEngine.CaptureMap mapped =
                    HistoricalCartographyEngine.analyze(detections, graph, thematicMap);
            if (appContext == null || sourceId.isEmpty()) return mapped;

            IndexCoreDatabase db = IndexCoreDatabase.get(appContext);
            long previousOutline = currentOutlineId;
            Map<Integer, Long> outlineByParagraph = updateOutlines(db, detections, page);
            long capturedAt = System.currentTimeMillis();

            for (HistoricalCartographyEngine.Unit unit : mapped.units()) {
                if (unit == null) continue;
                long outlineId = outlineForParagraph(
                        unit.paragraphIndex(), outlineByParagraph, previousOutline
                );
                String unitId = db.upsertSemanticUnit(
                        sourceId,
                        sessionId,
                        page,
                        unit,
                        outlineId,
                        capturedAt
                );
                if (unitId.isEmpty()) continue;
                db.replaceUnitTopics(unitId, unit.anchors());
                db.replaceUnitFacets(unitId, unit.facets());
                db.replaceUnitIntersections(unitId, unit.intersections());
            }
            db.recordPageCapture(sourceId, sessionId, page, markPageComplete);
            return mapped;
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

    private static void ensureStartedLocked(Context context) {
        if (context == null && appContext == null) return;
        Context target = context == null ? appContext : context.getApplicationContext();
        String active = IndexCoreSourceRegistry.activeSourceId(target);
        if (appContext != null && !sourceId.isEmpty() && sourceId.equals(active)) return;

        appContext = target;
        sourceId = active;
        sessionId = sessionId > 0 ? sessionId : System.currentTimeMillis();
        IndexCoreDatabase db = IndexCoreDatabase.get(appContext);
        db.ensureSource(sourceId);
        Arrays.fill(lastOutlineByDepth, 0L);
        IndexCoreOutlineState.Restored restored =
                IndexCoreOutlineState.restore(db, sourceId, lastOutlineByDepth);
        currentOutlineId = restored.currentId;
        outlineOrder = restored.nextOrder;
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