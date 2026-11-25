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

    private static synchronized void ensureInitialized(Context context) {
        SharedPreferences p = prefs(context);
        boolean fresh=!p.getBoolean("initialized",false);
        if(!fresh&&p.getBoolean("historical_lexicon_v1",false))return;

        // Read and verify the complete asset before changing any stored map.
        List<MapProfile> bundled=HistoricalLexicon.load(context);
        List<String> ids=fresh?new ArrayList<>():readIds(p);
        java.util.Set<String> present=new LinkedHashSet<>(ids);
        SharedPreferences.Editor edit=p.edit();
        long now=System.currentTimeMillis();
        if(fresh){
            SharedPreferences legacy=context.getSharedPreferences(LEGACY_MAP_FILE,Context.MODE_PRIVATE);
            String legacyText=legacy.getString("map_text","");
            if(legacyText==null)legacyText="";
            // Keep any actual legacy map. A fresh install has only the supplied maps.
            if(!legacyText.isEmpty()||legacy.contains("map_name")){
                ids.add(DEFAULT_ID);present.add(DEFAULT_ID);
                edit.putString(key(DEFAULT_ID,"name"),safe(legacy.getString("map_name","Hartă de cercetare"),"Hartă de cercetare"))
                    .putString(key(DEFAULT_ID,"folder"),"General").putString(key(DEFAULT_ID,"text"),legacyText)
                    .putLong(key(DEFAULT_ID,"created"),now);
            }
        }
        for(MapProfile profile:bundled){
            // Stable IDs, but never overwrite an existing edited map or its colour.
            if(!present.add(profile.id))continue;
            ids.add(profile.id);
            edit.putString(key(profile.id,"name"),profile.name)
                .putString(key(profile.id,"folder"),profile.folder)
                .putString(key(profile.id,"text"),profile.rawText)
                .putLong(key(profile.id,"created"),now)
                .putInt(key(profile.id,"color"),profile.color);
        }
        if(fresh)edit.putString("active_id",ids.get(0));
        // Keep explicit existing OCR selections; absent selection still means all maps.
        // One editor transaction publishes the maps AND the one-time import marker.
        edit.putString("ids",joinIds(ids)).putBoolean("initialized",true)
            .putBoolean("historical_lexicon_v1",true).apply();
    }

    public static List<MapProfile> list(Context context) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        List<MapProfile> out = new ArrayList<>();
        for (String id : readIds(p)) {
            out.add(read(context, p, id));
        }
        out.sort(Comparator
                .comparing((MapProfile value) -> value.folder.toLowerCase(Locale.ROOT))
                .thenComparing(value -> value.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    // An absent selection means all current and future profiles. The editing
    // profile remains independent of scanning. An empty explicit set is valid.
    public static List<MapProfile> scanProfiles(Context context, List<MapProfile> all) {
        ensureInitialized(context);
        java.util.Set<String> selected = prefs(context).getStringSet("scan_profile_ids", null);
        List<MapProfile> out = new ArrayList<>();
        for (MapProfile profile : all) {
            if (selected == null || selected.contains(profile.id)) out.add(profile);
        }
        return out;
    }

    public static void setScanProfiles(Context context, java.util.Set<String> selected) {
        ensureInitialized(context);
        List<MapProfile> all = list(context);
        java.util.Set<String> valid = new LinkedHashSet<>();
        for (MapProfile profile : all) if (selected.contains(profile.id)) valid.add(profile.id);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (valid.size() == all.size()) editor.remove("scan_profile_ids");
        else editor.putStringSet("scan_profile_ids", valid);
        editor.apply();
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
        return read(context, p, id);
    }

    public static String getActiveId(Context context) {
        return getActive(context).id;
    }

    public static MapProfile get(Context context, String id) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        if (id == null || !readIds(p).contains(id)) return null;
        return read(context, p, id);
    }

    public static String create(Context context, String name, String folder, String rawText) {
        return create(context, name, folder, rawText, null);
    }

    public static synchronized String create(Context context, String name, String folder, String rawText, Integer chosenColor) {
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
                .putInt(key(id, "color"), chosenColor == null ? MapColorPolicy.defaultColor(id) : MapColorPolicy.opaque(chosenColor))
                .apply();
        return id;
    }

    public static String duplicate(Context context, String sourceId) {
        MapProfile source = get(context, sourceId);
        if (source == null) return null;
        return create(context, source.name + " – copie", source.folder, source.rawText, source.color);
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

    public static synchronized boolean delete(Context context, String id) {
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
                .remove(key(id, "created"))
                .remove(key(id, "color"));
        java.util.Set<String> selected = p.getStringSet("scan_profile_ids", null);
        if (selected != null) {
            java.util.Set<String> remaining = new LinkedHashSet<>(selected);
            remaining.remove(id);
            e.putStringSet("scan_profile_ids", remaining);
        }
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

    /** Colour follows the immutable ID, not the map name. Saving never rewrites other fields. */
    public static synchronized void setMapColor(Context context, String id, int color) {
        ensureInitialized(context);
        SharedPreferences p = prefs(context);
        if (id != null && readIds(p).contains(id)) {
            p.edit().putInt(key(id, "color"), MapColorPolicy.opaque(color)).apply();
        }
    }

    public static int getMapColor(Context context, String id) {
        MapProfile profile = get(context, id);
        return profile == null ? MapColorPolicy.defaultColor(id) : profile.color;
    }

    private static synchronized MapProfile read(Context context, SharedPreferences p, String id) {
        String raw = p.getString(key(id, "text"), "");
        String colorKey = key(id, "color");
        if (!p.contains(colorKey)) {
            // One-time non-destructive migration from pre-1.1.4 node colours.
            int inherited = TopicMapStore.inheritMapColor(context, id, raw);
            p.edit().putInt(colorKey, inherited).apply();
        }
        return new MapProfile(
                id,
                safe(p.getString(key(id, "name"), "Hartă de cercetare"), "Hartă de cercetare"),
                normalizeFolder(p.getString(key(id, "folder"), "General")),
                raw,
                p.getLong(key(id, "created"), 0L),
                p.getInt(colorKey, MapColorPolicy.defaultColor(id))
        );
    }

    private static List<String> readIds(SharedPreferences p) {
        String raw = p.getString("ids", DEFAULT_ID);
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen=new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!id.isEmpty() && seen.add(id)) out.add(id);
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
