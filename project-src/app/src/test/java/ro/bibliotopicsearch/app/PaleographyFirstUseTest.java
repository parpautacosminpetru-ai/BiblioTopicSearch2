package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaleographyFirstUseTest {
    @Test public void originalModeRequiresNoRulesAndPreservesEveryCodePoint(){
        String text="Română · العربية · Ἑλληνικά · 𒀀 · [S001]\n";
        LiteralTransliterator.Result result=LiteralTransliterator.original().apply(text);
        assertEquals(text,result.text);
        assertEquals(0,result.substitutions);
        assertTrue(result.unmappedNonWhitespace>0);
        assertTrue(result.matches.isEmpty());
    }
    @Test public void originalModeIsNotAUserDefinedRuleSet(){
        assertTrue(LiteralTransliterator.original().rules().isEmpty());
        try{new LiteralTransliterator(java.util.Collections.emptyList());fail();}
        catch(IllegalArgumentException expected){}
    }
}
