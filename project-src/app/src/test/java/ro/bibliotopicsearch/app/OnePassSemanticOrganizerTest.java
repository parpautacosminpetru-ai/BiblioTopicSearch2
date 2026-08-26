package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public final class OnePassSemanticOrganizerTest {

    private static UniversalParagraphDetector.Detection d(
            int index,
            String paragraph,
            String subject,
            UniversalDetectionLexicon.Function function
    ) {
        return new UniversalParagraphDetector.Detection(
                index,
                paragraph,
                subject,
                function,
                UniversalDetectionLexicon.Function.UNKNOWN,
                0.92,
                0.84,
                Collections.emptyList(),
                EnumSet.noneOf(UniversalDetectionLexicon.Operator.class),
                Collections.emptyList()
        );
    }

    @Test
    public void repeatedOcrFramesCollapseIntoOneSemanticParagraph() {
        OnePassSemanticOrganizer.beginSession();
        UniversalParagraphDetector.Detection paragraph = d(
                0,
                "Inflația reduce treptat puterea de cumpărare a gospodăriilor.",
                "inflația",
                UniversalDetectionLexicon.Function.CAUSE_EFFECT
        );

        OnePassSemanticOrganizer.ingest(Collections.singletonList(paragraph), null);
        OnePassSemanticOrganizer.ingest(Collections.singletonList(paragraph), null);
        OnePassSemanticOrganizer.ingest(Collections.singletonList(paragraph), null);

        OnePassSemanticOrganizer.LiveState live = OnePassSemanticOrganizer.liveState();
        assertEquals(1, live.uniqueParagraphs());
        assertTrue(live.duplicatesMerged() >= 2);

        OnePassSemanticOrganizer.Snapshot snapshot = OnePassSemanticOrganizer.finishSession();
        assertEquals(1, snapshot.uniqueParagraphs());
        assertTrue(snapshot.paragraphs().get(0).sightings() >= 3);
    }

    @Test
    public void finalOrganizationPreservesDynamicHierarchyInOnePass() {
        OnePassSemanticOrganizer.beginSession();
        OnePassSemanticOrganizer.ingest(Arrays.asList(
                d(0,
                        "Inflația este fenomenul economic analizat la nivel general.",
                        "inflația",
                        UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1,
                        "Efectele inflației includ consecințe multiple asupra economiei și populației.",
                        "efectele inflației",
                        UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(2,
                        "Efectele economice ale inflației modifică veniturile reale, consumul și economisirea.",
                        "efectele economice ale inflației",
                        UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(3,
                        "Efectele economice directe ale inflației reduc imediat puterea de cumpărare.",
                        "efectele economice directe ale inflației",
                        UniversalDetectionLexicon.Function.DEVELOPMENT)
        ), null);

        OnePassSemanticOrganizer.Snapshot snapshot = OnePassSemanticOrganizer.finishSession();
        assertEquals(4, snapshot.uniqueParagraphs());
        assertTrue(snapshot.maxDepth() >= 3);
        assertEquals(0, snapshot.paragraphs().get(0).depth());
        assertEquals(1, snapshot.paragraphs().get(1).depth());
        assertEquals(2, snapshot.paragraphs().get(2).depth());
        assertEquals(3, snapshot.paragraphs().get(3).depth());
    }

    @Test
    public void researchAnswerAndClaimsAreMaterializedAtFinalization() {
        OnePassSemanticOrganizer.beginSession();
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Inflația poate reduce puterea de cumpărare și conduce la scăderea consumului.",
                0
        );
        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(
                "Care sunt efectele inflației?",
                null
        );

        OnePassSemanticOrganizer.ingest(Collections.singletonList(detection), profile);
        OnePassSemanticOrganizer.Snapshot snapshot = OnePassSemanticOrganizer.finishSession();

        assertEquals(1, snapshot.uniqueParagraphs());
        assertTrue(snapshot.claimCount() >= 1);
        assertFalse(snapshot.paragraphs().get(0).claims().isEmpty());
        assertFalse(snapshot.paragraphs().get(0).answerSegment().isEmpty());
        assertTrue(snapshot.paragraphs().get(0).answerScore() > 0.0);
        assertFalse(snapshot.bestAnswerSegment().isEmpty());
    }

    @Test
    public void lateSemanticFrameCannotReopenFinishedSession() {
        OnePassSemanticOrganizer.beginSession();
        OnePassSemanticOrganizer.ingest(Collections.singletonList(d(
                0,
                "Fotosinteza transformă energia luminoasă în energie chimică.",
                "fotosinteza",
                UniversalDetectionLexicon.Function.DEFINITION
        )), null);

        OnePassSemanticOrganizer.Snapshot first = OnePassSemanticOrganizer.finishSession();
        assertNotNull(first);
        assertEquals(1, first.uniqueParagraphs());
        assertFalse(OnePassSemanticOrganizer.isActive());

        OnePassSemanticOrganizer.ingest(Collections.singletonList(d(
                0,
                "Tectonica plăcilor explică mișcarea litosferei.",
                "tectonica plăcilor",
                UniversalDetectionLexicon.Function.EXPLANATION
        )), null);

        assertEquals(1, OnePassSemanticOrganizer.latestFinished().uniqueParagraphs());
        assertFalse(OnePassSemanticOrganizer.isActive());
    }
}
