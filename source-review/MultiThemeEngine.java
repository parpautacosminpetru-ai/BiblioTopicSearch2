package ro.bibliotopicsearch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Index comun: fiecare termen este cautat o singura data in acelasi cadru OCR.
 * Apartenenta la teme se propaga dupa potrivire; interpretarea ramane umana.
 */
public final class MultiThemeEngine {
    public static final String OVERVIEW = "__rezumat__";
    public static final String INTERSECTIONS = "__intersectii__";
    public static final String FILTER_PREFS = "bibliotopicsearch_theme_selection";
    public static final String FILTER_EXCLUDED = "excluded_theme_ids";

    private MultiThemeEngine() {}

    public static final class Theme {
        public final String id;
        public final String name;
        Theme(String id, String name) { this.id=id; this.name=name; }
    }

    private static final class Membership {
        final String themeId;
        final TopicNode node;
        Membership(String themeId, TopicNode node) {
            this.themeId=themeId;
            this.node=node;
        }
    }

    public static final class Plan {
        public final List<Theme> themes;
        public final int termCount;
        public final int membershipCount;
        final TopicMatcher.SearchPlan sharedSearchPlan;
        final Map<String, List<Membership>> memberships;
        final boolean stripDiacritics;

        Plan(List<Theme> themes, int termCount, int membershipCount,
             TopicMatcher.SearchPlan search, Map<String,List<Membership>> members,
             boolean strip) {
            this.themes=Collections.unmodifiableList(themes);
            this.termCount=termCount;
            this.membershipCount=membershipCount;
            this.sharedSearchPlan=search;
            this.memberships=members;
            this.stripDiacritics=strip;
        }

        public boolean hasLens(String id) {
            if (OVERVIEW.equals(id) || INTERSECTIONS.equals(id)) return true;
            for(Theme theme:themes) if(theme.id.equals(id)) return true;
            return false;
        }

        public String nameFor(String id) {
            for (Theme theme:themes) if (theme.id.equals(id)) return theme.name;
            return "Temă";
        }
    }

    public static Plan compile(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(FILTER_PREFS,Context.MODE_PRIVATE);
        Set<String> excluded = prefs.getStringSet(FILTER_EXCLUDED, Collections.emptySet());
        if (excluded == null) excluded=Collections.emptySet();

        boolean strip=AppPrefs.ignoreDiacritics(context);
        List<Theme> themes=new ArrayList<>();
        Map<String,List<Membership>> membership=new LinkedHashMap<>();
        Map<String,TopicNode> uniqueTerms=new LinkedHashMap<>();
        Set<String> associations=new HashSet<>();
        int links=0;

        for (MapProfile profile:TopicLibraryStore.list(context)) {
            if(excluded.contains(profile.id)) continue;
            TopicMap map=TopicMapStore.parseForProfile(
                    context,profile.id,profile.name,profile.rawText);
            themes.add(new Theme(profile.id,profile.name));
            for(TopicNode node:map.nodes) {
                if(!node.enabled) continue;
                List<String> words=node.terms.isEmpty() ?
                        Collections.singletonList(node.title):node.terms;
                for(String raw:words) {
                    String term=normalize(raw,strip);
                    if(term.isEmpty()) continue;
                    if(!uniqueTerms.containsKey(term)) {
                        TopicNode canonical=new TopicNode("index-"+uniqueTerms.size(),raw,0);
                        canonical.terms.add(raw);
                        canonical.enabled=true;
                        canonical.color=node.color;
                        canonical.symbol=node.symbol;
                        uniqueTerms.put(term,canonical);
                    }
                    String association=term+"|"+profile.id+"|"+node.path;
                    if(associations.add(association)) {
                        membership.computeIfAbsent(term,k->new ArrayList<>())
                                .add(new Membership(profile.id,node));
                        links++;
                    }
                }
            }
        }
        TopicMap common=new TopicMap("Index comun","",
                new ArrayList<>(uniqueTerms.values()));
        return new Plan(themes,uniqueTerms.size(),links,
                TopicMatcher.compile(context,common),membership,strip);
    }

    private static String normalize(String value, boolean strip) {
        if(value==null)return "";
        String term=value.toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\s+"," ");
        if(strip)term=Normalizer.normalize(term,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
        return term;
    }

    private static final class Occurrence {
        final String key;
        final MatchHit hit;
        final Set<String> themes=new LinkedHashSet<>();
        final Set<String> nodes=new LinkedHashSet<>();
        Occurrence(String key,MatchHit hit) {this.key=key;this.hit=hit;}
    }

    public static final class Scan {
        private final Map<String,List<MatchHit>> perTheme;
        private final Map<String,Occurrence> occurrences;
        private final List<MatchHit> lineHits;
        private final List<MatchHit> blockHits;
        private final Set<String> lineKeys;
        private final Set<String> blockKeys;
        public final int uniqueMatches;
        public final int lineIntersections;
        public final int blockIntersections;

        Scan(Map<String,List<MatchHit>> perTheme,Map<String,Occurrence> occur,
             List<MatchHit> lineHits,List<MatchHit> blockHits,
             Set<String> lineKeys,Set<String> blockKeys,
             int intersectedLines,int intersectedBlocks) {
            this.perTheme=perTheme;
            this.occurrences=occur;
            this.lineHits=lineHits;
            this.blockHits=blockHits;
            this.lineKeys=lineKeys;
            this.blockKeys=blockKeys;
            this.uniqueMatches=occur.size();
            this.lineIntersections=intersectedLines;
            this.blockIntersections=intersectedBlocks;
        }

        public List<MatchHit> visible(String id,boolean lineOnly) {
            if(OVERVIEW.equals(id))return Collections.emptyList();
            if(INTERSECTIONS.equals(id))return lineOnly ? lineHits : blockHits;
            List<MatchHit> result=perTheme.get(id);
            return result==null ? Collections.emptyList():result;
        }

        public int count(String id,boolean lineOnly) {
            if(OVERVIEW.equals(id))return uniqueMatches;
            if(INTERSECTIONS.equals(id))return lineOnly?lineHits.size():blockHits.size();
            List<MatchHit> results=perTheme.get(id);
            return results==null?0:results.size();
        }

        public String suggestions(MatchHit hit,Plan plan) {
            Occurrence item=occurrences.get(keyFor(hit));
            if(item==null)return "";
            StringBuilder out=new StringBuilder();
            for(String id:item.themes) {
                if(out.length()>0)out.append("; ");
                out.append(plan.nameFor(id));
            }
            return out.toString();
        }

        public String explain(MatchHit hit,Plan plan) {
            Occurrence item=occurrences.get(keyFor(hit));
            if(item==null)return "Potrivire OCR — verifică manual pagina.";
            StringBuilder out=new StringBuilder("Hărți în care este indexat acest termen:\n");
            for(String id:item.themes)out.append("• ").append(plan.nameFor(id)).append("\n");
            out.append("Categorii: ");
            boolean first=true;
            for(String node:item.nodes) {
                if(!first)out.append("; ");
                out.append(node);first=false;
            }
            if(lineKeys.contains(item.key)) {
                out.append("\nRegulă: termeni diferiți din categorii distincte pe aceeași linie OCR.");
            } else if(blockKeys.contains(item.key)) {
                out.append("\nRegulă: termeni diferiți într-un bloc OCR (nu obligatoriu paragraf).");
            } else {
                out.append("\nRegulă: numai potrivirea lexicală a termenului.");
            }
            out.append("\nSemnal de cercetare, nu concluzie istorică.");
            return out.toString();
        }

        public String summary(Plan plan,boolean lineOnly) {
            StringBuilder sb=new StringBuilder();
            sb.append("Ocurențe: ").append(uniqueMatches)
              .append("\nIntersecții de ").append(lineOnly?"linie":"bloc").append(": ")
              .append(count(INTERSECTIONS,lineOnly)).append("\n\n");
            for(Theme theme:plan.themes) {
                sb.append(theme.name).append(": ")
                  .append(count(theme.id,lineOnly)).append(" potriviri\n");
            }
            return sb.toString();
        }
    }

    public static Scan scan(Text text,Plan plan) {
        Map<String,Map<String,MatchHit>> byTheme=new LinkedHashMap<>();
        Map<String,Occurrence> occurrences=new LinkedHashMap<>();
        if(plan!=null)for(Theme t:plan.themes)byTheme.put(t.id,new LinkedHashMap<>());

        if(text!=null && plan!=null && plan.termCount>0) {
            // Un singur apel de potrivire pentru toate temele si cuvintele distincte.
            for(MatchHit found:TopicMatcher.find(text,plan.sharedSearchPlan)) {
                List<Membership> targets=plan.memberships.get(
                        normalize(found.searchTerm,plan.stripDiacritics));
                if(targets==null||targets.isEmpty())continue;
                String key=keyFor(found);
                Occurrence occurrence=occurrences.get(key);
                if(occurrence==null) {
                    Membership first=targets.get(0);
                    MatchHit display=new MatchHit(found.box,found.originalText,
                            found.searchTerm,first.node,found.detectedAt);
                    occurrence=new Occurrence(key,display);
                    occurrences.put(key,occurrence);
                }
                for(Membership target:targets) {
                    occurrence.themes.add(target.themeId);
                    occurrence.nodes.add(target.node.path);
                    Map<String,MatchHit> hits=byTheme.get(target.themeId);
                    if(hits!=null && !hits.containsKey(key)) {
                        hits.put(key,new MatchHit(found.box,found.originalText,
                                found.searchTerm,target.node,found.detectedAt));
                    }
                }
            }
        }

        Set<String> byLine=new LinkedHashSet<>();
        Set<String> byBlock=new LinkedHashSet<>();
        int matchedLines=0,matchedBlocks=0;
        if(text!=null)for(Text.TextBlock block:text.getTextBlocks()) {
            List<Occurrence> group=inside(occurrences.values(),block.getBoundingBox());
            Set<String> blockParticipants=intersectionParticipants(group);
            if(!blockParticipants.isEmpty()) {
                matchedBlocks++;
                byBlock.addAll(blockParticipants);
            }
            for(Text.Line line:block.getLines()) {
                List<Occurrence> lineItems=inside(occurrences.values(),line.getBoundingBox());
                Set<String> lineParticipants=intersectionParticipants(lineItems);
                if(!lineParticipants.isEmpty()) {
                    matchedLines++;
                    byLine.addAll(lineParticipants);
                }
            }
        }
        List<MatchHit> lineHits=new ArrayList<>();
        List<MatchHit> blockHits=new ArrayList<>();
        for(Occurrence o:occurrences.values()) {
            if(byLine.contains(o.key))lineHits.add(o.hit);
            if(byBlock.contains(o.key) || byLine.contains(o.key))blockHits.add(o.hit);
        }
        Map<String,List<MatchHit>> ready=new LinkedHashMap<>();
        for(Map.Entry<String,Map<String,MatchHit>> entry:byTheme.entrySet()) {
            ready.put(entry.getKey(),new ArrayList<>(entry.getValue().values()));
        }
        return new Scan(ready,occurrences,lineHits,blockHits,byLine,byBlock,
                matchedLines,matchedBlocks);
    }

    private static List<Occurrence> inside(Iterable<Occurrence> items,Rect rect) {
        List<Occurrence> out=new ArrayList<>();
        if(rect==null)return out;
        for(Occurrence item:items) {
            RectF r=item.hit.box;
            if(rect.contains(Math.round(r.centerX()),Math.round(r.centerY())))out.add(item);
        }
        return out;
    }

    private static Set<String> intersectionParticipants(List<Occurrence> items) {
        Set<String> out=new LinkedHashSet<>();
        for(int i=0;i<items.size();i++) {
            Occurrence a=items.get(i);
            for(int j=i+1;j<items.size();j++) {
                Occurrence b=items.get(j);
                // Nu numărăm o expresie și unul din propriile cuvinte ca două dovezi.
                if(overlapFraction(a.hit.box,b.hit.box)>0.75f)continue;
                if(a.hit.originalText.trim().equalsIgnoreCase(
                        b.hit.originalText.trim()))continue;
                if(a.nodes.equals(b.nodes) && a.themes.equals(b.themes))continue;
                out.add(a.key);
                out.add(b.key);
            }
        }
        return out;
    }

    private static float overlapFraction(RectF a,RectF b) {
        float w=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left));
        float h=Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        float minArea=Math.min(Math.max(0,a.width()*a.height()),
                Math.max(0,b.width()*b.height()));
        return minArea<=0?0:(w*h)/minArea;
    }

    private static String keyFor(MatchHit h) {
        RectF b=h.box;
        return h.originalText.toLowerCase(Locale.ROOT).trim()+"|"+
                Math.round(b.left/3f)+":"+Math.round(b.top/3f)+":"+
                Math.round(b.right/3f)+":"+Math.round(b.bottom/3f);
    }
}
