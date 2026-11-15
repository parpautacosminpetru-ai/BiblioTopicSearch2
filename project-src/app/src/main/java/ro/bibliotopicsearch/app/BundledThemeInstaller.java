package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Installs the seven verified, topic-specific maps supplied with BiblioTopicSearch 2.
 *
 * Nine syllabus files are consolidated to seven disciplines (three versions of
 * Europa medievala become ONE map). Installation is idempotent and never edits
 * user-created or user-edited maps. It runs only after the user's own preferences
 * have been initialized. No network and no auto-synced third-party content.
 */
public final class BundledThemeInstaller {
    private static final String TAG = "BiblioThemeInstaller";
    private static final String PREFS = "bibliotopicsearch_bundled_themes";
    private static final String INSTALLED_KEY = "curated_v2_2026_11_installed";

    private static final String[][] PACKS = new String[][] {
        {"01_studiul_izvoarelor_2026.txt", "Studiul izvoarelor — 2026", "Metodologie"},
        {"02_europa_moderna_2026.txt", "Europa modernă — 2026", "Istorie universală"},
        {"03_istoria_bizantului_2026.txt", "Istoria Bizanțului — 2026", "Istorie universală"},
        {"04_istoria_moderna_romanilor_2026.txt", "Istoria modernă a românilor — 2026", "Istorie românească"},
        {"05_europa_medievala_unificata_2020_2026.txt", "Europa medievală — unificată", "Istorie universală"},
        {"07_introducere_istoriografie_2026.txt", "Introducere în istoriografie — 2026", "Metodologie"},
        {"08_stat_societate_mentalitati_romanesti_38_subiecte.txt", "Stat, societate și mentalități — 38 subiecte", "Istorie românească"}
    };

    private BundledThemeInstaller() {}

    public static synchronized void ensureInstalled(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(INSTALLED_KEY, false)) return;

        // Read and validate every asset before touching the profile library.
        String[] raw = new String[PACKS.length];
        try {
            for (int i = 0; i < PACKS.length; i++) {
                raw[i] = loadAsset(app, PACKS[i][0]);
                if (raw[i].trim().isEmpty() || !raw[i].startsWith("# ")) {
                    throw new IOException("Invalid bundled theme: " + PACKS[i][0]);
                }
            }
        } catch (IOException error) {
            Log.e(TAG, "Curated themes not installed; source files remain unchanged", error);
            return;
        }

        // Do not erase any existing maps or destroy a user's selected scan set.
        MapProfile previousActive = TopicLibraryStore.getActive(app);
        List<MapProfile> before = TopicLibraryStore.list(app);
        boolean initialBlankDefault = before.size() == 1
                && previousActive.rawText.trim().isEmpty()
                && "default".equals(previousActive.id);

        String firstImportedId = null;
        for (int i = 0; i < PACKS.length; i++) {
            String name = PACKS[i][1];
            String folder = PACKS[i][2];
            String id = existingMapId(app, name, folder);
            if (id == null) {
                id = TopicLibraryStore.create(app, name, folder, raw[i]);
            }
            if (firstImportedId == null) firstImportedId = id;
            TopicLibraryStore.setSelectedForScan(app, id, true);
        }

        if (initialBlankDefault && firstImportedId != null) {
            TopicLibraryStore.setActive(app, firstImportedId);
            TopicLibraryStore.setSelectedForScan(app, previousActive.id, false);
        }
        prefs.edit().putBoolean(INSTALLED_KEY, true).apply();
    }

    private static String existingMapId(Context context, String name, String folder) {
        for (MapProfile profile : TopicLibraryStore.list(context)) {
            if (profile.name.equals(name) && profile.folder.equals(folder)) {
                return profile.id;
            }
        }
        return null;
    }

    private static String loadAsset(Context context, String filename) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open("themes/" + filename), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }
}
