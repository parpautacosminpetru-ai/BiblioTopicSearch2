package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns the one Lens query into a tiny lexical map; relation control words stay semantic, not lexical targets. */
public final class LensQueryMap {
    private LensQueryMap() {}

    public static TopicMap build(String query, TopicMap aliasSource, int targetColor) {
        String raw = query == null ? "" : query.trim();
        List<TopicNode> nodes = new ArrayList<>();
        if (raw.isEmpty()) return new TopicMap("LUPĂ", "", nodes);

        ResearchSemanticEngine.Profile profile = ResearchSemanticEngine.compile(raw, aliasSource);
        Set<String> terms = new LinkedHashSet<>();
        terms.addAll(profile.directTerms());
        terms.addAll(profile.aliasTerms());

        // A plain topic benefits from its full multi-word phrase; a question does not,
        // because the relation words (de ce, cauze, scop etc.) are not part of the target.
        if (!profile.explicitQuestion() && !raw.contains("?")) terms.add(raw);

        if (terms.isEmpty()) return new TopicMap("LUPĂ", raw, nodes);
        TopicNode node = new TopicNode("LUPĂ > ȚINTĂ", "ȚINTĂ", 1);
        node.enabled = true;
        node.color = targetColor;
        node.symbol = "T";
        for (String term : terms) {
            if (term != null && !term.trim().isEmpty()) node.terms.add(term.trim());
        }
        nodes.add(node);
        return new TopicMap("LUPĂ", raw, nodes);
    }
}
