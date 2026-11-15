package ro.bibliotopicsearch.app;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

/** Regression test: imported themes must be actually visible to the detector. */
@RunWith(AndroidJUnit4.class)
public class BundledThemeInstallerAndroidTest {
    @Test
    public void installedProfilesMustBeReadableAndSelectedForLiveOCR() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BundledThemeInstaller.ensureInstalled(ctx);
        assertEquals(7, BundledThemeInstaller.installedMapCount(ctx));
        List<MapProfile> selected = TopicLibraryStore.selectedForScan(ctx);
        Set<String> names = new HashSet<>();
        int loadedNodes = 0;
        int loadedTerms = 0;
        for (MapProfile profile : selected) {
            if (profile.rawText == null || profile.rawText.trim().isEmpty()) continue;
            names.add(profile.name);
            TopicMap parsed = TopicMapStore.parseForProfile(
                    ctx, profile.id, profile.name, profile.rawText);
            for (TopicNode node : parsed.nodes) {
                loadedNodes++;
                loadedTerms += node.terms.size();
            }
        }
        assertTrue("Seven maps must be selected", names.contains("Istoria Bizanțului — 2026"));
        assertTrue("Medieval map must be selected", names.contains("Europa medievală — unificată"));
        assertTrue("Should load at least 144 topic nodes", loadedNodes >= 144);
        assertTrue("Should load at least 1052 terms", loadedTerms >= 1052);

        // Running recovery again should not create duplicate profiles.
        assertEquals(0, BundledThemeInstaller.restoreMissing(ctx));
        assertEquals(7, BundledThemeInstaller.installedMapCount(ctx));
    }

    @Test
    public void homeScreenLaunchTriggersBundledThemeInstallation() {
        try (ActivityScenario<DetectorHomeActivity> scenario =
                     ActivityScenario.launch(DetectorHomeActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(7, BundledThemeInstaller.installedMapCount(activity));
                assertTrue(TopicLibraryStore.selectedForScan(activity).size() >= 7);
            });
        }
    }
}
