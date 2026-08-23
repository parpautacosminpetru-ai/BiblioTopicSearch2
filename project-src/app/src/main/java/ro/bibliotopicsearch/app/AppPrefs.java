package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "bibliotopicsearch_prefs";

    public enum MatchMode {
        EXACT, PREFIX, CONTAINS, FLEXIBLE
    }

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static MatchMode getMatchMode(Context context) {
        String value = prefs(context).getString("match_mode", MatchMode.PREFIX.name());
        try {
            return MatchMode.valueOf(value);
        } catch (Exception ignored) {
            return MatchMode.PREFIX;
        }
    }

    public static void setMatchMode(Context context, MatchMode mode) {
        prefs(context).edit().putString("match_mode", mode.name()).apply();
    }

    public static boolean ignoreDiacritics(Context context) {
        return prefs(context).getBoolean("ignore_diacritics", true);
    }

    public static void setIgnoreDiacritics(Context context, boolean value) {
        prefs(context).edit().putBoolean("ignore_diacritics", value).apply();
    }

    public static int compareChars(Context context) {
        return prefs(context).getInt("compare_chars", 0);
    }

    public static void setCompareChars(Context context, int value) {
        prefs(context).edit().putInt("compare_chars", Math.max(0, value)).apply();
    }

    public static int precision(Context context) {
        return prefs(context).getInt("precision", 90);
    }

    public static void setPrecision(Context context, int value) {
        prefs(context).edit().putInt("precision", Math.max(0, Math.min(100, value))).apply();
    }

    public static boolean ocrEnabled(Context context) {
        return prefs(context).getBoolean("ocr_enabled", true);
    }

    public static void setOcrEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("ocr_enabled", value).apply();
    }

    public static boolean floatingLabels(Context context) {
        return prefs(context).getBoolean("floating_labels", false);
    }

    public static void setFloatingLabels(Context context, boolean value) {
        prefs(context).edit().putBoolean("floating_labels", value).apply();
    }

    /** User-created theme/target layer. On by default for backwards compatibility. */
    public static boolean themeLayer(Context context) {
        return prefs(context).getBoolean("layer_theme", true);
    }

    public static void setThemeLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_theme", value).apply();
    }

    /** Built-in textual/discourse/syntactic helpers. Available without any user theme. */
    public static boolean textualLayer(Context context) {
        return prefs(context).getBoolean("layer_textual", false);
    }

    public static void setTextualLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_textual", value).apply();
    }

    /** Built-in semantic-function vocabulary. Available without any user theme. */
    public static boolean semanticLayer(Context context) {
        return prefs(context).getBoolean("layer_semantic", false);
    }

    public static void setSemanticLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_semantic", value).apply();
    }

    /** 0..3 preset used by the live magnifier/zoom button. */
    public static int zoomLevel(Context context) {
        return Math.max(0, Math.min(3, prefs(context).getInt("zoom_level", 0)));
    }

    public static void setZoomLevel(Context context, int value) {
        prefs(context).edit().putInt("zoom_level", Math.max(0, Math.min(3, value))).apply();
    }

    /** Compatibilitate cu versiunile anterioare. */
    public static boolean showLabels(Context context) {
        return floatingLabels(context);
    }

    public static void setShowLabels(Context context, boolean value) {
        setFloatingLabels(context, value);
    }

    public static boolean haptic(Context context) {
        return prefs(context).getBoolean("haptic", false);
    }

    public static void setHaptic(Context context, boolean value) {
        prefs(context).edit().putBoolean("haptic", value).apply();
    }

    public static boolean sound(Context context) {
        return prefs(context).getBoolean("sound", false);
    }

    public static void setSound(Context context, boolean value) {
        prefs(context).edit().putBoolean("sound", value).apply();
    }
}
