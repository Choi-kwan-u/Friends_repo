package level;

import java.util.ArrayList;
import java.util.List;

/** Standalone contract checks; no test framework dependency is required. */
public final class LevelModelTest {
    public static void main(final String[] args) {
        EnemyCell a = new EnemyCell("A", 1);
        EnemyCell c = new EnemyCell("C", 2);
        EnemyCell[][] input = {
            { a, null, c },
            { null, null, null }
        };
        WaveDefinition wave = new WaveDefinition("wave-01", input, 60, 120, 0, 2000);
        input[0][0] = null;
        input[1] = new EnemyCell[] { a };
        check(wave.getCell(0, 0) == a, "Input mutation changed the model");
        check(wave.getRows() == 2 && wave.getColumns() == 3,
                "Empty row or column was removed");
        check(wave.getEnemyCount() == 2, "Gaps were counted as enemies");
        check(wave.getCell(0, 1) == null && wave.getCell(1, 2) == null,
                "Empty cells moved");
        EnemyCell[][] output = wave.getGrid();
        output[0][2] = null;
        check(wave.getCell(0, 2) == c, "Getter exposed mutable grid storage");
        check(wave.getInitialX() == 60 && wave.getInitialY() == 120,
                "Non-default placement was lost");
        check(wave.getMovementBaseFrames() == 0,
                "Valid zero base timing was changed");

        List<WaveDefinition> inputWaves = new ArrayList<WaveDefinition>();
        inputWaves.add(wave);
        final WaveDefinition secondWave = new WaveDefinition("wave-02",
                new EnemyCell[][] { { null, a } }, 40, 100, 50, 1500);
        inputWaves.add(secondWave);
        final LevelDefinition level = new LevelDefinition(1, "stage-01", "Test",
                inputWaves);
        inputWaves.clear();
        check(level.getWaves().size() == 2, "Input list mutation changed the level");
        check(level.getWaves().get(0) == wave && level.getWaves().get(1) == secondWave,
                "Wave order changed");
        check(level.getWaves().get(1).getCell(0, 0) == null,
                "Second-wave placement changed");
        try {
            level.getWaves().clear();
            throw new AssertionError("Wave list is mutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: runtime users cannot change the design through this view.
        }

        expectInvalid(new Runnable() {
            public void run() {
                new WaveDefinition("wave-01", new EnemyCell[][] {
                    { new EnemyCell("A", 1), null }, { null }
                }, 20, 100, 60, 2000);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new WaveDefinition("wave-01", new EnemyCell[][] {
                    { null, null }, { null, null }
                }, 20, 100, 60, 2000);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                List<WaveDefinition> duplicate = new ArrayList<WaveDefinition>();
                duplicate.add(level.getWaves().get(0));
                duplicate.add(level.getWaves().get(0));
                new LevelDefinition(1, "stage-01", "Test", duplicate);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new LevelDefinition(2, "stage-01", "Test", level.getWaves());
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new EnemyCell("A", 0);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new WaveDefinition("wave-03", new EnemyCell[][] { null },
                        20, 100, 60, 2000);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new LevelDefinition(1, "stage-01", "Test",
                        new ArrayList<WaveDefinition>());
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                List<WaveDefinition> withNull = new ArrayList<WaveDefinition>();
                withNull.add(null);
                new LevelDefinition(1, "stage-01", "Test", withNull);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new EnemyCell(" A ", 1);
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new LevelDefinition(1, "invalid id", "Test", level.getWaves());
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new LevelDefinition(1, "stage-01", "  ", level.getWaves());
            }
        });
        final int[][] invalidParameters = {
            { -1, 100, 60, 2000 },
            { 20, -1, 60, 2000 },
            { 20, 100, -1, 2000 },
            { 20, 100, 60, 0 },
            { 20, 100, 60, -1 }
        };
        for (final int[] values : invalidParameters) {
            expectInvalid(new Runnable() {
                public void run() {
                    new WaveDefinition("wave-03", secondWave.getGrid(),
                            values[0], values[1], values[2], values[3]);
                }
            });
        }
        LevelFormatException formatError = new LevelFormatException(
                "waves[0]: invalid grid", new IllegalArgumentException("ragged"));
        check(formatError instanceof java.io.IOException && formatError.getCause() != null,
                "Format error lost its IO contract or cause");
        System.out.println("Level model contract checks passed.");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectInvalid(final Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid definition was accepted");
    }

    private LevelModelTest() {
    }
}
