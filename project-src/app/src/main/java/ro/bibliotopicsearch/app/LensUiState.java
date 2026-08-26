package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent semantic zoom; independent from physical camera pinch zoom. */
public final class LensUiState {
    private LensUiState() {}

    private static final String PREFS = "lens_v9";
    private static final String KEY_LEVEL = "semantic_zoom";

    public static LensDisplayPolicy.Level level(Context context) {
        int fallback = LensDisplayPolicy.Level.PARAGRAPH.ordinal();
        int value = prefs(context).getInt(KEY_LEVEL, fallback);
        LensDisplayPolicy.Level[] levels = LensDisplayPolicy.Level.values();
        value = Math.max(0, Math.min(levels.length - 1, value));
        return levels[value];
    }

    public static LensDisplayPolicy.Level closer(Context context) {
        LensDisplayPolicy.Level value = level(context).closer();
        set(context, value);
        return value;
    }

    public static LensDisplayPolicy.Level farther(Context context) {
        LensDisplayPolicy.Level value = level(context).farther();
        set(context, value);
        return value;
    }

    public static void set(Context context, LensDisplayPolicy.Level value) {
        if (context == null || value == null) return;
        prefs(context).edit().putInt(KEY_LEVEL, value.ordinal()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
