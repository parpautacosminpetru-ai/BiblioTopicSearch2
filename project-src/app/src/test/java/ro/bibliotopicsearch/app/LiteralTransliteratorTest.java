package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class LiteralTransliteratorTest{
    private LiteralTransliterator engine(String... pairs){
        java.util.List<LiteralTransliterator.Rule> rules=new java.util.ArrayList<>();
        for(int i=0;i<pairs.length;i+=2)rules.add(new LiteralTransliterator.Rule(pairs[i],pairs[i+1]));
        return new LiteralTransliterator(rules);
    }
    @Test public void exactCasePunctuationAndHistoricalCharacters(){
        LiteralTransliterator t=engine("Б","B","б","b","Ж","Zh","Ѣ","Ě","Ѡ","O","†","✝","?","!");
        LiteralTransliterator.Result r=t.apply("Бб ЖѢѠ†?\nΩ");
        assertEquals("Bb ZhĚO✝!\nΩ",r.text);
        assertEquals(7,r.substitutions);
        assertEquals(3,r.unmappedCharacters);
        assertEquals(1,r.unmappedNonWhitespace);
        assertEquals(Integer.valueOf(1),r.unmapped.get((int)'Ω'));
    }
    @Test public void longestSequenceWinsAndOutputNeverReenters(){
        assertEquals("Z C B",engine("a","B","ab","Z","b","C","B","WRONG").apply("ab b a").text);
        assertEquals("b c",engine("a","b","b","c").apply("a b").text);
    }
    @Test public void supplementaryCombiningWhitespaceAndDeletion(){
        assertEquals("A! x",engine("𐰀","A","e\u0301","!"," ","","x"," x").apply("𐰀e\u0301 x").text);
        assertEquals("é",engine("e\u0301","x").apply("é").text);
        assertEquals("",engine("§","").apply("§").text);
        assertEquals("A\tB",engine("a","A","b","B"," ","\t").apply("a b").text);
    }
    @Test public void contradictoryRulesAreRejected(){
        try{engine("a","b","a","c");fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,engine("a","b","a","b").rules().size());
        try{engine("","a");fail();}catch(IllegalArgumentException expected){}
        try{engine("\uD800","x");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void missingCharactersAlwaysRemainUnchanged(){
        LiteralTransliterator.Result r=engine("a","α").apply("a? Я 🕊️");
        assertEquals("α? Я 🕊️",r.text);
        assertEquals(4,r.unmappedNonWhitespace);
    }
    @Test public void tsvAndCsvRoundTripLiteralReservedCharacters(){
        LiteralTransliterator t=engine("\t"," ", "\n","\\", "#","|", "§","", "𐰀","e\u0301");
        String serialized=TransliterationTable.format(t.rules());
        assertEquals(t.apply("\t\n#§𐰀").text,new LiteralTransliterator(TransliterationTable.parse(serialized)).apply("\t\n#§𐰀").text);
        String csv="source,target\r\n\",\",\";\"\r\n\"\"\"\",\"„\"\r\n\"a,b\",\"x\ny\"\r\n§,\r\n";
        assertEquals(";„x\ny",new LiteralTransliterator(TransliterationTable.parse(csv)).apply(",\"a,b§").text);
        assertEquals("A",new LiteralTransliterator(TransliterationTable.parse("sursa;destinatie\nа;A\n")).apply("а").text);
        assertEquals("A",new LiteralTransliterator(TransliterationTable.parse("source\ttarget\n\\U00010400\tA\n")).apply("𐐀").text);
    }
}
