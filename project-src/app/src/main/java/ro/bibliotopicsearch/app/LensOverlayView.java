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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quiet overlay for LUPĂ v9. Internal semantic/cartographic structures remain available,
 * but only the layers required by the current zoom/query are painted.
 */
public final class LensOverlayView extends View {
    public interface OnHitTapListener { void onHitTap(MatchHit hit); }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footerSub = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<MatchHit> targetHits = new ArrayList<>();
    private OnHitTapListener tapListener;

    public LensOverlayView(Context context) { super(context); init(); }
    public LensOverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setWillNotDraw(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2.6f));
        badge.setColor(Color.WHITE);
        badge.setTextSize(sp(9f));
        badge.setFakeBoldText(true);
        footer.setStyle(Paint.Style.FILL);
        footer.setColor(Color.argb(224, 16, 24, 31));
        footerText.setColor(Color.WHITE);
        footerText.setTextSize(sp(11.5f));
        footerText.setFakeBoldText(true);
        footerSub.setColor(Color.rgb(195, 211, 221));
        footerSub.setTextSize(sp(9.5f));
    }

    public void setOnHitTapListener(OnHitTapListener listener) { tapListener = listener; }

    public void updateTargetHits(List<MatchHit> hits) {
        targetHits = hits == null ? new ArrayList<>() : new ArrayList<>(hits);
        invalidate();
    }

    public void clearHits() {
        targetHits = new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ResearchSemanticEngine.Profile profile = TopicMatcher.researchProfile();
        boolean queryActive = profile != null && profile.enabled();
        LensDisplayPolicy.Level level = LensUiState.level(getContext());
        LensDisplayPolicy.Plan plan = LensDisplayPolicy.plan(level, queryActive);

        if (plan.target) drawTargets(canvas);
        if (plan.subject) drawSemantic(canvas, SemanticTextMark.Kind.SUBJECT,
                LensPalette.get(getContext(), LensPalette.Role.SUBJECT), level);
        if (plan.function) drawSemantic(canvas, SemanticTextMark.Kind.FUNCTION,
                LensPalette.get(getContext(), LensPalette.Role.FUNCTION), level);
        if (plan.answer) drawAnswers(canvas);
        drawFooter(canvas, level, queryActive);
    }

    private void drawTargets(Canvas canvas) {
        int color = LensPalette.get(getContext(), LensPalette.Role.TARGET);
        for (MatchHit hit : targetHits) {
            if (hit == null || hit.box == null || hit.box.isEmpty()) continue;
            drawBox(canvas, hit.box, color, 42, 220, 2.5f);
        }
    }

    private void drawSemantic(Canvas canvas, SemanticTextMark.Kind kind, int color, LensDisplayPolicy.Level level) {
        List<SemanticTextMark> marks = TopicMatcher.latestSemanticTextMarks();
        if (marks == null || marks.isEmpty()) return;
        boolean onePerParagraph = level == LensDisplayPolicy.Level.SECTION
                || level == LensDisplayPolicy.Level.PARAGRAPH;
        Set<Integer> shown = new HashSet<>();
        for (SemanticTextMark mark : marks) {
            if (mark == null || mark.kind != kind || mark.box == null || mark.box.isEmpty()) continue;
            if (onePerParagraph && !shown.add(mark.paragraphIndex)) continue;
            int fillAlpha = 35 + (int) Math.round(mark.confidence * 38.0);
            drawBox(canvas, mark.box, color, fillAlpha, 225, mark.confidence >= 0.66 ? 3.0f : 2.1f);
        }
    }

    private void drawAnswers(Canvas canvas) {
        List<ResearchTextMark> marks = TopicMatcher.latestResearchTextMarks();
        if (marks == null || marks.isEmpty()) return;
        int color = LensPalette.get(getContext(), LensPalette.Role.ANSWER);
        boolean badgeDrawn = false;
        ResearchSemanticEngine.Answer answer = TopicMatcher.latestResearchAnswer();
        for (ResearchTextMark mark : marks) {
            if (mark == null || mark.box == null || mark.box.isEmpty()) continue;
            drawBox(canvas, mark.box, color, 58 + (int) Math.round(mark.relevance * 38), 245,
                    mark.relevance >= 0.70 ? 4.0f : 3.0f);
            if (!badgeDrawn) {
                String text = "R";
                if (answer != null) text += " " + (int) Math.round(answer.score() * 100.0) + "%";
                drawBadge(canvas, text, mark.box, color);
                badgeDrawn = true;
            }
        }
    }

    private void drawBox(Canvas canvas, RectF raw, int color, int fillAlpha, int strokeAlpha, float strokeDp) {
        RectF box = new RectF(raw);
        box.inset(-dp(1.8f), -dp(1.2f));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(color, fillAlpha));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(strokeDp));
        stroke.setColor(withAlpha(color, strokeAlpha));
        canvas.drawRoundRect(box, dp(4), dp(4), fill);
        canvas.drawRoundRect(box, dp(4), dp(4), stroke);
    }

    private void drawBadge(Canvas canvas, String text, RectF anchor, int color) {
        float pad = dp(5);
        float width = badge.measureText(text) + pad * 2;
        float height = dp(18);
        float left = clamp(anchor.left, dp(3), getWidth() - width - dp(3));
        float top = clamp(anchor.top - height - dp(2), dp(3), getHeight() - height - dp(3));
        RectF box = new RectF(left, top, left + width, top + height);
        fill.setColor(withAlpha(color, 242));
        canvas.drawRoundRect(box, dp(5), dp(5), fill);
        float baseline = box.centerY() - (badge.ascent() + badge.descent()) / 2f;
        canvas.drawText(text, box.left + pad, baseline, badge);
    }

    private void drawFooter(Canvas canvas, LensDisplayPolicy.Level level, boolean queryActive) {
        float margin = dp(10);
        float bottom = getHeight() - dp(78); // search bar belongs to the Activity below this area.
        float height = dp(52);
        RectF panel = new RectF(margin, bottom - height, getWidth() - margin, bottom);
        canvas.drawRoundRect(panel, dp(10), dp(10), footer);

        String first = "LUPĂ " + (level.ordinal() + 1) + "/" + LensDisplayPolicy.Level.values().length
                + " • " + level.labelRo();
        ResearchSemanticEngine.Answer answer = TopicMatcher.latestResearchAnswer();
        if (queryActive && answer != null) first += " • " + answer.relation().name();
        first = ellipsize(first, footerText, panel.width() - dp(20));
        canvas.drawText(first, panel.left + dp(10), panel.top + dp(19), footerText);

        String second;
        if (level == LensDisplayPolicy.Level.SOURCE) {
            ParagraphCartography.Map map = TopicMatcher.latestParagraphCartography();
            second = map == null || map.globalSubject().isEmpty()
                    ? "SURSĂ • nucleu încă nedeterminat"
                    : "SURSĂ • " + map.globalSubject();
        } else {
            UniversalParagraphDetector.Detection best = TopicMatcher.strongestLatestParagraph();
            if (best == null) second = "Apropie textul • caut ancora";
            else {
                String subject = best.subject() == null || best.subject().trim().isEmpty()
                        ? "?" : best.subject().trim();
                second = "S: " + subject;
                if (!queryActive && level.ordinal() >= LensDisplayPolicy.Level.PARAGRAPH.ordinal()) {
                    second += " • F: " + functionLabel(best.function());
                }
            }
        }
        second = ellipsize(second, footerSub, panel.width() - dp(20));
        canvas.drawText(second, panel.left + dp(10), panel.bottom - dp(10), footerSub);
    }

    private String functionLabel(UniversalDetectionLexicon.Function value) {
        if (value == null) return "?";
        switch (value) {
            case DEFINITION: return "DEFINIRE";
            case DESCRIPTION: return "DESCRIERE";
            case EXPLANATION: return "EXPLICARE";
            case CAUSE_EFFECT: return "CAUZĂ–EFECT";
            case PURPOSE: return "SCOP";
            case CONDITION: return "CONDIȚIE";
            case EXAMPLE: return "EXEMPLU";
            case ENUMERATION: return "ENUMERARE";
            case CLASSIFICATION: return "CLASIFICARE";
            case COMPARISON: return "COMPARARE";
            case CONTRAST: return "CONTRAST";
            case ARGUMENTATION: return "ARGUMENT";
            case EVIDENCE: return "DOVADĂ";
            case PROBLEM: return "PROBLEMĂ";
            case SOLUTION: return "SOLUȚIE";
            case SEQUENCE: return "PROCES";
            case CONCLUSION: return "CONCLUZIE";
            case INTRODUCTION: return "INTRODUCERE";
            case SUMMARY: return "SINTEZĂ";
            case TRANSITION: return "TRANZIȚIE";
            case DEVELOPMENT: return "DEZVOLTARE";
            case UNKNOWN:
            default: return "?";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            for (int i = targetHits.size() - 1; i >= 0; i--) {
                MatchHit hit = targetHits.get(i);
                if (hit == null || hit.box == null) continue;
                RectF area = new RectF(hit.box);
                area.inset(-dp(8), -dp(8));
                if (area.contains(x, y)) {
                    if (tapListener != null) tapListener.onHitTap(hit);
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private String ellipsize(String value, Paint paint, float width) {
        if (value == null) return "";
        if (paint.measureText(value) <= width) return value;
        int end = value.length();
        float dots = paint.measureText("…");
        while (end > 1 && paint.measureText(value, 0, end) + dots > width) end--;
        return value.substring(0, Math.max(1, end)).trim() + "…";
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float clamp(float value, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
