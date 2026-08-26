package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class UniversalParagraphDetectorTest {

    @Test
    public void detectsDefinitionAndLeadingSubject() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Fotosinteza este procesul prin care plantele transformă energia luminoasă în energie chimică."
        );

        assertEquals("Fotosinteza", detection.subject());
        assertEquals(UniversalDetectionLexicon.Function.DEFINITION, detection.function());
        assertTrue(detection.querySlots().contains(UniversalDetectionLexicon.Slot.WHAT));
    }

    @Test
    public void explicitTopicWinsOverInitialFrame() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "În ceea ce privește efectele economice ale inflației, acestea reduc puterea de cumpărare și modifică deciziile de consum."
        );

        assertEquals("efectele economice ale inflației", detection.subject());
        assertTrue(detection.subjectConfidence() > 0.9);
        assertTrue(detection.operators().contains(UniversalDetectionLexicon.Operator.TOPIC_FRAME));
    }

    @Test
    public void detectsCauseEffectAndRelevantQuerySlots() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Temperaturile ridicate accelerează evaporarea apei deoarece moleculele dobândesc mai multă energie; prin urmare, rata evaporării crește."
        );

        assertEquals(UniversalDetectionLexicon.Function.CAUSE_EFFECT, detection.function());
        assertTrue(detection.querySlots().contains(UniversalDetectionLexicon.Slot.WHY));
        assertTrue(detection.querySlots().contains(UniversalDetectionLexicon.Slot.EFFECT));
    }

    @Test
    public void detectsContrast() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Modelul A este rapid, însă modelul B este mai precis. În schimb, modelul A consumă mai puține resurse."
        );

        assertEquals(UniversalDetectionLexicon.Function.CONTRAST, detection.function());
        assertTrue(detection.operators().contains(UniversalDetectionLexicon.Operator.COMPARATIVE));
    }

    @Test
    public void keepsNegationAndModalityAsOperators() {
        UniversalParagraphDetector.Detection detection = UniversalParagraphDetector.detect(
                "Metoda nu poate produce rezultate stabile în toate condițiile."
        );

        assertTrue(detection.operators().contains(UniversalDetectionLexicon.Operator.NEGATION));
        assertTrue(detection.operators().contains(UniversalDetectionLexicon.Operator.MODALITY));
    }

    @Test
    public void splitsAndPreservesParagraphOrder() {
        String text = "Memoria de lucru este un sistem cu capacitate limitată.\n\n"
                + "De exemplu, repetarea poate menține temporar informația activă.";

        List<String> paragraphs = UniversalParagraphDetector.splitParagraphs(text);
        assertEquals(2, paragraphs.size());
        assertFalse(paragraphs.get(0).isEmpty());
        assertFalse(paragraphs.get(1).isEmpty());
    }
}
