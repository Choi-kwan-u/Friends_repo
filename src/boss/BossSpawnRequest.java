package boss;

import entity.Boss;

/**
 * Boss spawn information received from the Level Design System
 * (PRD 5.1 Require): boss type, spawn position, level and wave.
 *
 * Boss Developer 2.
 */
public final class BossSpawnRequest {

    /** Kind of boss to spawn. */
    private final Boss.BossType bossType;
    /** Spawn position in the X axis. */
    private final int positionX;
    /** Spawn position in the Y axis. */
    private final int positionY;
    /** Level the boss appears in. */
    private final int level;
    /** Wave the boss appears in. */
    private final int wave;

    /**
     * Constructor.
     *
     * @param bossType
     *            Kind of boss.
     * @param positionX
     *            Spawn X.
     * @param positionY
     *            Spawn Y.
     * @param level
     *            Level the boss appears in.
     * @param wave
     *            Wave the boss appears in.
     */
    public BossSpawnRequest(final Boss.BossType bossType, final int positionX,
            final int positionY, final int level, final int wave) {
        this.bossType = bossType;
        this.positionX = positionX;
        this.positionY = positionY;
        this.level = level;
        this.wave = wave;
    }

    public Boss.BossType getBossType() {
        return this.bossType;
    }

    public int getPositionX() {
        return this.positionX;
    }

    public int getPositionY() {
        return this.positionY;
    }

    public int getLevel() {
        return this.level;
    }

    public int getWave() {
        return this.wave;
    }
}
