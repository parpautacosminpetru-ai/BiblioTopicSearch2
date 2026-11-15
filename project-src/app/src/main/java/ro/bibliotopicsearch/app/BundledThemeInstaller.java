package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Offline, deterministic installation of the seven maps compiled from nine
 * university documents. Works on first install AND offers a visible repair
 * action when an earlier APK did not populate the local topic library.
 *
 * Never erases user maps. Normal launch respects user deletion and selection;
 * explicit restore adds only missing packaged profiles.
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

    public static int expectedMapCount() {
        return PACKS.length;
    }

    public static String[] packNames() {
        String[] names = new String[PACKS.length];
        for (int i = 0; i < PACKS.length; i++) names[i] = PACKS[i][1];
        return names;
    }

    public static int installedMapCount(Context context) {
        List<MapProfile> maps = TopicLibraryStore.list(context);
        int count = 0;
        for (String[] pack : PACKS) {
            for (MapProfile profile : maps) {
                if (profile.name.equals(pack[1]) && profile.folder.equals(pack[2])
                        && !profile.rawText.trim().isEmpty()) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public static synchronized void ensureInstalled(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(INSTALLED_KEY, false)) {
            // Recover a broken/empty installation, but do not undo intentional
            // deletion when at least one of the curated maps still exists.
            if (installedMapCount(app) == 0) restoreMissing(app);
            return;
        }
        restoreMissing(app);
    }

    /**
     * Restore ONLY absent or empty bundled profiles; return number repaired.
     * Returns -1 on validation/I/O failure (shown to user by the UI).
     */
    public static synchronized int restoreMissing(Context context) {
        Context app = context.getApplicationContext();
        String[] raw = new String[PACKS.length];
        try {
            for (int i = 0; i < PACKS.length; i++) {
                raw[i] = loadAsset(app, PACKS[i][0]);
                if (raw[i].trim().isEmpty() || !raw[i].startsWith("# ")
                        || !raw[i].contains("\n")) {
                    throw new IOException("Missing/invalid theme asset: " + PACKS[i][0]);
                }
            }
        } catch (IOException error) {
            Log.e(TAG, "Bundled theme import failed; user maps preserved", error);
            return -1;
        }

        // Use the same shared library visible to the library screen and detector.
        List<MapProfile> before = TopicLibraryStore.list(app);
        MapProfile active = TopicLibraryStore.getActive(app);
        boolean initialBlankDefault = before.size() == 1
                && "default".equals(active.id) && active.rawText.trim().isEmpty();
        int repaired = 0;
        String firstImportedId = null;

        for (int i = 0; i < PACKS.length; i++) {
            String[] pack = PACKS[i];
            MapProfile existing = find(app, pack[1], pack[2]);
            String id;
            if (existing == null) {
                id = TopicLibraryStore.create(app, pack[1], pack[2], raw[i]);
                repaired++;
                TopicLibraryStore.setSelectedForScan(app, id, true);
            } else if (existing.rawText.trim().isEmpty()) {
                id = existing.id;
                TopicLibraryStore.update(app, id, pack[1], pack[2], raw[i]);
                repaired++;
                TopicLibraryStore.setSelectedForScan(app, id, true);
            } else {
                id = existing.id;
            }
            if (firstImportedId == null) firstImportedId = id;
        }

        if (initialBlankDefault && firstImportedId != null) {
            TopicLibraryStore.setActive(app, firstImportedId);
            TopicLibraryStore.setSelectedForScan(app, active.id, false);
        }

        boolean complete = installedMapCount(app) == PACKS.length;
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(INSTALLED_KEY, complete).commit();
        return complete ? repaired : -1;
    }

    private static MapProfile find(Context context, String name, String folder) {
        for (MapProfile profile : TopicLibraryStore.list(context)) {
            if (profile.name.equals(name) && profile.folder.equals(folder)) return profile;
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
