package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OverlayView extends View {
    public interface OnHitTapListener {
        void onHitTap(MatchHit hit);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelConnectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoSubjectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoFunctionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint researchBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint researchBarStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint researchBarTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF researchBarBounds = new RectF();
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

        autoPanelPaint.setStyle(Paint.Style.FILL);
        autoPanelPaint.setColor(Color.argb(226, 20, 28, 36));
        autoAccentPaint.setStyle(Paint.Style.FILL);

        autoHeaderPaint.setColor(Color.rgb(190, 211, 224));
        autoHeaderPaint.setTextSize(sp(9.5f));
        autoHeaderPaint.setFakeBoldText(true);

        autoSubjectPaint.setColor(Color.WHITE);
        autoSubjectPaint.setTextSize(sp(14f));
        autoSubjectPaint.setFakeBoldText(true);

        autoFunctionPaint.setColor(Color.rgb(230, 236, 241));
        autoFunctionPaint.setTextSize(sp(11f));
        autoFunctionPaint.setFakeBoldText(true);

        researchBarPaint.setStyle(Paint.Style.FILL);
        researchBarPaint.setColor(Color.argb(236, 18, 28, 34));
        researchBarStrokePaint.setStyle(Paint.Style.STROKE);
        researchBarStrokePaint.setStrokeWidth(dp(1.8f));
        researchBarTextPaint.setColor(Color.WHITE);
        researchBarTextPaint.setTextSize(sp(11.5f));
        researchBarTextPaint.setFakeBoldText(true);

        TopicMatcher.setResearchQuery(AppPrefs.researchQuery(getContext()));
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

            if (nearest != null) {
                usedOld.add(nearestIndex);
                fresh.box = blend(nearest.box, fresh.box, currentWeight);
            }
            adjusted.add(fresh);
        }

        for (int i = 0; i < old.size(); i++) {
            if (usedOld.contains(i)) continue;
            MatchHit previous = old.get(i);
            if (now - previous.detectedAt <= holdMs) adjusted.add(previous);
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

        if (flashUntil > now) postInvalidateDelayed(70L);
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
        for (MatchHit hit : values) keys.add(occurrenceKey(hit));
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

        for (MatchHit hit : hits) {
            int color = hit.node.color;
            boolean flashing = flashActive && flashingOccurrenceKeys.contains(occurrenceKey(hit));

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(withAlpha(color, flashing ? 100 : 58));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(withAlpha(color, 245));
            strokePaint.setStrokeWidth(dp(flashing ? 4f : 2.5f));

            RectF box = paddedBox(hit.box);
            canvas.drawRoundRect(box, dp(4), dp(4), fillPaint);
            canvas.drawRoundRect(box, dp(4), dp(4), strokePaint);
        }

        // AUTO universal: S/F evidence and explicit research-answer span are independent
        // of the ordinary TEXT / SEM / TARGET display layers.
        drawSemanticTextMarks(canvas);
        drawResearchAnswerMarks(canvas);

        if (showLabels) {
            List<RectF> occupied = new ArrayList<>();
            int fullBudget = hits.size() <= 8 ? hits.size() : 8;
            int fullDrawn = 0;
            for (MatchHit hit : hits) {
                RectF box = paddedBox(hit.box);
                boolean wantFull = fullDrawn < fullBudget;
                boolean fullWasDrawn = drawSmartLabel(canvas, hit, box, occupied, wantFull);
                if (fullWasDrawn) fullDrawn++;
            }
        }

        drawResearchBar(canvas);
        drawAutoSemanticPanel(canvas);
        if (flashActive) postInvalidateDelayed(70L);
    }

    private void drawSemanticTextMarks(Canvas canvas) {
        List<SemanticTextMark> marks = TopicMatcher.latestSemanticTextMarks();
        if (marks == null || marks.isEmpty()) return;

        Set<String> badgeShown = new HashSet<>();
        for (SemanticTextMark mark : marks) {
            if (mark == null || mark.box == null || mark.box.isEmpty()) continue;

            boolean subject = mark.kind == SemanticTextMark.Kind.SUBJECT;
            int color = subject ? Color.rgb(38, 174, 208) : Color.rgb(231, 145, 50);
            int fillAlpha = 48 + (int) Math.round(mark.confidence * 52.0);
            int strokeAlpha = 175 + (int) Math.round(mark.confidence * 75.0);

            RectF box = new RectF(mark.box);
            box.inset(-dp(1.5f), -dp(1f));

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(withAlpha(color, fillAlpha));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(mark.confidence >= 0.66 ? 3.2f : 2.2f));
            strokePaint.setColor(withAlpha(color, strokeAlpha));
            canvas.drawRoundRect(box, dp(3), dp(3), fillPaint);
            canvas.drawRoundRect(box, dp(3), dp(3), strokePaint);

            String badgeKey = mark.kind.name() + "|" + mark.paragraphIndex;
            if (badgeShown.add(badgeKey)) {
                String badge = subject ? "S" : "F";
                drawSmallBadge(canvas, badge, box, color, badgeKey.hashCode());
            }
        }
    }

    private void drawResearchAnswerMarks(Canvas canvas) {
        List<ResearchTextMark> marks = TopicMatcher.latestResearchTextMarks();
        if (marks == null || marks.isEmpty()) return;
        ResearchSemanticEngine.Answer answer = TopicMatcher.latestResearchAnswer();
        int color = Color.rgb(48, 184, 102);
        boolean badgeDrawn = false;

        for (ResearchTextMark mark : marks) {
            if (mark == null || mark.box == null || mark.box.isEmpty()) continue;
            RectF box = new RectF(mark.box);
            box.inset(-dp(2.0f), -dp(1.5f));

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(withAlpha(color, 62 + (int) Math.round(mark.relevance * 42.0)));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(mark.relevance >= 0.70 ? 4.0f : 3.0f));
            strokePaint.setColor(withAlpha(color, 245));
            canvas.drawRoundRect(box, dp(4), dp(4), fillPaint);
            canvas.drawRoundRect(box, dp(4), dp(4), strokePaint);

            if (!badgeDrawn) {
                int pct = (int) Math.round((answer == null ? mark.relevance : answer.score()) * 100.0);
                drawSmallBadge(canvas, "R " + pct + "%", box, color, 991);
                badgeDrawn = true;
            }
        }
    }

    private void drawSmallBadge(Canvas canvas, String text, RectF anchor, int color, int salt) {
        float padding = dp(5);
        float width = Math.max(dp(18), autoHeaderPaint.measureText(text) + padding * 2);
        float height = dp(18);
        float left = clamp(anchor.left + (Math.abs(salt) % 2 == 0 ? 0 : dp(4)), dp(2), getWidth() - width - dp(2));
        float top = clamp(anchor.top - height - dp(2), dp(2), getHeight() - height - dp(2));
        RectF badgeBox = new RectF(left, top, left + width, top + height);
        labelBackgroundPaint.setStyle(Paint.Style.FILL);
        labelBackgroundPaint.setColor(withAlpha(color, 247));
        canvas.drawRoundRect(badgeBox, dp(5), dp(5), labelBackgroundPaint);

        float oldSize = autoHeaderPaint.getTextSize();
        int oldColor = autoHeaderPaint.getColor();
        autoHeaderPaint.setTextSize(sp(8.5f));
        autoHeaderPaint.setColor(Color.WHITE);
        float baseline = badgeBox.centerY() - (autoHeaderPaint.ascent() + autoHeaderPaint.descent()) / 2f;
        float x = badgeBox.centerX() - autoHeaderPaint.measureText(text) / 2f;
        canvas.drawText(text, x, baseline, autoHeaderPaint);
        autoHeaderPaint.setTextSize(oldSize);
        autoHeaderPaint.setColor(oldColor);
    }

    private void drawResearchBar(Canvas canvas) {
        float margin = dp(10);
        float bottom = getHeight() - dp(102);
        float top = bottom - dp(42);
        researchBarBounds.set(margin, top, getWidth() - margin, bottom);

        ResearchSemanticEngine.Profile profile = TopicMatcher.researchProfile();
        boolean enabled = profile != null && profile.enabled();
        int border = enabled ? Color.rgb(48, 184, 102) : Color.rgb(93, 112, 124);
        researchBarStrokePaint.setColor(withAlpha(border, 240));
        canvas.drawRoundRect(researchBarBounds, dp(10), dp(10), researchBarPaint);
        canvas.drawRoundRect(researchBarBounds, dp(10), dp(10), researchBarStrokePaint);

        String raw = profile == null ? "" : profile.rawQuery();
        String text = raw == null || raw.trim().isEmpty()
                ? "CERCETARE • temă sau întrebare…  (gol = tema activă)"
                : "CERCETARE • " + raw.trim();
        text = ellipsizeToWidth(text, researchBarTextPaint, researchBarBounds.width() - dp(22));
        float baseline = researchBarBounds.centerY()
                - (researchBarTextPaint.ascent() + researchBarTextPaint.descent()) / 2f;
        canvas.drawText(text, researchBarBounds.left + dp(11), baseline, researchBarTextPaint);
    }

    private void showResearchQueryEditor() {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setHint("Temă sau întrebare de cercetare");
        input.setText(AppPrefs.researchQuery(getContext()));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(getContext())
                .setTitle("Cercetare semantică")
                .setMessage("Un singur câmp: scrie o temă sau o întrebare. Gol = folosește tema activă.")
                .setView(input)
                .setPositiveButton("Aplică", (dialog, which) -> applyResearchQuery(input.getText().toString()))
                .setNeutralButton("Golește", (dialog, which) -> applyResearchQuery(""))
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void applyResearchQuery(String value) {
        String clean = value == null ? "" : value.trim();
        AppPrefs.setResearchQuery(getContext(), clean);
        TopicMatcher.setResearchQuery(clean);
        invalidate();
    }

    private void drawAutoSemanticPanel(Canvas canvas) {
        UniversalParagraphDetector.Detection best = TopicMatcher.strongestLatestParagraph();
        List<UniversalParagraphDetector.Detection> all = TopicMatcher.latestParagraphDetections();

        float margin = dp(10);
        float height = dp(best == null ? 38 : 78);
        float bottom = getHeight() - dp(14);
        float top = bottom - height;
        RectF panel = new RectF(margin, top, getWidth() - margin, bottom);

        canvas.drawRoundRect(panel, dp(10), dp(10), autoPanelPaint);

        double combined = best == null
                ? 0.0
                : best.subjectConfidence() * 0.55 + best.functionConfidence() * 0.45;
        int accent;
        if (best == null) accent = Color.rgb(91, 110, 124);
        else if (combined >= 0.66) accent = Color.rgb(52, 170, 112);
        else if (combined >= 0.42) accent = Color.rgb(226, 160, 67);
        else accent = Color.rgb(181, 94, 94);
        autoAccentPaint.setColor(accent);
        canvas.drawRoundRect(
                new RectF(panel.left, panel.top, panel.left + dp(5), panel.bottom),
                dp(10), dp(10), autoAccentPaint
        );

        float x = panel.left + dp(12);
        if (best == null) {
            canvas.drawText("AUTO SUBIECT + FUNCȚIE • ACTIV", x, panel.top + dp(24), autoHeaderPaint);
            return;
        }

        int paragraphIndex = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == best) {
                paragraphIndex = i;
                break;
            }
        }

        int subjectConfidence = (int) Math.round(best.subjectConfidence() * 100.0);
        int functionConfidence = (int) Math.round(best.functionConfidence() * 100.0);
        String header = "AUTO S/F • P" + (paragraphIndex + 1) + "/" + Math.max(1, all.size())
                + " • S " + subjectConfidence + "% • F " + functionConfidence + "%";
        canvas.drawText(header, x, panel.top + dp(17), autoHeaderPaint);

        String subject = best.subject();
        if (subject == null || subject.trim().isEmpty()) subject = "subiect nedeterminat";
        subject = ellipsizeToWidth("SUBIECT: " + subject.trim(), autoSubjectPaint, panel.width() - dp(25));
        canvas.drawText(subject, x, panel.top + dp(41), autoSubjectPaint);

        String function = "FUNCȚIE: " + functionLabel(best.function());
        if (best.secondaryFunction() != UniversalDetectionLexicon.Function.UNKNOWN) {
            function += "  ·  secundar: " + functionLabel(best.secondaryFunction());
        }
        function = ellipsizeToWidth(function, autoFunctionPaint, panel.width() - dp(25));
        canvas.drawText(function, x, panel.top + dp(64), autoFunctionPaint);
    }

    private String functionLabel(UniversalDetectionLexicon.Function function) {
        if (function == null) return "NEDETERMINATĂ";
        switch (function) {
            case INTRODUCTION: return "INTRODUCERE";
            case DEFINITION: return "DEFINIRE";
            case DESCRIPTION: return "DESCRIERE";
            case EXPLANATION: return "EXPLICARE";
            case CAUSE_EFFECT: return "CAUZĂ–EFECT";
            case PURPOSE: return "SCOP";
            case CONDITION: return "CONDIȚIE";
            case EXAMPLE: return "EXEMPLIFICARE";
            case ENUMERATION: return "ENUMERARE";
            case CLASSIFICATION: return "CLASIFICARE";
            case COMPARISON: return "COMPARARE";
            case CONTRAST: return "CONTRASTARE";
            case ARGUMENTATION: return "ARGUMENTARE";
            case EVIDENCE: return "DOVADĂ / SUPORT";
            case PROBLEM: return "PROBLEMĂ";
            case SOLUTION: return "SOLUȚIE";
            case SEQUENCE: return "SECVENȚĂ / PROCES";
            case TRANSITION: return "TRANZIȚIE";
            case SUMMARY: return "SINTETIZARE";
            case CONCLUSION: return "CONCLUZIE";
            case DEVELOPMENT: return "DEZVOLTARE";
            case UNKNOWN:
            default: return "NEDETERMINATĂ";
        }
    }

    private String ellipsizeToWidth(String value, Paint paint, float maxWidth) {
        if (value == null) return "";
        if (paint.measureText(value) <= maxWidth) return value;
        String ellipsis = "…";
        float ellipsisWidth = paint.measureText(ellipsis);
        int end = value.length();
        while (end > 1 && paint.measureText(value, 0, end) + ellipsisWidth > maxWidth) end--;
        return value.substring(0, Math.max(1, end)).trim() + ellipsis;
    }

    private RectF paddedBox(RectF source) {
        RectF box = new RectF(source);
        box.inset(-dp(2), -dp(1.5f));
        return box;
    }

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
        String path = hit.node.path == null ? hit.node.title : hit.node.path;
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();

            if (researchBarBounds.contains(x, y)) {
                showResearchQueryEditor();
                performClick();
                return true;
            }

            for (int i = hits.size() - 1; i >= 0; i--) {
                MatchHit hit = hits.get(i);
                RectF expanded = new RectF(hit.box);
                expanded.inset(-dp(8), -dp(8));
                if (expanded.contains(x, y)) {
                    if (tapListener != null) tapListener.onHitTap(hit);
                    performClick();
                    return true;
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
