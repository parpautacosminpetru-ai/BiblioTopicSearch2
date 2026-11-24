package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;

public final class TopicNode {
    public final String path;
    public final String title;
    public final int level;
    public final List<String> terms = new ArrayList<>();

    public int color;
    public String symbol;
    public boolean enabled;

    public TopicNode(String path, String title, int level) {
        this.path = path;
        this.title = title;
        this.level = level;
    }
}
