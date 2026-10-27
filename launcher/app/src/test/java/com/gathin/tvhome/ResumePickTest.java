package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class ResumePickTest {
    private static final long NOW = 100L * 24 * 3600 * 1000, DAY = 24L * 3600 * 1000;
    private static final HashSet<String> NONE = new HashSet<>();

    private static ResumePick.Item item(String pkg, String title, long engaged, float progress, int type) {
        return new ResumePick.Item(pkg, title, null, null, null, engaged, progress, type);
    }

    @Test public void subtitleAndProgress() {
        assertEquals("S1E2 Pilot", ResumePick.subtitle("Pilot", "1", "2"));
        assertNull(ResumePick.subtitle(" ", null, ""));
        assertEquals(-1f, ResumePick.progress(0, 100), 0f);
        assertEquals(1f, ResumePick.progress(200, 100), 0f);
    }

    @Test public void pickKeepsNewestFreshUnfinishedOnce() {
        List<ResumePick.Item> out = ResumePick.pick(Arrays.asList(
                item("a", "Old", NOW - 60 * DAY, 0.2f, 0),
                item("a", "Done", NOW - DAY, 0.97f, 0),
                item("b", "Next ep", NOW - 3 * DAY, 1f, 1),
                item("a", "Show", NOW - 2 * DAY, 0.3f, 0),
                item("a", "Show", NOW - DAY, 0.4f, 0)), NOW, 30, 3, NONE);
        assertEquals(2, out.size());
        assertEquals(0.4f, out.get(0).progress, 0f);
        assertEquals("Next ep", out.get(1).title);
    }

    @Test public void ageLimitPerAppLimitAndHidden() {
        List<ResumePick.Item> all = Arrays.asList(item("a", "A1", NOW - DAY, 0.1f, 0), item("a", "A2", NOW - 2 * DAY, 0.1f, 0),
                item("a", "A3", NOW - 40 * DAY, 0.1f, 0), item("b", "B1", NOW - 3 * DAY, 0.1f, 0));
        assertEquals(4, ResumePick.pick(all, NOW, 0, 0, NONE).size());
        assertEquals(3, ResumePick.pick(all, NOW, 7, 0, NONE).size());
        assertEquals("B1", ResumePick.pick(all, NOW, 7, 1, NONE).get(1).title);
        List<ResumePick.Item> hid = ResumePick.pick(all, NOW, 7, 1, new HashSet<>(Collections.singleton("a\nA1")));
        assertEquals("A2", hid.get(0).title);
        assertEquals(2, hid.size());
    }

    @Test public void suggestionsTakeTurnsAcrossApps() {
        List<ResumePick.Item> out = ResumePick.suggest(Arrays.asList(
                item("y", "Y1", 9, -1f, 10), item("y", "Y2", 8, -1f, 10), item("s", "S1", 5, -1f, 10), item("s", "S2", 4, -1f, 10)),
                NONE, new HashSet<>(Collections.singleton("s\nS1")));
        assertEquals(Arrays.asList("Y1", "S2", "Y2"), Arrays.asList(out.get(0).title, out.get(1).title, out.get(2).title));
    }
}
