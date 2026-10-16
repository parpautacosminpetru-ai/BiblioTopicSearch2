package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The OCR search never infers new assertions from a lexical match. */
public final class SourceArchiveStoreTest {
    @Test
    public void sourceFilesUseOpaqueStableNonUserControlledDirectory() {
        String one = SourceArchiveStore.sourceKey("source-1");
        assertEquals(one, SourceArchiveStore.sourceKey("source-1"));
        assertEquals(32, one.length());
        assertTrue(one.matches("[0-9a-f]{32}"));
        assertNotEquals(one, SourceArchiveStore.sourceKey("source-2"));
        assertNotEquals(one, SourceArchiveStore.sourceKey("../../another-file"));
    }

    @Test
    public void lexicalMatchesPreserveWholeWordBoundaries() {
        assertTrue(SourceArchiveStore.matchesAllWords(
                "Împăratul a interzis negustorilor accesul.", "negustorilor împăratul"));
        assertFalse(SourceArchiveStore.matchesAllWords(
                "Împăratul a interzis negustorilor accesul.", "negustori împăratul"));
        assertFalse(SourceArchiveStore.matchesAllWords(
                "Comercianții se află aici.", "comer"));
    }

    @Test
    public void foldingDiacriticsDoesNotRewriteOriginal() {
        String original = "Împăratul și comerțul.";
        assertTrue(SourceArchiveStore.matchesAllWords(original, "imparatul COMERTUL"));
        assertEquals("Împăratul și comerțul.", original);
        assertFalse(SourceArchiveStore.matchesAllWords(original, "armată"));
    }

    @Test
    public void noQueryCannotBecomeAnUnjustifiedMatch() {
        assertFalse(SourceArchiveStore.matchesAllWords("oricare pasaj", ""));
        assertFalse(SourceArchiveStore.matchesAllWords("oricare pasaj", null));
        assertFalse(SourceArchiveStore.matchesAllWords(null, "pasaj"));
    }
}
