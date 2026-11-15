"""CI checks for exhaustive but non-duplicated literal thematic coverage.

One expression may legitimately belong to multiple EXAM SUBJECTS; this is
NOT a duplicate OCR highlight (MainActivity consolidates one screen region).
Exact duplicates within one subject are always invalid.
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


def read_sections(path):
    sections = []
    heading = None
    words = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("# "):
            if heading is not None:
                sections.append((heading, words))
            heading = line[2:]
            words = []
        elif heading is not None:
            words.extend(t.strip() for t in line.split("|") if t.strip())
    if heading is not None:
        sections.append((heading, words))
    return sections


class ThemeAssetsTest(unittest.TestCase):
    def test_seven_curated_maps_and_only_one_medieval_map(self):
        packs = sorted(ASSETS.glob("*.txt"))
        self.assertEqual(7, len(packs), [p.name for p in packs])
        self.assertEqual(1, sum("europa_medievala" in p.name for p in packs))
        self.assertEqual(24, len(read_sections(ASSETS / "05_europa_medievala_unificata_2020_2026.txt")))

    def test_all_144_sections_covered_by_ten_or_more_independent_exact_anchors(self):
        total_sections = 0
        total_associations = 0
        globally_unique = set()
        for path in ASSETS.glob("*.txt"):
            sections = read_sections(path)
            self.assertTrue(sections)
            seen_titles = set()
            for title, terms in sections:
                with self.subTest(map=path.name, section=title):
                    self.assertNotIn(title, seen_titles)
                    seen_titles.add(title)
                    seen = set()
                    for term in terms:
                        self.assertTrue(term[:1] in "!?~-"
                                        or term[:1].isalnum(), term)
                        key = normalized(term)
                        self.assertTrue(key, "Empty OCR expression")
                        self.assertNotIn(key, seen, "Repeated within one subtopic: " + key)
                        self.assertFalse(re.fullmatch(r"![0-9]+", term),
                                         "Standalone years should not vibrate")
                        seen.add(key)
                        globally_unique.add(key)
                    self.assertGreaterEqual(len(terms), 10,
                            "Every subtopic needs multiple historical entry points")
                    if "bibliograf" in title.lower() or "repere pentru cuprinsuri" in title.lower():
                        self.assertTrue(all(t.startswith("-") for t in terms),
                                        "Bibliography only highlights, never vibrates")
                    total_associations += len(terms)
                    total_sections += 1
        self.assertEqual(144, total_sections)
        self.assertGreaterEqual(total_associations, 2200)
        self.assertGreaterEqual(len(globally_unique), 2100)

    def test_thirty_eight_romania_exam_subjects_are_distinct(self):
        subject_path = ASSETS / "08_stat_societate_mentalitati_romanesti_38_subiecte.txt"
        sections = read_sections(subject_path)
        self.assertEqual(38, len(sections))
        for number, (title, terms) in enumerate(sections, start=1):
            self.assertTrue(title.startswith(f"{number:02d} — "), title)
            self.assertGreaterEqual(len(terms), 10)

    def test_priority_context_and_visual_only_are_explicit(self):
        combined = ""
        for path in ASSETS.glob("*.txt"):
            s = path.read_text(encoding="utf-8")
            combined += s + "\n"
            self.assertRegex(s, r"(?:^|[|\s])\?[^|\s]")
        self.assertRegex(combined, r"(?:^|[|\s])![^|\s]")
        self.assertRegex(combined, r"(?:^|[|\s])-[^|\s]")


if __name__ == "__main__":
    unittest.main()
