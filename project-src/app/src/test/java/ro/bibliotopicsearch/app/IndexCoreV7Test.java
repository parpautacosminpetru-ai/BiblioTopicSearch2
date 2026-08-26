package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class IndexCoreV7Test {

    @Test
    public void facetQueryParsesIntersectionExclusionAndPageRange() {
        FacetIntersectionQuery.Parsed parsed = FacetIntersectionQuery.parse(
                "DOMAIN=HISTORY + RELATION=CAUSE + PRIMARY=PERSON - RELATION=EFFECT + PAGE=120..190"
        );
        assertEquals(4, parsed.filters.size());
        assertEquals("120", parsed.pageFrom);
        assertEquals("190", parsed.pageTo);
        assertFalse(parsed.filters.get(0).exclude);
        assertTrue(parsed.filters.get(3).exclude);
        assertEquals("EFFECT", parsed.filters.get(3).value);
    }

    @Test
    public void romanianOutlineRecognizesEditorialLevels() {
        SourceOutlineDetector.Heading part = SourceOutlineDetector.detectOne("PARTEA II Reforma", 0);
        SourceOutlineDetector.Heading chapter = SourceOutlineDetector.detectOne("Capitolul 4 Cauzele", 1);
        SourceOutlineDetector.Heading numbered = SourceOutlineDetector.detectOne("2.3 Consecințe politice", 2);
        assertNotNull(part);
        assertNotNull(chapter);
        assertNotNull(numbered);
        assertEquals("PART", part.kind);
        assertEquals(0, part.depth);
        assertEquals("CHAPTER", chapter.kind);
        assertEquals(1, chapter.depth);
        assertEquals("NUMBERED", numbered.kind);
        assertEquals(2, numbered.depth);
    }

    @Test
    public void knownPageOccurrenceIsStableAcrossSessions() {
        String a = IndexCoreDatabase.occurrenceKey("e-a", "src-a", 100L, "42", 3, 7L, "F:CAUSE");
        String b = IndexCoreDatabase.occurrenceKey("e-a", "src-a", 999L, "42", 3, 7L, "F:CAUSE");
        assertEquals(a, b);
    }

    @Test
    public void unknownPageOccurrenceSeparatesSessions() {
        String a = IndexCoreDatabase.occurrenceKey("e-a", "src-a", 100L, "", 3, 0L, "F:CAUSE|B:abc");
        String b = IndexCoreDatabase.occurrenceKey("e-a", "src-a", 999L, "", 3, 0L, "F:CAUSE|B:abc");
        assertNotEquals(a, b);
    }

    @Test
    public void coreEntryIdsAreStableLongAndCollisionResistantComparedWithShortDisplayCodes() {
        String a = IndexCoreDatabase.coreEntryId("Martin Luther");
        String b = IndexCoreDatabase.coreEntryId("Martin Luther");
        String c = IndexCoreDatabase.coreEntryId("Jean Calvin");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertTrue(a.startsWith("e-"));
        assertTrue(a.length() >= 30);
    }
}