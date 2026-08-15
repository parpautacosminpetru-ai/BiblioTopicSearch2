package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Camera overlay tuned for continuous OCR/semantic scanning.
 *
 * The renderer deliberately keeps the existing public API used by MainActivity,
 * but changes the visual behavior from static rectangles to a temporally smoothed
 * "semantic echo". OCR/semantic hits remain attached to their text geometry while
 * confidence controls fill, pulse strength and echo depth.
 *
 * The optional semantic fields introduced by newer app builds (similarity,
 * semantic, semanticCategory) are read by reflection. This keeps the class source
 * compatible with the older source archive stored in this repository while also
 * taking advantage of the richer 3.2 MatchHit model when present.
 */
public final class OverlayView extends View {
    public interface OnHitTapListener {
        void onHitTap(MatchHit hit);
    }

    private static final long FRAME_DELAY_MS = 66L;       // ~15 Hz visual refresh
    private static final long NEW_HIT_FLASH_MS = 520L;
    private static final long ECHO_PERIOD_MS = 1450L;
    private static final long SCAN_PERIOD_MS = 2300L;
    private static final float BOX_SMOOTHING = 0.34f;

    private final Object stateLock = new Object();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint echoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<String, RectF> smoothedBoxes = new HashMap<>();
    private final Map<String, Long> firstSeenAt = new HashMap<>();
    private List<MatchHit> hits = Collections.emptyList();
    private OnHitTapListener tapListener;
    private int detailLevel = 1;
    private float semanticZoom = 0.65f;
    private boolean showLabels = true;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(true);

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        echoPaint.setStyle(Paint.Style.STROKE);
        echoPaint.setStrokeCap(Paint.Cap.ROUND);

        scanPaint.setStyle(Paint.Style.STROKE);
        scanPaint.setStrokeWidth(dp(1.15f));
        scanPaint.setColor(Color.argb(74, 255, 255, 255));

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(sp(12f));
        labelPaint.setFakeBoldText(true);

        labelSubPaint.setStyle(Paint.Style.FILL);
        labelSubPaint.setColor(Color.argb(210, 236, 240, 242));
        labelSubPaint.setTextSize(sp(10f));

        labelBackgroundPaint.setStyle(Paint.Style.FILL);
        labelBackgroundPaint.setColor(Color.argb(196, 9, 12, 14));

        connectorPaint.setStyle(Paint.Style.STROKE);
        connectorPaint.setStrokeWidth(dp(1f));
        connectorPaint.setColor(Color.argb(110, 255, 255, 255));
    }

    public void setOnHitTapListener(OnHitTapListener listener) {
        tapListener = listener;
    }

    public void setDetailLevel(int level) {
        detailLevel = Math.max(0, Math.min(3, level));
        invalidate();
    }

    public void setSemanticZoom(float value) {
        // Keep a slightly wider range than 0..1 so newer builds can use an
        // amplified semantic zoom without breaking the renderer.
        semanticZoom = clamp(value, 0f, 2f);
        invalidate();
    }

    public void updateHits(List<MatchHit> incoming, int ignoredTransform, boolean labels) {
        List<MatchHit> safe = incoming == null
                ? Collections.emptyList()
                : new ArrayList<>(incoming);
        long now = SystemClock.uptimeMillis();
        Set<String> activeKeys = new HashSet<>();

        synchronized (stateLock) {
            for (MatchHit hit : safe) {
                if (hit == null || hit.box == null) continue;
                String key = occurrenceKey(hit);
                activeKeys.add(key);

                RectF old = smoothedBoxes.get(key);
                if (old == null) {
                    smoothedBoxes.put(key, new RectF(hit.box));
                    firstSeenAt.put(key, now);
                } else {
                    smoothedBoxes.put(key, blend(old, hit.box, BOX_SMOOTHING));
                }
            }
            smoothedBoxes.keySet().retainAll(activeKeys);
            firstSeenAt.keySet().retainAll(activeKeys);
            hits = safe;
            showLabels = labels;
        }
        invalidate();
    }

    public void clearHits() {
        synchronized (stateLock) {
            hits = Collections.emptyList();
            smoothedBoxes.clear();
            firstSeenAt.clear();
        }
        invalidate();
    }

    public List<MatchHit> getHitsSnapshot() {
        synchronized (stateLock) {
            return new ArrayList<>(hits);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final List<MatchHit> snapshot;
        final Map<String, RectF> boxes;
        final Map<String, Long> births;
        synchronized (stateLock) {
            snapshot = new ArrayList<>(hits);
            boxes = new HashMap<>(smoothedBoxes);
            births = new HashMap<>(firstSeenAt);
        }
        if (snapshot.isEmpty()) return;

        final long now = SystemClock.uptimeMillis();
        drawScanLine(canvas, now);

        for (int i = 0; i < snapshot.size(); i++) {
            MatchHit hit = snapshot.get(i);
            if (hit == null || hit.box == null) continue;

            String key = occurrenceKey(hit);
            RectF tracked = boxes.get(key);
            if (tracked == null || tracked.width() <= 0f || tracked.height() <= 0f) continue;

            float confidence = semanticConfidence(hit);
            boolean semantic = optionalBoolean(hit, "semantic", false);
            int topicColor = hit.node != null ? hit.node.color : Color.rgb(68, 205, 214);
            float phase = ((now + Math.abs(key.hashCode() % ECHO_PERIOD_MS)) % ECHO_PERIOD_MS)
                    / (float) ECHO_PERIOD_MS;
            float wave = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
            long born = births.containsKey(key) ? births.get(key) : now;
            float birthFlash = clamp(1f - ((now - born) / (float) NEW_HIT_FLASH_MS), 0f, 1f);
            float energy = clamp(0.34f + confidence * 0.66f + birthFlash * 0.22f, 0f, 1f);

            RectF core = padded(tracked, dp(1.5f + 2.4f * confidence));

            fillPaint.setColor(withAlpha(topicColor, (int) (18 + 54 * energy)));
            canvas.drawRoundRect(core, dp(7f), dp(7f), fillPaint);

            strokePaint.setStrokeWidth(dp(1.25f + 2.4f * energy));
            strokePaint.setColor(withAlpha(topicColor, (int) (118 + 118 * energy)));
            canvas.drawRoundRect(core, dp(7f), dp(7f), strokePaint);

            // Two expanding echoes emulate an ultrasound-like semantic return.
            // Their amplitude is confidence driven; they do not imply medical data.
            for (int ring = 0; ring < 2; ring++) {
                float local = (phase + ring * 0.5f) % 1f;
                float expansion = dp((3f + 13f * local) * (0.55f + confidence * 0.65f));
                RectF echo = padded(core, expansion);
                int alpha = (int) ((1f - local) * (48f + 82f * energy));
                echoPaint.setStrokeWidth(dp(0.8f + (1f - local) * 1.15f));
                echoPaint.setColor(withAlpha(topicColor, alpha));
                canvas.drawRoundRect(echo, dp(9f) + expansion * 0.16f,
                        dp(9f) + expansion * 0.16f, echoPaint);
            }

            // A short inner sweep makes stable text feel continuously sampled,
            // while the OCR geometry remains readable.
            float sweepX = core.left + core.width() * phase;
            scanPaint.setColor(withAlpha(topicColor, (int) (55 + 72 * wave * energy)));
            canvas.drawLine(sweepX, core.top + dp(2f), sweepX, core.bottom - dp(2f), scanPaint);

            if (showLabels && detailLevel > 0 && semanticZoom >= 0.12f) {
                drawLabel(canvas, hit, core, confidence, semantic);
            }
        }

        // Keep only the overlay animated; OCR and semantic inference continue on
        // their own existing worker/executor path.
        postInvalidateDelayed(FRAME_DELAY_MS);
    }

    private void drawScanLine(Canvas canvas, long now) {
        if (getHeight() <= 0 || getWidth() <= 0) return;
        float t = (now % SCAN_PERIOD_MS) / (float) SCAN_PERIOD_MS;
        float y = t * getHeight();

        scanPaint.setStrokeWidth(dp(1f));
        scanPaint.setColor(Color.argb(34, 255, 255, 255));
        canvas.drawLine(0f, y, getWidth(), y, scanPaint);
        scanPaint.setStrokeWidth(dp(0.6f));
        scanPaint.setColor(Color.argb(18, 255, 255, 255));
        canvas.drawLine(0f, y - dp(5f), getWidth(), y - dp(5f), scanPaint);
        canvas.drawLine(0f, y + dp(5f), getWidth(), y + dp(5f), scanPaint);
    }

    private void drawLabel(Canvas canvas, MatchHit hit, RectF box, float confidence, boolean semantic) {
        String title = hit.node != null && hit.node.title != null && !hit.node.title.trim().isEmpty()
                ? hit.node.title.trim()
                : safeText(hit.searchTerm);
        if (title.isEmpty()) title = safeText(hit.originalText);
        if (title.isEmpty()) return;

        String category = optionalString(hit, "semanticCategory", "");
        String suffix = semantic
                ? String.format(Locale.ROOT, "  %d%%", Math.round(confidence * 100f))
                : "";
        String primary = ellipsize(title + suffix, detailLevel >= 2 ? 42 : 28);
        String secondary = category.isEmpty() ? "" : ellipsize(category, 34);

        float padX = dp(7f);
        float padY = dp(4.5f);
        float primaryWidth = labelPaint.measureText(primary);
        float secondaryWidth = secondary.isEmpty() ? 0f : labelSubPaint.measureText(secondary);
        float labelWidth = Math.max(primaryWidth, secondaryWidth) + padX * 2f;
        float line1 = -labelPaint.ascent() + labelPaint.descent();
        float line2 = secondary.isEmpty() ? 0f : (-labelSubPaint.ascent() + labelSubPaint.descent() + dp(1f));
        float labelHeight = line1 + line2 + padY * 2f;

        float left = clamp(box.left, dp(4f), Math.max(dp(4f), getWidth() - labelWidth - dp(4f)));
        float top = box.top - labelHeight - dp(5f);
        if (top < dp(4f)) top = Math.min(getHeight() - labelHeight - dp(4f), box.bottom + dp(5f));
        RectF bg = new RectF(left, top, left + labelWidth, top + labelHeight);

        int topicColor = hit.node != null ? hit.node.color : Color.rgb(68, 205, 214);
        labelBackgroundPaint.setColor(Color.argb(202, 8, 10, 12));
        canvas.drawRoundRect(bg, dp(6f), dp(6f), labelBackgroundPaint);

        connectorPaint.setColor(withAlpha(topicColor, 150));
        canvas.drawLine(clamp(box.centerX(), bg.left, bg.right), bg.bottom,
                box.centerX(), box.top, connectorPaint);

        float x = bg.left + padX;
        float y = bg.top + padY - labelPaint.ascent();
        canvas.drawText(primary, x, y, labelPaint);
        if (!secondary.isEmpty() && detailLevel >= 2) {
            y += line1 + dp(1f);
            canvas.drawText(secondary, x, y - labelSubPaint.ascent(), labelSubPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
            float x = event.getX();
            float y = event.getY();
            List<MatchHit> snapshot = getHitsSnapshot();
            synchronized (stateLock) {
                for (int i = snapshot.size() - 1; i >= 0; i--) {
                    MatchHit hit = snapshot.get(i);
                    if (hit == null || hit.box == null) continue;
                    RectF box = smoothedBoxes.get(occurrenceKey(hit));
                    if (box != null && padded(box, dp(8f)).contains(x, y)) {
                        if (tapListener != null) tapListener.onHitTap(hit);
                        return true;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private String occurrenceKey(MatchHit hit) {
        RectF b = hit.box;
        int bx = b == null ? 0 : Math.round(b.centerX() / Math.max(1f, dp(56f)));
        int by = b == null ? 0 : Math.round(b.centerY() / Math.max(1f, dp(40f)));
        String node = hit.node == null ? "" : safeText(hit.node.title);
        return node + '|' + safeText(hit.searchTerm) + '|' + safeText(hit.originalText) + '|' + bx + ':' + by;
    }

    private float semanticConfidence(MatchHit hit) {
        float value = optionalFloat(hit, "similarity", Float.NaN);
        if (Float.isNaN(value)) return 0.72f;
        return clamp(value, 0f, 1f);
    }

    private static float optionalFloat(Object target, String name, float fallback) {
        try {
            Field field = target.getClass().getField(name);
            return field.getFloat(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean optionalBoolean(Object target, String name, boolean fallback) {
        try {
            Field field = target.getClass().getField(name);
            return field.getBoolean(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String optionalString(Object target, String name, String fallback) {
        try {
            Field field = target.getClass().getField(name);
            Object value = field.get(target);
            return value == null ? fallback : String.valueOf(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static RectF blend(RectF oldBox, RectF newBox, float alpha) {
        float keep = 1f - alpha;
        return new RectF(
                oldBox.left * keep + newBox.left * alpha,
                oldBox.top * keep + newBox.top * alpha,
                oldBox.right * keep + newBox.right * alpha,
                oldBox.bottom * keep + newBox.bottom * alpha
        );
    }

    private static RectF padded(RectF box, float p) {
        return new RectF(box.left - p, box.top - p, box.right + p, box.bottom + p);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String ellipsize(String value, int maxChars) {
        String text = safeText(value);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 1)).trim() + "…";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
