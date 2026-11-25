package ro.bibliotopicsearch.app;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoricalLexiconRuntimeTest {
    @Test public void completeAssetAndRealMapsPreserveAllTermsAndColors(){
        Context c=ApplicationProvider.getApplicationContext();
        List<MapProfile> bundled=HistoricalLexicon.load(c);
        assertEquals(448,bundled.size());
        Map<String,MapProfile> installed=new HashMap<>();
        for(MapProfile p:TopicLibraryStore.list(c))installed.put(p.id,p);
        Set<Integer> colors=new HashSet<>();int count=0,hash=0x811c9dc5;
        for(MapProfile p:bundled){
            MapProfile actual=installed.get(p.id);assertNotNull(p.name,actual);
            assertEquals(p.name,actual.name);assertEquals(p.rawText,actual.rawText);assertEquals(p.color,actual.color);
            assertTrue(colors.add(actual.color));
            TopicMap map=TopicMapStore.loadForScanning(c,Collections.singletonList(actual));
            assertEquals(1,map.nodes.size());
            TopicNode n=map.nodes.get(0);assertEquals(p.color,n.color);assertEquals(p.id,n.profileId);
            hash=HistoricalLexicon.hashTerm(hash,p.name);
            for(String term:n.terms)hash=HistoricalLexicon.hashTerm(hash,term);
            count+=n.terms.size();
        }
        assertEquals(76100,count);assertEquals(0x4550401a,hash);assertEquals(448,colors.size());
        assertEquals(installed.size(),TopicLibraryStore.list(c).size());
    }
    @Test public void allCategoriesCanCompileAndSearchTogether(){
        Context c=ApplicationProvider.getApplicationContext();
        List<MapProfile> maps=HistoricalLexicon.load(c);
        TopicMap combined=TopicMapStore.loadForScanning(c,maps);
        TopicMatcher.SearchPlan plan=TopicMatcher.compile(combined,false,0,AppPrefs.MatchMode.EXACT);
        assertTrue("Index unexpectedly small: "+plan.termCount(),plan.termCount()>70000);
        List<TopicMatcher.TextProbeHit> hits=TopicMatcher.probeText("cauzalitate efecte secolul XXI anul 2026 cooperare pace",plan);
        Set<String> categories=new HashSet<>();
        for(TopicMatcher.TextProbeHit h:hits)categories.add(h.node.profileName);
        for(String name:Arrays.asList("cauzalitate","efecte","secole","ani exacți","cooperare","pacificare"))assertTrue(name,categories.contains(name));
        List<TopicMatcher.TextProbeHit> shared=TopicMatcher.probeText("cauză",plan);
        assertTrue(shared.stream().anyMatch(h->h.node.profileName.equals("cauzalitate")));
    }
    @Test public void repeatedAccessDoesNotDuplicateMapsOrOverwriteUserColor(){
        Context c=ApplicationProvider.getApplicationContext();
        List<MapProfile> all=TopicLibraryStore.list(c);int before=all.size();
        String id=TopicLibraryStore.create(c,"Test privat temporar","Test","",0xff123456);
        try{
            assertEquals(before+1,TopicLibraryStore.list(c).size());assertEquals(0xff123456,TopicLibraryStore.get(c,id).color);
            assertEquals(before+1,TopicLibraryStore.list(c).size());
        }finally{TopicLibraryStore.delete(c,id);}
    }
}
