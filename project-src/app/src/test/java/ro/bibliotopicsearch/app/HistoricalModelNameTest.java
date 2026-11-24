package ro.bibliotopicsearch.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalModelNameTest {
    @Test public void explicitModelsRoundTrip(){
        for(String name:new String[]{"Cyrillic","rus","ell","ron","ro_medieval-1"})
            assertEquals(name,HistoricalModelName.fromFile(HistoricalModelName.file(name)));
    }
    @Test public void rejectsPathsAndLanguageExpressions(){
        for(String name:new String[]{"../rus.traineddata","a/b.traineddata","rus+ell.traineddata","a\\b.traineddata",".traineddata","rus.zip","rus.traineddata.exe","a b.traineddata",null}){
            try{HistoricalModelName.fromFile(name);fail(String.valueOf(name));}catch(IllegalArgumentException expected){}
        }
    }
}
