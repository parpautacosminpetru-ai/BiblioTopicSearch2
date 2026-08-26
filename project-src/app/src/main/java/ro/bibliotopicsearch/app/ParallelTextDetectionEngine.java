package ro.bibliotopicsearch.app;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes independent detection branches concurrently after OCR has produced text:
 *  1) existing lexical/topic-map matching;
 *  2) paragraph subject + discourse-function detection for every OCR TextBlock.
 *
 * The engine owns a small fixed pool. Create one per Activity/service and close it
 * when that owner is destroyed; do not create a new pool for every camera frame.
 */
public final class ParallelTextDetectionEngine implements AutoCloseable {
    private final ExecutorService pool;

    public static final class CombinedResult {
        private final List<MatchHit> lexicalHits;
        private final List<UniversalParagraphDetector.Detection> paragraphs;

        CombinedResult(
                List<MatchHit> lexicalHits,
                List<UniversalParagraphDetector.Detection> paragraphs
        ) {
            this.lexicalHits = Collections.unmodifiableList(new ArrayList<>(lexicalHits));
            this.paragraphs = Collections.unmodifiableList(new ArrayList<>(paragraphs));
        }

        public List<MatchHit> lexicalHits() { return lexicalHits; }
        public List<UniversalParagraphDetector.Detection> paragraphs() { return paragraphs; }

        public UniversalParagraphDetector.Detection strongestParagraph() {
            UniversalParagraphDetector.Detection best = null;
            double bestScore = -1.0;
            for (UniversalParagraphDetector.Detection detection : paragraphs) {
                double score = detection.subjectConfidence() * 0.55
                        + detection.functionConfidence() * 0.45;
                if (score > bestScore) {
                    bestScore = score;
                    best = detection;
                }
            }
            return best;
        }
    }

    public ParallelTextDetectionEngine() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(2, Math.min(4, cpus));
        this.pool = Executors.newFixedThreadPool(threads, new DetectorThreadFactory());
    }

    /**
     * Live OCR path. TextBlocks are treated as paragraph-like units because ML Kit
     * already groups spatially coherent lines; this avoids re-reading the same text.
     */
    public CombinedResult detect(Text text, TopicMatcher.SearchPlan plan) {
        if (text == null) {
            return new CombinedResult(Collections.emptyList(), Collections.emptyList());
        }

        Future<List<MatchHit>> lexicalFuture = pool.submit(() ->
                plan == null ? Collections.emptyList() : TopicMatcher.find(text, plan)
        );

        List<String> blocks = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            if (block == null) continue;
            String value = block.getText();
            if (value != null && !value.trim().isEmpty()) blocks.add(value.trim());
        }
        if (blocks.isEmpty() && text.getText() != null && !text.getText().trim().isEmpty()) {
            blocks.add(text.getText().trim());
        }

        List<Future<UniversalParagraphDetector.Detection>> paragraphFutures = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            final int index = i;
            final String blockText = blocks.get(i);
            paragraphFutures.add(pool.submit(() -> UniversalParagraphDetector.detect(blockText, index)));
        }

        List<MatchHit> lexicalHits = getOrFallback(
                lexicalFuture,
                () -> plan == null ? Collections.emptyList() : TopicMatcher.find(text, plan),
                Collections.emptyList()
        );

        List<UniversalParagraphDetector.Detection> detections = new ArrayList<>();
        for (int i = 0; i < paragraphFutures.size(); i++) {
            Future<UniversalParagraphDetector.Detection> future = paragraphFutures.get(i);
            final int index = i;
            final String blockText = blocks.get(i);
            UniversalParagraphDetector.Detection detection = getOrFallback(
                    future,
                    () -> UniversalParagraphDetector.detect(blockText, index),
                    null
            );
            if (detection != null) detections.add(detection);
        }

        return new CombinedResult(lexicalHits, detections);
    }

    /** Pasted/imported text path: split on blank lines and detect all paragraphs in parallel. */
    public List<UniversalParagraphDetector.Detection> detectText(String text) {
        List<String> paragraphs = UniversalParagraphDetector.splitParagraphs(text);
        if (paragraphs.isEmpty()) return Collections.emptyList();

        List<Future<UniversalParagraphDetector.Detection>> futures = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            final int index = i;
            final String paragraph = paragraphs.get(i);
            futures.add(pool.submit(() -> UniversalParagraphDetector.detect(paragraph, index)));
        }

        List<UniversalParagraphDetector.Detection> out = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            final int index = i;
            final String paragraph = paragraphs.get(i);
            UniversalParagraphDetector.Detection detection = getOrFallback(
                    futures.get(i),
                    () -> UniversalParagraphDetector.detect(paragraph, index),
                    null
            );
            if (detection != null) out.add(detection);
        }
        return out;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }

    private static <T> T getOrFallback(Future<T> future, Callable<T> fallback, T emptyValue) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return emptyValue;
        } catch (ExecutionException failed) {
            try {
                return fallback.call();
            } catch (Exception ignored) {
                return emptyValue;
            }
        }
    }

    private static final class DetectorThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "biblio-detect-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
