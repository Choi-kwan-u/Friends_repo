package entity;

import java.util.HashSet;
import java.util.Set;

import engine.DrawManager.SpriteType;

/**
 * Standalone checks that a bullet is identified by who fired it rather than
 * by the direction it happens to travel in.
 */
public final class BulletOwnershipTest {

	private BulletOwnershipTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkAimedShotsStayEnemyBullets();
		checkPoolResetsTheOwner();
		checkSpriteFollowsTheOwner();
		checkDefaultOwnerFromDirection();

		System.out.println("Bullet ownership checks passed.");
	}

	/**
	 * An aimed enemy shot can travel sideways or upwards when the player is
	 * beside or above its shooter. It must still count as an enemy bullet, or
	 * the collision code would treat it as a player shot and let it destroy
	 * the enemy that fired it.
	 */
	private static void checkAimedShotsStayEnemyBullets() {
		int[] verticalSpeeds = { 4, 0, -4 };
		String[] labels = { "downward", "horizontal", "upward" };

		for (int i = 0; i < verticalSpeeds.length; i++) {
			Bullet bullet = BulletPool.getBullet(100, 100, verticalSpeeds[i],
					2, Bullet.Owner.ENEMY);
			check(bullet.isEnemyBullet(),
					"An " + labels[i] + " aimed shot stays an enemy bullet");
			check(bullet.getOwner() == Bullet.Owner.ENEMY,
					"Owner of an " + labels[i] + " aimed shot is ENEMY");
			recycle(bullet);
		}
	}

	/**
	 * The pool hands the same object out again, so a bullet must never keep
	 * the owner it had on its previous flight.
	 */
	private static void checkPoolResetsTheOwner() {
		Bullet enemyShot = BulletPool.getBullet(10, 10, -4,
				Bullet.Owner.ENEMY);
		check(enemyShot.isEnemyBullet(), "Upward enemy shot is an enemy bullet");
		recycle(enemyShot);

		// The pool is not empty now, so this call reuses the object above.
		Bullet playerShot = BulletPool.getBullet(10, 10, -6,
				Bullet.Owner.PLAYER);
		check(playerShot == enemyShot,
				"The pool hands the recycled object back, so the check below "
						+ "really exercises the reuse path");
		check(!playerShot.isEnemyBullet(),
				"A recycled bullet does not keep its previous owner");
		recycle(playerShot);

		Bullet reused = BulletPool.getBullet(10, 10, 4, Bullet.Owner.ENEMY);
		check(reused.isEnemyBullet(),
				"A recycled bullet takes the owner it is handed out with");
		recycle(reused);
	}

	/** An upward enemy shot must not be drawn as a player bullet. */
	private static void checkSpriteFollowsTheOwner() {
		Bullet upwardEnemyShot = BulletPool.getBullet(10, 10, -4,
				Bullet.Owner.ENEMY);
		check(upwardEnemyShot.getSpriteType() == SpriteType.EnemyBullet,
				"An upward enemy shot is drawn as an enemy bullet");
		recycle(upwardEnemyShot);

		Bullet playerShot = BulletPool.getBullet(10, 10, -6,
				Bullet.Owner.PLAYER);
		check(playerShot.getSpriteType() == SpriteType.Bullet,
				"A player shot is drawn as a player bullet");
		recycle(playerShot);
	}

	/**
	 * Callers that do not name an owner keep the old meaning of the speed
	 * sign, so upstream code that was not changed still behaves as before.
	 */
	private static void checkDefaultOwnerFromDirection() {
		Bullet falling = BulletPool.getBullet(10, 10, 4);
		check(falling.isEnemyBullet(),
				"A downward bullet with no stated owner is an enemy bullet");
		recycle(falling);

		Bullet rising = BulletPool.getBullet(10, 10, -6);
		check(!rising.isEnemyBullet(),
				"An upward bullet with no stated owner is a player bullet");
		recycle(rising);
	}

	private static void recycle(final Bullet bullet) {
		Set<Bullet> one = new HashSet<Bullet>();
		one.add(bullet);
		BulletPool.recycle(one);
	}
}
