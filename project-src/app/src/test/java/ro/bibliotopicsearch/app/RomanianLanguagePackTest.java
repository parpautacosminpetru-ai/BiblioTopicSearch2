package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class RomanianLanguagePackTest {

    @Test
    public void normalizesOldCedillaAndMissingDiacriticForms() {
        assertEquals("Știință și țară", RomanianLanguagePack.normalizeOrthography("Ştiinţă și ţară"));
        assertEquals("stiinta si tara", RomanianLanguagePack.fold("Știință și țară"));
    }

    @Test
    public void coversRomanianClosedGrammarClasses() {
        assertTrue(RomanianLanguagePack.isFunctionWord("către"));
        assertTrue(RomanianLanguagePack.isFunctionWord("aceștia"));
        assertTrue(RomanianLanguagePack.isFunctionWord("fiindcă"));
        assertTrue(RomanianLanguagePack.isCoreference("aceasta"));
        assertTrue(RomanianLanguagePack.isNegation("niciodată"));

        RomanianLanguagePack.Analysis prep = RomanianLanguagePack.analyze("către");
        assertTrue(prep.possiblePartsOfSpeech.contains(RomanianLanguagePack.PartOfSpeech.PREPOSITION));
        RomanianLanguagePack.Analysis pron = RomanianLanguagePack.analyze("aceștia");
        assertTrue(pron.possiblePartsOfSpeech.contains(RomanianLanguagePack.PartOfSpeech.PRONOUN));
        RomanianLanguagePack.Analysis numeral = RomanianLanguagePack.analyze("1536");
        assertTrue(numeral.possiblePartsOfSpeech.contains(RomanianLanguagePack.PartOfSpeech.NUMERAL));
    }

    @Test
    public void groupsProductiveNominalAndAdjectivalInflections() {
        assertEquals(RomanianMorphology.familyKey("reformă"), RomanianMorphology.familyKey("reformei"));
        assertEquals(RomanianMorphology.familyKey("reformă"), RomanianMorphology.familyKey("reformelor"));
        assertEquals(RomanianMorphology.familyKey("medical"), RomanianMorphology.familyKey("medicală"));
        assertEquals(RomanianMorphology.familyKey("medical"), RomanianMorphology.familyKey("medicale"));
        assertEquals(RomanianMorphology.familyKey("protestant"), RomanianMorphology.familyKey("protestante"));
    }

    @Test
    public void broadKeyLinksProductiveVerbFormsWithoutMakingThemIdentityKeys() {
        String a = RomanianMorphology.broadKey("analizează");
        assertEquals(a, RomanianMorphology.broadKey("analizând"));
        assertEquals(a, RomanianMorphology.broadKey("analizat"));
        assertFalse(a.isEmpty());
    }

    @Test
    public void familyMatcherReturnsExactInflectedOcrSurface() {
        String text = "Cauzele reformei protestante au fost discutate în secțiune.";
        assertEquals(
                "reformei protestante",
                RomanianFamilyMatcher.findSurface(text, "reforma protestantă")
        );
    }

    @Test
    public void domainRoutingWorksOnInflectedRomanian() {
        List<String> facets = RomanianDomainLexicon.facetsFor(
                "Cauzele reformei protestante sunt analizate în contextul secolului al XVI-lea."
        );
        assertTrue(facets.contains("DOMAIN=HISTORY"));
        assertTrue(facets.contains("DOMAIN=RELIGION"));
    }

    @Test
    public void paragraphDetectorDoesNotPromoteFunctionWordsAsSubjectFallback() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Către final, reformele economice au produs consecințe sociale importante.", 0
        );
        assertFalse(RomanianLanguagePack.isFunctionWord(detection.subject()));
    }
}
