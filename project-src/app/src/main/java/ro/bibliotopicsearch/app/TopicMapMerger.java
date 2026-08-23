package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;

/** Combines independent search layers without changing their nodes or source maps. */
public final class TopicMapMerger {
    private TopicMapMerger() {}

    public static TopicMap merge(String name, TopicMap... maps) {
        List<TopicNode> nodes = new ArrayList<>();
        StringBuilder raw = new StringBuilder();

        if (maps != null) {
            for (TopicMap map : maps) {
                if (map == null) continue;
                nodes.addAll(map.nodes);
                if (map.rawText != null && !map.rawText.trim().isEmpty()) {
                    if (raw.length() > 0) raw.append("\n\n");
                    raw.append(map.rawText.trim());
                }
            }
        }
        return new TopicMap(name == null ? "Straturi active" : name, raw.toString(), nodes);
    }
}
