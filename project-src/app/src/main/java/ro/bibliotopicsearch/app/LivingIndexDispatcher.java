package ro.bibliotopicsearch.app;

import android.content.Context;

import com.google.mlkit.vision.text.Text;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking bridge between live semantic detection and the persistent index.
 *
 * The camera/OCR worker must never wait for JSON/SQLite writes. This dispatcher has
 * a single daemon worker, a busy gate and a short sampling interval. If indexing is
 * still running, the newest index sample is simply skipped; OCR and semantic search
 * remain live and the next eligible sample catches up.
 */
public final class LivingIndexDispatcher {
    private LivingIndexDispatcher() {}

    private static final long MIN_SAMPLE_MS = 650L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "biblio-index-dispatcher");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static final AtomicBoolean ACCEPTING = new AtomicBoolean(false);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean OBSERVE_BUSY = new AtomicBoolean(false);
    private static final AtomicLong LAST_ACCEPTED_AT = new AtomicLong(0L);

    public static void start(Context context, long sessionId) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        ACCEPTING.set(true);
        WORKER.execute(() -> {
            try {
                LivingIndexRuntime.start(app, sessionId);
                STARTED.set(true);
                LAST_ACCEPTED_AT.set(0L);
            } catch (RuntimeException ignored) {
                STARTED.set(false);
            }
        });
    }

    public static void stop() {
        ACCEPTING.set(false);
        WORKER.execute(() -> {
            try {
                if (STARTED.get()) LivingIndexRuntime.stop();
            } catch (RuntimeException ignored) {
                // Lifecycle shutdown must not crash the camera activity.
            } finally {
                STARTED.set(false);
                OBSERVE_BUSY.set(false);
                LAST_ACCEPTED_AT.set(0L);
            }
        });
    }

    public static void observe(
            Text text,
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ParagraphCartography.Map cartography
    ) {
        if (!ACCEPTING.get() || !STARTED.get()) return;
        if (text == null || detections == null || detections.isEmpty() || graph == null || cartography == null) return;

        long now = System.currentTimeMillis();
        long previous = LAST_ACCEPTED_AT.get();
        if (now - previous < MIN_SAMPLE_MS) return;
        if (!LAST_ACCEPTED_AT.compareAndSet(previous, now)) return;
        if (!OBSERVE_BUSY.compareAndSet(false, true)) return;

        WORKER.execute(() -> {
            try {
                if (ACCEPTING.get() && STARTED.get()) {
                    LivingIndexRuntime.observe(text, detections, graph, cartography);
                }
            } catch (RuntimeException ignored) {
                // Indexing is a sidecar; OCR/search must remain usable after any bad sample.
            } finally {
                OBSERVE_BUSY.set(false);
            }
        });
    }

    static boolean isStartedForTest() {
        return STARTED.get();
    }

    static boolean isBusyForTest() {
        return OBSERVE_BUSY.get();
    }
}
