package boss;

import entity.Boss;

/**
 * Boss information for the Gameplay HUD (PRD 5.2 Provide):
 * boss type, current health, max health, current phase and boss state.
 *
 * Boss Developer 2.
 */
public final class BossHudInfo {

    /** Kind of boss. */
    private final Boss.BossType bossType;
    /** Current health. */
    private final int currentHealth;
    /** Maximum health. */
    private final int maxHealth;
    /** Current phase. */
    private final int currentPhase;
    /** Current state. */
    private final Boss.State bossState;

    BossHudInfo(final Boss boss) {
        this.bossType = boss.getType();
        this.currentHealth = boss.getCurrentHealth();
        this.maxHealth = boss.getMaxHealth();
        this.currentPhase = boss.getPhase();
        this.bossState = boss.getState();
    }

    public Boss.BossType getBossType() {
        return this.bossType;
    }

    public int getCurrentHealth() {
        return this.currentHealth;
    }

    public int getMaxHealth() {
        return this.maxHealth;
    }

    public int getCurrentPhase() {
        return this.currentPhase;
    }

    public Boss.State getBossState() {
        return this.bossState;
    }

    /** @return True if the HUD should show the boss health bar. */
    public boolean isVisible() {
        return this.bossState != Boss.State.DEFEATED;
    }
}
