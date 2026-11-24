package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;

public final class TopicNode {
    public final String profileId;
    public final String profileName;
    public final String path;
    public final String title;
    public final int level;
    public final List<String> terms = new ArrayList<>();

    public int color;
    public String symbol;
    public boolean enabled;

    public TopicNode(String path, String title, int level) {
        this("", "", path, title, level);
    }

    public TopicNode(String profileId, String profileName, String path, String title, int level) {
        this.profileId = profileId == null ? "" : profileId;
        this.profileName = profileName == null ? "" : profileName;
        this.path = path;
        this.title = title;
        this.level = level;
    }

    public String identityKey() {
        return profileId.length() + ":" + profileId + path;
    }

    public String displayName() {
        return profileName.isEmpty() ? title : profileName + " · " + title;
    }
}
