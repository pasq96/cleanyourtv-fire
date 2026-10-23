package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

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
}
