package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Owned by the matching worker; never does temporal matching on the UI thread. */
final class LiveFrameStabilizer {
    private final TemporalEvidence<MatchHit> tracker = new TemporalEvidence<>();
    private PageContinuity.Sample previous;
    private long epoch = -1;

    List<MatchHit> update(long generation, Text text, List<MatchHit> hits, long timestamp, int precision) {
        if (epoch != generation) {
            tracker.clear();
            previous = null;
            epoch = generation;
        }
        PageContinuity.Sample sample = new PageContinuity.Sample();
        if (text != null) for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
            for (Text.Element element : line.getElements()) {
                Rect box = element.getBoundingBox();
                if (box == null || box.height() <= 0) continue;
                String value = TopicMatcher.normalize(element.getText(), true);
                if (!value.isEmpty() && value.indexOf(' ') < 0)
                    sample.add(value, box.exactCenterX(), box.exactCenterY(), box.height());
            }
        PageContinuity.State state = PageContinuity.compare(previous, sample);
        previous = sample;
        List<TemporalEvidence.Observation<MatchHit>> observations = new ArrayList<>(hits.size());
        for (MatchHit raw : hits) {
            MatchHit hit = raw.withTimestamp(timestamp);
            // Distinct map/node identities always stay separate.
            // Include the actual text in the identity: a changed OCR word
            // cannot inherit geometry or confirmation from another reading.
            String key = hit.identityKey() + "\u0001" + hit.originalText + "\u0001" + hit.kind;
            observations.add(new TemporalEvidence.Observation<>(key, hit.box.centerX(),
                    hit.box.centerY(), Math.max(1f, hit.segments.get(0).height()),
                    hit.kind != MatchHit.Kind.LITERAL, hit));
        }
        List<MatchHit> result = new ArrayList<>();
        float currentWeight = 0.28f + 0.72f * Math.max(0, Math.min(100, precision)) / 100f;
        for (TemporalEvidence.Result<MatchHit> tracked : tracker.update(observations, state, timestamp)) {
            MatchHit fresh = tracked.current.value;
            // Never display a word absent from the latest OCR result.
            if (tracked.held) continue;
            MatchHit old = tracked.previous == null ? null : tracked.previous.value;
            if (old == null || old.segments.size() != fresh.segments.size() || currentWeight >= 1f) {
                result.add(fresh);
                continue;
            }
            List<RectF> boxes = new ArrayList<>(fresh.segments.size());
            for (int i = 0; i < fresh.segments.size(); i++) {
                RectF a = old.segments.get(i), b = fresh.segments.get(i);
                float w = currentWeight, v = 1f - w;
                boxes.add(new RectF(a.left * v + b.left * w, a.top * v + b.top * w,
                        a.right * v + b.right * w, a.bottom * v + b.bottom * w));
            }
            result.add(fresh.withGeometry(boxes, false));
        }
        return result;
    }
}
