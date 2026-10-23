package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

public class FireOsTest {
    private static final String HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final String SETTINGS = "com.amazon.tv.launcher.ui.MainSettingsActivity";

    @Test
    public void keepsOnlyTheHomeScreen() {
        assertEquals(Collections.singleton(HOME),
                FireOs.homeClasses(Arrays.asList(SETTINGS, HOME, "com.amazon.tv.launcher.ui.DeepLinkActivity", null)));
    }

    @Test
    public void withoutHomeActivityKeepsAllButSettings() {
        assertEquals(new HashSet<>(Collections.singletonList("x.Launcher")),
                FireOs.homeClasses(Arrays.asList("x.Launcher", SETTINGS)));
    }

    @Test
    public void guardDebouncesThenBacksOffALoop() {
        FireOs.Guard g = new FireOs.Guard();
        assertTrue(g.allow(1_000));
        assertFalse(g.allow(1_300));                       // same Home press
        for (long t = 2_000; t <= 4_000; t += 1_000) assertTrue(g.allow(t));
        assertFalse(g.allow(5_000));                       // 5th within 8 s: loop
        assertTrue(g.paused(5_000 + FireOs.LOOP_BACKOFF_MS - 1));
        assertTrue(g.allow(5_000 + FireOs.LOOP_BACKOFF_MS));
    }

    @Test
    public void guardAllowsNormalUse() {
        FireOs.Guard g = new FireOs.Guard();
        for (long t = 0; t < 100_000; t += 3_000) assertTrue(g.allow(t));
    }

    @Test
    public void iconBannerBackgroundFromOpaqueEdges() {
        int[] red = {0xFFFF0000, 0xFFFF0000, 0x00000000, 0xFFFF0000};
        assertEquals(0xFFCC0000, IconBanner.average(red, red.length));      // darkened to 80 %
        assertEquals(0xFF2A2D33, IconBanner.average(new int[8], 8));          // see-through icon
    }
}
