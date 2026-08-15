package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TopicMapStore {
    private static final String PREF_FILE = "bibliotopicsearch_map";

    private static final int[] DEFAULT_COLORS = new int[] {
            Color.rgb(218, 73, 96),
            Color.rgb(48, 129, 157),
            Color.rgb(58, 146, 114),
            Color.rgb(222, 148, 54),
            Color.rgb(127, 90, 168),
            Color.rgb(76, 112, 157),
            Color.rgb(190, 91, 55),
            Color.rgb(82, 142, 63),
            Color.rgb(184, 72, 143),
            Color.rgb(61, 150, 148),
            Color.rgb(112, 98, 79),
            Color.rgb(192, 116, 35)
    };

    public static final String DEFAULT_MAP = "";
    private static final String LEGACY_DEFAULT_MAP_KEY = "229544ec885cd448";

    private TopicMapStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static String getMapName(Context context) {
        return TopicLibraryStore.getActive(context).name;
    }

    public static String getRawMap(Context context) {
        MapProfile active = TopicLibraryStore.getActive(context);
        String stored = active.rawText == null ? DEFAULT_MAP : active.rawText;
        if (LEGACY_DEFAULT_MAP_KEY.equals(key(stored))) {
            TopicLibraryStore.update(context, active.id, active.name, active.folder, DEFAULT_MAP);
            return DEFAULT_MAP;
        }
        return stored;
    }

    public static void saveMap(Context context, String name, String text) {
        TopicLibraryStore.updateActiveMap(
                context,
                safe(name, "Hartă de cercetare"),
                text == null ? "" : text
        );
    }

    public static TopicMap load(Context context) {
        MapProfile active = TopicLibraryStore.getActive(context);
        return parseForProfile(context, active.id, active.name, active.rawText);
    }

    public static TopicMap parse(Context context, String name, String rawText) {
        return parseForProfile(context, TopicLibraryStore.getActiveId(context), name, rawText);
    }

    public static TopicMap parseForProfile(Context context, String profileId, String name, String rawText) {
        List<TopicNode> nodes = new ArrayList<>();
        Map<Integer, String> hierarchy = new LinkedHashMap<>();
        TopicNode current = null;
        String[] lines = (rawText == null ? "" : rawText).split("\\r?\\n");

        for (String sourceLine : lines) {
            String line = sourceLine == null ? "" : sourceLine.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String title = line.substring(level).trim();
                if (title.isEmpty()) title = "Nod " + (nodes.size() + 1);

                List<Integer> remove = new ArrayList<>();
                for (Integer existingLevel : hierarchy.keySet()) {
                    if (existingLevel >= level) remove.add(existingLevel);
                }
                for (Integer removeLevel : remove) hierarchy.remove(removeLevel);
                hierarchy.put(level, title);

                StringBuilder path = new StringBuilder();
                for (Map.Entry<Integer, String> entry : hierarchy.entrySet()) {
                    if (path.length() > 0) path.append(" > ");
                    path.append(entry.getValue());
                }

                current = new TopicNode(path.toString(), title, level);
                int defaultColor = DEFAULT_COLORS[nodes.size() % DEFAULT_COLORS.length];
                current.color = getNodeColorForProfile(context, profileId, current.path, defaultColor);
                current.symbol = getNodeSymbolForProfile(context, profileId, current.path, defaultSymbol(level));
                current.enabled = getNodeEnabledForProfile(context, profileId, current.path, true);
                nodes.add(current);
                continue;
            }

            if (current == null) continue;

            String termLine = line;
            if (termLine.startsWith("- ") || termLine.startsWith("• ")) {
                termLine = termLine.substring(2).trim();
            }

            String[] pieces = termLine.split("\\|");
            for (String piece : pieces) {
                String term = piece.trim();
                if (!term.isEmpty() && !current.terms.contains(term)) current.terms.add(term);
            }
        }

        return new TopicMap(safe(name, "Hartă de cercetare"), rawText == null ? "" : rawText, nodes);
    }

    public static void setNodeColor(Context context, String path, int color) {
        prefs(context).edit().putInt(profileNodeKey(context, path, "color"), color).apply();
    }

    public static int getNodeColor(Context context, String path, int defaultColor) {
        return getNodeColorForProfile(context, TopicLibraryStore.getActiveId(context), path, defaultColor);
    }

    private static int getNodeColorForProfile(Context context, String profileId, String path, int defaultColor) {
        SharedPreferences p = prefs(context);
        String namespaced = profileNodeKey(profileId, path, "color");
        if (p.contains(namespaced)) return p.getInt(namespaced, defaultColor);
        if ("default".equals(profileId)) {
            String legacy = legacyNodeKey(path, "color");
            if (p.contains(legacy)) return p.getInt(legacy, defaultColor);
        }
        return defaultColor;
    }

    public static void setNodeSymbol(Context context, String path, String symbol) {
        prefs(context).edit().putString(profileNodeKey(context, path, "symbol"), symbol == null ? "" : symbol).apply();
    }

    public static String getNodeSymbol(Context context, String path, String defaultSymbol) {
        return getNodeSymbolForProfile(context, TopicLibraryStore.getActiveId(context), path, defaultSymbol);
    }

    private static String getNodeSymbolForProfile(Context context, String profileId, String path, String defaultSymbol) {
        SharedPreferences p = prefs(context);
        String namespaced = profileNodeKey(profileId, path, "symbol");
        if (p.contains(namespaced)) return p.getString(namespaced, defaultSymbol);
        if ("default".equals(profileId)) {
            String legacy = legacyNodeKey(path, "symbol");
            if (p.contains(legacy)) return p.getString(legacy, defaultSymbol);
        }
        return defaultSymbol;
    }

    public static void setNodeEnabled(Context context, String path, boolean enabled) {
        prefs(context).edit().putBoolean(profileNodeKey(context, path, "enabled"), enabled).apply();
    }

    public static boolean getNodeEnabled(Context context, String path, boolean defaultValue) {
        return getNodeEnabledForProfile(context, TopicLibraryStore.getActiveId(context), path, defaultValue);
    }

    private static boolean getNodeEnabledForProfile(Context context, String profileId, String path, boolean defaultValue) {
        SharedPreferences p = prefs(context);
        String namespaced = profileNodeKey(profileId, path, "enabled");
        if (p.contains(namespaced)) return p.getBoolean(namespaced, defaultValue);
        if ("default".equals(profileId)) {
            String legacy = legacyNodeKey(path, "enabled");
            if (p.contains(legacy)) return p.getBoolean(legacy, defaultValue);
        }
        return defaultValue;
    }

    public static void setAllEnabled(Context context, TopicMap map, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (TopicNode node : map.nodes) {
            editor.putBoolean(profileNodeKey(context, node.path, "enabled"), enabled);
        }
        editor.apply();
    }

    public static void setOnlyNode(Context context, TopicMap map, String path) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (TopicNode node : map.nodes) {
            editor.putBoolean(profileNodeKey(context, node.path, "enabled"), node.path.equals(path));
        }
        editor.apply();
    }

    public static void setLevelEnabled(Context context, TopicMap map, int level, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (TopicNode node : map.nodes) {
            if (node.level == level) {
                editor.putBoolean(profileNodeKey(context, node.path, "enabled"), enabled);
            }
        }
        editor.apply();
    }

    private static String profileNodeKey(Context context, String path, String field) {
        return profileNodeKey(TopicLibraryStore.getActiveId(context), path, field);
    }

    private static String profileNodeKey(String profileId, String path, String field) {
        return "profile_" + key(profileId == null ? "default" : profileId) + "_node_" + key(path) + "_" + field;
    }

    private static String legacyNodeKey(String path, String field) {
        return "node_" + key(path) + "_" + field;
    }

    private static String defaultSymbol(int level) {
        String[] symbols = {"●", "◆", "■", "▲", "✦", "✚", "◉", "◇"};
        return symbols[Math.max(0, level - 1) % symbols.length];
    }

    private static String key(String value) {
        if (value == null) value = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                out.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}
