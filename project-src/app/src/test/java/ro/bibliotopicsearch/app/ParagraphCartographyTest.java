package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public final class ParagraphCartographyTest {

    private static UniversalParagraphDetector.Detection d(
            int index,
            String subject,
            UniversalDetectionLexicon.Function function
    ) {
        return new UniversalParagraphDetector.Detection(
                index,
                subject + " este analizat în acest paragraf.",
                subject,
                function,
                UniversalDetectionLexicon.Function.UNKNOWN,
                0.92,
                0.82,
                Collections.emptyList(),
                EnumSet.noneOf(UniversalDetectionLexicon.Operator.class),
                Collections.emptyList()
        );
    }

    @Test
    public void narrowingCanCreateArbitraryDynamicDepth() {
        ParagraphCartography.Map map = ParagraphCartography.build(Arrays.asList(
                d(0, "inflația", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1, "efectele inflației", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(2, "efectele economice ale inflației", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(3, "efectele economice directe ale inflației", UniversalDetectionLexicon.Function.DEVELOPMENT)
        ));

        assertEquals(4, map.nodes().size());
        assertEquals(0, map.nodes().get(0).depth());
        assertEquals(1, map.nodes().get(1).depth());
        assertEquals(2, map.nodes().get(2).depth());
        assertEquals(3, map.nodes().get(3).depth());
        assertEquals(ParagraphCartography.Link.NARROWS, map.nodes().get(3).link());
        assertTrue(map.maxDepth() >= 3);
    }

    @Test
    public void repeatedEarlierSubjectCreatesReturnLink() {
        ParagraphCartography.Map map = ParagraphCartography.build(Arrays.asList(
                d(0, "inflația", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1, "șomajul", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(2, "inflația", UniversalDetectionLexicon.Function.DEVELOPMENT)
        ));

        ParagraphCartography.Node returned = map.nodes().get(2);
        assertEquals(ParagraphCartography.Link.RETURNS, returned.link());
        assertEquals(0, returned.parentParagraphIndex());
        assertEquals(0, returned.depth());
    }

    @Test
    public void explicitExampleBecomesChildSupportBranch() {
        ParagraphCartography.Map map = ParagraphCartography.build(Arrays.asList(
                d(0, "inflația", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1, "inflația", UniversalDetectionLexicon.Function.EXAMPLE)
        ));

        ParagraphCartography.Node example = map.nodes().get(1);
        assertEquals(ParagraphCartography.Link.EXEMPLIFIES, example.link());
        assertEquals(0, example.parentParagraphIndex());
        assertEquals(1, example.depth());
    }

    @Test
    public void unrelatedSubjectStartsNewRootBranch() {
        ParagraphCartography.Map map = ParagraphCartography.build(Arrays.asList(
                d(0, "fotosinteza", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1, "tectonica plăcilor", UniversalDetectionLexicon.Function.DEVELOPMENT)
        ));

        ParagraphCartography.Node shift = map.nodes().get(1);
        assertEquals(ParagraphCartography.Link.SHIFTS, shift.link());
        assertEquals(-1, shift.parentParagraphIndex());
        assertEquals(0, shift.depth());
    }

    @Test
    public void mapExposesGlobalSubjectAndNodeLookup() {
        ParagraphCartography.Map map = ParagraphCartography.build(Arrays.asList(
                d(0, "inflația", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(1, "efectele inflației", UniversalDetectionLexicon.Function.DEVELOPMENT),
                d(2, "inflația", UniversalDetectionLexicon.Function.CONCLUSION)
        ));

        assertTrue(map.globalSubject().toLowerCase().contains("infla"));
        assertNotNull(map.nodeForParagraph(1));
    }
}
