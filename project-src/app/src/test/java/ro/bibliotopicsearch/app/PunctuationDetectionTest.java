package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PunctuationDetectionTest {
    private TopicNode node(String id,String... terms){
        TopicNode n=new TopicNode(id,id,"N","N",1);
        n.enabled=true;n.terms.addAll(Arrays.asList(terms));return n;
    }
    private TopicMatcher.SearchPlan plan(TopicNode... nodes){
        return TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(nodes)),false,0,AppPrefs.MatchMode.EXACT);
    }
    private List<String> terms(TopicMatcher.SearchPlan plan,String text){
        List<String> out=new ArrayList<>();
        for(TopicMatcher.TextProbeHit h:TopicMatcher.probeText(text,plan)){
            assertEquals(text.substring(h.start,h.end),h.original);
            out.add(h.node.profileId+":"+h.term);
        }
        return out;
    }
    @Test public void everyConfiguredSignAndOnlyThoseSigns(){
        assertEquals(Arrays.asList("A:,","A:!","A:?","A:;","A::","A:.","A:…"),
                terms(plan(node("A",",","!","?",";",":",".","…")),"Salut, bine! Da? A; B: C. …"));
    }
    @Test public void distinctSignsAndSequencesRemainLiteral(){
        assertEquals(Arrays.asList("A:?!","A:...","A:…","A:—","A:–","A:-"),
                terms(plan(node("A","?!","...","…","—","–","-")),"?! ? ! ... … — – -"));
        assertTrue(terms(plan(node("A","?!")),"? !").isEmpty());
        assertTrue(terms(plan(node("A","? !")),"?!").isEmpty());
        assertTrue(terms(plan(node("A","Doamne?!")),"Doamne? !").isEmpty());
    }
    @Test public void signsInsideDatesAndCompoundsDoNotBreakWordRules(){
        assertEquals(Arrays.asList("P:.","P:.","P:-","P:'"),
                terms(plan(node("P",".","-","'"),node("words","12","stat","l")),"12.05.2026 anti-stat l'amour"));
    }
    @Test public void attachedHyphenAtEndOfLineStillCountsAsASign(){
        assertEquals(Arrays.asList("P:-"),terms(plan(node("P","-"),node("word","organi","zare")),"organi-\nzare"));
    }
    @Test public void sharedPunctuationRetainsMapIdentity(){
        assertEquals(Arrays.asList("A:!","B:!"),terms(plan(node("A","!"),node("B","!")),"Doamne!"));
    }
    @Test public void reservedCharactersRoundTripInMap(){
        for(String value:Arrays.asList("#","##","|","||","\\","# titlu","- ceva","• ceva","a|b","? !","Doamne!","§","\\"+"|"))
            assertEquals(Arrays.asList(value),MapTermSyntax.decode(MapTermSyntax.encode(value)));
        assertEquals(Arrays.asList("credință","preot"),MapTermSyntax.decode("credință | preot"));
        assertEquals(Arrays.asList("stat"),MapTermSyntax.decode("- stat"));
        assertEquals(Arrays.asList("|","#"),MapTermSyntax.decode("\\| | \\#"));
    }
}
