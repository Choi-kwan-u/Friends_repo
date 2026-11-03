package level;

/**
 * Immutable wave design data. Grid coordinates are [row][column]. Timing
 * values are base values, before runtime difficulty adjustment.
 */
public final class WaveDefinition {
    private final String id;
    private final EnemyCell[][] grid;
    private final int initialX;
    private final int initialY;
    private final int movementBaseFrames;
    private final int shootingIntervalMs;
    private final int enemyCount;

    public WaveDefinition(final String id, final EnemyCell[][] grid,
            final int initialX, final int initialY,
            final int movementBaseFrames, final int shootingIntervalMs) {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid wave id");
        }
        if (initialX < 0 || initialY < 0) {
            throw new IllegalArgumentException("Formation coordinates must be nonnegative");
        }
        if (movementBaseFrames < 0 || shootingIntervalMs <= 0) {
            throw new IllegalArgumentException("Invalid base timing values");
        }
        if (grid == null || grid.length == 0 || grid[0] == null
                || grid[0].length == 0) {
            throw new IllegalArgumentException("Grid must have rows and columns");
        }
        int columns = grid[0].length;
        this.grid = new EnemyCell[grid.length][];
        int count = 0;
        for (int row = 0; row < grid.length; row++) {
            if (grid[row] == null || grid[row].length != columns) {
                throw new IllegalArgumentException("Grid rows must have equal lengths");
            }
            this.grid[row] = grid[row].clone();
            for (EnemyCell cell : this.grid[row]) {
                if (cell != null) {
                    count++;
                }
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("A wave must contain at least one enemy");
        }
        this.id = id;
        this.initialX = initialX;
        this.initialY = initialY;
        this.movementBaseFrames = movementBaseFrames;
        this.shootingIntervalMs = shootingIntervalMs;
        this.enemyCount = count;
    }

    public String getId() {
        return id;
    }

    /** Returns a copy preserving every row, column, and empty cell. */
    public EnemyCell[][] getGrid() {
        EnemyCell[][] copy = new EnemyCell[grid.length][];
        for (int row = 0; row < grid.length; row++) {
            copy[row] = grid[row].clone();
        }
        return copy;
    }

    public EnemyCell getCell(final int row, final int column) {
        return grid[row][column];
    }

    public int getRows() {
        return grid.length;
    }

    public int getColumns() {
        return grid[0].length;
    }

    public int getEnemyCount() {
        return enemyCount;
    }

    public int getInitialX() {
        return initialX;
    }

    public int getInitialY() {
        return initialY;
    }

    public int getMovementBaseFrames() {
        return movementBaseFrames;
    }

    public int getShootingIntervalMs() {
        return shootingIntervalMs;
    }
}
