package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OverlayView extends View {
    public interface OnHitTapListener {
        void onHitTap(List<MatchHit> hits);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelConnectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<MatchHit> hits = new ArrayList<>();
    private Set<String> previousOccurrenceKeys = new HashSet<>();
    private Set<String> flashingOccurrenceKeys = new HashSet<>();
    private long flashUntil = 0L;
    private boolean showLabels = true;
    private OnHitTapListener tapListener;

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
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2.5f));

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(sp(13));
        labelPaint.setFakeBoldText(true);

        labelSubPaint.setColor(Color.argb(235, 255, 255, 255));
        labelSubPaint.setTextSize(sp(9.5f));

        labelConnectorPaint.setStyle(Paint.Style.STROKE);
        labelConnectorPaint.setStrokeWidth(dp(2f));
    }

    public void setOnHitTapListener(OnHitTapListener listener) {
        tapListener = listener;
    }

    public void clearHits() {
        hits = new ArrayList<>();
        previousOccurrenceKeys.clear();
        flashingOccurrenceKeys.clear();
        invalidate();
    }

    public List<MatchHit> getHitsSnapshot() {
        return new ArrayList<>(hits);
    }

    /**
     * 100 = coordonatele OCR curente au prioritate maximă (precizie mare).
     * Valori mai mici aplică mai multă netezire și păstrează foarte scurt o
     * potrivire dacă OCR-ul o ratează într-un singur cadru.
     */
    public void updateHits(List<MatchHit> incoming, int precision, boolean labels) {
        showLabels = labels;
        List<MatchHit> old = hits;
        List<MatchHit> adjusted = new ArrayList<>();
        Set<Integer> usedOld = new HashSet<>();
        Map<String, List<Integer>> oldByIdentity = new HashMap<>();
        for (int i = 0; i < old.size(); i++) {
            oldByIdentity
                    .computeIfAbsent(old.get(i).identityKey(), ignored -> new ArrayList<>())
                    .add(i);
        }

        int safePrecision = Math.max(0, Math.min(100, precision));
        float currentWeight = 0.35f + 0.65f * (safePrecision / 100f);
        float maxDistance = dp(34f + (100 - safePrecision) * 0.55f);
        long holdMs = 70L + (100 - safePrecision) * 2L;
        long now = System.currentTimeMillis();

        for (MatchHit fresh : incoming) {
            MatchHit nearest = null;
            int nearestIndex = -1;
            float nearestDistance = Float.MAX_VALUE;

            List<Integer> sameIdentity = oldByIdentity.get(fresh.identityKey());
            if (sameIdentity != null) {
                for (int i : sameIdentity) {
                    if (usedOld.contains(i)) continue;
                    MatchHit previous = old.get(i);

                    float dx = previous.box.centerX() - fresh.box.centerX();
                    float dy = previous.box.centerY() - fresh.box.centerY();
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance < nearestDistance && distance <= maxDistance) {
                        nearestDistance = distance;
                        nearest = previous;
                        nearestIndex = i;
                    }
                }
            }

            RectF box = fresh.box;
            if (nearest != null) {
                usedOld.add(nearestIndex);
                if (fresh.segments.size() == 1 && nearest.segments.size() == 1)
                    box = blend(nearest.box, fresh.box, currentWeight);
            }
            if (fresh.segments.size() == 1) {
                adjusted.add(new MatchHit(box, fresh.originalText, fresh.searchTerm, fresh.node, fresh.detectedAt));
            } else adjusted.add(fresh);
        }

        // Evită pâlpâirea când OCR-ul pierde termenul pentru un singur cadru.
        for (int i = 0; i < old.size(); i++) {
            if (usedOld.contains(i)) continue;
            MatchHit previous = old.get(i);
            if (now - previous.detectedAt <= holdMs) {
                adjusted.add(previous);
            }
        }

        Set<String> nowKeys = occurrenceKeys(adjusted);
        Set<String> newKeys = new HashSet<>(nowKeys);
        newKeys.removeAll(previousOccurrenceKeys);
        if (!newKeys.isEmpty()) {
            flashingOccurrenceKeys = newKeys;
            flashUntil = now + 220L;
        }
        previousOccurrenceKeys = nowKeys;
        hits = adjusted;
        invalidate();

        if (flashUntil > now) {
            postInvalidateDelayed(70L);
        }
    }

    private RectF blend(RectF oldBox, RectF newBox, float currentWeight) {
        float previousWeight = 1f - currentWeight;
        return new RectF(
                oldBox.left * previousWeight + newBox.left * currentWeight,
                oldBox.top * previousWeight + newBox.top * currentWeight,
                oldBox.right * previousWeight + newBox.right * currentWeight,
                oldBox.bottom * previousWeight + newBox.bottom * currentWeight
        );
    }

    private Set<String> occurrenceKeys(List<MatchHit> values) {
        Set<String> keys = new HashSet<>();
        for (MatchHit hit : values) {
            keys.add(occurrenceKey(hit));
        }
        return keys;
    }

    private String occurrenceKey(MatchHit hit) {
        int x = Math.round(hit.box.centerX() / Math.max(1f, dp(8)));
        int y = Math.round(hit.box.centerY() / Math.max(1f, dp(8)));
        return hit.identityKey() + "|" + x + "|" + y;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        boolean flashActive = now < flashUntil;

        // Equal OCR boxes may belong to several maps: draw each colour in a
        // separate band instead of painting the last map over all others.
        Map<String, List<MatchHit>> groups = new LinkedHashMap<>();
        Map<String, RectF> geometry = new HashMap<>();
        for (MatchHit hit : hits) for (RectF box : hit.segments) {
            String key = Math.round(box.left / 4f) + ":" + Math.round(box.top / 4f) + ":" +
                    Math.round(box.right / 4f) + ":" + Math.round(box.bottom / 4f);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(hit);
            geometry.putIfAbsent(key, box);
        }
        for (Map.Entry<String, List<MatchHit>> entry : groups.entrySet()) {
            List<MatchHit> group = entry.getValue();
            MatchHit first = group.get(0);
            RectF box = paddedBox(geometry.get(entry.getKey()));
            Set<Integer> colors = new LinkedHashSet<>();
            boolean flashing = false;
            for (MatchHit hit : group) {
                colors.add(hit.node.color);
                flashing |= flashActive && flashingOccurrenceKeys.contains(occurrenceKey(hit));
            }
            int color = first.node.color;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(withAlpha(color, flashing ? 90 : 42));
            canvas.drawRoundRect(box, dp(3), dp(3), fillPaint);
            if (colors.size() == 1) {
                strokePaint.setColor(withAlpha(color, 245));
                strokePaint.setStrokeWidth(dp(flashing ? 3 : 2));
                canvas.drawRoundRect(box, dp(3), dp(3), strokePaint);
            } else {
                float step = box.width() / colors.size();
                float left = box.left;
                for (int c : colors) {
                    fillPaint.setColor(withAlpha(c, 245));
                    canvas.drawRect(left, box.top, left + step, box.top + dp(3), fillPaint);
                    canvas.drawRect(left, box.bottom - dp(3), left + step, box.bottom, fillPaint);
                    left += step;
                }
            }
        }

        if (showLabels) {
            List<RectF> occupied = new ArrayList<>();
            int fullBudget = Math.min(8, hits.size());
            int fullDrawn = 0;
            for (MatchHit hit : hits) {
                RectF box = paddedBox(hit.segments.get(0));
                boolean wantFull = fullDrawn < fullBudget;
                boolean fullWasDrawn = drawSmartLabel(canvas, hit, box, occupied, wantFull);
                if (fullWasDrawn) fullDrawn++;
                if (occupied.size() >= 24) break; // highlights remain visible for every hit
            }
        }

        if (flashActive) postInvalidateDelayed(70L);
    }

    private RectF paddedBox(RectF source) {
        RectF box = new RectF(source);
        box.inset(-dp(2), -dp(1.5f));
        return box;
    }

    /**
     * Etichetele complete sunt plasate în jurul cuvântului fără să se suprapună.
     * Dacă nu mai există loc sau sunt multe rezultate, desenăm un badge compact,
     * însă highlight-ul OCR rămâne vizibil pentru toate potrivirile.
     */
    private boolean drawSmartLabel(
            Canvas canvas,
            MatchHit hit,
            RectF box,
            List<RectF> occupied,
            boolean allowFull
    ) {
        if (!allowFull) {
            drawCompactBadge(canvas, hit, box, occupied);
            return false;
        }

        String symbol = hit.node.symbol == null ? "" : hit.node.symbol.trim();
        String term = hit.searchTerm == null ? "" : hit.searchTerm.trim();
        String path = hit.node.displayName();
        String main = (symbol.isEmpty() ? "" : symbol + " ") + ellipsizeEnd(term, 30);
        String sub = ellipsizeStart(path == null ? "" : path, 42);

        float textWidth = Math.max(labelPaint.measureText(main), labelSubPaint.measureText(sub));
        float width = Math.min(getWidth() - dp(8), textWidth + dp(12));
        float height = sub.isEmpty() ? dp(24) : dp(38);

        List<RectF> candidates = new ArrayList<>();
        float baseLeft = clamp(box.left, dp(4), getWidth() - width - dp(4));
        candidates.add(new RectF(baseLeft, box.top - dp(3) - height, baseLeft + width, box.top - dp(3)));
        candidates.add(new RectF(baseLeft, box.bottom + dp(3), baseLeft + width, box.bottom + dp(3) + height));

        float rightAligned = clamp(box.right - width, dp(4), getWidth() - width - dp(4));
        candidates.add(new RectF(rightAligned, box.top - dp(3) - height, rightAligned + width, box.top - dp(3)));
        candidates.add(new RectF(rightAligned, box.bottom + dp(3), rightAligned + width, box.bottom + dp(3) + height));

        RectF chosen = null;
        for (RectF candidate : candidates) {
            if (candidate.top < dp(4) || candidate.bottom > getHeight() - dp(4)) continue;
            if (!intersectsAny(candidate, occupied)) {
                chosen = candidate;
                break;
            }
        }

        if (chosen == null) {
            drawCompactBadge(canvas, hit, box, occupied);
            return false;
        }

        occupied.add(new RectF(chosen));
        boolean above = chosen.bottom <= box.top;
        labelConnectorPaint.setColor(withAlpha(hit.node.color, 235));
        canvas.drawLine(
                chosen.centerX(),
                above ? chosen.bottom : chosen.top,
                box.centerX(),
                above ? box.top : box.bottom,
                labelConnectorPaint
        );

        labelBackgroundPaint.setColor(withAlpha(hit.node.color, 238));
        canvas.drawRoundRect(chosen, dp(6), dp(6), labelBackgroundPaint);

        float mainBaseline = sub.isEmpty()
                ? chosen.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f
                : chosen.top + dp(15);
        canvas.drawText(main, chosen.left + dp(6), mainBaseline, labelPaint);
        if (!sub.isEmpty()) canvas.drawText(sub, chosen.left + dp(6), chosen.bottom - dp(6), labelSubPaint);
        return true;
    }

    private void drawCompactBadge(Canvas canvas, MatchHit hit, RectF box, List<RectF> occupied) {
        String symbol = hit.node.symbol == null ? "" : hit.node.symbol.trim();
        String text = symbol.isEmpty() ? "•" : ellipsizeEnd(symbol, 2);
        float size = dp(22);
        float left = clamp(box.right + dp(3), dp(4), getWidth() - size - dp(4));
        float top = clamp(box.centerY() - size / 2f, dp(4), getHeight() - size - dp(4));
        RectF badge = new RectF(left, top, left + size, top + size);

        if (intersectsAny(badge, occupied)) {
            left = clamp(box.left - size - dp(3), dp(4), getWidth() - size - dp(4));
            badge = new RectF(left, top, left + size, top + size);
        }
        occupied.add(new RectF(badge));
        labelBackgroundPaint.setColor(withAlpha(hit.node.color, 225));
        canvas.drawRoundRect(badge, dp(8), dp(8), labelBackgroundPaint);

        float oldSize = labelPaint.getTextSize();
        labelPaint.setTextSize(sp(11));
        float baseline = badge.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f;
        float x = badge.centerX() - labelPaint.measureText(text) / 2f;
        canvas.drawText(text, x, baseline, labelPaint);
        labelPaint.setTextSize(oldSize);
    }

    private boolean intersectsAny(RectF value, List<RectF> occupied) {
        RectF expanded = new RectF(value);
        expanded.inset(-dp(2), -dp(2));
        for (RectF other : occupied) {
            if (RectF.intersects(expanded, other)) return true;
        }
        return false;
    }

    private float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private String ellipsizeEnd(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private String ellipsizeStart(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return "…" + value.substring(value.length() - Math.max(1, maxChars - 1));
    }

    private List<MatchHit> hitsAt(float x, float y) {
        List<MatchHit> matches = new ArrayList<>();
        for (int i = hits.size() - 1; i >= 0; i--) {
            MatchHit hit = hits.get(i);
            for (RectF part : hit.segments) {
                RectF expanded = new RectF(part);
                expanded.inset(-dp(6), -dp(6));
                if (expanded.contains(x, y)) { matches.add(hit); break; }
            }
        }
        return matches;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Empty parts of the overlay pass touch through to PreviewView,
            // enabling camera autofocus and pinch-to-zoom.
            return !hitsAt(event.getX(), event.getY()).isEmpty();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            List<MatchHit> matches = hitsAt(event.getX(), event.getY());
            if (tapListener != null && !matches.isEmpty()) tapListener.onHitTap(matches);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
