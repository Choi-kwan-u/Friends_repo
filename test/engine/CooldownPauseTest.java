package engine;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CooldownPauseTest {
    @Test void waitingTimeAndVarianceSurviveALongPause() throws Exception {
        Cooldown timer = Core.getVariableCooldown(5000, 1000);
        timer.reset(); set(timer, "time", System.currentTimeMillis() - 500);
        int duration = (int) get(timer, "duration"); timer.setPaused(true);
        long remaining = (long) get(timer, "time") + duration - (long) get(timer, "pausedAt");
        age(timer, "time", 12000); age(timer, "pausedAt", 12000);
        assertFalse(timer.checkFinished()); timer.setPaused(false);
        assertEquals(duration, get(timer, "duration"));
        assertTrue(Math.abs((long) get(timer, "time") + duration - System.currentTimeMillis() - remaining) < 100);
    }

    @Test void repeatedPauseAndResumeAreHarmlessForASharedTimer() throws Exception {
        Cooldown timer = Core.getCooldown(2000); timer.reset(); timer.setPaused(true);
        age(timer, "time", 12000); age(timer, "pausedAt", 12000);
        long paused = (long) get(timer, "pausedAt"); timer.setPaused(true);
        assertEquals(paused, get(timer, "pausedAt")); timer.setPaused(false);
        long start = (long) get(timer, "time"); timer.setPaused(false);
        assertEquals(start, get(timer, "time")); assertFalse(timer.checkFinished());
        assertTrue(Math.abs(System.currentTimeMillis() - start) < 100);
    }

    @Test void anUnstartedTimerStaysUnstarted() throws Exception {
        Cooldown timer = Core.getCooldown(2000); timer.setPaused(true);
        age(timer, "pausedAt", 12000); assertTrue(timer.checkFinished()); timer.setPaused(false);
        assertEquals(0L, get(timer, "time")); assertTrue(timer.checkFinished());
    }

    @Test void resetDuringAPauseUsesTheFrozenClock() throws Exception {
        Cooldown timer = Core.getCooldown(2000); timer.setPaused(true);
        age(timer, "pausedAt", 12000); timer.reset();
        assertEquals(get(timer, "pausedAt"), get(timer, "time"));
        assertFalse(timer.checkFinished()); timer.setPaused(false); assertFalse(timer.checkFinished());
    }

    static Object get(Object target, String name) throws Exception {
        Field field = Cooldown.class.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    static void set(Object target, String name, Object value) throws Exception {
        Field field = Cooldown.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    static void age(Object target, String name, long millis) throws Exception { set(target, name, (long) get(target, name) - millis); }
}
