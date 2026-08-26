package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Four stable user-customizable roles. Display policy shows at most three simultaneously. */
public final class LensPalette {
    private LensPalette() {}

    public enum Role { TARGET, SUBJECT, FUNCTION, ANSWER }

    private static final String PREFS = "lens_v9_colors";

    public static int get(Context context, Role role) {
        return prefs(context).getInt(role.name(), defaultColor(role));
    }

    public static void set(Context context, Role role, int color) {
        if (context == null || role == null) return;
        prefs(context).edit().putInt(role.name(), color).apply();
    }

    public static void reset(Context context) {
        if (context != null) prefs(context).edit().clear().apply();
    }

    private static int defaultColor(Role role) {
        switch (role) {
            case TARGET: return Color.rgb(246, 196, 68);
            case SUBJECT: return Color.rgb(38, 174, 208);
            case FUNCTION: return Color.rgb(231, 145, 50);
            case ANSWER:
            default: return Color.rgb(48, 184, 102);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
