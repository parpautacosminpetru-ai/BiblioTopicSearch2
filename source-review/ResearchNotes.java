package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Notițe de cercetare confirmate manual, păstrate exclusiv în memoria dispozitivului. */
public final class ResearchNotes extends SQLiteOpenHelper {
    private static final String DATABASE="bibliotopicsearch_notes.db";

    public static final class Note {
        public long id;
        public long timestamp;
        public String source;
        public String page;
        public String term;
        public String themes;
        public String rule;
        public String excerpt;
        public String observation;
    }

    public ResearchNotes(Context context) {
        super(context,DATABASE,null,1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE research_notes ("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "timestamp INTEGER NOT NULL,"+
                "source TEXT NOT NULL,page TEXT NOT NULL,"+
                "term TEXT NOT NULL,themes TEXT NOT NULL,rule TEXT NOT NULL,"+
                "excerpt TEXT NOT NULL,observation TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_research_source ON research_notes(source)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        // Baza de date este conservată; migrațiile viitoare vor fi explicite.
    }

    public long add(String source,String page,String term,String themes,
                    String rule,String excerpt,String observation) {
        ContentValues values=new ContentValues();
        values.put("timestamp",System.currentTimeMillis());
        values.put("source",source);
        values.put("page",page);
        values.put("term",term);
        values.put("themes",themes);
        values.put("rule",rule);
        values.put("excerpt",excerpt);
        values.put("observation",observation);
        return getWritableDatabase().insertOrThrow("research_notes",null,values);
    }

    public int count() {
        try(Cursor cursor=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM research_notes",null)) {
            return cursor.moveToFirst()?cursor.getInt(0):0;
        }
    }

    public List<Note> recent(int limit) {
        List<Note> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,timestamp,source,page,term,themes,rule,excerpt,observation "+
                "FROM research_notes ORDER BY id DESC LIMIT "+Math.max(1,Math.min(limit,500)),null)) {
            while(c.moveToNext())result.add(note(c));
        }
        return result;
    }

    private Note note(Cursor c) {
        Note n=new Note();
        n.id=c.getLong(0);n.timestamp=c.getLong(1);n.source=c.getString(2);
        n.page=c.getString(3);n.term=c.getString(4);n.themes=c.getString(5);
        n.rule=c.getString(6);n.excerpt=c.getString(7);n.observation=c.getString(8);
        return n;
    }

    public String exportCsv() {
        StringBuilder csv=new StringBuilder("\uFEFFid,data,sursa,pagina,termen,teme,regula,fragment_OCR_de_verificat,observatii\n");
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,timestamp,source,page,term,themes,rule,excerpt,observation "+
                "FROM research_notes ORDER BY id",null)) {
            while(c.moveToNext()) {
                csv.append(c.getLong(0)).append(',');
                csv.append(csvValue(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT)
                        .format(new Date(c.getLong(1)))));
                for(int i=2;i<=8;i++)csv.append(',').append(csvValue(c.getString(i)));
                csv.append('\n');
            }
        }
        return csv.toString();
    }

    private static String csvValue(String value) {
        if(value==null)return "\"\"";
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
