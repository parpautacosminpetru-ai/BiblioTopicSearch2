package ro.bibliotopicsearch.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DictionaryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "bibliotopicsearch_dictionary.db";
    private static final int DB_VERSION = 1;

    public static final class Entry {
        public final String term;
        public final String definition;
        public final String synonyms;
        public final String antonyms;
        public final String source;

        Entry(String term, String definition, String synonyms, String antonyms, String source) {
            this.term = term;
            this.definition = definition;
            this.synonyms = synonyms;
            this.antonyms = antonyms;
            this.source = source;
        }
    }

    public DictionaryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE entries (" +
                "term_key TEXT PRIMARY KEY," +
                "term TEXT NOT NULL," +
                "definition TEXT," +
                "synonyms TEXT," +
                "antonyms TEXT," +
                "source TEXT" +
                ")"
        );
        db.execSQL("CREATE INDEX idx_entries_term ON entries(term)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS entries");
        onCreate(db);
    }

    public long count() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM entries", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public void clearAll() {
        getWritableDatabase().delete("entries", null, null);
    }

    public Entry lookup(String term) {
        if (term == null || term.trim().isEmpty()) return null;
        String key = key(term);

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                "entries",
                new String[]{"term", "definition", "synonyms", "antonyms", "source"},
                "term_key=?",
                new String[]{key},
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) return null;
            return new Entry(
                    cursor.getString(0),
                    empty(cursor.getString(1)),
                    empty(cursor.getString(2)),
                    empty(cursor.getString(3)),
                    empty(cursor.getString(4))
            );
        }
    }

    public int importCsv(Context context, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IllegalArgumentException("Nu pot deschide fișierul.");

        int imported = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String first = reader.readLine();
            if (first == null) return 0;

            List<String> header = parseCsvLine(first);
            boolean hasHeader = !header.isEmpty() &&
                    header.get(0).trim().toLowerCase(Locale.ROOT).contains("term");

            if (!hasHeader) {
                imported += insertRecord(db, header);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                imported += insertRecord(db, parseCsvLine(line));
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return imported;
    }

    private int insertRecord(SQLiteDatabase db, List<String> fields) {
        if (fields.isEmpty()) return 0;
        String term = get(fields, 0).trim();
        if (term.isEmpty()) return 0;

        Object[] args = new Object[] {
                key(term),
                term,
                get(fields, 1),
                get(fields, 2),
                get(fields, 3),
                get(fields, 4)
        };

        db.execSQL(
                "INSERT OR REPLACE INTO entries(term_key,term,definition,synonyms,antonyms,source) " +
                "VALUES(?,?,?,?,?,?)",
                args
        );
        return 1;
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String get(List<String> values, int index) {
        return index < values.size() ? values.get(index).trim() : "";
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static String key(String value) {
        return TopicMatcher.normalize(value, true);
    }
}
