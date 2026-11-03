import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Bullet;
import entity.BulletType;
import entity.PlayerStatus;
import entity.Ship;
import entity.ShipType;
import screen.GameScreen;

/** Small standalone regression checks for the B2 ship-stat changes. */
public final class PlayerStatSourceTest {

	private PlayerStatSourceTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static void checkInvalidSpeed(final Ship ship, final int speed) {
		try {
			ship.setSpeed(speed);
			throw new AssertionError("Expected invalid speed to be rejected.");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void checkInvalidInterval(final Ship ship,
			final int interval) {
		try {
			ship.setShootingInterval(interval);
			throw new AssertionError(
					"Expected invalid shooting interval to be rejected.");
		} catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("Shooting interval"),
					"Ship-specific interval error message");
		}
	}

	private static long getCooldownStart(final Cooldown cooldown)
			throws Exception {
		Field time = Cooldown.class.getDeclaredField("time");
		time.setAccessible(true);
		return time.getLong(cooldown);
	}

	private static void setCooldownStart(final Cooldown cooldown,
			final long timeValue) throws Exception {
		Field time = Cooldown.class.getDeclaredField("time");
		time.setAccessible(true);
		time.setLong(cooldown, timeValue);
	}

	private static void setNextShotTime(final Ship ship, final long timeValue)
			throws Exception {
		Field nextShotTime = Ship.class.getDeclaredField("nextShotTime");
		nextShotTime.setAccessible(true);
		nextShotTime.setLong(ship, timeValue);
	}

	private static void checkCooldownValidation() {
		// A zero cooldown means "always ready" and is used by the upstream
		// infinite mode, so only a negative duration is rejected.
		Core.getCooldown(0);
		try {
			Core.getCooldown(-1);
			throw new AssertionError("Negative cooldown should be rejected.");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		try {
			Core.getVariableCooldown(1000, -1);
			throw new AssertionError("Negative variance should be rejected.");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		try {
			Core.getVariableCooldown(1000, 1000);
			throw new AssertionError(
					"Variance equal to duration should be rejected.");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void checkCooldownDurationChanges() throws Exception {
		Cooldown shortened = Core.getCooldown(5000);
		shortened.reset();
		setCooldownStart(shortened, System.currentTimeMillis() - 3000);
		long shortenedStart = getCooldownStart(shortened);
		shortened.setDuration(1000);
		check(getCooldownStart(shortened) == shortenedStart,
				"Shortening preserves the original start time");
		check(shortened.checkFinished(),
				"Shortened duration applies to elapsed cooldown time");

		Cooldown extended = Core.getCooldown(1000);
		extended.reset();
		setCooldownStart(extended, System.currentTimeMillis() - 3000);
		long extendedStart = getCooldownStart(extended);
		extended.setDuration(5000);
		check(getCooldownStart(extended) == extendedStart,
				"Extending preserves the original start time");
		check(!extended.checkFinished(),
				"Extended duration applies to elapsed cooldown time");
	}

	private static void checkShipTypesAndLives() throws Exception {
		ShipType[] types = { ShipType.STANDARD, ShipType.SPEEDSTER,
				ShipType.TANK };
		int[] speeds = { 2, 4, 1 };
		int[] intervals = { 750, 400, 1000 };
		int[] maxLives = { 4, 3, 5 };
		for (int i = 0; i < types.length; i++) {
			Ship ship = new Ship(100, 100, types[i]);
			check(ship.getShipType() == types[i],
					"Selected ship type is retained");
			check(ship.getBaseSpeed() == speeds[i]
					&& ship.getSpeed() == speeds[i],
					"Selected type provides baseline movement speed");
			check(ship.getBaseShootingInterval() == intervals[i]
					&& ship.getShootingInterval() == intervals[i],
					"Selected type provides baseline firing interval");

			GameScreen screen = new GameScreen(
					new GameState(1, 0, 3, 0, 0),
					new GameSettings(5, 4, 60, 2000), false,
					800, 600, 60, types[i]);
			Field statusField = GameScreen.class
					.getDeclaredField("playerStatus");
			statusField.setAccessible(true);
			PlayerStatus status = (PlayerStatus) statusField.get(screen);
			check(status.getMaxLives() == maxLives[i],
					"Selected type maximum lives reaches PlayerStatus");
			check(status.getLives() == Math.min(3, maxLives[i]),
					"Initial lives are clamped to selected type maximum");
		}
	}

	private static void checkBulletPatterns() throws Exception {
		Set<Bullet> tankBullets = new HashSet<Bullet>();
		Ship tank = new Ship(100, 100, ShipType.TANK);
		check(tank.getShipType().getBulletType() == BulletType.MULTI,
				"Tank selects multi-shot ammunition");
		int multiCount = tank.shoot(tankBullets);
		check(multiCount == 3 && tankBullets.size() == 3,
				"Multi-shot returns and creates three bullets");

		Set<Bullet> rapidBullets = new HashSet<Bullet>();
		Ship speedster = new Ship(100, 100, ShipType.SPEEDSTER);
		check(speedster.getShipType().getBulletType() == BulletType.RAPID,
				"Speedster selects rapid ammunition");
		int rapidCount = speedster.shoot(rapidBullets);
		check(rapidCount == 1 && rapidBullets.size() == rapidCount,
				"Rapid ammunition fires its first bullet immediately");
		for (int i = 1; i < BulletType.RAPID.getShotCount(); i++) {
			int countBefore = rapidBullets.size();
			setNextShotTime(speedster, 0);
			int reported = speedster.updateBurst(rapidBullets);
			int actuallyAdded = rapidBullets.size() - countBefore;
			check(reported == actuallyAdded && actuallyAdded > 0,
					"Each rapid burst step reports its actual added bullets");
			rapidCount += reported;
		}
		check(rapidCount == rapidBullets.size()
				&& rapidBullets.size() == BulletType.RAPID.getShotCount(),
				"Rapid burst returns the total number of bullets added");
		setNextShotTime(speedster, 0);
		check(speedster.updateBurst(rapidBullets) == 0
				&& rapidBullets.size() == BulletType.RAPID.getShotCount(),
				"Rapid burst stops after all configured bullets are fired");
	}

	private static void checkPlayerLifeManagement() {
		PlayerStatus status = new PlayerStatus(2, 3);
		check(status.isAlive() && status.getLives() == 2,
				"B1 starts with the configured lives");
		check(status.takeDamage(1) && status.getLives() == 1,
				"B1 damage consumes a life");
		check(status.takeDamage(2) && status.getLives() == 0
				&& !status.isAlive(),
				"B1 damage clamps lives to zero and reports death");
		check(!status.takeDamage(1) && status.getLives() == 0,
				"B1 does not apply damage after death");
	}

	public static void main(final String[] args) throws Exception {
		Ship ship = new Ship(100, 100);
		check(ship.getSpeed() == 2, "Default ship speed");
		check(ship.getBaseSpeed() == 2, "Default baseline ship speed");
		check(ship.getShootingInterval() == 750,
				"Default shooting interval");
		check(ship.getBaseShootingInterval() == 750,
				"Default baseline shooting interval");

		ship.setSpeed(1);
		ship.setBaseSpeed(4);
		check(ship.getBaseSpeed() == 4 && ship.getSpeed() == 1,
				"Changing baseline speed preserves a temporary current speed");
		ship.moveRight();
		check(ship.getPositionX() == 101, "Temporary movement speed remains");
		ship.moveLeft();
		check(ship.getPositionX() == 100, "Updated reverse movement speed");
		ship.setSpeed(2);
		ship.setBaseSpeed(6);
		check(ship.getSpeed() == 2,
				"Changing baseline does not cancel an active slowdown");
		ship.setSpeed(0);
		ship.moveRight();
		check(ship.getPositionX() == 100, "Zero speed disables movement");
		checkInvalidSpeed(ship, -1);
		check(ship.getSpeed() == 0,
				"Invalid speed leaves current speed intact");
		ship.setSpeed(12);
		ship.setShootingInterval(300);
		ship.setBaseShootingInterval(1000);
		check(ship.getShootingInterval() == 300
				&& ship.getBaseShootingInterval() == 1000,
				"Changing baseline shooting interval preserves current interval");
		ship.restoreBaseStats();
		check(ship.getSpeed() == 6 && ship.getShootingInterval() == 1000,
				"Restoring stats returns to baseline values");

		Ship firingShip = new Ship(100, 100);
		Set<Bullet> bullets = new HashSet<Bullet>();
		int normalCount = firingShip.shoot(bullets);
		check(normalCount == 1 && bullets.size() == normalCount,
				"A new ship creates and counts its normal bullet");
		int originalInterval = firingShip.getShootingInterval();
		firingShip.setBaseShootingInterval(900);
		check(firingShip.getBaseShootingInterval() == 900
				&& firingShip.getShootingInterval() == originalInterval,
				"Changing base interval does not change the current interval");
		firingShip.setShootingInterval(60000);
		check(firingShip.getShootingInterval() == 60000,
				"Updated shooting interval");
		check(firingShip.shoot(bullets) == 0,
				"Changing interval does not grant an immediate extra shot");

		checkInvalidInterval(firingShip, 0);
		checkInvalidInterval(firingShip, -1);
		check(firingShip.getShootingInterval() == 60000,
				"Invalid interval leaves current interval intact");

		try {
			Core.getVariableCooldown(1000, 100).setDuration(500);
			throw new AssertionError(
					"Variable cooldown duration should not be changed.");
		} catch (IllegalStateException expected) {
			// Expected.
		}
		checkCooldownValidation();
		checkCooldownDurationChanges();
		checkShipTypesAndLives();
		checkBulletPatterns();
		checkPlayerLifeManagement();

		System.out.println("Player stat source checks passed.");
	}
}
