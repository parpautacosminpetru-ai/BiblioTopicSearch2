"""Integrity checks for offline thematic dictionaries derived from nine syllabi.

These are part of the same lightweight SQL-test stage in GitHub Actions, and
do NOT need Android, Google services, or internet access.
"""
from pathlib import Path
import re
import unicodedata
import unittest

ASSETS = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "themes"


def normalized(term):
    term = term.strip()
    if term[:1] in "!?~-":
        term = term[1:].strip()
    term = unicodedata.normalize("NFD", term)
    term = "".join(c for c in term if unicodedata.category(c) != "Mn")
    term = re.sub(r"[^\w]+", " ", term.lower(), flags=re.UNICODE)
    return " ".join(term.split())


class ThemeAssetsTest(unittest.TestCase):
    def test_seven_curated_maps_no_old_medieval_duplicates(self):
        packs = sorted(ASSETS.glob("*.txt"))
        self.assertEqual(7, len(packs), [p.name for p in packs])
        medieval = ASSETS / "05_europa_medievala_unificata_2020_2026.txt"
        headings = [x for x in medieval.read_text(encoding="utf-8").splitlines()
                    if x.startswith("# ")]
        self.assertEqual(24, len(headings), "merge semantically duplicated medieval sections")
        self.assertEqual(
            1,
            sum("europa_medievala" in p.name for p in packs),
            "the three medieval syllabi must load as ONE map",
        )

    def test_every_map_has_unique_normalized_terms(self):
        for path in ASSETS.glob("*.txt"):
            with self.subTest(map=path.name):
                content = path.read_text(encoding="utf-8")
                titles = [line for line in content.splitlines() if line.startswith("# ")]
                self.assertGreater(len(titles), 0)
                seen = set()
                for line in content.splitlines():
                    if not line.strip() or line.startswith("# "):
                        continue
                    for raw in line.split("|"):
                        key = normalized(raw)
                        self.assertTrue(key, f"Empty term: {path.name}")
                        self.assertNotIn(key, seen, f"Duplicate {key} in {path.name}")
                        seen.add(key)
                self.assertGreater(len(seen), 20)

    def test_thirty_eight_romania_exam_subjects_preserved(self):
        filename = ASSETS / "08_stat_societate_mentalitati_romanesti_38_subiecte.txt"
        titles = [
            line[2:] for line in filename.read_text(encoding="utf-8").splitlines()
            if line.startswith("# ")
        ]
        self.assertEqual(38, len(titles))
        self.assertTrue(titles[0].startswith("01"))
        self.assertTrue(titles[-1].startswith("38"))

    def test_explicit_alert_levels_are_defined(self):
        names = ["01_studiul_izvoarelor_2026.txt",
                 "02_europa_moderna_2026.txt",
                 "03_istoria_bizantului_2026.txt"]
        for name in names:
            content = (ASSETS / name).read_text(encoding="utf-8")
            self.assertIn("!", content)
            self.assertIn("?", content)
            self.assertIn("-", content)


if __name__ == "__main__":
    unittest.main()
