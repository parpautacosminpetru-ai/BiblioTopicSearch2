"""Regression tests for the actual proof gate in source-reader SQL."""
import sqlite3
import re
import unittest
from pathlib import Path

PATH = Path(__file__).resolve().parents[1] / "app/src/main/java/ro/bibliotopicsearch/app"
SOURCE = (PATH / "IndexCoreCartographyReader.java").read_text(encoding="utf8")
DB = (PATH / "IndexCoreDatabase.java").read_text(encoding="utf8")

def sql_literals(java):
    return "".join(re.findall(r'"([^"]*)"', java))

def proof_join():
    part = SOURCE.split("final String completeProofJoin =",1)[1].split(";",1)[0]
    return sql_literals(part)

class PageProofTests(unittest.TestCase):
    def setup_db(self):
        db = sqlite3.connect(":memory:")
        db.executescript("""
            CREATE TABLE idx_original_pages (source_id TEXT, page TEXT, PRIMARY KEY(source_id,page));
            CREATE TABLE idx_page_coverage (source_id TEXT,page TEXT,is_complete INTEGER,PRIMARY KEY(source_id,page));
        """)
        fragment = DB.split("private static void createVerifiedCaptureSchema(SQLiteDatabase db)", 1)[1]
        fragment = fragment.split("public static final class SourceRecord", 1)[0]
        db.execute("CREATE TABLE idx_sources(source_id TEXT PRIMARY KEY)")
        for item in re.findall(r'db\.execSQL\((.*?)\);', fragment, re.S):
            sql = sql_literals(item)
            if "REFERENCES idx_original_pages" not in sql or True:
                db.execute(sql)
        db.execute("INSERT INTO idx_original_pages VALUES ('book','1')")
        db.execute("INSERT INTO idx_page_coverage VALUES ('book','1',1)")
        return db

    def test_old_complete_flag_must_not_count(self):
        db = self.setup_db()
        sql = "SELECT COUNT(*)" + proof_join()
        self.assertEqual(0, db.execute(sql, ("book",)).fetchone()[0])
        db.execute("""INSERT INTO idx_page_processing
            (source_id,page,ocr_digest,indexed_digest,verified_digest)
            VALUES ('book','1','hash','','')""")
        self.assertEqual(0, db.execute(sql, ("book",)).fetchone()[0])
        db.execute("UPDATE idx_page_processing SET indexed_digest='hash' WHERE page='1'")
        self.assertEqual(0, db.execute(sql, ("book",)).fetchone()[0])
        db.execute("UPDATE idx_page_processing SET verified_digest='hash' WHERE page='1'")
        self.assertEqual(1, db.execute(sql, ("book",)).fetchone()[0])
        db.execute("UPDATE idx_page_processing SET ocr_digest='different' WHERE page='1'")
        self.assertEqual(0, db.execute(sql, ("book",)).fetchone()[0])

if __name__ == "__main__":
    unittest.main()
