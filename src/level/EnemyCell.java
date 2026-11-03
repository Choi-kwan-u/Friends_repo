package level;

/** Immutable design data for one occupied grid cell; null represents a gap. */
public final class EnemyCell {
    private final String enemyType;
    private final int health;

    public EnemyCell(final String enemyType, final int health) {
        if (enemyType == null || enemyType.trim().isEmpty()
                || !enemyType.equals(enemyType.trim())) {
            throw new IllegalArgumentException("enemyType must be nonblank without surrounding whitespace");
        }
        if (health <= 0) {
            throw new IllegalArgumentException("health must be positive");
        }
        this.enemyType = enemyType;
        this.health = health;
    }

    public String getEnemyType() {
        return enemyType;
    }

    public int getHealth() {
        return health;
    }
}
