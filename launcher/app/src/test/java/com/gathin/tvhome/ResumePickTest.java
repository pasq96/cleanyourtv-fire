package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ResumePickTest {
    private static final long NOW = 100L * 24 * 3600 * 1000, DAY = 24L * 3600 * 1000;

    private static ResumePick.Item item(String pkg, String title, long engaged, float progress, int type) {
        return new ResumePick.Item(pkg, title, null, null, null, engaged, progress, type);
    }

    @Test public void subtitleAndProgress() {
        assertEquals("S1E2 Pilot", ResumePick.subtitle("Pilot", "1", "2"));
        assertNull(ResumePick.subtitle(" ", null, ""));
        assertEquals(-1f, ResumePick.progress(0, 100), 0f);
        assertEquals(0.5f, ResumePick.progress(50, 100), 0f);
        assertEquals(1f, ResumePick.progress(200, 100), 0f);
    }

    @Test public void pickKeepsNewestFreshUnfinishedOnce() {
        List<ResumePick.Item> out = ResumePick.pick(Arrays.asList(
                item("a", "Old", NOW - 60 * DAY, 0.2f, 0),
                item("a", "Done", NOW - DAY, 0.97f, 0),
                item("b", "Next ep", NOW - 3 * DAY, 1f, 1),
                item("a", "Show", NOW - 2 * DAY, 0.3f, 0),
                item("a", "Show", NOW - DAY, 0.4f, 0)), NOW);
        assertEquals(2, out.size());
        assertEquals(0.4f, out.get(0).progress, 0f);
        assertEquals("Next ep", out.get(1).title);
    }
}
