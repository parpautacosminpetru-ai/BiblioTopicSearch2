"""Regression tests executing the SQL assembled by IndexCoreCartographyReader.java.

The queries are extracted from the actual Java source so the tests detect future
changes to the application's SQL, not a separately copied imitation.
"""
from pathlib import Path
import re
import sqlite3
import unittest

SOURCE = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/ro/bibliotopicsearch/app/IndexCoreCartographyReader.java"
).read_text(encoding="utf-8")


def literals(fragment):
    return "".join(re.findall(r'"([^"]*)"', fragment))


def method_sql(name, limit=1000):
    match = re.search(r"public static List<\w+>\s+" + name + r"\s*\(", SOURCE)
    if not match:
        raise AssertionError("Method not found: " + name)
    definition = re.search(r"\bString sql\s*=\s*(.*?);", SOURCE[match.end():], re.S)
    if not definition:
        raise AssertionError("SQL not found: " + name)
    sql = literals(definition.group(1))
    return sql + str(limit) if sql.rstrip().endswith("LIMIT") else sql


def scoped_complete_sql():
    join = SOURCE.split("final String completeProofJoin =", 1)[1].split(";", 1)[0]
    start = SOURCE.index('"SELECT COUNT(DISTINCT CAST(c.page AS INTEGER))"')
    end = SOURCE.index("new String[]{sourceId", start)
    return ("SELECT COUNT(DISTINCT CAST(c.page AS INTEGER))"
            + literals(join) + literals(SOURCE[start:end])[len("SELECT COUNT(DISTINCT CAST(c.page AS INTEGER))"):])


def connect():
    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE idx_page_coverage(source_id TEXT, page TEXT, is_complete INTEGER);
        CREATE TABLE idx_original_pages(source_id TEXT, page TEXT);
        CREATE TABLE idx_page_processing(
            source_id TEXT, page TEXT,
            ocr_digest TEXT, indexed_digest TEXT, verified_digest TEXT
        );
        CREATE TABLE idx_semantic_units(
            unit_id TEXT PRIMARY KEY,
            source_id TEXT, page TEXT, page_key TEXT, paragraph_index INTEGER, unit_kind TEXT
        );
        CREATE TABLE idx_unit_topics(unit_id TEXT, topic_path TEXT, topic_title TEXT);
        CREATE TABLE idx_intersections(
            unit_id TEXT, left_topic_path TEXT, right_topic_path TEXT
        );
    """)
    return db


class CartographySqlRegressionTest(unittest.TestCase):
    def test_scope_does_not_count_unrelated_pages_or_alias_twice(self):
        db = connect()
        db.executemany(
            "INSERT INTO idx_page_coverage VALUES (?, ?, ?)",
            [("src", str(p), 1) for p in list(range(1, 6)) + list(range(11, 16))]
            + [("src", "001", 1), ("src", "6alpha", 1), ("src", "7", 0)],
        )
        # Only completed pages with archived, indexed and human-verified
        # evidence may count toward the exhaustivity percentage.
        for page in [str(p) for p in list(range(1, 6)) + list(range(11, 16))] + ["001"]:
            db.execute("INSERT INTO idx_original_pages VALUES ('src',?)", (page,))
            db.execute("INSERT INTO idx_page_processing VALUES ('src',?,'h','h','h')", (page,))
            db.execute("INSERT INTO idx_semantic_units VALUES (?,?,?,?,?,?)",
                       ("proof-"+page,"src",page,"page:"+page,0,"PARAGRAPH"))
        completed = db.execute(scoped_complete_sql(), ("src", "1", "10")).fetchone()[0]
        self.assertEqual(5, completed)
        db.close()

    def test_distinct_captures_same_page_local_index_are_not_collapsed(self):
        db = connect()
        db.executemany(
            "INSERT INTO idx_semantic_units VALUES (?, ?, ?, ?, ?, ?)",
            [("u1", "src", "1", "page:1", 0, "PARAGRAPH"),
             ("u2", "src", "1", "page:1", 0, "PARAGRAPH")],
        )
        db.executemany(
            "INSERT INTO idx_unit_topics VALUES (?, ?, ?)",
            [("u1", "politic", "Politic"), ("u2", "politic", "Politic")],
        )

        topic = db.execute(method_sql("topicsForSource"), ("src",)).fetchone()
        self.assertEqual(2, topic[2])
        matrix = db.execute(method_sql("matrix")).fetchone()
        self.assertEqual(2, matrix[3])
        range_row = db.execute(method_sql("coverageRanges"), ("src",)).fetchone()
        self.assertEqual(2, range_row[3])
        db.close()

    def test_proven_crossing_is_not_reported_as_co_presence_in_reverse_order(self):
        db = connect()
        db.execute(
            "INSERT INTO idx_semantic_units VALUES ('u1','src','1','page:1',0,'CLAUSE')"
        )
        db.executemany(
            "INSERT INTO idx_unit_topics VALUES (?, ?, ?)",
            [("u1", "Zoo", "Zoo"), ("u1", "apple", "Apple")],
        )
        db.execute(
            "INSERT INTO idx_intersections VALUES ('u1','apple','Zoo')"
        )
        rows = db.execute(method_sql("coPresence"), ("src",)).fetchall()
        self.assertEqual([], rows)

        # A second unit with no proven crossing is still legitimate co-presence.
        db.execute(
            "INSERT INTO idx_semantic_units VALUES ('u2','src','2','page:2',0,'CLAUSE')"
        )
        db.executemany(
            "INSERT INTO idx_unit_topics VALUES (?, ?, ?)",
            [("u2", "Zoo", "Zoo"), ("u2", "apple", "Apple")],
        )
        rows = db.execute(method_sql("coPresence"), ("src",)).fetchall()
        self.assertEqual(1, len(rows))
        self.assertEqual(1, rows[0][2])
        db.close()


if __name__ == "__main__":
    unittest.main()
