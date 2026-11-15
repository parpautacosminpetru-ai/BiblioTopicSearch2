package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.List;

public final class UniversalIndexFormatTest {
    @Test public void aliasesAreCanonicallyFoldedWithoutConflatingDifferentIds() {
        assertEquals("armata", UniversalIndexStore.fold("Armáta"));
        assertEquals("sec. xix", UniversalIndexStore.fold("SEC. XIX"));
        assertEquals("", UniversalIndexStore.fold(null));
    }

    @Test public void csvPreservesQuotedAliasesAndEmbeddedSeparator() {
        List<String> values = UniversalIndexStore.splitCsv(
                "Q42,\"Ana, Maria\",PERSOANĂ,\"Ana M.|Maria Ana\",GND", ',');
        assertEquals(5, values.size());
        assertEquals("Ana, Maria",values.get(1));
        assertEquals("Ana M.|Maria Ana",values.get(3));
    }

    @Test public void csvSupportsSemicolonAndEscapedQuote() {
        List<String> values = UniversalIndexStore.splitCsv(
                "GND:23;\"Ioan \"\"cel Mare\"\"\";PERSOANĂ;\"Ioan|Iohannes\";GND",';');
        assertEquals(5,values.size());
        assertEquals("Ioan \"cel Mare\"",values.get(1));
        assertEquals("Ioan|Iohannes",values.get(3));
    }

    @Test public void separateCanonicalIndicesOnSameSurfaceAreNotCollapsed() {
        TopicNode a = new TopicNode("PERSOANĂ:A", "Maria", 1);
        TopicNode b = new TopicNode("ORAȘ:B", "Maria", 1);
        a.enabled=true;b.enabled=true;
        a.terms.add("Maria");b.terms.add("Maria");
        DeterministicMultiIndexMatcher.Plan plan =
                DeterministicMultiIndexMatcher.compile(
                        new TopicMap("test","",java.util.Arrays.asList(a,b)));
        assertEquals(2,DeterministicMultiIndexMatcher.allOccurrences(plan,"Maria").size());
        LiveEvidenceAnalyzer.Snapshot result =
                LiveEvidenceAnalyzer.inspect(java.util.Collections.singletonList("Maria"),plan);
        assertEquals(1,result.overlaps());
        assertTrue(result.cooccurrences()==0);
    }
}
