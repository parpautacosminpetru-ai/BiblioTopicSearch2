package ro.bibliotopicsearch.app;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User-owned workspace metadata. Evidence is never rewritten here: this file only
 * stores project/source labels, pins, user annotations and the user's synthesis draft.
 */
public final class ResearchWorkspaceStore {
    private ResearchWorkspaceStore() {}

    private static final String FILE = "research_workspace.json";

    public static final class SourceMeta {
        private final String title;
        private final String author;
        private final String locator;

        SourceMeta(String title, String author, String locator) {
            this.title = safe(title);
            this.author = safe(author);
            this.locator = safe(locator);
        }

        public String title() { return title; }
        public String author() { return author; }
        public String locator() { return locator; }

        public String displayName(long sessionId) {
            if (!title.isEmpty()) return title;
            return "Sursa " + sessionId;
        }
    }

    public static final class State {
        private final String projectTitle;
        private final String synthesisDraft;
        private final Map<String, SourceMeta> sources;
        private final Set<String> pinnedEvidence;
        private final Map<String, String> evidenceNotes;

        State(
                String projectTitle,
                String synthesisDraft,
                Map<String, SourceMeta> sources,
                Set<String> pinnedEvidence,
                Map<String, String> evidenceNotes
        ) {
            this.projectTitle = safe(projectTitle).isEmpty() ? "Cercetare" : safe(projectTitle);
            this.synthesisDraft = synthesisDraft == null ? "" : synthesisDraft;
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            this.pinnedEvidence = Collections.unmodifiableSet(new LinkedHashSet<>(pinnedEvidence));
            this.evidenceNotes = Collections.unmodifiableMap(new LinkedHashMap<>(evidenceNotes));
        }

        public String projectTitle() { return projectTitle; }
        public String synthesisDraft() { return synthesisDraft; }
        public Map<String, SourceMeta> sources() { return sources; }
        public Set<String> pinnedEvidence() { return pinnedEvidence; }
        public Map<String, String> evidenceNotes() { return evidenceNotes; }
        public SourceMeta source(long sessionId) {
            SourceMeta value = sources.get(String.valueOf(sessionId));
            return value == null ? new SourceMeta("", "", "") : value;
        }
        public boolean isPinned(String evidenceId) { return pinnedEvidence.contains(evidenceId); }
        public String note(String evidenceId) { return evidenceNotes.getOrDefault(evidenceId, ""); }
    }

    public static State load(Context context) {
        if (context == null) return empty();
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) return empty();
        try {
            JSONObject root = new JSONObject(read(file));
            Map<String, SourceMeta> sources = new LinkedHashMap<>();
            JSONObject sourceJson = root.optJSONObject("sources");
            if (sourceJson != null) {
                java.util.Iterator<String> keys = sourceJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject value = sourceJson.optJSONObject(key);
                    if (value == null) continue;
                    sources.put(key, new SourceMeta(
                            value.optString("title", ""),
                            value.optString("author", ""),
                            value.optString("locator", "")
                    ));
                }
            }

            Set<String> pins = new LinkedHashSet<>();
            org.json.JSONArray pinJson = root.optJSONArray("pins");
            if (pinJson != null) {
                for (int i = 0; i < pinJson.length(); i++) {
                    String value = pinJson.optString(i, "");
                    if (!value.isEmpty()) pins.add(value);
                }
            }

            Map<String, String> notes = new LinkedHashMap<>();
            JSONObject noteJson = root.optJSONObject("notes");
            if (noteJson != null) {
                java.util.Iterator<String> keys = noteJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    notes.put(key, noteJson.optString(key, ""));
                }
            }

            return new State(
                    root.optString("projectTitle", "Cercetare"),
                    root.optString("synthesisDraft", ""),
                    sources,
                    pins,
                    notes
            );
        } catch (Exception ignored) {
            return empty();
        }
    }

    public static void setProjectTitle(Context context, String title) {
        State old = load(context);
        save(context, new State(title, old.synthesisDraft, old.sources, old.pinnedEvidence, old.evidenceNotes));
    }

    public static void setSynthesisDraft(Context context, String draft) {
        State old = load(context);
        save(context, new State(old.projectTitle, draft, old.sources, old.pinnedEvidence, old.evidenceNotes));
    }

    public static void setSource(Context context, long sessionId, String title, String author, String locator) {
        State old = load(context);
        Map<String, SourceMeta> sources = new LinkedHashMap<>(old.sources);
        sources.put(String.valueOf(sessionId), new SourceMeta(title, author, locator));
        save(context, new State(old.projectTitle, old.synthesisDraft, sources, old.pinnedEvidence, old.evidenceNotes));
    }

    public static void setEvidenceNote(Context context, String evidenceId, String note) {
        if (evidenceId == null || evidenceId.trim().isEmpty()) return;
        State old = load(context);
        Map<String, String> notes = new LinkedHashMap<>(old.evidenceNotes);
        String clean = note == null ? "" : note;
        if (clean.trim().isEmpty()) notes.remove(evidenceId);
        else notes.put(evidenceId, clean);
        save(context, new State(old.projectTitle, old.synthesisDraft, old.sources, old.pinnedEvidence, notes));
    }

    public static void setPinned(Context context, String evidenceId, boolean pinned) {
        if (evidenceId == null || evidenceId.trim().isEmpty()) return;
        State old = load(context);
        Set<String> pins = new LinkedHashSet<>(old.pinnedEvidence);
        if (pinned) pins.add(evidenceId); else pins.remove(evidenceId);
        save(context, new State(old.projectTitle, old.synthesisDraft, old.sources, pins, old.evidenceNotes));
    }

    private static State empty() {
        return new State("Cercetare", "", Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap());
    }

    private static void save(Context context, State state) {
        if (context == null || state == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("projectTitle", state.projectTitle);
            root.put("synthesisDraft", state.synthesisDraft);

            JSONObject sources = new JSONObject();
            for (Map.Entry<String, SourceMeta> entry : state.sources.entrySet()) {
                SourceMeta meta = entry.getValue();
                JSONObject value = new JSONObject();
                value.put("title", meta.title);
                value.put("author", meta.author);
                value.put("locator", meta.locator);
                sources.put(entry.getKey(), value);
            }
            root.put("sources", sources);

            org.json.JSONArray pins = new org.json.JSONArray();
            for (String pin : state.pinnedEvidence) pins.put(pin);
            root.put("pins", pins);

            JSONObject notes = new JSONObject();
            for (Map.Entry<String, String> note : state.evidenceNotes.entrySet()) {
                notes.put(note.getKey(), note.getValue());
            }
            root.put("notes", notes);

            write(new File(context.getFilesDir(), FILE), root.toString());
        } catch (JSONException | IOException ignored) {
            // User metadata must never interrupt OCR or evidence persistence.
        }
    }

    private static String read(File file) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8
        ))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) out.append(buffer, 0, read);
        }
        return out.toString();
    }

    private static void write(File file, String value) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(temp), StandardCharsets.UTF_8
        )) {
            writer.write(value == null ? "" : value);
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot replace workspace");
        if (!temp.renameTo(file)) {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8
            )) {
                writer.write(value == null ? "" : value);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
