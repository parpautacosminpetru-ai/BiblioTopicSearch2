package ro.bibliotopicsearch.app;

public final class MapProfile {
    public final String id;
    public String name;
    public String folder;
    public String rawText;
    public final long createdAt;
    public final int color;

    public MapProfile(String id, String name, String folder, String rawText, long createdAt) {
        this(id, name, folder, rawText, createdAt, MapColorPolicy.defaultColor(id));
    }

    public MapProfile(String id, String name, String folder, String rawText, long createdAt, int color) {
        this.id = id;
        this.color = MapColorPolicy.opaque(color);
        this.name = name == null ? "Hartă de cercetare" : name;
        this.folder = folder == null ? "General" : folder;
        this.rawText = rawText == null ? "" : rawText;
        this.createdAt = createdAt;
    }
}
