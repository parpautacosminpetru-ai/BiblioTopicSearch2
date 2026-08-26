package ro.bibliotopicsearch.app;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight bridge from the already-computed TopicMatcher semantic sidecar into
 * the one-pass organizer. It never runs OCR or semantic detection itself.
 */
public final class OnePassLiveCollector {
    private OnePassLiveCollector() {}

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicInteger GENERATION = new AtomicInteger(0);

    public static void start() {
        if (!OnePassSemanticOrganizer.isActive()) OnePassSemanticOrganizer.beginSession();
        if (!RUNNING.compareAndSet(false, true)) return;

        final int generation = GENERATION.incrementAndGet();
        Thread worker = new Thread(() -> {
            try {
                while (generation == GENERATION.get() && OnePassSemanticOrganizer.isActive()) {
                    List<UniversalParagraphDetector.Detection> detections =
                            TopicMatcher.latestParagraphDetections();
                    if (detections != null && !detections.isEmpty()) {
                        OnePassSemanticOrganizer.ingest(
                                detections,
                                TopicMatcher.researchProfile()
                        );
                    }
                    try {
                        Thread.sleep(110L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                RUNNING.set(false);
            }
        }, "one-pass-live-collector");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public static void stop() {
        GENERATION.incrementAndGet();
        RUNNING.set(false);
    }

    static boolean runningForTests() {
        return RUNNING.get();
    }
}
