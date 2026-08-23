package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;

public final class TopicMap {
    public final String name;
    public final String rawText;
    public final List<TopicNode> nodes;

    public TopicMap(String name, String rawText, List<TopicNode> nodes) {
        this.name = name;
        this.rawText = rawText;
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }
}
