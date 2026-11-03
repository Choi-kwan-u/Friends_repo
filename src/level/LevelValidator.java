package level;

import java.util.Objects;

/** Checks import policy without changing a definition or applying it to a game. */
public final class LevelValidator {
    private final LevelImportPolicy policy;

    public LevelValidator(final LevelImportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void validate(final LevelDefinition level) throws LevelFormatException {
        Objects.requireNonNull(level, "level");
        validateId(level.getId(), "id");
        validateName(level.getName());
        maximum(level.getWaves().size(), policy.getMaxWaves(), "waves");
        for (int index = 0; index < level.getWaves().size(); index++) {
            WaveDefinition wave = level.getWaves().get(index);
            String path = "waves[" + index + "]";
            validateId(wave.getId(), path + ".id");
            maximum(wave.getRows(), policy.getMaxRows(), path + ".formation.grid");
            maximum(wave.getColumns(), policy.getMaxColumns(), path + ".formation.grid[0]");
            maximum(wave.getMovementBaseFrames(), policy.getMaxMovementBaseFrames(),
                    path + ".movementBaseFrames");
            int shooting = wave.getShootingIntervalMs();
            if (shooting < policy.getMinShootingIntervalMs()
                    || shooting > policy.getMaxShootingIntervalMs()) {
                throw new LevelFormatException(path + ".shootingIntervalMs: expected "
                        + policy.getMinShootingIntervalMs() + ".." + policy.getMaxShootingIntervalMs());
            }
            for (int row = 0; row < wave.getRows(); row++) {
                for (int column = 0; column < wave.getColumns(); column++) {
                    EnemyCell cell = wave.getCell(row, column);
                    if (cell != null) {
                        String cellPath = path + ".formation.grid[" + row + "][" + column + "]";
                        validateUnicode(cell.getEnemyType(), cellPath + ".enemyType");
                        if (!policy.getEnemyTypes().contains(cell.getEnemyType())) {
                            throw new LevelFormatException(cellPath + ".enemyType: unsupported enemy type");
                        }
                        maximum(cell.getHealth(), policy.getMaxEnemyHealth(), cellPath + ".health");
                    }
                }
            }
        }
    }

    void validateId(final String value, final String path) throws LevelFormatException {
        maximum(value.length(), policy.getMaxIdLength(), path);
    }

    void validateName(final String value) throws LevelFormatException {
        validateUnicode(value, "name");
        maximum(value.codePointCount(0, value.length()), policy.getMaxNameLength(), "name");
    }

    static void maximum(final int actual, final int maximum, final String path)
            throws LevelFormatException {
        if (actual > maximum) {
            throw new LevelFormatException(path + ": exceeds configured maximum " + maximum);
        }
    }

    static void validateUnicode(final String value, final String path) throws LevelFormatException {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 == value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new LevelFormatException(path + ": unpaired UTF-16 surrogate at index " + index);
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new LevelFormatException(path + ": unpaired UTF-16 surrogate at index " + index);
            }
        }
    }
}
