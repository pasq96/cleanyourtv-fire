package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ThemesTest {
    @Test public void defaultsByDevice() {
        assertEquals("fire", Themes.defaultId(true));
        assertEquals("stock", Themes.defaultId(false));
    }

    @Test public void nextCyclesAndRecovers() {
        assertEquals("clean", Themes.next("fire"));
        assertEquals("fire", Themes.next("stock"));
        assertEquals("fire", Themes.next("unknown"));
    }
}
