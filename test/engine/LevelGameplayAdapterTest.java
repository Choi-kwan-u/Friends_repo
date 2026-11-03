package engine;

import level.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LevelGameplayAdapterTest {
    private WaveDefinition wave(String id, String enemy, int x, int y, int movement, int shooting) {
        return new WaveDefinition(id, new EnemyCell[][] {{new EnemyCell(enemy, 3), null}},
                x, y, movement, shooting);
    }
    private LevelDefinition level(WaveDefinition... waves) {
        return new LevelDefinition(1, "campaign-stage-07", "Editable ID is not official", Arrays.asList(waves));
    }

    @Test
    void difficultyPreservesDesignGeometryAndDoesNotMutateTheDefinition() {
        WaveDefinition wave = wave("wave-01", "A", 60, 120, 60, 2000);
        LevelDefinition level = level(wave);
        for (double multiplier : new double[] {1.25, 1.0, 0.8}) {
            GameSettings settings = LevelGameplayAdapter.prepare(level, 448, 520, multiplier).get(0);
            assertEquals(60, settings.getInitialX());
            assertEquals(120, settings.getInitialY());
            assertEquals(2, settings.getFormationWidth());
            assertEquals(Math.round(60 * multiplier), settings.getBaseSpeed());
            assertEquals(Math.round(2000 * multiplier), settings.getShootingFrecuency());
        }
        assertEquals(60, wave.getMovementBaseFrames());
        assertEquals(2000, wave.getShootingIntervalMs());
        assertNull(wave.getCell(0, 1));
        assertEquals(3, wave.getCell(0, 0).getHealth());
    }

    @Test
    void validatesLaterWavesBeforeReturningAnyPreparedLevel() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LevelGameplayAdapter.prepare(level(wave("first", "A", 20, 100, 60, 2000),
                        wave("second", "boss", 20, 100, 60, 2000)), 448, 520, 1));
        assertTrue(error.getMessage().contains("waves[1]"));
        assertTrue(error.getMessage().contains("boss"));
    }

    @Test
    void rejectsUnsupportedGeometryAndUnsafeTimingWithoutInventingBalanceCaps() {
        for (WaveDefinition invalid : Arrays.asList(
                wave("bad-x", "A", 19, 100, 60, 2000),
                wave("bad-y", "A", 20, 101, 60, 2000),
                wave("hud", "A", 20, 20, 60, 2000),
                wave("right", "A", Integer.MAX_VALUE, 100, 60, 2000),
                wave("bottom", "A", 20, 440, 60, 2000),
                wave("movement", "A", 20, 100, Integer.MAX_VALUE, 2000),
                wave("shooting", "A", 20, 100, 60, Integer.MAX_VALUE))) {
            assertThrows(IllegalArgumentException.class,
                    () -> LevelGameplayAdapter.prepare(level(invalid), 448, 520, 1));
        }
        // Health is not capped at a suggested, unagreed balance value.
        WaveDefinition healthy = new WaveDefinition("healthy",
                new EnemyCell[][] {{new EnemyCell("A", 100)}}, 20, 100, 0, 1);
        GameSettings settings = LevelGameplayAdapter.prepare(level(healthy), 448, 520, 1).get(0);
        assertEquals(1, settings.getBaseSpeed());
        assertEquals(250, settings.getShootingFrecuency());
    }

    @Test
    void rejectsInvalidMultiplierAndKeepsWaveOrder() {
        LevelDefinition level = level(wave("second", "B", 20, 100, 60, 2000),
                wave("first", "A", 60, 120, 90, 3000));
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,
                    () -> LevelGameplayAdapter.prepare(level, 448, 520, invalid));
        List<GameSettings> settings = LevelGameplayAdapter.prepare(level, 448, 520, 1);
        assertEquals(20, settings.get(0).getInitialX());
        assertEquals(60, settings.get(1).getInitialX());
        assertThrows(UnsupportedOperationException.class, () -> settings.clear());
    }
}
