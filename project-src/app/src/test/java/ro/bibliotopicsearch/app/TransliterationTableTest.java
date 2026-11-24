package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransliterationTableTest{
    @Test public void spacesAndLineBreaksAreLiteral(){
        LiteralTransliterator t=new LiteralTransliterator(TransliterationTable.parse("source\ttarget\n\\n\t/\n \t_\nA\tB\n"));
        assertEquals("B_B/B",t.apply("A A\nA").text);
    }
    @Test public void invalidSystemsAreNotPartiallyApplied(){
        for(String text:new String[]{"source\ttarget\nx\ta\nx\tb","source,target\n\"unterminated,a","source\ttarget\n\\U00110000\tx","source,target\n,x"}){
            try{TransliterationTable.parse(text);fail("Expected invalid rules");}catch(IllegalArgumentException expected){}
        }
    }
}
