"""Tests create the facsimile schema directly from Java's SQL expressions."""
import re
import sqlite3
import unittest
from pathlib import Path

SRC = (Path(__file__).resolve().parents[1]
       / "app/src/main/java/ro/bibliotopicsearch/app/IndexCoreDatabase.java"
       ).read_text(encoding="utf8")

def schema():
    body = SRC.split("private static void createFacsimileSchema(SQLiteDatabase db)", 1)[1].split(
        "public static final class SourceRecord", 1)[0]
    sql = []
    for expression in re.findall(r'db\.execSQL\((.*?)\);', body, re.S):
        strings = re.findall(r'"([^"]*)"', expression)
        sql.append("".join(strings))
    return sql

def connect():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("CREATE TABLE idx_sources (source_id TEXT PRIMARY KEY)")
    for statement in schema():
        db.execute(statement)
    return db

class FacsimileSchemaTests(unittest.TestCase):
    def test_schema_is_extracted_and_pages_are_immutable_by_primary_key(self):
        db = connect()
        db.execute("INSERT INTO idx_sources VALUES ('book')")
        db.execute("""INSERT INTO idx_original_documents
                   VALUES ('book','sha','x/original.pdf','Book',5,1)""")
        db.execute("""INSERT INTO idx_original_pages
                   VALUES ('book','12','PDF','sha','x/original.pdf',0,1)""")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("""INSERT INTO idx_original_pages
                       VALUES ('book','12','IMAGE','different','x/image.jpg',-1,2)""")
        db.execute("""INSERT INTO idx_original_ocr_lines
                   VALUES ('book','12',0,'Text original OCR',0.1,0.2,0.8,0.3)""")
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM idx_original_ocr_lines").fetchone()[0])
        db.execute("DELETE FROM idx_sources WHERE source_id='book'")
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM idx_original_pages").fetchone()[0])
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM idx_original_ocr_lines").fetchone()[0])

    def test_missing_original_page_cannot_accept_orphan_ocr(self):
        db = connect()
        db.execute("INSERT INTO idx_sources VALUES ('book')")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("""INSERT INTO idx_original_ocr_lines
                       VALUES ('book','999',0,'orphan',0,0,1,1)""")

    def test_transaction_abort_keeps_previous_ocr(self):
        db = connect()
        db.execute("INSERT INTO idx_sources VALUES ('book')")
        db.execute("""INSERT INTO idx_original_pages
                   VALUES ('book','1','IMAGE','sha','x/1.jpg',-1,1)""")
        db.execute("""INSERT INTO idx_original_ocr_lines
                   VALUES ('book','1',0,'verified',0,0,1,1)""")
        try:
            with db:
                db.execute("DELETE FROM idx_original_ocr_lines WHERE source_id='book'")
                db.execute("""INSERT INTO idx_original_ocr_lines
                           VALUES ('book','1',0,'replacement',0,0,1,1)""")
                raise RuntimeError("interrupt")
        except RuntimeError:
            pass
        self.assertEqual("verified", db.execute(
            "SELECT raw_text FROM idx_original_ocr_lines WHERE source_id='book'").fetchone()[0])

if __name__ == "__main__":
    unittest.main()
