package entity;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager.SpriteType;

/**
 * Standalone checks for the shooting cooldown R2 asks to be held per enemy
 * ship rather than per formation, so that enemies fire independently of
 * each other.
 *
 * The cooldown is what decides whether an enemy may fire this frame, so a
 * check that always answered yes would put the formation back to firing as
 * one block.
 */
public final class EnemyShootingCooldownTest {

	/** Cooldown long enough that it cannot elapse during the checks. */
	private static final int LONG_COOLDOWN = 60000;
	/** Cooldown short enough to be waited out. */
	private static final int SHORT_COOLDOWN = 10;

	private EnemyShootingCooldownTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkAnEnemyWithoutACooldownCannotShoot();
		checkAnEnemyWithAnElapsedCooldownCanShoot();
		checkFiringStartsTheCooldownAgain();
		checkADestroyedEnemyCannotShoot();
		checkTwoEnemiesFireOnTheirOwnCooldowns();
		checkAnEnemyCanShootAgainOnceItsCooldownElapses();

		System.out.println("Enemy shooting cooldown checks passed.");
	}

	/**
	 * Only the bottom row of a formation is given a cooldown, so an enemy
	 * without one must never fire.
	 */
	private static void checkAnEnemyWithoutACooldownCannotShoot() {
		EnemyShip enemy = newEnemy();

		check(enemy.getShootingCooldown() == null,
				"A new enemy has no shooting cooldown of its own");
		check(!enemy.canShoot(),
				"An enemy without a cooldown is not allowed to shoot");
	}

	/** An enemy whose cooldown is over is ready to fire. */
	private static void checkAnEnemyWithAnElapsedCooldownCanShoot() {
		EnemyShip enemy = newEnemy();
		enemy.setShootingCooldown(Core.getCooldown(LONG_COOLDOWN));

		check(enemy.canShoot(),
				"An enemy whose cooldown is over is ready to shoot");
	}

	/** Firing restarts the cooldown, so the same enemy cannot fire twice. */
	private static void checkFiringStartsTheCooldownAgain() {
		EnemyShip enemy = newEnemy();
		enemy.setShootingCooldown(Core.getCooldown(LONG_COOLDOWN));
		check(enemy.canShoot(), "The enemy starts ready to shoot");

		enemy.resetShootingCooldown();
		check(!enemy.canShoot(),
				"An enemy that has just fired waits for its cooldown");
	}

	/** A destroyed enemy stops shooting, cooldown or not. */
	private static void checkADestroyedEnemyCannotShoot() {
		EnemyShip enemy = newEnemy();
		enemy.setShootingCooldown(Core.getCooldown(LONG_COOLDOWN));
		check(enemy.canShoot(), "The enemy starts ready to shoot");

		enemy.destroy();
		check(enemy.isDestroyed(), "The enemy was destroyed");
		check(!enemy.canShoot(), "A destroyed enemy does not shoot");
	}

	/**
	 * The point of R2: the cooldown belongs to the ship. One enemy firing
	 * must leave the other one free to fire in the same frame.
	 */
	private static void checkTwoEnemiesFireOnTheirOwnCooldowns() {
		EnemyShip first = newEnemy();
		EnemyShip second = newEnemy();
		first.setShootingCooldown(Core.getCooldown(LONG_COOLDOWN));
		second.setShootingCooldown(Core.getCooldown(LONG_COOLDOWN));

		check(first.getShootingCooldown() != second.getShootingCooldown(),
				"Two enemies do not share one cooldown object");
		check(first.canShoot() && second.canShoot(),
				"Both enemies start ready to shoot");

		first.resetShootingCooldown();
		check(!first.canShoot(), "The enemy that fired is now waiting");
		check(second.canShoot(),
				"The other enemy is still free to fire in the same frame");

		// And the other way round, so neither order is a special case.
		second.resetShootingCooldown();
		check(!second.canShoot(),
				"The second enemy waits once it has fired as well");
	}

	/** Waiting out the cooldown makes an enemy ready again. */
	private static void checkAnEnemyCanShootAgainOnceItsCooldownElapses()
			throws Exception {
		EnemyShip enemy = newEnemy();
		Cooldown cooldown = Core.getCooldown(SHORT_COOLDOWN);
		enemy.setShootingCooldown(cooldown);
		enemy.resetShootingCooldown();
		check(!enemy.canShoot(), "The enemy waits right after firing");

		// Short enough to wait out, with room for a slow machine.
		Thread.sleep(SHORT_COOLDOWN * 10L);
		check(enemy.canShoot(),
				"The enemy is ready again once its cooldown has elapsed");
	}

	private static EnemyShip newEnemy() {
		return new EnemyShip(0, 0, SpriteType.EnemyShipA1);
	}
}
