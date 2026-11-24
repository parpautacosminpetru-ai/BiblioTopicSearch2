package ro.bibliotopicsearch.app;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class LatestOnlyTest {
    @Test public void burstKeepsOnlyNewestPendingFrame() {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        List<Integer> seen = new ArrayList<>();
        LatestOnly<Integer> latest = new LatestOnly<>(executor::add, seen::add);
        for (int i = 0; i < 1000; i++) latest.offer(i);
        assertEquals(1, executor.size());
        executor.remove().run();
        assertEquals(Arrays.asList(999), seen);
        assertTrue(executor.isEmpty());
    }

    @Test public void newInputsDuringWorkReplacePendingFrame() {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        List<Integer> seen = new ArrayList<>();
        final class Work {
            LatestOnly<Integer> latest;
            void consume(Integer value) {
                seen.add(value);
                if (value == 1) {
                    latest.offer(2);
                    latest.offer(3);
                    latest.offer(4);
                }
            }
        }
        Work work = new Work();
        work.latest = new LatestOnly<>(executor::add, work::consume);
        work.latest.offer(1);
        executor.remove().run();
        assertEquals(1, executor.size());
        executor.remove().run();
        assertEquals(Arrays.asList(1, 4), seen);
        assertTrue(executor.isEmpty());
    }

    @Test public void clearAndCloseDiscardPendingInputs() {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        List<Integer> seen = new ArrayList<>();
        LatestOnly<Integer> latest = new LatestOnly<>(executor::add, seen::add);
        latest.offer(1);
        latest.clear();
        executor.remove().run();
        assertTrue(seen.isEmpty());
        latest.offer(2);
        latest.close();
        latest.offer(3);
        executor.remove().run();
        assertTrue(seen.isEmpty());
        assertTrue(executor.isEmpty());
    }
}
