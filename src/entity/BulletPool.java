package entity;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements a pool of recyclable bullets.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class BulletPool {

	/** Set of already created bullets. */
	private static Set<Bullet> pool = new HashSet<Bullet>();

	/**
	 * Constructor, not called.
	 */
	private BulletPool() {

	}

	/**
	 * Returns a bullet from the pool if one is available, a new one if there
	 * isn't.
	 *
	 * @param positionX
	 *            Requested position of the bullet in the X axis.
	 * @param positionY
	 *            Requested position of the bullet in the Y axis.
	 * @param speed
	 *            Requested speed of the bullet, positive or negative depending
	 *            on direction - positive is down.
	 * @return Requested bullet.
	 */
	public static Bullet getBullet(final int positionX,
			final int positionY, final int speed) {
		return getBullet(positionX, positionY, speed,
				speed < 0 ? Bullet.Owner.PLAYER : Bullet.Owner.ENEMY);
	}

	/**
	 * Returns a bullet from the pool, or a new one, with an explicit owner.
	 * The owner is always written, so a recycled bullet never keeps the one
	 * it had on its previous flight.
	 *
	 * @param positionX
	 *            Requested position of the bullet in the X axis.
	 * @param positionY
	 *            Requested position of the bullet in the Y axis.
	 * @param speed
	 *            Requested speed of the bullet, positive or negative.
	 * @param owner
	 *            Side that fired the bullet.
	 * @return Requested bullet.
	 */
	public static Bullet getBullet(final int positionX,
			final int positionY, final int speed,
			final Bullet.Owner owner) {
		Bullet bullet;
		if (!pool.isEmpty()) {
			bullet = pool.iterator().next();
			pool.remove(bullet);
			bullet.setPositionX(positionX - bullet.getWidth() / 2);
			bullet.setPositionY(positionY);
			bullet.setSpeed(speed);
			bullet.setOwner(owner);
			bullet.setSprite();
		} else {
			bullet = new Bullet(positionX, positionY, speed, owner);
			bullet.setPositionX(positionX - bullet.getWidth() / 2);
		}

		bullet.setSpeedX(0);
		return bullet;
	}

	/**
	 * Returns a bullet travelling on both axes, guessing the owner from the
	 * sign of the vertical speed.
	 *
	 * @deprecated Use {@link #getBullet(int, int, int, int, Bullet.Owner)}.
	 *             An aimed enemy shot can travel sideways or upwards, and
	 *             guessing from the speed hands it back as a player bullet,
	 *             which lets it destroy the enemy that fired it. No caller
	 *             uses this overload any more.
	 * @param x
	 *            Requested position of the bullet in the X axis.
	 * @param y
	 *            Requested position of the bullet in the Y axis.
	 * @param speedY
	 *            Requested speed on the Y axis.
	 * @param speedX
	 *            Requested speed on the X axis.
	 * @return Requested bullet.
	 */
	@Deprecated
	public static Bullet getBullet(
        int x, int y, int speedY, int speedX) {

    	Bullet bullet = getBullet(x, y, speedY);
    	bullet.setSpeedX(speedX);

    	return bullet;
	}

	/**
	 * Returns a bullet travelling on both axes, with an explicit owner.
	 *
	 * @param x
	 *            Requested position of the bullet in the X axis.
	 * @param y
	 *            Requested position of the bullet in the Y axis.
	 * @param speedY
	 *            Requested speed on the Y axis.
	 * @param speedX
	 *            Requested speed on the X axis.
	 * @param owner
	 *            Side that fired the bullet.
	 * @return Requested bullet.
	 */
	public static Bullet getBullet(final int x, final int y, final int speedY,
			final int speedX, final Bullet.Owner owner) {
		Bullet bullet = getBullet(x, y, speedY, owner);
		bullet.setSpeedX(speedX);
		return bullet;
	}

	/**
	 * Adds one or more bullets to the list of available ones.
	 *
	 * @param bullet
	 *            Bullets to recycle.
	 */
	public static void recycle(final Set<Bullet> bullet) {
		pool.addAll(bullet);
	}
}
