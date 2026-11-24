package ro.bibliotopicsearch.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PaleographySystemTest {
    @Test public void jsonPreservesExplicitEmptyTargetsAndSupplementaryCharacters()throws Exception{
        String json="{\"name\":\"Test\",\"rules\":[{\"source\":\"Ж\",\"target\":\"Zh\"},{\"source\":\"§\",\"target\":\"\"},{\"source\":\"𐰀\",\"target\":\"A\"}]}";
        TransliterationStore.Imported result=TransliterationStore.parse(json,"Other");
        assertEquals("Test",result.name);
        assertEquals("ZhA?",new LiteralTransliterator(result.rules).apply("Ж§𐰀?").text);
    }
    @Test public void reservedBraceIsNotAJsonObject()throws Exception{
        assertEquals("X",new LiteralTransliterator(TransliterationStore.parse("{,X\n","S").rules).apply("{").text);
    }
}
