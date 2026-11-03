package entity.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builds the HUD lines for the buffs currently active. */
public final class BuffHud {

    /** At or below this many remaining seconds, the line blinks. */
    private static final long BLINK_THRESHOLD_SECONDS = 2;
    /** Blink half-period, in milliseconds. */
    private static final long BLINK_PERIOD_MS = 250;

    private BuffHud() {
    }

    public static List<String> buildLines(final Map<String, Long> remaining,
                                          final long nowMs) {
        List<Map.Entry<String, Long>> entries =
                new ArrayList<Map.Entry<String, Long>>(remaining.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(final Map.Entry<String, Long> left,
                               final Map.Entry<String, Long> right) {
                return left.getValue().compareTo(right.getValue());
            }
        });

        List<String> lines = new ArrayList<String>();
        for (Map.Entry<String, Long> entry : entries) {
            boolean blinking = entry.getValue() <= BLINK_THRESHOLD_SECONDS;
            if (!blinking || (nowMs / BLINK_PERIOD_MS) % 2 == 0)
                lines.add(entry.getKey());
        }
        return lines;
    }
}
