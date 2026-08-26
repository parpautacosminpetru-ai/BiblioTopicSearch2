package ro.bibliotopicsearch.app;

import android.content.Context;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live bridge for the deterministic index. It reuses already-computed semantic
 * detections, keeps OCR coordinates only transiently, and never stores camera frames.
 */
public final class LivingIndexRuntime {
    private LivingIndexRuntime() {}

    private static final Object LOCK = new Object();
    private static final Pattern PAGE_CUE = Pattern.compile("(?i)\\b(?:p\\.?|pag\\.?|pagina)\\s*(\\d{1,4})\\b");

    private static Context appContext;
    private static LivingIndexStore.State state = new LivingIndexStore.State();
    private static List<LivingIndexEngine.Candidate> latestCandidates = Collections.emptyList();
    private static List<LivingIndexTextMark> latestMarks = Collections.emptyList();
    private static long sourceId;
    private static long sessionId;
    private static String currentPage = "";
    private static long lastSaveAt;
    private static boolean dirty;

    public static void start(Context context, long session) {
        if (context == null) return;
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            state = LivingIndexStore.load(appContext);
            sessionId = session > 0 ? session : System.currentTimeMillis();
            String stableSource = IndexCoreSourceRegistry.activeSourceId(appContext);
            sourceId = IndexCoreSourceRegistry.legacyNumericId(stableSource);
            currentPage = "";
            dirty = false;
            latestCandidates = Collections.emptyList();
            latestMarks = Collections.emptyList();
            IndexCoreRuntime.start(appContext, sessionId);
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            flushLocked();
            IndexCoreRuntime.stop();
            latestMarks = Collections.emptyList();
        }
    }

    /** Full path when an ML Kit Text object is available: includes exact transient boxes. */
    public static void observe(
            Text text,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ParagraphCartography.Map cartography
    ) {
        if (text == null || detections == null || detections.isEmpty()) return;
        synchronized (LOCK) {
            if (appContext == null) return;
            String page = detectPage(text);
            if (!page.isEmpty()) currentPage = page;
            process(text, detections, graph, cartography);
        }
    }

    /** Fast collector path: no second OCR, only already available paragraph detections. */
    public static void observeDetections(List<UniversalParagraphDetector.Detection> detections) {
        if (detections == null || detections.isEmpty()) return;
        synchronized (LOCK) {
            if (appContext == null) return;
            String page = detectPageFromDetections(detections);
            if (!page.isEmpty()) currentPage = page;
            SemanticGraph graph = SemanticGraphBuilder.build(detections);
            ParagraphCartography.Map cartography = ParagraphCartography.build(detections, graph);
            process(null, detections, graph, cartography);
        }
    }

    private static void process(
            Text text,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ParagraphCartography.Map cartography
    ) {
        List<LivingIndexEngine.Candidate> first = LivingIndexAutoOrganizerEngine.detect(
                detections, graph, cartography, state
        );
        boolean sourceMode = AppPrefs.indexMode(appContext) == AppPrefs.IndexMode.SOURCE;
        boolean changed = false;

        for (LivingIndexEngine.Candidate candidate : first) {
            if (candidate == null || candidate.surface().isEmpty()) continue;
            if (!sourceMode && candidate.knownId().isEmpty()) continue;

            boolean autoValidate = candidate.validated()
                    && candidate.category() != LivingIndexStore.Category.INBOX;
            LivingIndexStore.Ref ref = new LivingIndexStore.Ref(
                    sourceId,
                    candidate.paragraphIndex(),
                    currentPage,
                    candidate.contextCode(),
                    candidate.axes(),
                    System.currentTimeMillis()
            );
            LivingIndexStore.Entry before = state.findCanonical(candidate.surface());
            if (before != null && hasRef(before, ref)) continue;

            int oldRefs = before == null ? -1 : before.refs().size();
            int oldRecurrence = before == null ? -1 : before.recurrence();
            LivingIndexStore.Entry after = state.merge(
                    candidate.surface(),
                    candidate.category(),
                    autoValidate,
                    ref
            );
            if (after != null && (before == null
                    || after.refs().size() != oldRefs
                    || after.recurrence() != oldRecurrence)) changed = true;
        }

        // v7 authoritative ledger: unlimited occurrences + stable source + outline + facets.
        IndexCoreRuntime.observeBatch(detections, first, state, currentPage);

        if (changed) {
            dirty = true;
            long now = System.currentTimeMillis();
            if (now - lastSaveAt >= 750L) flushLocked();
        }

        latestCandidates = Collections.unmodifiableList(new ArrayList<>(
                LivingIndexAutoOrganizerEngine.detect(detections, graph, cartography, state)
        ));
        if (text != null) {
            latestMarks = Collections.unmodifiableList(new ArrayList<>(
                    LivingIndexTextMarker.build(text, latestCandidates, state)
            ));
        }
    }

    private static boolean hasRef(LivingIndexStore.Entry entry, LivingIndexStore.Ref ref) {
        String key = ref.key();
        for (LivingIndexStore.Ref existing : entry.refs()) if (existing.key().equals(key)) return true;
        return false;
    }

    public static List<LivingIndexTextMark> latestMarks() {
        synchronized (LOCK) { return latestMarks; }
    }

    public static List<LivingIndexEngine.Candidate> latestCandidates() {
        synchronized (LOCK) { return latestCandidates; }
    }

    public static LivingIndexStore.State state() {
        synchronized (LOCK) { return state; }
    }

    public static LivingIndexOrganizer.Index organizedIndex() {
        synchronized (LOCK) { return LivingIndexOrganizer.build(state); }
    }

    public static String currentPage() {
        synchronized (LOCK) { return currentPage; }
    }

    /** Compatibility numeric ID. Stable across sessions for the active v7 source. */
    public static long sourceId() {
        synchronized (LOCK) { return sourceId; }
    }

    public static String sourceKey() {
        synchronized (LOCK) { return IndexCoreRuntime.sourceId(); }
    }

    public static long sessionId() {
        synchronized (LOCK) { return sessionId; }
    }

    public static boolean validate(String id, LivingIndexStore.Category category) {
        synchronized (LOCK) {
            boolean changed = state.validate(id, category);
            if (changed) {
                IndexCoreRuntime.syncEntry(state.byId(id));
                dirty = true;
                flushLocked();
            }
            return changed;
        }
    }

    public static void reload(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            state = LivingIndexStore.load(appContext);
            IndexCoreDatabase.get(appContext).migrateLegacyOnce(state);
        }
    }

    public static void flush() {
        synchronized (LOCK) { flushLocked(); }
    }

    private static void flushLocked() {
        if (!dirty || appContext == null) return;
        LivingIndexStore.save(appContext, state);
        dirty = false;
        lastSaveAt = System.currentTimeMillis();
    }

    private static String detectPage(Text text) {
        String full = text.getText() == null ? "" : text.getText();
        Matcher cue = PAGE_CUE.matcher(full);
        if (cue.find()) return cue.group(1);

        List<Text.TextBlock> blocks = text.getTextBlocks();
        if (blocks == null || blocks.isEmpty()) return "";
        for (int pass = 0; pass < 4; pass++) {
            int index;
            if (pass == 0) index = 0;
            else if (pass == 1) index = blocks.size() - 1;
            else if (pass == 2) index = Math.min(1, blocks.size() - 1);
            else index = Math.max(0, blocks.size() - 2);
            String value = blocks.get(index).getText() == null ? "" : blocks.get(index).getText().trim();
            if (value.matches("\\d{1,4}")) return value;
        }
        return "";
    }

    private static String detectPageFromDetections(List<UniversalParagraphDetector.Detection> detections) {
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            Matcher cue = PAGE_CUE.matcher(detection.paragraph());
            if (cue.find()) return cue.group(1);
        }
        if (!detections.isEmpty()) {
            String first = detections.get(0).paragraph().trim();
            String last = detections.get(detections.size() - 1).paragraph().trim();
            if (first.matches("\\d{1,4}")) return first;
            if (last.matches("\\d{1,4}")) return last;
        }
        return "";
    }
}