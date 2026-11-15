package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveDetectionPipelineTest {
    @Test public void neverQueuesMoreThanOneFrameAndRecoversAfterCompletion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger hits = new AtomicInteger();

        LiveDetectionPipeline<String, String> pipeline = new LiveDetectionPipeline<>(
                0L, Runnable::run,
                value -> {
                    entered.countDown();
                    release.await(2L, TimeUnit.SECONDS);
                    return value;
                },
                new LiveDetectionPipeline.Listener<String>() {
                    @Override public void onResult(String value) {
                        hits.incrementAndGet();
                        completed.countDown();
                    }
                    @Override public void onError(Exception error) {
                        completed.countDown();
                    }
                }
        );
        try {
            assertTrue(pipeline.offer("primul"));
            assertTrue(entered.await(2L, TimeUnit.SECONDS));
            assertFalse(pipeline.offer("cadru de prisos"));
            release.countDown();
            assertTrue(completed.await(2L, TimeUnit.SECONDS));
            assertEquals(1, hits.get());
            assertFalse(pipeline.isBusy());
            assertTrue(pipeline.offer("al doilea"));
        } finally {
            pipeline.close();
        }
    }

    @Test public void closedGateRejectsFrames() {
        LiveDetectionPipeline<String, String> pipeline = new LiveDetectionPipeline<>(
                200L, Runnable::run,
                value -> value,
                new LiveDetectionPipeline.Listener<String>() {
                    @Override public void onResult(String value) {}
                    @Override public void onError(Exception error) {}
                }
        );
        pipeline.close();
        assertFalse(pipeline.offer("după oprire"));
    }

    @Test public void exceptionDoesNotPermanentlyLockGate() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        LiveDetectionPipeline<String, String> pipeline = new LiveDetectionPipeline<>(
                0L, Runnable::run,
                value -> { throw new IllegalArgumentException("OCR error"); },
                new LiveDetectionPipeline.Listener<String>() {
                    @Override public void onResult(String value) {}
                    @Override public void onError(Exception failure) { failed.countDown(); }
                }
        );
        try {
            assertTrue(pipeline.offer("eroare"));
            assertTrue(failed.await(2L, TimeUnit.SECONDS));
            assertFalse(pipeline.isBusy());
            assertTrue(pipeline.offer("reîncercare"));
        } finally {
            pipeline.close();
        }
    }
}
