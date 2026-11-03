package boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import engine.Cooldown;
import engine.Core;
import entity.BossBullet;

/**
 * Boss attack system: attack interval and cooldowns, attack patterns per
 * phase, and the special attack (PRD 2.3 - 2.5).
 *
 * The phase itself is decided by Boss Developer 1. This class is only told
 * about it through {@link #setPhase(int)}.
 *
 * Boss Developer 2.
 */
public final class BossAttackSystem {

    /** Shortest allowed time between two attacks, in milliseconds. */
    private static final int MIN_INTERVAL = 150;

    /**
     * What happened in one call to {@link BossAttackSystem#update}.
     */
    public static final class AttackResult {

        /** Name of the pattern or special attack. */
        private final String attackType;
        /** True for a special attack. */
        private final boolean special;
        /** True for the warning before a special attack. */
        private final boolean windup;
        /** Bullets fired. Empty during windup. */
        private final List<BossBullet> bullets;

        /**
         * Constructor.
         *
         * @param attackType
         *            Name of the pattern.
         * @param special
         *            True for a special attack.
         * @param windup
         *            True for the warning before a special attack.
         * @param bullets
         *            Bullets fired.
         */
        AttackResult(final String attackType, final boolean special,
                final boolean windup, final List<BossBullet> bullets) {
            this.attackType = attackType;
            this.special = special;
            this.windup = windup;
            this.bullets = Collections.unmodifiableList(bullets);
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

        public List<BossBullet> getBullets() {
            return this.bullets;
        }
    }

    /** Attack data of the boss. */
    private final BossAttackConfig config;
    /** Random generator used to pick patterns. */
    private final Random random;
    /** Current phase. */
    private int phase;
    /** False after the boss is defeated. */
    private boolean active;
    /** Time until the next attack. */
    private Cooldown attackCooldown;
    /** Special attack waiting for its windup to end, or null. */
    private SpecialAttack pendingSpecial;
    /** Windup time of the pending special attack. */
    private Cooldown windupCooldown;
    /** Current attack interval multiplier from rapid fire. */
    private double buffMultiplier;
    /** Time until the rapid fire effect ends, or null. */
    private Cooldown buffCooldown;
    /** True after the first update, when the boss battle really begins. */
    private boolean started;
    /**
     * True when a phase that allows the special attack has just begun. The
     * first special attack of the phase then starts right away, without
     * waiting for the attack interval or the special cooldown.
     */
    private boolean specialDueAtPhaseStart;

    /**
     * Constructor. Cooldowns start on the first {@link #update} call,
     * so the first attack comes one interval after the battle begins.
     *
     * @param config
     *            Attack data of the boss.
     * @param random
     *            Random generator used to pick patterns.
     */
    public BossAttackSystem(final BossAttackConfig config,
            final Random random) {
        this.config = config;
        this.random = random;
        this.phase = 1;
        this.active = true;
        this.buffMultiplier = 1.0;
        restartAttackCooldown();
    }

    /**
     * Changes the attack setup to the given phase (PRD 2.4).
     * Any special attack being prepared is cancelled.
     *
     * @param newPhase
     *            New phase number.
     */
    public void setPhase(final int newPhase) {
        if (newPhase == this.phase)
            return;
        boolean specialWasEnabled = this.config.getPhase(this.phase)
                .isSpecialEnabled();
        this.phase = newPhase;
        this.pendingSpecial = null;
        restartAttackCooldown();
        // The first special attack of the phase is used right away (PRD 2.5).
        if (!specialWasEnabled)
            markSpecialDueIfEnabled();
    }

    /** Stops all attacks, used when the boss is defeated. */
    public void stop() {
        this.active = false;
        this.pendingSpecial = null;
    }

    /**
     * Runs the attack logic. Call once per frame.
     *
     * @param originX
     *            Horizontal center of the boss.
     * @param originY
     *            Bottom of the boss.
     * @param targetX
     *            Current X of the player.
     * @param targetY
     *            Current Y of the player.
     * @return What happened this frame, or null if nothing.
     */
    public AttackResult update(final int originX, final int originY,
            final int targetX, final int targetY) {
        if (!this.active)
            return null;

        // GameScreen calls this only after its input delay, like
        // EnemyShipFormation, so the countdown does not use up the first
        // attack interval.
        if (!this.started) {
            this.started = true;
            restartAttackCooldown();
            markSpecialDueIfEnabled();
            return null;
        }

        if (this.buffCooldown != null && this.buffCooldown.checkFinished()) {
            this.buffCooldown = null;
            this.buffMultiplier = 1.0;
            // The running wait was shortened by the buff, so start it again
            // with the normal interval.
            restartAttackCooldown();
        }

        // 1. A special attack is being prepared: nothing else happens.
        if (this.pendingSpecial != null) {
            if (!this.windupCooldown.checkFinished())
                return null;
            SpecialAttack special = this.pendingSpecial;
            this.pendingSpecial = null;
            List<BossBullet> bullets = special.execute(originX, originY,
                    targetX, targetY, this.config.getDamage());
            if (special.getBuffMilliseconds() > 0) {
                this.buffMultiplier = special.getIntervalMultiplier();
                this.buffCooldown = Core.getCooldown(
                        special.getBuffMilliseconds());
                this.buffCooldown.reset();
            }
            restartAttackCooldown();
            return new AttackResult(special.getName(), true, false, bullets);
        }

        // 2. Wait for the attack interval, unless the first special attack
        // of the phase is due.
        if (!this.specialDueAtPhaseStart
                && !this.attackCooldown.checkFinished())
            return null;

        // 3. Special attack, only in allowed phases. The first one starts
        // as soon as the phase begins, later ones wait for the cooldown.
        BossAttackConfig.PhaseConfig phaseConfig = this.config
                .getPhase(this.phase);
        SpecialAttack special = this.config.getSpecialAttack();
        if (phaseConfig.isSpecialEnabled() && special != null
                && (this.specialDueAtPhaseStart || special.isReady())) {
            this.specialDueAtPhaseStart = false;
            this.pendingSpecial = special;
            this.windupCooldown = Core.getCooldown(
                    Math.max(1, special.getWindupMilliseconds()));
            this.windupCooldown.reset();
            return new AttackResult(special.getName(), true, true,
                    new ArrayList<BossBullet>());
        }

        // 4. Normal attack, pattern chosen by the phase weights.
        AttackPattern pattern = phaseConfig.pick(this.random);
        List<BossBullet> bullets = pattern.fire(originX, originY, targetX,
                targetY, this.config.getDamage());
        restartAttackCooldown();
        return new AttackResult(pattern.getName(), false, false, bullets);
    }

    /** @return Current time between attacks, in milliseconds. */
    public int getCurrentIntervalMilliseconds() {
        double interval = this.config.getBaseIntervalMilliseconds()
                * this.config.getPhase(this.phase).getIntervalMultiplier()
                * this.buffMultiplier;
        return Math.max(MIN_INTERVAL, (int) Math.round(interval));
    }

    /**
     * Marks the first special attack of the phase as due if the current
     * phase allows it. Later uses wait for the special cooldown.
     */
    private void markSpecialDueIfEnabled() {
        this.specialDueAtPhaseStart = this.config.getSpecialAttack() != null
                && this.config.getPhase(this.phase).isSpecialEnabled();
    }

    /** Starts waiting for the next attack with the current interval. */
    private void restartAttackCooldown() {
        this.attackCooldown = Core.getCooldown(getCurrentIntervalMilliseconds());
        this.attackCooldown.reset();
    }

    public int getPhase() {
        return this.phase;
    }

    public boolean isActive() {
        return this.active;
    }

    /** @return True while a special attack is being prepared. */
    public boolean isWindingUp() {
        return this.pendingSpecial != null;
    }
}
