package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class LiteralDetectionTest {
    private TopicNode node(String id, String title, String... terms) {
        TopicNode n = new TopicNode(id,id,title,title,1);
        n.enabled = true; n.terms.addAll(Arrays.asList(terms)); return n;
    }
    private TopicMatcher.SearchPlan plan(TopicNode... nodes) {
        return TopicMatcher.compile(new TopicMap("Test","",Arrays.asList(nodes)), false, 4, AppPrefs.MatchMode.EXACT);
    }
    private List<String> scan(TopicMatcher.SearchPlan plan, String text) {
        List<String> values = LiteralText.values(text,false);
        List<String> result = new ArrayList<>();
        plan.scan(values,new boolean[values.size()],new boolean[values.size()],-1,-1,null,
                (start,end,terms,kind) -> {
                    for (TopicMatcher.CompiledTerm t : terms) result.add(t.node.profileId+":"+t.raw);
                });
        return result;
    }
    @Test public void wholeWordsNoPrefixOrSubstring() {
        TopicMatcher.SearchPlan p = plan(node("A","Tema","stat","art","religie","Ion"));
        assertTrue(scan(p,"statuie partid antireligie Ionescu").isEmpty());
        assertEquals(Arrays.asList("A:stat","A:religie","A:Ion"),scan(p,"Stat, religie. Ion!"));
    }
    @Test public void punctuationCannotInventAPhrase() {
        TopicMatcher.SearchPlan p = plan(node("A","Tema","ordine socială","stat, biserică"));
        assertTrue(scan(p,"ordine. Socială").isEmpty());
        assertTrue(scan(p,"ordine — socială").isEmpty());
        assertTrue(scan(p,"ordine, socială").isEmpty());
        assertEquals(Arrays.asList("A:ordine socială"),scan(p,"Ordine socială."));
        assertEquals(Arrays.asList("A:stat, biserică"),scan(p,"stat, biserică"));
        assertTrue(scan(p,"stat biserică").isEmpty());
    }
    @Test public void datesAndHyphenatedWordsAreNotSplitIntoFalseMatches() {
        TopicMatcher.SearchPlan p = plan(node("A","Tema","12","05","stat","12.05.2026","anti-stat"));
        assertEquals(Arrays.asList("A:12.05.2026","A:anti-stat"),scan(p,"12.05.2026 anti-stat"));
        assertTrue(scan(p,"12,5 120 statul").isEmpty());
    }
    @Test public void diacriticsDistinguishWordsAndTitlesAreNotImplicit() {
        TopicMatcher.SearchPlan p = plan(node("A","Stat"),node("B","Tema","față","țară"));
        assertTrue(scan(p,"stat fata tara").isEmpty());
        assertEquals(Arrays.asList("B:față","B:țară"),scan(p,"FAȚĂ ŢARĂ"));
    }
    @Test public void overlappingMapsKeepTheirOwnIdentity() {
        TopicMatcher.SearchPlan p = plan(node("A","A","stat"),node("B","B","stat"),node("C","C","stat modern"));
        assertEquals(Arrays.asList("A:stat","B:stat","C:stat modern"),scan(p,"stat modern"));
    }
    @Test public void alignmentNeverSkipsTextOrAssignsWrongOffsets() {
        assertArrayEquals(new int[]{0,5},LiteralText.align("stat modern",Arrays.asList("stat","modern")));
        assertNull(LiteralText.align("stat vechi modern",Arrays.asList("stat","modern")));
        assertArrayEquals(new int[]{0,4,5},LiteralText.align("stat,modern",Arrays.asList("stat",",","modern")));
    }
    @Test public void lineBreakAndColumnGuard() {
        assertTrue(LiteralText.sameColumn(0,0,200,20,0,24,190,44));
        assertFalse(LiteralText.sameColumn(0,0,200,20,240,24,440,44));
        assertFalse(LiteralText.sameColumn(0,0,200,20,0,80,190,100));
        assertEquals(1,LiteralText.terminalFragment("de organi-",LiteralText.lex("de organi-",false)));
        assertEquals(-1,LiteralText.terminalFragment("de organi -",LiteralText.lex("de organi -",false)));
    }
    @Test public void incompleteWordAndUnsafeGapsAreNeverMatched() {
        TopicMatcher.SearchPlan p=plan(node("A","A","organi","zare","stat modern"));
        List<String> matches=new ArrayList<>();
        p.scan(Arrays.asList("organi","-","zare","stat","modern"),
                new boolean[]{true,true,true,false,false},new boolean[]{false,false,false,false,true},
                -1,-1,null,(a,b,terms,kind)->matches.add(terms.get(0).raw));
        assertTrue(matches.isEmpty());
    }
    @Test public void ambiguousReconstructionDoesNotMarkEitherSpelling() {
        TopicMatcher.SearchPlan p=plan(node("A","A","organizare","organi-zare"));
        List<String> matches=new ArrayList<>();
        p.scan(Arrays.asList("organizare"),new boolean[1],new boolean[1],-1,0,"organi-zare",
                (a,b,terms,kind)->matches.add(terms.get(0).raw));
        assertTrue(matches.isEmpty());
    }
}
