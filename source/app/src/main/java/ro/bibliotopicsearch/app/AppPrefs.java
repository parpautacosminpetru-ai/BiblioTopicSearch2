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
