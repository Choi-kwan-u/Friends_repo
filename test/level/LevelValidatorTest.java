package level;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelValidatorTest {
    private final LevelValidator validator = new LevelValidator(LevelImportPolicy.defaults());

    @Test
    void preservesSparseDefinitionsAndBaseValues() throws Exception {
        EnemyCell[][] grid = { { null, new EnemyCell("A", 10), null }, { null, null, null } };
        WaveDefinition wave = new WaveDefinition("wave-01", grid, 40, 120, 0, 100);
        LevelDefinition level = level("Stage", wave);
        validator.validate(level);
        assertSame(wave, level.getWaves().get(0));
        assertEquals(2, wave.getRows());
        assertEquals(3, wave.getColumns());
        assertNull(wave.getCell(0, 0));
        assertNull(wave.getCell(1, 1));
        assertEquals(40, wave.getInitialX());
        assertEquals(120, wave.getInitialY());
        assertEquals(0, wave.getMovementBaseFrames());
    }

    @Test
    void reportsUnknownEnemyAndHealthWithTheirCellLocation() {
        rejected(level("Stage", wave("boss", 1, 1, 1, 60, 1000)),
                "waves[0].formation.grid[0][0].enemyType");
        rejected(level("Stage", wave("A", 11, 1, 1, 60, 1000)),
                "waves[0].formation.grid[0][0].health");
    }

    @Test
    void allowsTheCallerToReplaceProvisionalLimitsAndRegistry() throws Exception {
        LevelImportPolicy policy = LevelImportPolicy.builder()
                .enemyTypes(Arrays.asList("boss")).maxEnemyHealth(20)
                .maxRows(9).maxColumns(10).maxMovementBaseFrames(1000)
                .minShootingIntervalMs(1).maxShootingIntervalMs(100000).build();
        new LevelValidator(policy).validate(level("Custom",
                wave("boss", 20, 9, 10, 1000, 1)));
        new LevelValidator(policy).validate(level("Custom",
                wave("boss", 20, 9, 10, 1000, 100000)));
    }

    @Test
    void acceptsBoundariesAndRejectsValuesJustOutsideThem() throws Exception {
        validator.validate(level("Boundary", wave("A", 10, 7, 8, 600, 60000)));
        rejected(level("Stage", wave("A", 1, 8, 1, 60, 1000)), "waves[0].formation.grid");
        rejected(level("Stage", wave("A", 1, 1, 9, 60, 1000)), "waves[0].formation.grid[0]");
        rejected(level("Stage", wave("A", 1, 1, 1, 601, 1000)), "waves[0].movementBaseFrames");
        rejected(level("Stage", wave("A", 1, 1, 1, 60, 99)), "waves[0].shootingIntervalMs");
        rejected(level("Stage", wave("A", 1, 1, 1, 60, 60001)), "waves[0].shootingIntervalMs");
    }

    @Test
    void boundsTheNumberOfOrderedWaves() throws Exception {
        List<WaveDefinition> waves = new ArrayList<WaveDefinition>();
        for (int index = 0; index < 10; index++) {
            waves.add(new WaveDefinition("wave-" + index,
                    new EnemyCell[][] { { new EnemyCell("A", 1) } }, 0, 0, 0, 100));
        }
        validator.validate(new LevelDefinition(1, "stage", "Ten waves", waves));
        waves.add(new WaveDefinition("wave-10",
                new EnemyCell[][] { { new EnemyCell("A", 1) } }, 0, 0, 0, 100));
        rejected(new LevelDefinition(1, "stage", "Eleven waves", waves), "waves");
    }

    @Test
    void boundsTextAndCountsSupplementaryCharactersOnce() throws Exception {
        WaveDefinition wave = wave("A", 1, 1, 1, 60, 1000);
        String id = repeat("a", 64);
        validator.validate(new LevelDefinition(1, id, repeat("\ud83d\ude80", 100),
                Collections.singletonList(wave)));
        rejected(new LevelDefinition(1, id + "a", "Stage", Collections.singletonList(wave)), "id");
        rejected(level(repeat("\ud83d\ude80", 101), wave), "name");
        WaveDefinition longWaveId = new WaveDefinition(id + "a", wave.getGrid(), 0, 0, 0, 100);
        rejected(level("Stage", longWaveId), "waves[0].id");
        new LevelValidator(LevelImportPolicy.builder().maxIdLength(65).maxNameLength(101).build())
                .validate(new LevelDefinition(1, id + "a", repeat("x", 101),
                        Collections.singletonList(longWaveId)));
    }

    @Test
    void rejectsMalformedUnicodeAndNullDefinitionsWithoutChangingValidData() throws Exception {
        LevelDefinition valid = level("한글 \ud83d\ude80", wave("A", 1, 1, 1, 60, 1000));
        validator.validate(valid);
        rejected(level("bad\ud800", valid.getWaves().get(0)), "name");
        rejected(level("bad\udc00", valid.getWaves().get(0)), "name");
        rejected(level("Stage", wave("bad\ud800", 1, 1, 1, 60, 1000)),
                "waves[0].formation.grid[0][0].enemyType");
        assertThrows(NullPointerException.class, () -> validator.validate(null));
        assertThrows(NullPointerException.class, () -> new LevelValidator(null));
        assertEquals("한글 \ud83d\ude80", valid.getName());
    }

    private static String repeat(final String text, final int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(text);
        return result.toString();
    }

    private void rejected(final LevelDefinition definition, final String path) {
        LevelFormatException error = assertThrows(LevelFormatException.class,
                () -> validator.validate(definition));
        assertTrue(error.getMessage().startsWith(path + ":"), error.getMessage());
    }

    private static LevelDefinition level(final String name, final WaveDefinition wave) {
        return new LevelDefinition(1, "stage", name, Collections.singletonList(wave));
    }

    private static WaveDefinition wave(final String type, final int health,
            final int rows, final int columns, final int movement, final int shooting) {
        EnemyCell[][] grid = new EnemyCell[rows][columns];
        grid[0][0] = new EnemyCell(type, health);
        return new WaveDefinition("wave-01", grid, 20, 100, movement, shooting);
    }
}
