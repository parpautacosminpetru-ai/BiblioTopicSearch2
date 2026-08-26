package ro.bibliotopicsearch.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persistent user-owned index. It stores labels and provenance, never page images
 * and never generated assertions. Unknown candidates live in INBOX until the user
 * validates their category; validated entries become deterministic future detectors.
 */
public final class LivingIndexStore {
    private LivingIndexStore() {}

    private static final String FILE = "living_index_v5.json";

    public enum Category {
        PERSON,
        PLACE,
        ORGANIZATION,
        EVENT,
        DATE,
        PERIOD,
        WORK,
        LAW,
        CONCEPT,
        DOMAIN,
        METHOD,
        TERM,
        INBOX
    }

    public static final class Ref {
        private final long sourceId;
        private final int paragraphIndex;
        private final String page;
        private final String contextCode;
        private final List<String> axes;
        private final long seenAt;

        Ref(long sourceId, int paragraphIndex, String page, String contextCode, List<String> axes, long seenAt) {
            this.sourceId = sourceId;
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.page = safe(page);
            this.contextCode = safe(contextCode);
            this.axes = Collections.unmodifiableList(new ArrayList<>(axes == null ? Collections.emptyList() : axes));
            this.seenAt = seenAt;
        }

        public long sourceId() { return sourceId; }
        public int paragraphIndex() { return paragraphIndex; }
        public String page() { return page; }
        public String contextCode() { return contextCode; }
        public List<String> axes() { return axes; }
        public long seenAt() { return seenAt; }

        String key() {
            return sourceId + "|" + paragraphIndex + "|" + page + "|" + contextCode;
        }
    }

    public static final class Entry {
        private final String id;
        private String canonical;
        private Category category;
        private final Set<String> aliases = new LinkedHashSet<>();
        private final List<Ref> refs = new ArrayList<>();
        private int recurrence;
        private boolean validated;
        private long createdAt;
        private long lastSeenAt;

        Entry(String id, String canonical, Category category, boolean validated, long now) {
            this.id = safe(id);
            this.canonical = safe(canonical);
            this.category = category == null ? Category.INBOX : category;
            this.validated = validated;
            this.createdAt = now;
            this.lastSeenAt = now;
            this.recurrence = 1;
            if (!this.canonical.isEmpty()) aliases.add(this.canonical);
        }

        public String id() { return id; }
        public String canonical() { return canonical; }
        public Category category() { return category; }
        public Set<String> aliases() { return Collections.unmodifiableSet(aliases); }
        public List<Ref> refs() { return Collections.unmodifiableList(refs); }
        public int recurrence() { return recurrence; }
        public boolean validated() { return validated; }
        public long createdAt() { return createdAt; }
        public long lastSeenAt() { return lastSeenAt; }
        public int color() { return colorFor(category, id); }
        public String code() { return codeFor(category, id); }

        void seen(String surface, Ref ref, long now) {
            String clean = safe(surface);
            if (!clean.isEmpty()) aliases.add(clean);
            recurrence++;
            lastSeenAt = now;
            if (ref != null) addRef(ref);
        }

        void addRef(Ref ref) {
            String key = ref.key();
            for (Ref existing : refs) if (existing.key().equals(key)) return;
            refs.add(ref);
            if (refs.size() > 256) refs.remove(0);
        }

        void validate(Category value) {
            category = value == null || value == Category.INBOX ? Category.TERM : value;
            validated = true;
        }
    }

    public static final class State {
        private final Map<String, Entry> byId = new LinkedHashMap<>();

        public List<Entry> entries() { return Collections.unmodifiableList(new ArrayList<>(byId.values())); }

        public List<Entry> inbox() {
            List<Entry> out = new ArrayList<>();
            for (Entry entry : byId.values()) if (!entry.validated() || entry.category() == Category.INBOX) out.add(entry);
            out.sort((a, b) -> Integer.compare(b.recurrence(), a.recurrence()));
            return out;
        }

        public List<Entry> validated() {
            List<Entry> out = new ArrayList<>();
            for (Entry entry : byId.values()) if (entry.validated() && entry.category() != Category.INBOX) out.add(entry);
            return out;
        }

        public Entry byId(String id) { return byId.get(id); }

        public Entry findCanonical(String value) {
            String key = fold(value);
            if (key.isEmpty()) return null;
            for (Entry entry : byId.values()) {
                if (fold(entry.canonical()).equals(key)) return entry;
                for (String alias : entry.aliases()) if (fold(alias).equals(key)) return entry;
            }
            return null;
        }

        Entry merge(String surface, Category suggested, boolean autoValidate, Ref ref) {
            String clean = safe(surface);
            if (clean.isEmpty()) return null;
            Entry existing = findCanonical(clean);
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.seen(clean, ref, now);
                return existing;
            }
            Category category = autoValidate && suggested != null && suggested != Category.INBOX
                    ? suggested : Category.INBOX;
            boolean validated = autoValidate && category != Category.INBOX;
            String id = stableId(clean);
            Entry created = new Entry(id, clean, category, validated, now);
            if (ref != null) created.addRef(ref);
            byId.put(id, created);
            return created;
        }

        boolean validate(String id, Category category) {
            Entry entry = byId.get(id);
            if (entry == null) return false;
            entry.validate(category);
            return true;
        }
    }

    public static State load(Context context) {
        State out = new State();
        if (context == null) return out;
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) return out;
        try {
            JSONObject root = new JSONObject(read(file));
            JSONArray values = root.optJSONArray("entries");
            if (values == null) return out;
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                Category category = enumValue(item.optString("category"), Category.INBOX);
                long createdAt = item.optLong("createdAt", System.currentTimeMillis());
                Entry entry = new Entry(
                        item.optString("id", stableId(item.optString("canonical", ""))),
                        item.optString("canonical", ""),
                        category,
                        item.optBoolean("validated", false),
                        createdAt
                );
                entry.recurrence = Math.max(1, item.optInt("recurrence", 1));
                entry.lastSeenAt = item.optLong("lastSeenAt", createdAt);
                entry.aliases.clear();
                JSONArray aliases = item.optJSONArray("aliases");
                if (aliases != null) for (int j = 0; j < aliases.length(); j++) entry.aliases.add(aliases.optString(j));
                if (entry.aliases.isEmpty() && !entry.canonical.isEmpty()) entry.aliases.add(entry.canonical);
                JSONArray refs = item.optJSONArray("refs");
                if (refs != null) {
                    for (int j = 0; j < refs.length(); j++) {
                        JSONObject r = refs.getJSONObject(j);
                        List<String> axes = new ArrayList<>();
                        JSONArray ax = r.optJSONArray("axes");
                        if (ax != null) for (int k = 0; k < ax.length(); k++) axes.add(ax.optString(k));
                        entry.addRef(new Ref(
                                r.optLong("sourceId", 0L),
                                r.optInt("paragraphIndex", 0),
                                r.optString("page", ""),
                                r.optString("contextCode", ""),
                                axes,
                                r.optLong("seenAt", 0L)
                        ));
                    }
                }
                if (!entry.id.isEmpty()) out.byId.put(entry.id, entry);
            }
        } catch (Exception ignored) {
            return new State();
        }
        return out;
    }

    public static void save(Context context, State state) {
        if (context == null || state == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("version", 5);
            JSONArray values = new JSONArray();
            for (Entry entry : state.entries()) {
                JSONObject item = new JSONObject();
                item.put("id", entry.id());
                item.put("canonical", entry.canonical());
                item.put("category", entry.category().name());
                item.put("validated", entry.validated());
                item.put("recurrence", entry.recurrence());
                item.put("createdAt", entry.createdAt());
                item.put("lastSeenAt", entry.lastSeenAt());
                JSONArray aliases = new JSONArray();
                for (String alias : entry.aliases()) aliases.put(alias);
                item.put("aliases", aliases);
                JSONArray refs = new JSONArray();
                for (Ref ref : entry.refs()) {
                    JSONObject r = new JSONObject();
                    r.put("sourceId", ref.sourceId());
                    r.put("paragraphIndex", ref.paragraphIndex());
                    r.put("page", ref.page());
                    r.put("contextCode", ref.contextCode());
                    r.put("seenAt", ref.seenAt());
                    JSONArray axes = new JSONArray();
                    for (String axis : ref.axes()) axes.put(axis);
                    r.put("axes", axes);
                    refs.put(r);
                }
                item.put("refs", refs);
                values.put(item);
            }
            root.put("entries", values);
            File target = new File(context.getFilesDir(), FILE);
            File temp = new File(context.getFilesDir(), FILE + ".tmp");
            write(temp, root.toString());
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) write(target, root.toString());
        } catch (Exception ignored) {
            // Index persistence must not interrupt live OCR.
        }
    }

    public static String codeFor(Category category, String id) {
        String prefix;
        switch (category == null ? Category.INBOX : category) {
            case PERSON: prefix = "N"; break;
            case PLACE: prefix = "L"; break;
            case ORGANIZATION: prefix = "O"; break;
            case EVENT: prefix = "E"; break;
            case DATE:
            case PERIOD: prefix = "T"; break;
            case WORK: prefix = "W"; break;
            case LAW: prefix = "J"; break;
            case DOMAIN: prefix = "D"; break;
            case METHOD: prefix = "M"; break;
            case CONCEPT: prefix = "C"; break;
            case TERM: prefix = "K"; break;
            case INBOX:
            default: prefix = "?"; break;
        }
        int hash = Math.abs(safe(id).hashCode()) % 10000;
        return prefix + String.format(Locale.ROOT, "%04d", hash);
    }

    public static int colorFor(Category category, String id) {
        int base;
        switch (category == null ? Category.INBOX : category) {
            case PERSON: base = 0xFF3F7FC4; break;
            case PLACE: base = 0xFF3B9A73; break;
            case ORGANIZATION: base = 0xFF7B61B8; break;
            case EVENT: base = 0xFFC56A45; break;
            case DATE:
            case PERIOD: base = 0xFF5D879F; break;
            case WORK: base = 0xFF8F7350; break;
            case LAW: base = 0xFF5865A8; break;
            case DOMAIN: base = 0xFF4C8B92; break;
            case METHOD: base = 0xFF7D8651; break;
            case CONCEPT: base = 0xFF4E86A8; break;
            case TERM: base = 0xFF6E7F8B; break;
            case INBOX:
            default: base = 0xFF9A7940; break;
        }
        // Stable tiny luminance variation makes nearby codes easier to distinguish.
        int delta = (Math.abs(safe(id).hashCode()) % 25) - 12;
        int r = Math.max(0, Math.min(255, ((base >> 16) & 255) + delta));
        int g = Math.max(0, Math.min(255, ((base >> 8) & 255) + delta));
        int b = Math.max(0, Math.min(255, (base & 255) + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String stableId(String value) {
        String folded = fold(value);
        return "idx-" + Integer.toHexString(folded.hashCode()) + "-" + Math.max(1, folded.length());
    }

    private static Category enumValue(String value, Category fallback) {
        try { return Category.valueOf(value); } catch (Exception ignored) { return fallback; }
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static String read(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) >= 0) out.append(buffer, 0, n);
        }
        return out.toString();
    }

    private static void write(File file, String value) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(value == null ? "" : value);
        }
    }
}
