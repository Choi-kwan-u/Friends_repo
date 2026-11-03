package event;

/**
 * Classification of a defeated enemy, carried by {@link EnemyDefeatedEvent}.
 *
 * <p>A listener reads this instead of deriving the kind of enemy from a
 * score, a colour or a sprite. The classification is recorded when the enemy
 * is created, so it stays valid after the animation frame has changed and
 * after destruction has replaced the sprite with an explosion.</p>
 */
public enum DefeatedEnemyType {
    /** Weakest normal enemy, either animation frame. */
    A,
    /** Medium normal enemy, either animation frame. */
    B,
    /** Strongest normal enemy, either animation frame. */
    C,
    /** Special enemy that crosses the top of the screen. */
    BONUS,
    /** Slow boss. */
    BOSS_A,
    /** Fast boss, stronger earlier. */
    BOSS_B,
    /** No classification was supplied by the caller. */
    UNKNOWN
}
