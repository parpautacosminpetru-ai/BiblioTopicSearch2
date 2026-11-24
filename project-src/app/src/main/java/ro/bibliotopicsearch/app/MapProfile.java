package ro.bibliotopicsearch.app;

public final class MapProfile {
    public final String id;
    public String name;
    public String folder;
    public String rawText;
    public final long createdAt;

    public MapProfile(String id, String name, String folder, String rawText, long createdAt) {
        this.id = id;
        this.name = name == null ? "Hartă de cercetare" : name;
        this.folder = folder == null ? "General" : folder;
        this.rawText = rawText == null ? "" : rawText;
        this.createdAt = createdAt;
    }
}
