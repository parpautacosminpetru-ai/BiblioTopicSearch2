package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PageEvidenceTest{
    private PageEvidence.Reading<String> r(String id,float x,String value){
        return new PageEvidence.Reading<>(id,new float[]{x,10,x+60,30},value);
    }
    @Test public void sameTextAndMapMustAgreeAtSamePosition(){
        assertEquals(Arrays.asList("enhanced"),PageEvidence.agree(Arrays.asList(r("religie/preot",10,"raw")),
                Arrays.asList(r("religie/preot",12,"enhanced"),r("istorie/preot",12,"other"),r("religie/preot",150,"far"))));
    }
    @Test public void oneReadingCannotConfirmTwoOccurrences(){
        assertEquals(1,PageEvidence.agree(Arrays.asList(r("a",10,"raw")),
                Arrays.asList(r("a",10,"first"),r("a",11,"second"))).size());
    }
    @Test public void allWordsOfExpressionMustAlign(){
        assertEquals(0f,PageEvidence.overlap(new float[]{0,0,20,20,30,0,50,20},
                new float[]{0,0,20,20,90,0,110,20}),.001f);
        assertTrue(PageEvidence.agree(Collections.emptyList(),Arrays.asList(r("a",10,"candidate"))).isEmpty());
    }
}
