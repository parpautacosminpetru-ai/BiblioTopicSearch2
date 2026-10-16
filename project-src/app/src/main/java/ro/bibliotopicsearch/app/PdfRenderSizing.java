package ro.bibliotopicsearch.app;

/** Pure sizing policy for rendering PDF pages safely before local OCR. */
public final class PdfRenderSizing {
    private PdfRenderSizing() {}

    public static final class Size {
        public final int width;
        public final int height;
        public final float scale;
        public final long pixels;

        Size(int width, int height, float scale) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.scale = Math.max(0.01f, scale);
            this.pixels = (long) this.width * (long) this.height;
        }
    }

    public static Size compute(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            long maxPixels,
            float maxScale
    ) {
        int sw = Math.max(1, sourceWidth);
        int sh = Math.max(1, sourceHeight);
        int tw = Math.max(sw, Math.max(1, targetWidth));
        long cap = Math.max(1L, maxPixels);
        float scaleCap = Math.max(1f, maxScale);

        double scale = Math.min(scaleCap, tw / (double) sw);
        double pixels = sw * scale * sh * scale;
        if (pixels > cap) {
            scale *= Math.sqrt(cap / pixels);
        }
        if (scale <= 0.0) scale = 1.0;

        int width = Math.max(1, (int) Math.round(sw * scale));
        int height = Math.max(1, (int) Math.round(sh * scale));

        // Rounding may exceed the cap by a tiny amount; trim once deterministically.
        long actual = (long) width * height;
        if (actual > cap) {
            double correction = Math.sqrt(cap / (double) actual);
            width = Math.max(1, (int) Math.floor(width * correction));
            height = Math.max(1, (int) Math.floor(height * correction));
            scale = Math.min(width / (double) sw, height / (double) sh);
        }
        return new Size(width, height, (float) scale);
    }
}
