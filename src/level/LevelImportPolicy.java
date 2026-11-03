package level;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable import limits. Defaults are the review proposals from issue #16. */
public final class LevelImportPolicy {
    private final Set<String> enemyTypes;
    private final int maxFileBytes;
    private final int maxWaves;
    private final int maxRows;
    private final int maxColumns;
    private final int maxEnemyHealth;
    private final int maxMovementBaseFrames;
    private final int minShootingIntervalMs;
    private final int maxShootingIntervalMs;
    private final int maxIdLength;
    private final int maxNameLength;

    private LevelImportPolicy(final Builder builder) {
        positive(builder.maxFileBytes, "maxFileBytes");
        positive(builder.maxWaves, "maxWaves");
        positive(builder.maxRows, "maxRows");
        positive(builder.maxColumns, "maxColumns");
        positive(builder.maxEnemyHealth, "maxEnemyHealth");
        positive(builder.minShootingIntervalMs, "minShootingIntervalMs");
        positive(builder.maxIdLength, "maxIdLength");
        positive(builder.maxNameLength, "maxNameLength");
        if (builder.maxMovementBaseFrames < 0) {
            throw new IllegalArgumentException("maxMovementBaseFrames must be nonnegative");
        }
        if (builder.maxShootingIntervalMs < builder.minShootingIntervalMs) {
            throw new IllegalArgumentException("Shooting interval maximum must be >= minimum");
        }
        if (builder.enemyTypes == null || builder.enemyTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one supported enemy type is required");
        }
        Set<String> copy = new LinkedHashSet<String>();
        for (String type : builder.enemyTypes) {
            // Apply the same identifier rules as an occupied model cell.
            new EnemyCell(type, 1);
            copy.add(type);
        }
        this.enemyTypes = Collections.unmodifiableSet(copy);
        this.maxFileBytes = builder.maxFileBytes;
        this.maxWaves = builder.maxWaves;
        this.maxRows = builder.maxRows;
        this.maxColumns = builder.maxColumns;
        this.maxEnemyHealth = builder.maxEnemyHealth;
        this.maxMovementBaseFrames = builder.maxMovementBaseFrames;
        this.minShootingIntervalMs = builder.minShootingIntervalMs;
        this.maxShootingIntervalMs = builder.maxShootingIntervalMs;
        this.maxIdLength = builder.maxIdLength;
        this.maxNameLength = builder.maxNameLength;
    }

    public static LevelImportPolicy defaults() { return builder().build(); }
    public static Builder builder() { return new Builder(); }
    public Set<String> getEnemyTypes() { return enemyTypes; }
    public int getMaxFileBytes() { return maxFileBytes; }
    public int getMaxWaves() { return maxWaves; }
    public int getMaxRows() { return maxRows; }
    public int getMaxColumns() { return maxColumns; }
    public int getMaxEnemyHealth() { return maxEnemyHealth; }
    public int getMaxMovementBaseFrames() { return maxMovementBaseFrames; }
    public int getMinShootingIntervalMs() { return minShootingIntervalMs; }
    public int getMaxShootingIntervalMs() { return maxShootingIntervalMs; }
    public int getMaxIdLength() { return maxIdLength; }
    public int getMaxNameLength() { return maxNameLength; }

    private static void positive(final int value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /** Each setting can be replaced without changing the shared file format. */
    public static final class Builder {
        private Collection<String> enemyTypes = Arrays.asList("A", "B", "C");
        private int maxFileBytes = 1024 * 1024;
        private int maxWaves = 10;
        private int maxRows = 7;
        private int maxColumns = 8;
        private int maxEnemyHealth = 10;
        private int maxMovementBaseFrames = 600;
        private int minShootingIntervalMs = 100;
        private int maxShootingIntervalMs = 60000;
        private int maxIdLength = 64;
        private int maxNameLength = 100;

        public Builder enemyTypes(final Collection<String> value) {
            this.enemyTypes = value == null ? null : new LinkedHashSet<String>(value);
            return this;
        }
        public Builder maxFileBytes(final int value) { maxFileBytes = value; return this; }
        public Builder maxWaves(final int value) { maxWaves = value; return this; }
        public Builder maxRows(final int value) { maxRows = value; return this; }
        public Builder maxColumns(final int value) { maxColumns = value; return this; }
        public Builder maxEnemyHealth(final int value) { maxEnemyHealth = value; return this; }
        public Builder maxMovementBaseFrames(final int value) { maxMovementBaseFrames = value; return this; }
        public Builder minShootingIntervalMs(final int value) { minShootingIntervalMs = value; return this; }
        public Builder maxShootingIntervalMs(final int value) { maxShootingIntervalMs = value; return this; }
        public Builder maxIdLength(final int value) { maxIdLength = value; return this; }
        public Builder maxNameLength(final int value) { maxNameLength = value; return this; }
        public LevelImportPolicy build() { return new LevelImportPolicy(this); }

        private Builder() {
        }
    }
}
