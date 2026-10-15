package ro.bibliotopicsearch.app;

import android.content.Context;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-triggered, non-sampled capture path for real historical cartography.
 * Live OCR is only a preview; this explicit capture is what counts toward exhaustivity.
 */
public final class ExactCartographyCapture {
    private ExactCartographyCapture() {}

    private static final Pattern PAGE_CUE =
            Pattern.compile("(?i)\\b(?:p\\.?|pag\\.?|pagina)\\s*(\\d{1,5})\\b");

    public static final class Report {
        public final String page;
        public final boolean pageComplete;
        public final boolean completionRequested;
        public final HistoricalCartographyEngine.CaptureMap map;

        Report(
                String page,
                boolean pageComplete,
                boolean completionRequested,
                HistoricalCartographyEngine.CaptureMap map
        ) {
            this.page = page == null ? "" : page.trim();
            this.pageComplete = pageComplete;
            this.completionRequested = completionRequested;
            this.map = map;
        }

        public String compact() {
            HistoricalCartographyEngine.CaptureMap value = map;
            if (value == null) return "captură fără unități";
            StringBuilder out = new StringBuilder();
            if (!page.isEmpty()) out.append("pag. ").append(page).append(" • ");
            out.append(value.paragraphCount()).append(" paragrafe • ")
                    .append(value.clauseCount()).append(" clauze • ")
                    .append(value.distinctTopics()).append(" indici • ")
                    .append(value.intersectionCount()).append(" ×");
            if (value.ambiguousCount() > 0) out.append(" • ").append(value.ambiguousCount()).append(" ambigue");
            if (completionRequested) out.append(pageComplete ? " • PAGINĂ COMPLETĂ" : " • pagina nu a putut fi închisă");
            return out.toString();
        }
    }

    public static Report capture(
            Context context,
            Text text,
            TopicMap thematicMap,
            String pageOverride,
            boolean markPageComplete
    ) {
        if (context == null || text == null) {
            return new Report("", false, markPageComplete,
                    HistoricalCartographyEngine.analyze(Collections.emptyList(), null, thematicMap));
        }

        List<UniversalParagraphDetector.Detection> detections = new ArrayList<>();
        List<Text.TextBlock> blocks = text.getTextBlocks();
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                Text.TextBlock block = blocks.get(i);
                if (block == null || block.getText() == null || block.getText().trim().isEmpty()) continue;
                detections.add(UniversalParagraphDetector.detect(block.getText().trim(), i));
            }
        }

        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        String page = safe(pageOverride);
        if (page.isEmpty()) page = detectPage(text);
        boolean complete = markPageComplete && !page.isEmpty();

        HistoricalCartographyEngine.CaptureMap map = IndexCoreRuntime.captureExhaustive(
                context.getApplicationContext(),
                detections,
                graph,
                thematicMap,
                page,
                complete
        );
        return new Report(page, complete, markPageComplete, map);
    }

    public static String detectPage(Text text) {
        if (text == null) return "";
        String full = text.getText() == null ? "" : text.getText();
        Matcher cue = PAGE_CUE.matcher(full);
        if (cue.find()) return cue.group(1);

        List<Text.TextBlock> blocks = text.getTextBlocks();
        if (blocks == null || blocks.isEmpty()) return "";
        int[] candidates = {0, blocks.size() - 1, Math.min(1, blocks.size() - 1), Math.max(0, blocks.size() - 2)};
        for (int index : candidates) {
            if (index < 0 || index >= blocks.size()) continue;
            String value = blocks.get(index).getText() == null ? "" : blocks.get(index).getText().trim();
            if (value.matches("\\d{1,5}")) return value;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
