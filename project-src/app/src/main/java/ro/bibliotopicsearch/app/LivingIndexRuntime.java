package ro.bibliotopicsearch.app;

import android.content.Context;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live bridge for the deterministic index. Runs on the existing semantic worker,
 * keeps only text labels/coordinates transiently, and never stores camera frames.
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
    private static String currentPage = "";
    private static long lastSaveAt;
    private static boolean dirty;

    public static void start(Context context, long sessionId) {
        if (context == null) return;
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            state = LivingIndexStore.load(appContext);
            sourceId = sessionId > 0 ? sessionId : System.currentTimeMillis();
            currentPage = "";
            dirty = false;
            latestCandidates = Collections.emptyList();
            latestMarks = Collections.emptyList();
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            flushLocked();
            latestMarks = Collections.emptyList();
        }
    }

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

            List<LivingIndexEngine.Candidate> first = LivingIndexEngine.detect(detections, graph, cartography, state);
            boolean sourceMode = AppPrefs.indexMode(appContext) == AppPrefs.IndexMode.SOURCE;
            boolean changed = false;

            for (LivingIndexEngine.Candidate candidate : first) {
                if (candidate == null || candidate.surface().isEmpty()) continue;
                // In research mode the personal index still recognizes known terms,
                // but it does not fill the unknown inbox unless the user chose SOURCE mode.
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

            if (changed) {
                dirty = true;
                long now = System.currentTimeMillis();
                if (now - lastSaveAt >= 750L) flushLocked();
            }

            // Re-detect from the updated index so newly collected entries get their stable code immediately.
            latestCandidates = Collections.unmodifiableList(new ArrayList<>(
                    LivingIndexEngine.detect(detections, graph, cartography, state)
            ));
            latestMarks = Collections.unmodifiableList(new ArrayList<>(
                    LivingIndexTextMarker.build(text, latestCandidates, state)
            ));
        }
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

    public static String currentPage() {
        synchronized (LOCK) { return currentPage; }
    }

    public static long sourceId() {
        synchronized (LOCK) { return sourceId; }
    }

    public static boolean validate(String id, LivingIndexStore.Category category) {
        synchronized (LOCK) {
            boolean changed = state.validate(id, category);
            if (changed) {
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
        // Page numbers are commonly isolated at the top/bottom. Restrict the heuristic
        // to the first/last two OCR blocks to avoid treating years in body text as pages.
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
}
