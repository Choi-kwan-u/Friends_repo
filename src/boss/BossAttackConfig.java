package boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import entity.Boss;

/**
 * Attack data of each kind of boss (PRD 4) and the attack setup of each
 * phase (PRD 2.4). Numbers are first guesses and should be tuned by play
 * testing.
 *
 * Damage is counted in player lives because PlayerStatus works with lives.
 *
 * Boss Developer 2.
 */
public final class BossAttackConfig {

    /**
     * Attack setup of one phase.
     */
    public static final class PhaseConfig {

        /** Multiplier for the base attack interval, smaller is faster. */
        private final double intervalMultiplier;
        /** True if the special attack can be used in this phase. */
        private final boolean specialEnabled;
        /** Patterns used in this phase. */
        private final List<AttackPattern> patterns =
                new ArrayList<AttackPattern>();
        /** Weight of each pattern, bigger is used more often. */
        private final List<Integer> weights = new ArrayList<Integer>();
        /** Sum of all weights. */
        private int totalWeight;

        /**
         * Constructor.
         *
         * @param intervalMultiplier
         *            Multiplier for the base attack interval.
         * @param specialEnabled
         *            True if the special attack is allowed.
         */
        public PhaseConfig(final double intervalMultiplier,
                final boolean specialEnabled) {
            if (intervalMultiplier <= 0)
                throw new IllegalArgumentException(
                        "Interval multiplier must be positive.");
            this.intervalMultiplier = intervalMultiplier;
            this.specialEnabled = specialEnabled;
        }

        /**
         * Adds a pattern to this phase.
         *
         * @param pattern
         *            Pattern to add.
         * @param weight
         *            How often it is used compared to the others.
         * @return This phase, so calls can be chained.
         */
        public PhaseConfig add(final AttackPattern pattern, final int weight) {
            if (weight <= 0)
                throw new IllegalArgumentException("Weight must be positive.");
            this.patterns.add(pattern);
            this.weights.add(weight);
            this.totalWeight += weight;
            return this;
        }

        /**
         * Picks a pattern at random, following the weights.
         *
         * @param random
         *            Random generator.
         * @return Chosen pattern.
         */
        public AttackPattern pick(final Random random) {
            if (this.patterns.isEmpty())
                throw new IllegalStateException("Phase has no patterns.");
            int roll = random.nextInt(this.totalWeight);
            for (int i = 0; i < this.patterns.size(); i++) {
                roll -= this.weights.get(i);
                if (roll < 0)
                    return this.patterns.get(i);
            }
            return this.patterns.get(this.patterns.size() - 1);
        }

        public double getIntervalMultiplier() {
            return this.intervalMultiplier;
        }

        public boolean isSpecialEnabled() {
            return this.specialEnabled;
        }
    }

    /** Base time between two attacks, in milliseconds. */
    private final int baseIntervalMilliseconds;
    /** Lives the player loses per bullet. */
    private final int damage;
    /** Attack setup of phase 1, 2 and 3. */
    private final PhaseConfig[] phases;
    /** Special attack of this boss. */
    private final SpecialAttack specialAttack;

    /**
     * Constructor.
     *
     * @param baseIntervalMilliseconds
     *            Base time between two attacks.
     * @param damage
     *            Lives the player loses per bullet.
     * @param phases
     *            Attack setup of each phase, starting from phase 1.
     * @param specialAttack
     *            Special attack of the boss.
     */
    public BossAttackConfig(final int baseIntervalMilliseconds,
            final int damage, final PhaseConfig[] phases,
            final SpecialAttack specialAttack) {
        if (baseIntervalMilliseconds <= 0)
            throw new IllegalArgumentException(
                    "Attack interval must be positive.");
        if (phases.length == 0)
            throw new IllegalArgumentException("At least one phase needed.");
        this.baseIntervalMilliseconds = baseIntervalMilliseconds;
        this.damage = damage;
        this.phases = phases.clone();
        this.specialAttack = specialAttack;
    }

    /**
     * Creates the attack data for a kind of boss.
     *
     * @param type
     *            Kind of boss.
     * @return New attack data. A new object is made each time so that
     *         cooldowns are not shared between bosses.
     */
    public static BossAttackConfig forType(final Boss.BossType type) {
        switch (type) {
        case BOSS_B:
            return createBossB();
        case BOSS_A:
        default:
            return createBossA();
        }
    }

    /**
     * Boss A: slow, attacks every 2 seconds.
     * Phase 1 single shot, phase 2 multi shot, phase 3 spread + special.
     *
     * @return Attack data of boss A.
     */
    private static BossAttackConfig createBossA() {
        PhaseConfig phase1 = new PhaseConfig(1.0, false)
                .add(new AttackPattern.SingleShot(3), 1);
        PhaseConfig phase2 = new PhaseConfig(0.75, false)
                .add(new AttackPattern.MultiShot(3, 60, 90, 120), 3)
                .add(new AttackPattern.SingleShot(4), 1);
        PhaseConfig phase3 = new PhaseConfig(0.5, true)
                .add(new AttackPattern.SpreadShot(3.5, 7, 90), 3)
                .add(new AttackPattern.MultiShot(3.5, 45, 70, 90, 110, 135), 1);
        return new BossAttackConfig(2000, 1,
                new PhaseConfig[] { phase1, phase2, phase3 },
                new SpecialAttack.RingBurst(12000, 1500, 16, 2.0));
    }

    /**
     * Boss B: fast, attacks every 1.5 seconds.
     *
     * @return Attack data of boss B.
     */
    private static BossAttackConfig createBossB() {
        PhaseConfig phase1 = new PhaseConfig(1.0, false)
                .add(new AttackPattern.SingleShot(4), 2)
                .add(new AttackPattern.MultiShot(4, 75, 90, 105), 1);
        PhaseConfig phase2 = new PhaseConfig(0.7, false)
                .add(new AttackPattern.MultiShot(4, 60, 75, 90, 105, 120), 3)
                .add(new AttackPattern.SpreadShot(4, 5, 60), 1);
        PhaseConfig phase3 = new PhaseConfig(0.5, true)
                .add(new AttackPattern.SpreadShot(4.5, 9, 120), 3)
                .add(new AttackPattern.SingleShot(6), 1);
        return new BossAttackConfig(1500, 1,
                new PhaseConfig[] { phase1, phase2, phase3 },
                new SpecialAttack.RapidFire(10000, 800, 0.4, 3000));
    }

    /**
     * Returns the setup of a phase. Phases above the last one use the last.
     *
     * @param phase
     *            Phase number starting from 1.
     * @return Setup of the phase.
     */
    public PhaseConfig getPhase(final int phase) {
        int index = Math.max(0, Math.min(phase, this.phases.length) - 1);
        return this.phases[index];
    }

    public int getBaseIntervalMilliseconds() {
        return this.baseIntervalMilliseconds;
    }

    public int getDamage() {
        return this.damage;
    }

    public SpecialAttack getSpecialAttack() {
        return this.specialAttack;
    }
}
