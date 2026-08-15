package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TopicLibraryStore {
    private static final String FILE = "bibliotopicsearch_library";
    private static final String LEGACY_MAP_FILE = "bibliotopicsearch_map";
    private static final String DEFAULT_ID = "default";

    private TopicLibraryStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static void ensureInitialized(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean("initialized", false)) return;

        SharedPreferences legacy = context.getSharedPreferences(LEGACY_MAP_FILE, Context.MODE_PRIVATE);
        String legacyName = safe(legacy.getString("map_name", "Hartă de cercetare"), "Hartă de cercetare");
        String legacyText = legacy.getString("map_text", "");
        if (legacyText == null) legacyText = "";

        long now = System.currentTimeMillis();
        p.edit()
                .putBoolean("initialized", true)
                .putString("ids", DEFAULT_ID)
                .putString("active_id", DEFAULT_ID)
                .putString(key(DEFAULT_ID, "name"), legacyName)
                .putString(key(DEFAULT_ID, "folder"), "General")
                .putString(key(DEFAULT_ID, "text"), legacyText)
                .putLong(key(DEFAULT_ID, "created"), now)
                .apply();
    }

    public static List<MapProfile> list(Context context) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        List<MapProfile> out = new ArrayList<>();
        for (String id : readIds(p)) {
            out.add(read(p, id));
        }
        out.sort(Comparator
                .comparing((MapProfile value) -> value.folder.toLowerCase(Locale.ROOT))
                .thenComparing(value -> value.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    public static MapProfile getActive(Context context) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        String id = p.getString("active_id", DEFAULT_ID);
        if (id == null || !readIds(p).contains(id)) {
            List<String> ids = readIds(p);
            id = ids.isEmpty() ? DEFAULT_ID : ids.get(0);
            p.edit().putString("active_id", id).apply();
        }
        return read(p, id);
    }

    public static String getActiveId(Context context) {
        return getActive(context).id;
    }

    public static MapProfile get(Context context, String id) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        if (id == null || !readIds(p).contains(id)) return null;
        return read(p, id);
    }

    public static String create(Context context, String name, String folder, String rawText) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        String id = UUID.randomUUID().toString().replace("-", "");
        List<String> ids = readIds(p);
        ids.add(id);
        p.edit()
                .putString("ids", joinIds(ids))
                .putString(key(id, "name"), safe(name, "Hartă nouă"))
                .putString(key(id, "folder"), normalizeFolder(folder))
                .putString(key(id, "text"), rawText == null ? "" : rawText)
                .putLong(key(id, "created"), System.currentTimeMillis())
                .apply();
        return id;
    }

    public static String duplicate(Context context, String sourceId) {
        MapProfile source = get(context, sourceId);
        if (source == null) return null;
        return create(context, source.name + " – copie", source.folder, source.rawText);
    }

    public static void update(Context context, String id, String name, String folder, String rawText) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        if (!readIds(p).contains(id)) return;
        p.edit()
                .putString(key(id, "name"), safe(name, "Hartă de cercetare"))
                .putString(key(id, "folder"), normalizeFolder(folder))
                .putString(key(id, "text"), rawText == null ? "" : rawText)
                .apply();
    }

    public static void updateActiveMap(Context context, String name, String rawText) {
        MapProfile active = getActive(context);
        update(context, active.id, name, active.folder, rawText);
    }

    public static void setActive(Context context, String id) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        if (id != null && readIds(p).contains(id)) {
            p.edit().putString("active_id", id).apply();
        }
    }

    public static boolean delete(Context context, String id) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        List<String> ids = readIds(p);
        if (!ids.contains(id)) return false;
        if (ids.size() <= 1) return false;

        ids.remove(id);
        SharedPreferences.Editor e = p.edit()
                .putString("ids", joinIds(ids))
                .remove(key(id, "name"))
                .remove(key(id, "folder"))
                .remove(key(id, "text"))
                .remove(key(id, "created"));
        String active = p.getString("active_id", DEFAULT_ID);
        if (id.equals(active)) e.putString("active_id", ids.get(0));
        e.apply();
        return true;
    }

    public static List<String> folders(Context context) {
        List<String> folders = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MapProfile profile : list(context)) {
            if (seen.add(profile.folder)) folders.add(profile.folder);
        }
        Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);
        return folders;
    }

    private static MapProfile read(SharedPreferences p, String id) {
        return new MapProfile(
                id,
                safe(p.getString(key(id, "name"), "Hartă de cercetare"), "Hartă de cercetare"),
                normalizeFolder(p.getString(key(id, "folder"), "General")),
                p.getString(key(id, "text"), ""),
                p.getLong(key(id, "created"), 0L)
        );
    }

    private static List<String> readIds(SharedPreferences p) {
        String raw = p.getString("ids", DEFAULT_ID);
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        }
        if (out.isEmpty()) out.add(DEFAULT_ID);
        return out;
    }

    private static String joinIds(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            if (out.length() > 0) out.append(',');
            out.append(id);
        }
        return out.toString();
    }

    private static String key(String id, String field) {
        return "profile_" + id + "_" + field;
    }

    private static String normalizeFolder(String folder) {
        String value = safe(folder, "General")
                .replace('\\', '/')
                .replaceAll("/{2,}", "/");
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.isEmpty() ? "General" : value;
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}
