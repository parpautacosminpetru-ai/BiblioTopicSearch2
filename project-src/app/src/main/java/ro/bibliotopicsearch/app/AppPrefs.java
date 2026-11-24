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
        String value = prefs(context).getString("literal_match_mode_v115", MatchMode.EXACT.name());
        try {
            return MatchMode.valueOf(value);
        } catch (Exception ignored) {
            return MatchMode.EXACT;
        }
    }

    public static void setMatchMode(Context context, MatchMode mode) {
        prefs(context).edit().putString("literal_match_mode_v115", mode.name()).apply();
    }

    public static boolean ignoreDiacritics(Context context) {
        return prefs(context).getBoolean("literal_ignore_diacritics_v115", false);
    }

    public static void setIgnoreDiacritics(Context context, boolean value) {
        prefs(context).edit().putBoolean("literal_ignore_diacritics_v115", value).apply();
    }

    public static boolean ocrRepair(Context context) {
        return prefs(context).getBoolean("literal_ocr_repair_v115", false);
    }

    public static void setOcrRepair(Context context, boolean value) {
        prefs(context).edit().putBoolean("literal_ocr_repair_v115", value).apply();
    }

    public static boolean joinHyphenation(Context context) {
        return prefs(context).getBoolean("literal_join_hyphenation", false);
    }

    public static void setJoinHyphenation(Context context, boolean value) {
        prefs(context).edit().putBoolean("literal_join_hyphenation", value).apply();
    }

    public static boolean searchNodeTitles(Context context) {
        return prefs(context).getBoolean("literal_search_titles", false);
    }

    public static void setSearchNodeTitles(Context context, boolean value) {
        prefs(context).edit().putBoolean("literal_search_titles", value).apply();
    }

    public static int compareChars(Context context) {
        return prefs(context).getInt("literal_compare_chars_v115", 0);
    }

    public static void setCompareChars(Context context, int value) {
        prefs(context).edit().putInt("literal_compare_chars_v115", Math.max(0, value)).apply();
    }

    public static int precision(Context context) {
        return prefs(context).getInt("literal_precision_v115", 100);
    }

    public static void setPrecision(Context context, int value) {
        prefs(context).edit().putInt("literal_precision_v115", Math.max(0, Math.min(100, value))).apply();
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
