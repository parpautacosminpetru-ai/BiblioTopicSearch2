package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class MapProbeTest {
    private TopicNode node(String id,boolean enabled,String... terms){
        TopicNode n=new TopicNode(id,id,"Nod","Nod",1);
        n.enabled=enabled;n.terms.addAll(Arrays.asList(terms));return n;
    }
    private TopicMatcher.SearchPlan plan(TopicNode... nodes){
        return TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(nodes)),false,0,AppPrefs.MatchMode.EXACT);
    }
    @Test public void religionOnlyReturnsExplicitTermsWithOriginalOffsets(){
        TopicMatcher.SearchPlan p=plan(node("religie",true,"biserică","credință","preot","creștinism"),
                node("politică",false,"politică"),node("titlu",true));
        String raw="Politică, societate și instituție. BISERICĂ; credință, preoție, creștinism.";
        List<TopicMatcher.TextProbeHit> hits=TopicMatcher.probeText(raw,p);
        assertEquals(3,hits.size());
        assertEquals("BISERICĂ",hits.get(0).original);
        for(TopicMatcher.TextProbeHit h:hits){
            assertEquals("religie",h.node.profileId);
            assertEquals(raw.substring(h.start,h.end),h.original);
        }
    }
    @Test public void twoMapsMayMatchTheSameTextWithoutCrossContamination(){
        TopicMatcher.SearchPlan p=plan(node("religie",true,"biserică","preot"),node("istorie",true,"biserică","domnitor"));
        List<TopicMatcher.TextProbeHit> hits=TopicMatcher.probeText("biserică domnitor",p);
        assertEquals(3,hits.size());
        assertEquals("religie",hits.get(0).node.profileId);
        assertEquals("istorie",hits.get(1).node.profileId);
        assertEquals("istorie",hits.get(2).node.profileId);
    }
    @Test public void punctuationAndParagraphBarriersHold(){
        TopicMatcher.SearchPlan p=plan(node("religie",true,"credință creștină","creștină"));
        assertEquals(1,TopicMatcher.probeText("credință. Creștină",p).size());
        assertEquals(1,TopicMatcher.probeText("credință\n\ncreștină",p).size());
        assertEquals(2,TopicMatcher.probeText("credință creștină",p).size());
    }
    @Test public void verificationRejectsMisassignedTerms(){
        TopicMatcher.SearchPlan p=plan(node("religie",true,"preot"));
        TopicMatcher.CompiledTerm term=p.candidates(1,"preot").get(0);
        assertTrue(p.verifies(term,"PREOT",MatchHit.Kind.LITERAL));
        assertFalse(p.verifies(term,"politică",MatchHit.Kind.LITERAL));
        TopicMatcher.CompiledTerm foreign=new TopicMatcher.CompiledTerm("preot","preot",1,node("other",true,"preot"));
        assertFalse(p.verifies(foreign,"preot",MatchHit.Kind.LITERAL));
    }
}
