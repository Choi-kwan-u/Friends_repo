package entity;

import java.awt.Color;

import engine.DrawManager.SpriteType;

/**
 * Implements a bullet fired by the boss.
 *
 * Unlike {@link Bullet}, which only moves vertically, a boss bullet can move
 * in any direction, so multi shot, spread shot and special attacks are
 * possible. Boss bullets are kept in their own set in the game screen.
 *
 * Boss Developer 2.
 */
public class BossBullet extends Entity {

    /** Width of a boss bullet, same as a normal bullet. */
    private static final int BULLET_WIDTH = 3 * 2;
    /** Height of a boss bullet, same as a normal bullet. */
    private static final int BULLET_HEIGHT = 5 * 2;

    /** Exact X position, kept as a double for diagonal movement. */
    private double exactX;
    /** Exact Y position, kept as a double for diagonal movement. */
    private double exactY;
    /** Horizontal speed in pixels per frame. */
    private final double speedX;
    /** Vertical speed in pixels per frame, positive is down. */
    private final double speedY;
    /** Lives the player loses when hit by this bullet. */
    private final int damage;
    /** True if this bullet comes from a special attack. */
    private final boolean special;

    /**
     * Constructor, establishes the bullet's properties.
     *
     * @param centerX
     *            Horizontal center of the bullet when it is created.
     * @param positionY
     *            Initial position of the bullet in the Y axis.
     * @param speedX
     *            Horizontal speed in pixels per frame.
     * @param speedY
     *            Vertical speed in pixels per frame, positive is down.
     * @param damage
     *            Lives the player loses when hit.
     * @param special
     *            True if fired by a special attack.
     */
    public BossBullet(final double centerX, final double positionY,
            final double speedX, final double speedY, final int damage,
            final boolean special) {
        super((int) Math.round(centerX - BULLET_WIDTH / 2.0),
                (int) Math.round(positionY), BULLET_WIDTH, BULLET_HEIGHT,
                special ? Color.MAGENTA : Color.WHITE);
        this.spriteType = SpriteType.EnemyBullet;
        this.exactX = centerX - BULLET_WIDTH / 2.0;
        this.exactY = positionY;
        this.speedX = speedX;
        this.speedY = speedY;
        this.damage = damage;
        this.special = special;
    }

    /**
     * Creates a bullet that flies at the given angle.
     * Screen coordinates are used: 0 degrees is right, 90 degrees is down.
     *
     * @param originX
     *            Horizontal center of the bullet.
     * @param originY
     *            Initial position of the bullet in the Y axis.
     * @param angleDegrees
     *            Direction of the bullet in degrees.
     * @param speed
     *            Speed in pixels per frame.
     * @param damage
     *            Lives the player loses when hit.
     * @param special
     *            True if fired by a special attack.
     * @return New boss bullet.
     */
    public static BossBullet fromAngle(final int originX, final int originY,
            final double angleDegrees, final double speed, final int damage,
            final boolean special) {
        double radians = Math.toRadians(angleDegrees);
        return new BossBullet(originX, originY, Math.cos(radians) * speed,
                Math.sin(radians) * speed, damage, special);
    }

    /**
     * Calculates the angle from one point to another.
     *
     * @param fromX
     *            Start X.
     * @param fromY
     *            Start Y.
     * @param toX
     *            Target X.
     * @param toY
     *            Target Y.
     * @return Angle in degrees, 0 is right and 90 is down.
     */
    public static double angleTo(final int fromX, final int fromY,
            final int toX, final int toY) {
        return Math.toDegrees(Math.atan2(toY - fromY, toX - fromX));
    }

    /**
     * Moves the bullet. Call once per frame.
     */
    public final void update() {
        this.exactX += this.speedX;
        this.exactY += this.speedY;
        this.positionX = (int) Math.round(this.exactX);
        this.positionY = (int) Math.round(this.exactY);
    }

    /**
     * Checks if the bullet has left the play area.
     *
     * @param topLimit
     *            Top of the play area.
     * @param screenWidth
     *            Width of the screen.
     * @param screenHeight
     *            Height of the screen.
     * @return True if the bullet is outside the play area.
     */
    public final boolean isOutside(final int topLimit, final int screenWidth,
            final int screenHeight) {
        return this.positionY + this.height < topLimit
                || this.positionY > screenHeight
                || this.positionX + this.width < 0
                || this.positionX > screenWidth;
    }

    public final double getSpeedX() {
        return this.speedX;
    }

    public final double getSpeedY() {
        return this.speedY;
    }

    public final int getDamage() {
        return this.damage;
    }

    public final boolean isSpecial() {
        return this.special;
    }
}
