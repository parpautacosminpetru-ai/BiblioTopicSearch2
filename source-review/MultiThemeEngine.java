package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Un singur rezultat OCR este căutat în toate hărțile. Nu atribuie sens pasajelor;
 * intersecțiile reprezintă numai coapariții observabile, verificabile manual.
 */
public final class MultiThemeEngine {
    public static final String OVERVIEW = "__rezumat__";
    public static final String INTERSECTIONS = "__intersectii__";

    private MultiThemeEngine() {}

    public static final class Theme {
        public final String id;
        public final String name;
        final TopicMatcher.SearchPlan searchPlan;

        Theme(String id, String name, TopicMatcher.SearchPlan searchPlan) {
            this.id = id;
            this.name = name == null ? "Temă" : name;
            this.searchPlan = searchPlan;
        }
    }

    public static final class Plan {
        public final List<Theme> themes;
        public final int termCount;

        Plan(List<Theme> themes, int termCount) {
            this.themes = themes;
            this.termCount = termCount;
        }

        public boolean hasLens(String id) {
            if (OVERVIEW.equals(id) || INTERSECTIONS.equals(id)) return true;
            for (Theme theme : themes) if (theme.id.equals(id)) return true;
            return false;
        }

        public String nameFor(String id) {
            for (Theme theme : themes) if (theme.id.equals(id)) return theme.name;
            return "Temă";
        }
    }

    public static Plan compile(Context context) {
        List<Theme> themes = new ArrayList<>();
        int total = 0;
        for (MapProfile profile : TopicLibraryStore.list(context)) {
            TopicMap map = TopicMapStore.parseForProfile(
                    context, profile.id, profile.name, profile.rawText
            );
            TopicMatcher.SearchPlan searchPlan = TopicMatcher.compile(context, map);
            themes.add(new Theme(profile.id, profile.name, searchPlan));
            total += searchPlan.termCount();
        }
        return new Plan(Collections.unmodifiableList(themes), total);
    }

    private static final class Occurrence {
        final String key;
        final MatchHit hit;
        final Set<String> themeIds = new LinkedHashSet<>();
        final Set<String> nodePaths = new LinkedHashSet<>();

        Occurrence(String key, MatchHit hit) {
            this.key = key;
            this.hit = hit;
        }
    }

    public static final class Scan {
        private final Map<String, List<MatchHit>> byTheme;
        private final Map<String, Occurrence> occurrences;
        private final List<MatchHit> intersections;
        private final Set<String> lineKeys;
        private final Set<String> blockKeys;
        public final int lineIntersections;
        public final int blockIntersections;
        public final int uniqueMatches;

        Scan(Map<String, List<MatchHit>> byTheme,
             Map<String, Occurrence> occurrences,
             List<MatchHit> intersections,
             Set<String> lineKeys,
             Set<String> blockKeys,
             int lineIntersections,
             int blockIntersections) {
            this.byTheme = byTheme;
            this.occurrences = occurrences;
            this.intersections = intersections;
            this.lineKeys = lineKeys;
            this.blockKeys = blockKeys;
            this.lineIntersections = lineIntersections;
            this.blockIntersections = blockIntersections;
            this.uniqueMatches = occurrences.size();
        }

        public List<MatchHit> visible(String id) {
            if (OVERVIEW.equals(id)) return Collections.emptyList();
            if (INTERSECTIONS.equals(id)) return intersections;
            List<MatchHit> selected = byTheme.get(id);
            return selected == null ? Collections.emptyList() : selected;
        }

        public int count(String id) {
            if (OVERVIEW.equals(id)) return uniqueMatches;
            if (INTERSECTIONS.equals(id)) return intersections.size();
            List<MatchHit> selected = byTheme.get(id);
            return selected == null ? 0 : selected.size();
        }

        public String explain(MatchHit hit, Plan plan) {
            Occurrence occ = occurrences.get(keyFor(hit));
            if (occ == null) return "Potrivire OCR; verifică pagina și contextul.";
            StringBuilder sb = new StringBuilder("Detectat în hărțile:");
            for (String id : occ.themeIds) {
                sb.append("\n• ").append(plan.nameFor(id));
            }
            sb.append("\nNoduri ale indexului: ");
            boolean first = true;
            for (String node : occ.nodePaths) {
                if (!first) sb.append("; ");
                sb.append(node);
                first = false;
            }
            if (lineKeys.contains(occ.key)) {
                sb.append("\nRegulă: coapariție de termeni diferiți pe aceeași linie OCR.");
            } else if (blockKeys.contains(occ.key)) {
                sb.append("\nRegulă: coapariție într-un bloc OCR (nu neapărat paragraf).");
            } else {
                sb.append("\nRegulă: potrivire a termenului; nu există o intersecție locală validată.");
            }
            sb.append("\nAceasta este o sugestie de consultare, NU o concluzie istorică.");
            return sb.toString();
        }
    }

    public static Scan scan(Text text, Plan plan) {
        Map<String, List<MatchHit>> byTheme = new LinkedHashMap<>();
        Map<String, Occurrence> occurrences = new LinkedHashMap<>();
        if (text != null && plan != null) {
            for (Theme theme : plan.themes) {
                Map<String, MatchHit> uniqueInTheme = new LinkedHashMap<>();
                for (MatchHit hit : TopicMatcher.find(text, theme.searchPlan)) {
                    String key = keyFor(hit);
                    uniqueInTheme.putIfAbsent(key, hit);
                    Occurrence occ = occurrences.get(key);
                    if (occ == null) {
                        occ = new Occurrence(key, hit);
                        occurrences.put(key, occ);
                    }
                    occ.themeIds.add(theme.id);
                    occ.nodePaths.add(theme.name + " / " + hit.node.path);
                }
                byTheme.put(theme.id, new ArrayList<>(uniqueInTheme.values()));
            }
        }

        Set<String> lineKeys = new LinkedHashSet<>();
        Set<String> blockKeys = new LinkedHashSet<>();
        int matchingLines = 0;
        int matchingBlocks = 0;
        if (text != null) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                Rect box = block.getBoundingBox();
                List<Occurrence> inBlock = inside(occurrences.values(), box);
                if (hasIntersection(inBlock)) {
                    matchingBlocks++;
                    for (Occurrence occ : inBlock) blockKeys.add(occ.key);
                }
                for (Text.Line line : block.getLines()) {
                    List<Occurrence> inLine = inside(occurrences.values(), line.getBoundingBox());
                    if (hasIntersection(inLine)) {
                        matchingLines++;
                        for (Occurrence occ : inLine) lineKeys.add(occ.key);
                    }
                }
            }
        }

        List<MatchHit> crossHits = new ArrayList<>();
        for (Occurrence occ : occurrences.values()) {
            if (lineKeys.contains(occ.key) || blockKeys.contains(occ.key)) {
                crossHits.add(occ.hit);
            }
        }
        return new Scan(byTheme, occurrences, crossHits,
                lineKeys, blockKeys, matchingLines, matchingBlocks);
    }

    private static List<Occurrence> inside(
            Iterable<Occurrence> occurrences, Rect rect) {
        List<Occurrence> result = new ArrayList<>();
        if (rect == null) return result;
        for (Occurrence occ : occurrences) {
            RectF box = occ.hit.box;
            if (rect.contains(Math.round(box.centerX()), Math.round(box.centerY()))) {
                result.add(occ);
            }
        }
        return result;
    }

    private static boolean hasIntersection(List<Occurrence> list) {
        Set<String> differentTerms = new LinkedHashSet<>();
        Set<String> themes = new LinkedHashSet<>();
        Set<String> nodes = new LinkedHashSet<>();
        for (Occurrence occ : list) {
            differentTerms.add(occ.hit.originalText.trim().toLowerCase(Locale.ROOT));
            themes.addAll(occ.themeIds);
            nodes.addAll(occ.nodePaths);
        }
        // Același termen duplicat în două hărți NU este suficient.
        return differentTerms.size() >= 2 && (themes.size() >= 2 || nodes.size() >= 2);
    }

    private static String keyFor(MatchHit hit) {
        RectF box = hit.box;
        String original = hit.originalText == null ? "" :
                hit.originalText.trim().toLowerCase(Locale.ROOT);
        return original + "|" + Math.round(box.left / 3f)
                + ":" + Math.round(box.top / 3f)
                + ":" + Math.round(box.right / 3f)
                + ":" + Math.round(box.bottom / 3f);
    }
}
