package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class HistoricalLexiconColorTest {
    @Test public void everyCategoryOwnsADifferentOpaqueColor(){
        Set<Integer> colors=new HashSet<>();
        for(int i=0;i<448;i++){
            int color=HistoricalLexiconColors.color(i);
            assertEquals(255,color>>>24);assertTrue("duplicate "+i,colors.add(color));
        }
        assertEquals(448,colors.size());
    }
    @Test public void mapCodesAreUniqueAndStable(){
        Set<String> codes=new HashSet<>();
        for(int i=0;i<448;i++){
            String id="lexhist-v1-"+(i+1);
            assertEquals(i,HistoricalLexiconColors.indexOf(id));assertTrue(codes.add(HistoricalLexiconColors.label(id)));
        }
        assertEquals("H001",HistoricalLexiconColors.label("lexhist-v1-1"));
        assertEquals("H448",HistoricalLexiconColors.label("lexhist-v1-448"));
        assertEquals("",HistoricalLexiconColors.label("personal-map"));
    }
    @Test public void exactPrefixAndContainsPreserveAllMapMemberships(){
        TermIndex<String> index=new TermIndex<>();
        index.add("an","A");index.add("an","B");index.add("anul","C");
        assertEquals(java.util.Arrays.asList("A","B"),index.exact("an"));
        assertEquals(java.util.Arrays.asList("A","B","C"),index.prefix("anului"));
        assertEquals(java.util.Arrays.asList("A","B","C"),index.contains("anulan"));
    }
}
