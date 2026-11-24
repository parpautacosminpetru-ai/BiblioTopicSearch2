package ro.bibliotopicsearch.app;

import java.util.List;

/** A profile owns exactly one opaque RGB colour, independent of its name,
 * folder, active selection, node hierarchy and matching mode. */
final class MapColorPolicy {
    private static final int[] PALETTE = {
            0xFFDA4960, 0xFF30819D, 0xFF3A9272, 0xFFDE9436, 0xFF7F5AA8,
            0xFF4C709D, 0xFFBE5B37, 0xFF528E3F, 0xFFB8488F, 0xFF3D9694,
            0xFF70624F, 0xFFC07423
    };
    private MapColorPolicy() {}

    static int opaque(int color) { return 0xFF000000 | (color & 0x00FFFFFF); }

    static int defaultColor(String id) {
        int offset = id == null || "default".equals(id) ? 0 : (id.hashCode() & Integer.MAX_VALUE) % PALETTE.length;
        return PALETTE[offset];
    }

    static void applyToNodes(List<TopicNode> nodes, int color) {
        int rgb = opaque(color);
        for (TopicNode node : nodes) node.color = rgb;
    }

    static int contrastText(int color) {
        double r = ((color >>> 16) & 255) / 255.0;
        double g = ((color >>> 8) & 255) / 255.0;
        double b = (color & 255) / 255.0;
        double luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
        return luminance > 0.179 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double linear(double x) {
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }
}
