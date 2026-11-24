package ro.bibliotopicsearch.app;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PaleographySoundRulesTest{
    @Test public void soundOffsetsFollowLongestLiteralRule(){
        LiteralTransliterator engine=new LiteralTransliterator(TransliterationTable.parse(
                "source\ttarget\tsound\tlanguage\nЖ\tZh\tje\tro-RO\nЖА\tX\tja\tro-RO\nА\tA\ta\tro-RO\n"));
        LiteralTransliterator.Result result=engine.positioned("ЖА?Ж");
        assertEquals("X?Zh",result.text);
        assertEquals(2,result.matches.size());
        assertEquals("ja",result.matches.get(0).rule.sound);
        assertEquals(0,result.matches.get(0).start);
        assertEquals(1,result.matches.get(0).end);
        assertEquals(2,result.matches.get(1).start);
        assertEquals(4,result.matches.get(1).end);
        assertEquals("ro-RO",result.matches.get(1).rule.language);
    }
    @Test public void oldTablesAndSoundColumnsSurviveExport(){
        List<LiteralTransliterator.Rule> rules=TransliterationTable.parse("source,target,sound,language\nЖ,Zh,je,ro-RO\nѠ,O,,\n");
        List<LiteralTransliterator.Rule> roundtrip=TransliterationTable.parse(TransliterationTable.format(rules));
        assertEquals("je",roundtrip.get(0).sound);assertEquals("",roundtrip.get(1).sound);
        assertEquals("ro-RO",roundtrip.get(0).language);
        assertTrue(new LiteralTransliterator(TransliterationTable.parse("source,target\nx,y\n")).positioned("x").matches.get(0).rule.sound.isEmpty());
    }
    @Test public void contradictoryPronunciationsAreRejected(){
        try{TransliterationTable.parse("source\ttarget\tsound\tlanguage\nЖ\tZ\tje\tro\nЖ\tZ\tze\tro\n");fail();}
        catch(IllegalArgumentException expected){}
    }
}
