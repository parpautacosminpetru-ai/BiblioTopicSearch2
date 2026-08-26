package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.UUID;

/** Stable source identity independent of OCR sessions. */
public final class IndexCoreSourceRegistry {
    private static final String PREFS = "index_core_v7_prefs";
    private static final String ACTIVE = "active_source_id";

    private IndexCoreSourceRegistry() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String activeSourceId(Context context) {
        String id = prefs(context).getString(ACTIVE, "");
        if (id == null || id.trim().isEmpty()) {
            id = newSource(context, "", "", "", "", "");
        }
        IndexCoreDatabase.get(context).ensureSource(id);
        return id;
    }

    public static void setActiveSource(Context context, String sourceId) {
        if (context == null || sourceId == null || sourceId.trim().isEmpty()) return;
        IndexCoreDatabase.get(context).ensureSource(sourceId.trim());
        prefs(context).edit().putString(ACTIVE, sourceId.trim()).apply();
    }

    public static String newSource(
            Context context,
            String title,
            String author,
            String edition,
            String isbn,
            String locator
    ) {
        String id = "src-" + UUID.randomUUID();
        IndexCoreDatabase db = IndexCoreDatabase.get(context);
        db.ensureSource(id);
        db.updateSource(id, title, author, edition, isbn, locator);
        prefs(context).edit().putString(ACTIVE, id).apply();
        return id;
    }

    public static void updateActive(
            Context context,
            String title,
            String author,
            String edition,
            String isbn,
            String locator
    ) {
        String id = activeSourceId(context);
        IndexCoreDatabase.get(context).updateSource(id, title, author, edition, isbn, locator);
        // Compatibility metadata remains available to the existing workspace UI.
        ResearchWorkspaceStore.setSource(context, legacyNumericId(id), title, author,
                joinLocator(edition, isbn, locator));
    }

    public static IndexCoreDatabase.SourceRecord activeSource(Context context) {
        return IndexCoreDatabase.get(context).source(activeSourceId(context));
    }

    public static List<IndexCoreDatabase.SourceRecord> sources(Context context) {
        return IndexCoreDatabase.get(context).listSources();
    }

    /** Stable bridge for v5/v6 APIs that still expose numeric source IDs. */
    public static long legacyNumericId(String sourceId) {
        String value = sourceId == null ? "" : sourceId;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        if (hash == Long.MIN_VALUE) return Long.MAX_VALUE;
        return Math.abs(hash);
    }

    private static String joinLocator(String edition, String isbn, String locator) {
        StringBuilder out = new StringBuilder();
        if (edition != null && !edition.trim().isEmpty()) out.append(edition.trim());
        if (isbn != null && !isbn.trim().isEmpty()) {
            if (out.length() > 0) out.append(" • ");
            out.append("ISBN ").append(isbn.trim());
        }
        if (locator != null && !locator.trim().isEmpty()) {
            if (out.length() > 0) out.append(" • ");
            out.append(locator.trim());
        }
        return out.toString();
    }
}