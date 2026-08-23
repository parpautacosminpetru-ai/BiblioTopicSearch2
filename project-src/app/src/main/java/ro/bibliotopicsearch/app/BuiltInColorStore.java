package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists user-selected colors for built-in TEXTUAL and SEMANTIC categories. */
public final class BuiltInColorStore {
    private static final String PREF_FILE = "bibliotopicsearch_builtin_colors";
    private static final String PREFIX = "color|";

    private BuiltInColorStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void apply(Context context, TopicMap map) {
        if (context == null || map == null) return;
        SharedPreferences p = prefs(context);
        for (TopicNode node : map.nodes) {
            if (node == null || !BuiltInMaps.isBuiltInPath(node.path)) continue;
            String key = key(node.path);
            if (p.contains(key)) node.color = p.getInt(key, node.color);
        }
    }

    public static void setColor(Context context, String path, int color) {
        if (context == null || path == null || !BuiltInMaps.isBuiltInPath(path)) return;
        prefs(context).edit().putInt(key(path), color).apply();
    }

    public static void clearColor(Context context, String path) {
        if (context == null || path == null) return;
        prefs(context).edit().remove(key(path)).apply();
    }

    private static String key(String path) {
        return PREFIX + path;
    }
}
