package ro.bibliotopicsearch.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "bibliotopicsearch_prefs";

    public enum MatchMode {
        EXACT, PREFIX, CONTAINS, FLEXIBLE
    }

    /** Same single bar, two deterministic purposes. */
    public enum IndexMode {
        SOURCE,      // empty bar: learn/cartograph the source itself; persist index only
        RESEARCH     // non-empty bar: external topic/question; persist evidence workspace
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
        boolean enabled = prefs(context).getBoolean("ocr_enabled", true);
        if (enabled) {
            OnePassLiveCollector.start();
            LivingIndexRuntime.start(context, System.currentTimeMillis());
        }
        return enabled;
    }

    /**
     * OCR LIVE is the one-pass session boundary. SOURCE mode finishes into the
     * Living Index only; RESEARCH mode persists the evidence session/workspace.
     */
    public static void setOcrEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("ocr_enabled", value).apply();

        if (value) {
            OnePassSemanticOrganizer.beginSession();
            OnePassLiveCollector.start();
            LivingIndexRuntime.start(context, System.currentTimeMillis());
            return;
        }

        OnePassLiveCollector.stop();
        LivingIndexRuntime.stop();
        if (!OnePassSemanticOrganizer.isActive()) return;

        OnePassSemanticOrganizer.ingest(
                TopicMatcher.latestParagraphDetections(),
                TopicMatcher.researchProfile()
        );

        final Context appContext = context.getApplicationContext();
        final Activity activity = context instanceof Activity ? (Activity) context : null;
        final IndexMode mode = indexMode(context);
        Thread worker = new Thread(() -> {
            OnePassSemanticOrganizer.Snapshot snapshot = OnePassSemanticOrganizer.finishSession();

            if (mode == IndexMode.SOURCE) {
                // Ethical/source-learning mode: do not write paragraph text or page images to disk.
                Runnable openIndex = () -> {
                    Intent intent = new Intent(
                            activity == null ? appContext : activity,
                            LivingIndexActivity.class
                    );
                    if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    (activity == null ? appContext : activity).startActivity(intent);
                };
                if (activity != null && !activity.isFinishing()) activity.runOnUiThread(openIndex);
                else openIndex.run();
                return;
            }

            if (snapshot == null || snapshot.paragraphs().isEmpty()) return;
            try {
                OrganizedSessionStore.save(appContext, snapshot);
            } catch (Exception ignored) {
                return;
            }

            Runnable open = () -> {
                Intent intent = new Intent(
                        activity == null ? appContext : activity,
                        OrganizedSessionActivity.class
                );
                if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                (activity == null ? appContext : activity).startActivity(intent);
            };

            if (activity != null && !activity.isFinishing()) activity.runOnUiThread(open);
            else open.run();
        }, "one-pass-finalize");
        worker.setDaemon(true);
        worker.start();
    }

    public static IndexMode indexMode(Context context) {
        SharedPreferences p = prefs(context);
        String explicit = p.getString("index_mode", null);
        if (explicit != null) {
            try { return IndexMode.valueOf(explicit); } catch (Exception ignored) { /* fall through */ }
        }
        String query = p.getString("research_query", "");
        return query == null || query.trim().isEmpty() ? IndexMode.SOURCE : IndexMode.RESEARCH;
    }

    public static void setIndexMode(Context context, IndexMode mode) {
        prefs(context).edit().putString("index_mode", (mode == null ? IndexMode.SOURCE : mode).name()).apply();
    }

    /** Optional focus in SOURCE mode. Empty means cartograph everything indexable. */
    public static String sourceIndexFocus(Context context) {
        String value = prefs(context).getString("source_index_focus", "");
        return value == null ? "" : value;
    }

    public static void setSourceIndexFocus(Context context, String value) {
        prefs(context).edit().putString("source_index_focus", value == null ? "" : value.trim()).apply();
    }

    public static boolean floatingLabels(Context context) {
        return prefs(context).getBoolean("floating_labels", false);
    }

    public static void setFloatingLabels(Context context, boolean value) {
        prefs(context).edit().putBoolean("floating_labels", value).apply();
    }

    public static boolean themeLayer(Context context) {
        return prefs(context).getBoolean("layer_theme", true);
    }

    public static void setThemeLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_theme", value).apply();
    }

    public static boolean textualLayer(Context context) {
        return prefs(context).getBoolean("layer_textual", false);
    }

    public static void setTextualLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_textual", value).apply();
    }

    public static boolean semanticLayer(Context context) {
        return prefs(context).getBoolean("layer_semantic", false);
    }

    public static void setSemanticLayer(Context context, boolean value) {
        prefs(context).edit().putBoolean("layer_semantic", value).apply();
    }

    public static int zoomLevel(Context context) {
        return Math.max(0, Math.min(3, prefs(context).getInt("zoom_level", 0)));
    }

    public static void setZoomLevel(Context context, int value) {
        prefs(context).edit().putInt("zoom_level", Math.max(0, Math.min(3, value))).apply();
    }

    /** Effective research query. SOURCE mode deliberately returns empty. */
    public static String researchQuery(Context context) {
        if (indexMode(context) == IndexMode.SOURCE) return "";
        String value = prefs(context).getString("research_query", "");
        return value == null ? "" : value;
    }

    public static String storedResearchQuery(Context context) {
        String value = prefs(context).getString("research_query", "");
        return value == null ? "" : value;
    }

    /** Empty single bar => SOURCE. Any explicit text => RESEARCH. */
    public static void setResearchQuery(Context context, String value) {
        String clean = value == null ? "" : value.trim();
        IndexMode mode = clean.isEmpty() ? IndexMode.SOURCE : IndexMode.RESEARCH;
        prefs(context).edit()
                .putString("research_query", clean)
                .putString("index_mode", mode.name())
                .apply();
    }

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
