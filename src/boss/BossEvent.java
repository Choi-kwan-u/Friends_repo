package boss;

import entity.Boss;
import event.GameplayEvent;

/**
 * Boss event sent to other systems (PRD 6). Every event carries the boss
 * type, position, health, phase and state.
 *
 * Boss Developer 2.
 */
public final class BossEvent implements GameplayEvent {

    /** Kinds of boss events. */
    public enum Type {
        /** The boss appeared. */
        SPAWNED("BossSpawned"),
        /** The boss was hit. */
        DAMAGED("BossDamaged"),
        /** The boss entered a new phase. */
        PHASE_CHANGED("BossPhaseChanged"),
        /** The boss attacked, or is preparing a special attack. */
        ATTACK("BossAttack"),
        /** The boss was defeated. */
        DEFEATED("BossDefeated");

        /** Name of the event as written in the PRD. */
        private final String eventName;

        Type(final String eventName) {
            this.eventName = eventName;
        }

        public String getEventName() {
            return this.eventName;
        }
    }

    /** Kind of event. */
    private final Type type;
    /** Kind of boss. */
    private final Boss.BossType bossType;
    /** Horizontal center of the boss. */
    private final int positionX;
    /** Vertical center of the boss. */
    private final int positionY;
    /** Current health of the boss. */
    private final int currentHealth;
    /** Maximum health of the boss. */
    private final int maxHealth;
    /** Phase of the boss after this event. */
    private final int phase;
    /** State of the boss. */
    private final Boss.State state;
    /** Health lost, for DAMAGED. */
    private final int damageAmount;
    /** Phase before the change, for PHASE_CHANGED. */
    private final int previousPhase;
    /** Name of the attack, for ATTACK. */
    private final String attackType;
    /** True for a special attack, for ATTACK. */
    private final boolean special;
    /** True for the warning before a special attack, for ATTACK. */
    private final boolean windup;
    /** Number of bullets fired, for ATTACK. */
    private final int bulletCount;

    private BossEvent(final Type type, final Boss boss, final int phase,
            final int damageAmount, final int previousPhase,
            final String attackType, final boolean special,
            final boolean windup, final int bulletCount) {
        this.type = type;
        this.bossType = boss.getType();
        this.positionX = boss.getPositionX() + boss.getWidth() / 2;
        this.positionY = boss.getPositionY() + boss.getHeight() / 2;
        this.currentHealth = boss.getCurrentHealth();
        this.maxHealth = boss.getMaxHealth();
        this.phase = phase;
        this.state = boss.getState();
        this.damageAmount = damageAmount;
        this.previousPhase = previousPhase;
        this.attackType = attackType;
        this.special = special;
        this.windup = windup;
        this.bulletCount = bulletCount;
    }

    static BossEvent spawned(final Boss boss) {
        return new BossEvent(Type.SPAWNED, boss, boss.getPhase(), 0, 0, null,
                false, false, 0);
    }

    static BossEvent damaged(final Boss boss, final int amount) {
        return new BossEvent(Type.DAMAGED, boss, boss.getPhase(), amount, 0,
                null, false, false, 0);
    }

    static BossEvent phaseChanged(final Boss boss, final int oldPhase,
            final int newPhase) {
        return new BossEvent(Type.PHASE_CHANGED, boss, newPhase, 0, oldPhase,
                null, false, false, 0);
    }

    static BossEvent attack(final Boss boss, final String attackType,
            final boolean special, final boolean windup,
            final int bulletCount) {
        return new BossEvent(Type.ATTACK, boss, boss.getPhase(), 0, 0,
                attackType, special, windup, bulletCount);
    }

    static BossEvent defeated(final Boss boss) {
        return new BossEvent(Type.DEFEATED, boss, boss.getPhase(), 0, 0, null,
                false, false, 0);
    }

    public Type getType() {
        return this.type;
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

    public int getCurrentHealth() {
        return this.currentHealth;
    }

    public int getMaxHealth() {
        return this.maxHealth;
    }

    public int getPhase() {
        return this.phase;
    }

    public Boss.State getState() {
        return this.state;
    }

    public int getDamageAmount() {
        return this.damageAmount;
    }

    public int getPreviousPhase() {
        return this.previousPhase;
    }

    public String getAttackType() {
        return this.attackType;
    }

    public boolean isSpecial() {
        return this.special;
    }

    public boolean isWindup() {
        return this.windup;
    }

    public int getBulletCount() {
        return this.bulletCount;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(this.type.getEventName())
                .append(" [").append(this.bossType)
                .append(", HP ").append(this.currentHealth).append('/')
                .append(this.maxHealth)
                .append(", phase ").append(this.phase)
                .append(", state ").append(this.state)
                .append(", at ").append(this.positionX).append(',')
                .append(this.positionY);
        if (this.type == Type.DAMAGED)
            text.append(", damage ").append(this.damageAmount);
        if (this.type == Type.PHASE_CHANGED)
            text.append(", from phase ").append(this.previousPhase);
        if (this.type == Type.ATTACK)
            text.append(", attack ").append(this.attackType)
                    .append(this.windup ? " (warning)" : "")
                    .append(", bullets ").append(this.bulletCount);
        return text.append(']').toString();
    }
}
