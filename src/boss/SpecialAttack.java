package boss;

import java.util.ArrayList;
import java.util.List;

import engine.Cooldown;
import engine.Core;
import engine.DifficultyAdjustWhilePlaying;
import entity.BossBullet;

/**
 * Boss special attack (PRD 2.5). Each boss has one.
 *
 * A special attack has a cooldown and can only be used when the cooldown
 * is finished. Before it fires there is a short windup, during which a
 * BossAttack event with windup = true is sent so that HUD and Visual Effect
 * can show a warning.
 *
 * Boss Developer 2.
 */
public abstract class SpecialAttack {

    /** Name of the special attack, used in BossAttack events. */
    private final String name;
    /** Time between the warning and the attack, in milliseconds. */
    private final int windupMilliseconds;
    /** Time until the special attack can be used again. */
    private final Cooldown cooldown;

    /**
     * Constructor.
     *
     * @param name
     *            Name of the special attack.
     * @param cooldownMilliseconds
     *            Time until it can be used again.
     * @param windupMilliseconds
     *            Time between the warning and the attack.
     */
    protected SpecialAttack(final String name, final int cooldownMilliseconds,
            final int windupMilliseconds) {
        if (cooldownMilliseconds <= 0)
            throw new IllegalArgumentException(
                    "Special attack cooldown must be positive.");
        this.name = name;
        this.windupMilliseconds = Math.max(0, windupMilliseconds);
        this.cooldown = Core.getCooldown(cooldownMilliseconds);
        // Starts counting now. The first use in a phase does not wait for
        // it (see BossAttackSystem); later uses do.
        this.cooldown.reset();
    }

    public final String getName() {
        return this.name;
    }

    public final int getWindupMilliseconds() {
        return this.windupMilliseconds;
    }

    /**
     * Checks the cooldown between two uses. The first use in a phase does
     * not wait for it (see BossAttackSystem).
     *
     * @return True if the cooldown is finished.
     */
    public final boolean isReady() {
        return this.cooldown.checkFinished();
    }

    /**
     * Uses the special attack and restarts its cooldown.
     *
     * @param originX
     *            Horizontal center of the boss.
     * @param originY
     *            Bottom of the boss.
     * @param targetX
     *            Current X of the player.
     * @param targetY
     *            Current Y of the player.
     * @param damage
     *            Lives the player loses per bullet.
     * @return Bullets fired, may be empty.
     */
    public final List<BossBullet> execute(final int originX,
            final int originY, final int targetX, final int targetY,
            final int damage) {
        this.cooldown.reset();
        return perform(originX, originY, targetX, targetY, damage);
    }

    /**
     * Creates the bullets of the special attack.
     *
     * @param originX
     *            Horizontal center of the boss.
     * @param originY
     *            Bottom of the boss.
     * @param targetX
     *            Current X of the player.
     * @param targetY
     *            Current Y of the player.
     * @param damage
     *            Lives the player loses per bullet.
     * @return Bullets fired, may be empty.
     */
    protected abstract List<BossBullet> perform(int originX, int originY,
            int targetX, int targetY, int damage);

    /** @return Multiplier for the attack interval after use, 1 = no change. */
    public double getIntervalMultiplier() {
        return 1.0;
    }

    /** @return How long the interval multiplier lasts, in milliseconds. */
    public int getBuffMilliseconds() {
        return 0;
    }

    /**
     * Many bullets at the same time in every direction. The ring turns a
     * little each time so that the safe gaps move.
     */
    public static final class RingBurst extends SpecialAttack {

        /** Number of bullets in the ring. */
        private final int count;
        /** Speed in pixels per frame. */
        private final double speed;
        /** Current rotation of the ring in degrees. */
        private double offset;

        /**
         * Constructor.
         *
         * @param cooldownMilliseconds
         *            Time until it can be used again.
         * @param windupMilliseconds
         *            Time between the warning and the attack.
         * @param count
         *            Number of bullets.
         * @param speed
         *            Speed in pixels per frame.
         */
        public RingBurst(final int cooldownMilliseconds,
                final int windupMilliseconds, final int count,
                final double speed) {
            super("ring_burst", cooldownMilliseconds, windupMilliseconds);
            this.count = count;
            this.speed = speed;
        }

        @Override
        protected List<BossBullet> perform(final int originX,
                final int originY, final int targetX, final int targetY,
                final int damage) {
            List<BossBullet> bullets = new ArrayList<BossBullet>();
            double step = 360.0 / this.count;
            double adjustedSpeed = DifficultyAdjustWhilePlaying.getInstance()
                    .adjustEnemyBulletSpeed(this.speed);
            for (int i = 0; i < this.count; i++)
                bullets.add(BossBullet.fromAngle(originX, originY,
                        this.offset + i * step, adjustedSpeed, damage, true));
            this.offset = (this.offset + step / 2) % 360.0;
            return bullets;
        }
    }

    /**
     * Many bullets over a wide angle towards the player.
     */
    public static final class WideSpread extends SpecialAttack {

        /** Number of bullets. */
        private final int count;
        /** Total angle of the fan in degrees. */
        private final double spreadAngle;
        /** Speed in pixels per frame. */
        private final double speed;

        /**
         * Constructor.
         *
         * @param cooldownMilliseconds
         *            Time until it can be used again.
         * @param windupMilliseconds
         *            Time between the warning and the attack.
         * @param count
         *            Number of bullets, at least 2.
         * @param spreadAngle
         *            Total angle of the fan in degrees.
         * @param speed
         *            Speed in pixels per frame.
         */
        public WideSpread(final int cooldownMilliseconds,
                final int windupMilliseconds, final int count,
                final double spreadAngle, final double speed) {
            super("wide_spread", cooldownMilliseconds, windupMilliseconds);
            if (count < 2)
                throw new IllegalArgumentException(
                        "Wide spread needs at least two bullets.");
            this.count = count;
            this.spreadAngle = spreadAngle;
            this.speed = speed;
        }

        @Override
        protected List<BossBullet> perform(final int originX,
                final int originY, final int targetX, final int targetY,
                final int damage) {
            double center = BossBullet.angleTo(originX, originY, targetX,
                    targetY);
            double step = this.spreadAngle / (this.count - 1);
            double first = center - this.spreadAngle / 2;
            List<BossBullet> bullets = new ArrayList<BossBullet>();
            for (int i = 0; i < this.count; i++)
                bullets.add(BossBullet.fromAngle(originX, originY,
                        first + i * step, this.speed, damage, true));
            return bullets;
        }
    }

    /**
     * Raises the attack speed for a short time. Fires no bullets itself.
     */
    public static final class RapidFire extends SpecialAttack {

        /** Multiplier for the attack interval while active. */
        private final double intervalMultiplier;
        /** How long the effect lasts, in milliseconds. */
        private final int durationMilliseconds;

        /**
         * Constructor.
         *
         * @param cooldownMilliseconds
         *            Time until it can be used again.
         * @param windupMilliseconds
         *            Time between the warning and the effect.
         * @param intervalMultiplier
         *            Interval multiplier, between 0 and 1.
         * @param durationMilliseconds
         *            How long the effect lasts.
         */
        public RapidFire(final int cooldownMilliseconds,
                final int windupMilliseconds, final double intervalMultiplier,
                final int durationMilliseconds) {
            super("rapid_fire", cooldownMilliseconds, windupMilliseconds);
            if (intervalMultiplier <= 0 || intervalMultiplier > 1)
                throw new IllegalArgumentException(
                        "Interval multiplier must be in (0, 1].");
            this.intervalMultiplier = intervalMultiplier;
            this.durationMilliseconds = durationMilliseconds;
        }

        @Override
        protected List<BossBullet> perform(final int originX,
                final int originY, final int targetX, final int targetY,
                final int damage) {
            return new ArrayList<BossBullet>();
        }

        @Override
        public double getIntervalMultiplier() {
            return this.intervalMultiplier;
        }

        @Override
        public int getBuffMilliseconds() {
            return this.durationMilliseconds;
        }
    }
}
