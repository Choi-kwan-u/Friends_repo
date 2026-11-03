package boss;

import entity.Boss;

/**
 * Boss information for the Level Design System (PRD 5.1 Provide):
 * whether a boss is present, whether it was defeated, its type and the
 * battle state.
 *
 * Boss Developer 2.
 */
public final class BossStatus {

    /** State of the boss battle. */
    public enum BattleState {
        /** No boss has appeared. */
        NONE,
        /** The boss battle is going on. */
        IN_BATTLE,
        /** The boss was defeated and the battle is over. */
        ENDED
    }

    /** True while a boss is fighting. */
    private final boolean present;
    /** True after the boss was defeated. */
    private final boolean defeated;
    /** Kind of boss, or null if none appeared. */
    private final Boss.BossType bossType;
    /** State of the battle. */
    private final BattleState battleState;

    BossStatus(final boolean present, final boolean defeated,
            final Boss.BossType bossType, final BattleState battleState) {
        this.present = present;
        this.defeated = defeated;
        this.bossType = bossType;
        this.battleState = battleState;
    }

    public boolean isPresent() {
        return this.present;
    }

    public boolean isDefeated() {
        return this.defeated;
    }

    public Boss.BossType getBossType() {
        return this.bossType;
    }

    public BattleState getBattleState() {
        return this.battleState;
    }

    @Override
    public String toString() {
        return "BossStatus[present=" + this.present + ", defeated="
                + this.defeated + ", type=" + this.bossType + ", battle="
                + this.battleState + "]";
    }
}
