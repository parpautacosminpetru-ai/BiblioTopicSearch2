package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Separate, offline canonical registry. One authority-qualified ID is one
 * concept, variants are searchable forms, and OCR never writes a canonical
 * concept without an explicit researcher action.
 *
 * The stored schema is additive; pre-existing topic maps and archival tables
 * are never modified by this SQLiteOpenHelper.
 */
public final class UniversalIndexStore extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE = "universal_index_1.db";
    private static UniversalIndexStore instance;

    public static synchronized UniversalIndexStore get(Context context) {
        if (instance == null) instance = new UniversalIndexStore(context.getApplicationContext());
        return instance;
    }

    private UniversalIndexStore(Context context) {
        super(context, DATABASE, null, DATABASE_VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE concepts (" +
                "id TEXT PRIMARY KEY NOT NULL, label TEXT NOT NULL, " +
                "category TEXT NOT NULL, provenance TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE variants (" +
                "concept_id TEXT NOT NULL REFERENCES concepts(id) ON DELETE CASCADE, " +
                "folded TEXT NOT NULL, original TEXT NOT NULL, " +
                "PRIMARY KEY(concept_id, folded))");
        db.execSQL("CREATE INDEX idx_variants_folded ON variants(folded)");
        db.execSQL("CREATE TABLE inbox (" +
                "kind TEXT NOT NULL, folded TEXT NOT NULL, original TEXT NOT NULL, " +
                "quote TEXT NOT NULL, seen_count INTEGER NOT NULL DEFAULT 0, " +
                "created INTEGER NOT NULL, " +
                "PRIMARY KEY(kind,folded))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // A future migration must preserve research evidence and canonical IDs.
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Universal index schema migration required");
        }
    }

    public static String fold(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public static final class Counts {
        public final int concepts;
        public final int variants;
        public final int pending;
        Counts(int concepts, int variants, int pending) {
            this.concepts = concepts;
            this.variants = variants;
            this.pending = pending;
        }
    }

    public Counts counts() {
        SQLiteDatabase db = getReadableDatabase();
        return new Counts(count(db, "concepts"), count(db, "variants"), count(db, "inbox"));
    }

    private static int count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static String clean(String value, int max) {
        String s = value == null ? "" : value.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void writeConcept(SQLiteStatement concept, SQLiteStatement variant,
            String id, String label, String category, String source, String aliases) {
        String key = clean(id, 160);
        String head = clean(label, 260);
        if (key.isEmpty() || head.isEmpty()) return;
        concept.clearBindings();
        concept.bindString(1, key);
        concept.bindString(2, head);
        concept.bindString(3, clean(category, 100).isEmpty() ? "CONCEPT" : clean(category, 100));
        concept.bindString(4, clean(source, 240).isEmpty() ? "UTILIZATOR" : clean(source, 240));
        concept.executeInsert();
        List<String> forms = new ArrayList<>();
        forms.add(head);
        if (aliases != null) forms.addAll(Arrays.asList(aliases.split("\\|", -1)));
        for (String raw : forms) {
            String surface = clean(raw, 260);
            String normalized = fold(surface);
            if (normalized.isEmpty()) continue;
            variant.clearBindings();
            variant.bindString(1, key);
            variant.bindString(2, normalized);
            variant.bindString(3, surface);
            variant.executeInsert();
        }
    }

    public synchronized void addCanonical(
            String id, String label, String category, String source, String aliases
    ) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement concept = db.compileStatement(
                "INSERT OR IGNORE INTO concepts (id,label,category,provenance) VALUES (?,?,?,?)");
             SQLiteStatement variant = db.compileStatement(
                     "INSERT OR IGNORE INTO variants (concept_id,folded,original) VALUES (?,?,?)")) {
            writeConcept(concept, variant, id, label, category, source, aliases);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static final class ImportResult {
        public final int processed, invalid;
        ImportResult(int processed, int invalid) {
            this.processed = processed;
            this.invalid = invalid;
        }
    }

    /**
     * UTF-8 CSV with header: id,label,category,aliases,source.
     * Comma, semicolon and TAB delimiters are accepted. Alias variants use |.
     * A repeated canonical ID adds variants, never creates a duplicate concept.
     */
    public synchronized ImportResult importCsv(Reader reader) throws IOException {
        try (BufferedReader in = new BufferedReader(reader)) {
            String header = in.readLine();
            if (header == null) throw new IOException("Fișier CSV gol");
            if (header.startsWith("\uFEFF")) header = header.substring(1);
            char sep = header.indexOf('\t') >= 0 ? '\t'
                    : header.indexOf(';') >= 0 ? ';' : ',';
            List<String> fields = splitCsv(header, sep);
            Map<String, Integer> positions = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                positions.put(fold(fields.get(i)), i);
            }
            if (!positions.containsKey("id") || !positions.containsKey("label")) {
                throw new IOException("Prima linie trebuie să includă id,label.");
            }

            SQLiteDatabase db = getWritableDatabase();
            int processed = 0;
            int invalid = 0;
            db.beginTransaction();
            try (SQLiteStatement concept = db.compileStatement(
                    "INSERT OR IGNORE INTO concepts (id,label,category,provenance) VALUES (?,?,?,?)");
                 SQLiteStatement variant = db.compileStatement(
                    "INSERT OR IGNORE INTO variants (concept_id,folded,original) VALUES (?,?,?)")) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (processed + invalid >= 200000) {
                        throw new IOException("Import limitat la 200.000 intrări pe fișier.");
                    }
                    List<String> row = splitCsv(line, sep);
                    String id = field(row, positions, "id");
                    String label = field(row, positions, "label");
                    if (id.isEmpty() || label.isEmpty()) { invalid++; continue; }
                    try {
                        writeConcept(concept, variant, id, label,
                                field(row, positions, "category"),
                                field(row, positions, "source"),
                                field(row, positions, "aliases"));
                        processed++;
                    } catch (RuntimeException malformed) {
                        invalid++;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return new ImportResult(processed, invalid);
        }
    }

    private static String field(List<String> row, Map<String,Integer> positions, String key) {
        Integer i = positions.get(key);
        return i == null || i < 0 || i >= row.size() ? "" : row.get(i).trim();
    }

    static List<String> splitCsv(String line, char sep) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i+1) == '"') {
                    token.append('"'); i++;
                } else quoted = !quoted;
            } else if (c == sep && !quoted) {
                result.add(token.toString()); token.setLength(0);
            } else token.append(c);
        }
        if (quoted) throw new IllegalArgumentException("CSV quoted field incomplete");
        result.add(token.toString());
        return result;
    }

    public synchronized void addToInbox(String kind, String original, String quote) {
        String label = clean(original, 260);
        String category = clean(kind, 100);
        if (label.isEmpty() || category.isEmpty()) return;
        String folded = fold(label);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("kind", category);
            row.put("folded", folded);
            row.put("original", label);
            row.put("quote", clean(quote, 1300));
            row.put("seen_count", 0);
            row.put("created", System.currentTimeMillis());
            db.insertWithOnConflict("inbox", null, row, SQLiteDatabase.CONFLICT_IGNORE);
            db.execSQL("UPDATE inbox SET seen_count=seen_count+1 WHERE kind=? AND folded=?",
                    new Object[]{category, folded});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static final class Pending {
        public final String kind, original, quote;
        public final int seenCount;
        Pending(String kind, String original, String quote, int seenCount) {
            this.kind = kind; this.original = original;
            this.quote = quote; this.seenCount = seenCount;
        }
    }

    public List<Pending> pending(int limit) {
        List<Pending> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT kind,original,quote,seen_count FROM inbox " +
                        "ORDER BY seen_count DESC, created ASC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, Math.min(limit, 500)))})) {
            while (c.moveToNext()) result.add(new Pending(
                    c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
        }
        return result;
    }

    public synchronized void reject(Pending value) {
        getWritableDatabase().delete("inbox", "kind=? AND folded=?",
                new String[]{value.kind, fold(value.original)});
    }

    public synchronized void approve(Pending value, String id, String category) {
        if (value == null) return;
        String label = value.original;
        String canonicalId = clean(id, 160);
        if (canonicalId.isEmpty()) throw new IllegalArgumentException("ID canonic obligatoriu");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement concept = db.compileStatement(
                "INSERT OR IGNORE INTO concepts (id,label,category,provenance) VALUES (?,?,?,?)");
             SQLiteStatement variant = db.compileStatement(
                "INSERT OR IGNORE INTO variants (concept_id,folded,original) VALUES (?,?,?)")) {
            writeConcept(concept, variant, canonicalId, label, category,
                    "OCR • VALIDAT DE UTILIZATOR", "");
            db.delete("inbox", "kind=? AND folded=?", new String[]{value.kind, fold(label)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void seedChronology() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement concept = db.compileStatement(
                "INSERT OR IGNORE INTO concepts (id,label,category,provenance) VALUES (?,?,?,?)");
             SQLiteStatement variant = db.compileStatement(
                "INSERT OR IGNORE INTO variants (concept_id,folded,original) VALUES (?,?,?)")) {
            for (int year = 1; year <= 2100; year++) {
                writeConcept(concept, variant, "CHRON:YEAR:" + year, "An " + year,
                        "CRONOLOGIE", "GENERAT • verificare contextuală",
                        String.valueOf(year) + "|anul " + year + "|în anul " + year);
            }
            for (int century = 1; century <= 21; century++) {
                String roman = roman(century);
                writeConcept(concept, variant, "CHRON:CENTURY:" + century,
                        "Secolul " + roman, "CRONOLOGIE", "GENERAT • verificare contextuală",
                        "secolul " + roman + "|secolul al " + roman + "-lea|sec. " + roman +
                                "|sec " + roman);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String roman(int n) {
        StringBuilder b = new StringBuilder();
        int[] values = {10,9,5,4,1};
        String[] symbols = {"X","IX","V","IV","I"};
        for (int i=0;i<values.length;i++) while (n >= values[i]) {
            n -= values[i]; b.append(symbols[i]);
        }
        return b.toString();
    }

    /** Fully local: adds enabled canonical concepts as independent index nodes. */
    public TopicMap loadMap() {
        List<TopicNode> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT c.id,c.label,c.category,v.original " +
                        "FROM concepts c JOIN variants v ON v.concept_id=c.id " +
                        "WHERE c.active=1 ORDER BY c.id,v.folded", null)) {
            String previous = null;
            TopicNode node = null;
            while (c.moveToNext()) {
                String id = c.getString(0);
                if (!id.equals(previous)) {
                    String title = c.getString(1);
                    String category = c.getString(2);
                    node = new TopicNode("INDEX UNIVERSAL > " + category + " > " + id, title, 2);
                    node.enabled = true;
                    node.symbol = "I";
                    node.color = "CRONOLOGIE".equalsIgnoreCase(category)
                            ? Color.rgb(55, 147, 207) : Color.rgb(167, 121, 209);
                    result.add(node);
                    previous = id;
                }
                if (node != null) node.terms.add(c.getString(3));
            }
        }
        return new TopicMap("Indici canonici", "", result);
    }

    public void exportCsv(Writer output) throws IOException {
        output.write("id,label,category,aliases,source\n");
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT c.id,c.label,c.category,c.provenance,v.original " +
                        "FROM concepts c LEFT JOIN variants v ON v.concept_id=c.id " +
                        "ORDER BY c.id,v.folded", null)) {
            String id = null, label = "", category = "", provenance = "";
            List<String> variants = new ArrayList<>();
            while (c.moveToNext()) {
                String incoming = c.getString(0);
                if (id != null && !id.equals(incoming)) {
                    writeExportRow(output,id,label,category,provenance,variants);
                    variants.clear();
                }
                if (!incoming.equals(id)) {
                    id=incoming; label=c.getString(1); category=c.getString(2);
                    provenance=c.getString(3);
                }
                String surface=c.getString(4);
                if (surface != null && !fold(surface).equals(fold(label))) variants.add(surface);
            }
            if (id != null) writeExportRow(output,id,label,category,provenance,variants);
        }
        output.flush();
    }

    private static void writeExportRow(Writer writer, String id, String label,
            String category, String source, List<String> variants) throws IOException {
        writer.write(csv(id) + "," + csv(label) + "," + csv(category) + ","
                + csv(String.join("|",variants)) + "," + csv(source) + "\n");
    }
    private static String csv(String field) {
        return "\"" + (field == null ? "" : field).replace("\"","\"\"") + "\"";
    }
}
