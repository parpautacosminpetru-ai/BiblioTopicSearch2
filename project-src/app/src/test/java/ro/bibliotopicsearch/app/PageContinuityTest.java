package ro.bibliotopicsearch.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class PageContinuityTest {
    private PageContinuity.Sample page(String prefix, float dx, float dy, float height) {
        PageContinuity.Sample s = new PageContinuity.Sample();
        for (int i = 0; i < 12; i++) s.add(prefix + i, 30 + i * 25 + dx, 60 + i * 6 + dy, height);
        return s;
    }

    @Test public void slightJitterIsStableButMovingCameraIsNot() {
        PageContinuity.Sample a = page("text", 0, 0, 20);
        assertEquals(PageContinuity.State.STABLE, PageContinuity.compare(a, page("text", 3, 4, 20)));
        assertEquals(PageContinuity.State.MOVED, PageContinuity.compare(a, page("text", 0, 80, 20)));
        assertEquals(PageContinuity.State.MOVED, PageContinuity.compare(a, page("text", 0, 0, 35)));
    }

    @Test public void newPageAndEmptyFrameDoNotRetainOldMarks() {
        PageContinuity.Sample a = page("first", 0, 0, 20);
        assertEquals(PageContinuity.State.CHANGED, PageContinuity.compare(a, page("other", 0, 0, 20)));
        assertEquals(PageContinuity.State.UNKNOWN, PageContinuity.compare(a, new PageContinuity.Sample()));
    }

    @Test public void repeatedWordsAreNotMisleadingAnchors() {
        PageContinuity.Sample a = new PageContinuity.Sample();
        a.add("same", 10, 10, 10);
        a.add("same", 50, 50, 10);
        a.add("same", 100, 100, 10);
        assertTrue(a.anchors.isEmpty());
    }
}
