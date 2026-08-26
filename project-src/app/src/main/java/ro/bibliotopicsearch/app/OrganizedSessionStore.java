package ro.bibliotopicsearch.app;

import android.content.Context;

import org.json.JSONArray;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local JSON persistence for finalized one-pass semantic sessions. */
public final class OrganizedSessionStore {
    private OrganizedSessionStore() {}

    private static final String DIR = "semantic_sessions";
    private static final String LATEST = "latest.txt";

    public static File save(Context context, OnePassSemanticOrganizer.Snapshot snapshot) throws IOException {
        if (context == null || snapshot == null) throw new IOException("Missing context or snapshot");
        File dir = ensureDir(context);
        String name = "session-" + Math.max(1L, snapshot.startedAt()) + ".json";
        File target = new File(dir, name);
        File temp = new File(dir, name + ".tmp");

        writeUtf8(temp, toJson(snapshot).toString());
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace session file");
        if (!temp.renameTo(target)) {
            writeUtf8(target, readUtf8(temp));
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
        writeUtf8(new File(dir, LATEST), name);
        return target;
    }

    public static OnePassSemanticOrganizer.Snapshot loadLatest(Context context) {
        if (context == null) return null;
        File dir = ensureDir(context);
        File target = null;
        File pointer = new File(dir, LATEST);
        if (pointer.isFile()) {
            try {
                String name = readUtf8(pointer).trim();
                if (!name.isEmpty()) target = new File(dir, name);
            } catch (IOException ignored) {
                target = null;
            }
        }
        if (target == null || !target.isFile()) target = newestSessionFile(dir);
        if (target == null || !target.isFile()) return null;
        return loadFile(target);
    }

    /** All persisted One-Pass sessions, oldest first, for the global research workspace. */
    public static List<OnePassSemanticOrganizer.Snapshot> loadAll(Context context) {
        List<OnePassSemanticOrganizer.Snapshot> out = new ArrayList<>();
        if (context == null) return out;
        File dir = ensureDir(context);
        File[] files = dir.listFiles((d, name) -> name.startsWith("session-") && name.endsWith(".json"));
        if (files == null || files.length == 0) return out;
        java.util.Arrays.sort(files, (a, b) -> {
            long left = sessionIdFromName(a.getName());
            long right = sessionIdFromName(b.getName());
            if (left == right) return Long.compare(a.lastModified(), b.lastModified());
            return Long.compare(left, right);
        });
        for (File file : files) {
            OnePassSemanticOrganizer.Snapshot snapshot = loadFile(file);
            if (snapshot != null) out.add(snapshot);
        }
        return out;
    }

    public static boolean hasLatest(Context context) {
        return loadLatest(context) != null;
    }

    private static OnePassSemanticOrganizer.Snapshot loadFile(File target) {
        if (target == null || !target.isFile()) return null;
        try {
            return fromJson(new JSONObject(readUtf8(target)));
        } catch (IOException | JSONException | RuntimeException ignored) {
            return null;
        }
    }

    private static long sessionIdFromName(String name) {
        if (name == null) return 0L;
        try {
            int start = name.indexOf('-');
            int end = name.lastIndexOf('.');
            if (start < 0 || end <= start) return 0L;
            return Long.parseLong(name.substring(start + 1, end));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static File ensureDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static File newestSessionFile(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("session-") && name.endsWith(".json"));
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File file : files) if (file.lastModified() > newest.lastModified()) newest = file;
        return newest;
    }

    private static void writeUtf8(File file, String value) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8
        )) {
            writer.write(value == null ? "" : value);
        }
    }

    private static String readUtf8(File file) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8
        ))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) out.append(buffer, 0, read);
        }
        return out.toString();
    }

    private static JSONObject toJson(OnePassSemanticOrganizer.Snapshot snapshot) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("startedAt", snapshot.startedAt());
            root.put("finishedAt", snapshot.finishedAt());
            root.put("framesObserved", snapshot.framesObserved());
            root.put("duplicatesMerged", snapshot.duplicatesMerged());
            root.put("query", snapshot.query());
            root.put("globalSubject", snapshot.globalSubject());
            root.put("maxDepth", snapshot.maxDepth());
            root.put("claimCount", snapshot.claimCount());
            root.put("bestAnswerSegment", snapshot.bestAnswerSegment());
            root.put("bestAnswerScore", snapshot.bestAnswerScore());

            JSONArray paragraphs = new JSONArray();
            for (OnePassSemanticOrganizer.Paragraph paragraph : snapshot.paragraphs()) {
                JSONObject p = new JSONObject();
                p.put("index", paragraph.index());
                p.put("depth", paragraph.depth());
                p.put("parentIndex", paragraph.parentIndex());
                p.put("link", paragraph.link().name());
                p.put("text", paragraph.text());
                p.put("subject", paragraph.subject());
                p.put("function", paragraph.function().name());
                p.put("secondaryFunction", paragraph.secondaryFunction().name());
                p.put("subjectConfidence", paragraph.subjectConfidence());
                p.put("functionConfidence", paragraph.functionConfidence());
                p.put("sightings", paragraph.sightings());
                p.put("answerSegment", paragraph.answerSegment());
                p.put("answerScore", paragraph.answerScore());
                p.put("answerIntent", paragraph.answerIntent().name());
                p.put("answerRelation", paragraph.answerRelation().name());

                JSONArray claims = new JSONArray();
                for (OnePassSemanticOrganizer.Claim claim : paragraph.claims()) {
                    JSONObject c = new JSONObject();
                    c.put("raw", claim.raw());
                    c.put("subject", claim.subject());
                    c.put("predicate", claim.predicate());
                    c.put("object", claim.object());
                    c.put("relation", claim.relation().name());
                    c.put("confidence", claim.confidence());

                    JSONArray operators = new JSONArray();
                    for (SemanticGraph.Operator operator : claim.operators()) {
                        operators.put(operator.name());
                    }
                    c.put("operators", operators);

                    JSONObject slots = new JSONObject();
                    for (Map.Entry<SemanticGraph.Slot, String> slot : claim.slots().entrySet()) {
                        slots.put(slot.getKey().name(), slot.getValue());
                    }
                    c.put("slots", slots);
                    claims.put(c);
                }
                p.put("claims", claims);
                paragraphs.put(p);
            }
            root.put("paragraphs", paragraphs);
            return root;
        } catch (JSONException e) {
            throw new IOException("Cannot encode organized session", e);
        }
    }

    private static OnePassSemanticOrganizer.Snapshot fromJson(JSONObject root) throws JSONException {
        JSONArray paragraphsJson = root.optJSONArray("paragraphs");
        List<OnePassSemanticOrganizer.Paragraph> paragraphs = new ArrayList<>();
        if (paragraphsJson != null) {
            for (int i = 0; i < paragraphsJson.length(); i++) {
                JSONObject p = paragraphsJson.getJSONObject(i);
                JSONArray claimsJson = p.optJSONArray("claims");
                List<OnePassSemanticOrganizer.Claim> claims = new ArrayList<>();
                if (claimsJson != null) {
                    for (int j = 0; j < claimsJson.length(); j++) {
                        JSONObject c = claimsJson.getJSONObject(j);
                        Set<SemanticGraph.Operator> operators = EnumSet.noneOf(SemanticGraph.Operator.class);
                        JSONArray operatorJson = c.optJSONArray("operators");
                        if (operatorJson != null) {
                            for (int k = 0; k < operatorJson.length(); k++) {
                                SemanticGraph.Operator operator = enumValue(
                                        SemanticGraph.Operator.class,
                                        operatorJson.optString(k),
                                        null
                                );
                                if (operator != null) operators.add(operator);
                            }
                        }

                        EnumMap<SemanticGraph.Slot, String> slots = new EnumMap<>(SemanticGraph.Slot.class);
                        JSONObject slotJson = c.optJSONObject("slots");
                        if (slotJson != null) {
                            java.util.Iterator<String> keys = slotJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                SemanticGraph.Slot slot = enumValue(SemanticGraph.Slot.class, key, null);
                                if (slot != null) slots.put(slot, slotJson.optString(key, ""));
                            }
                        }

                        claims.add(new OnePassSemanticOrganizer.Claim(
                                c.optString("raw", ""),
                                c.optString("subject", ""),
                                c.optString("predicate", ""),
                                c.optString("object", ""),
                                enumValue(
                                        SemanticGraph.Relation.class,
                                        c.optString("relation"),
                                        SemanticGraph.Relation.GENERIC
                                ),
                                operators,
                                slots,
                                c.optDouble("confidence", 0.0)
                        ));
                    }
                }

                paragraphs.add(new OnePassSemanticOrganizer.Paragraph(
                        p.optInt("index", i),
                        p.optInt("depth", 0),
                        p.optInt("parentIndex", -1),
                        enumValue(
                                ParagraphCartography.Link.class,
                                p.optString("link"),
                                ParagraphCartography.Link.ROOT
                        ),
                        p.optString("text", ""),
                        p.optString("subject", ""),
                        enumValue(
                                UniversalDetectionLexicon.Function.class,
                                p.optString("function"),
                                UniversalDetectionLexicon.Function.UNKNOWN
                        ),
                        enumValue(
                                UniversalDetectionLexicon.Function.class,
                                p.optString("secondaryFunction"),
                                UniversalDetectionLexicon.Function.UNKNOWN
                        ),
                        p.optDouble("subjectConfidence", 0.0),
                        p.optDouble("functionConfidence", 0.0),
                        p.optInt("sightings", 1),
                        p.optString("answerSegment", ""),
                        p.optDouble("answerScore", 0.0),
                        enumValue(
                                ResearchSemanticEngine.Intent.class,
                                p.optString("answerIntent"),
                                ResearchSemanticEngine.Intent.TOPIC
                        ),
                        enumValue(
                                SemanticGraph.Relation.class,
                                p.optString("answerRelation"),
                                SemanticGraph.Relation.GENERIC
                        ),
                        claims
                ));
            }
        }

        return new OnePassSemanticOrganizer.Snapshot(
                root.optLong("startedAt", 0L),
                root.optLong("finishedAt", 0L),
                root.optLong("framesObserved", 0L),
                root.optInt("duplicatesMerged", 0),
                root.optString("query", ""),
                root.optString("globalSubject", ""),
                root.optInt("maxDepth", 0),
                root.optInt("claimCount", 0),
                root.optString("bestAnswerSegment", ""),
                root.optDouble("bestAnswerScore", 0.0),
                paragraphs
        );
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
