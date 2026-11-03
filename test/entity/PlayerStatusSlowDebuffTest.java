package entity;

import java.util.ArrayList;
import java.util.List;

/** Deterministic checks for the B5 player slow debuff. */
public final class PlayerStatusSlowDebuffTest {

	private static final class FakeClockStatus extends PlayerStatus {
		private long now = 1000000L;

		private FakeClockStatus(final int lives, final int maxLives) {
			super(lives, maxLives, true);
		}

		@Override
		long currentTimeMillis() {
			return this.now;
		}

		private void advance(final long milliseconds) {
			this.now += milliseconds;
		}
	}

	private PlayerStatusSlowDebuffTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkShipMovementRates();
		checkTankDirectionChanges();
		checkDurationAndRefresh();
		checkShieldImmunityAndCleansing();
		checkDeadPlayerAndBaseSpeedChange();
		checkCurrentSpeedOverrideAndBaseRestore();
		checkShootingStatsAreUntouched();
		checkSlowEventsMatchApplicationResults();
		System.out.println("Player slow debuff checks passed.");
	}

	private static void checkSlowEventsMatchApplicationResults() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		final List<PlayerStatusEvent> events =
				new ArrayList<PlayerStatusEvent>();
		status.addListener(new PlayerStatusListener() {
			@Override
			public void onPlayerStatusEvent(final PlayerStatusEvent event) {
				events.add(event);
			}
		});
		Ship ship = new Ship(100, 100);

		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.STARTED
				&& events.size() == 1
				&& events.get(0).getType()
						== PlayerStatusEvent.Type.SLOW_STARTED,
				"Starting the slow publishes one matching event");
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.REFRESHED
				&& events.size() == 2
				&& events.get(1).getType()
						== PlayerStatusEvent.Type.SLOW_REFRESHED,
				"Refreshing the slow publishes one matching event");
		status.advance(PlayerStatus.getSlowDuration());
		status.update();
		check(events.size() == 3
				&& events.get(2).getType()
						== PlayerStatusEvent.Type.SLOW_ENDED,
				"Expiring the slow publishes one end event");
		status.update();
		check(events.size() == 3,
				"Repeated updates do not duplicate the end event");
	}

	private static void checkShipMovementRates() {
		checkMovementRate(ShipType.STANDARD, 2, 1);
		checkMovementRate(ShipType.SPEEDSTER, 4, 2);
		checkChameleonMovementIsSlowed();

		Ship tank = new Ship(100, 100, ShipType.TANK);
		FakeClockStatus status = new FakeClockStatus(3, 5);
		check(status.applySlow(tank)
				== PlayerStatus.SlowApplicationResult.STARTED,
				"TANK slow starts");
		for (int i = 0; i < 20; i++)
			tank.moveRight();
		check(tank.getPositionX() == 110,
				"TANK moves an average of half a pixel per input");
	}

	private static void checkChameleonMovementIsSlowed() {
		Ship ship = new Ship(100, 100, ShipType.SPEEDSTER);
		ship.activateChameleonHull();
		FakeClockStatus status = new FakeClockStatus(2, 2);
		status.applySlow(ship);
		ship.moveDown();
		check(ship.getPositionY() == 104,
				"Slow also halves movement while the chameleon hull is active");
	}

	private static void checkMovementRate(final ShipType type,
			final int baseSpeed, final int expectedSlowSpeed) {
		Ship ship = new Ship(100, 100, type);
		PlayerStatus status = new FakeClockStatus(
				Math.min(3, type.getMaxLives()), type.getMaxLives());
		check(ship.getBaseSpeed() == baseSpeed, type + " has its expected base speed");
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.STARTED,
				type + " slow starts");
		ship.moveRight();
		check(ship.getPositionX() == 100 + expectedSlowSpeed,
				type + " moves at half speed");
	}

	private static void checkTankDirectionChanges() {
		Ship tank = new Ship(100, 100, ShipType.TANK);
		FakeClockStatus status = new FakeClockStatus(3, 5);
		status.applySlow(tank);
		tank.moveRight();
		tank.moveLeft();
		check(tank.getPositionX() == 100,
				"Changing direction does not transfer fractional movement");
		tank.moveRight();
		check(tank.getPositionX() == 101,
				"Rightward fraction is retained only for rightward input");
		tank.moveLeft();
		check(tank.getPositionX() == 100,
				"Leftward movement cancels the matching rightward pixel");
	}

	private static void checkDurationAndRefresh() {
		Ship ship = new Ship(100, 100, ShipType.STANDARD);
		FakeClockStatus status = new FakeClockStatus(3, 3);
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.STARTED,
				"First application starts the effect");
		check(status.getSlowRemainingMillis() == 2000,
				"A new slow reports its full duration");
		status.advance(1200);
		check(status.getSlowRemainingMillis() == 800,
				"Slow duration follows the clock");
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.REFRESHED,
				"Reapplication refreshes rather than stacks");
		check(status.getSlowRemainingMillis() == 2000,
				"Refresh restarts the full duration");
		ship.moveRight();
		check(ship.getPositionX() == 101,
				"Refreshed slow is not compounded");

		status.advance(PlayerStatus.getSlowDuration() - 1);
		status.update();
		check(status.isSlowActive(), "Slow remains active before expiry");
		status.advance(1);
		status.update();
		check(!status.isSlowActive()
				&& status.getSlowRemainingMillis() == 0,
				"Slow ends exactly at its expiry boundary");
		int positionBeforeNormalMove = ship.getPositionX();
		ship.moveRight();
		check(ship.getPositionX() - positionBeforeNormalMove == 2,
				"Normal STANDARD movement resumes after expiry");
	}

	private static void checkShieldImmunityAndCleansing() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		Ship ship = new Ship(100, 100, ShipType.STANDARD);
		status.activateShield();
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.BLOCKED_BY_SHIELD,
				"An active shield blocks a new slow");
		check(!status.isSlowActive(), "Blocked slow does not become active");

		status.clearShield();
		status.applySlow(ship);
		status.activateShield();
		status.update();
		check(!status.isSlowActive(),
				"An active shield clears an existing slow on update");
		int position = ship.getPositionX();
		ship.moveRight();
		check(ship.getPositionX() - position == ship.getBaseSpeed(),
				"Normal movement resumes after shield cleanses slow");
		check(status.isShieldActive(),
				"Slow cleansing does not alter the B4 shield");
	}

	private static void checkDeadPlayerAndBaseSpeedChange() {
		FakeClockStatus dead = new FakeClockStatus(1, 3);
		dead.takeDamage(1);
		check(dead.applySlow(new Ship(100, 100))
				== PlayerStatus.SlowApplicationResult.PLAYER_DEAD,
				"A dead player cannot receive slow");

		Ship ship = new Ship(100, 100, ShipType.STANDARD);
		ship.setShootingInterval(333);
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.applySlow(ship);
		ship.setBaseSpeed(4);
		check(ship.getSpeed() == 2,
				"B2 base setter still does not change current speed directly");
		status.update();
		check(ship.getSpeed() == 4,
				"Slow follows an updated base speed");
		ship.moveRight();
		check(ship.getPositionX() == 102,
				"Updated base speed is slowed by fifty percent");
		status.advance(PlayerStatus.getSlowDuration());
		status.update();
		check(ship.getSpeed() == 4,
				"Expiry leaves the latest base speed restored");
		check(ship.getShootingInterval() == 333,
				"Slow expiry does not restore or alter the firing interval");

		Ship tank = new Ship(100, 100, ShipType.TANK);
		FakeClockStatus tankStatus = new FakeClockStatus(3, 5);
		tankStatus.applySlow(tank);
		tank.moveRight();
		tank.setBaseSpeed(3);
		tankStatus.update();
		tank.moveRight();
		check(tank.getPositionX() == 101,
				"A base-speed change clears stale fractional movement");
	}

	private static void checkShootingStatsAreUntouched() {
		Ship ship = new Ship(100, 100, ShipType.TANK);
		ship.setShootingInterval(432);
		ship.setBulletType(BulletType.MULTI);
		FakeClockStatus status = new FakeClockStatus(3, 5);
		status.applySlow(ship);
		status.advance(PlayerStatus.getSlowDuration());
		status.update();
		check(ship.getShootingInterval() == 432,
				"Slow never changes the current firing interval");
		check(ship.getCurrentBulletType() == BulletType.MULTI,
				"Slow never changes the current bullet type");
	}

	private static void checkCurrentSpeedOverrideAndBaseRestore() {
		Ship overriddenShip = new Ship(100, 100, ShipType.STANDARD);
		FakeClockStatus overriddenStatus = new FakeClockStatus(3, 3);
		overriddenStatus.applySlow(overriddenShip);
		overriddenShip.setSpeed(6);
		overriddenStatus.update();
		overriddenShip.moveRight();
		check(overriddenShip.getPositionX() == 103,
				"An explicit current-speed change is preserved while slowed");
		overriddenStatus.advance(PlayerStatus.getSlowDuration());
		overriddenStatus.update();
		check(overriddenShip.getSpeed() == 6,
				"Slow expiry preserves a standalone current-speed override");

		Ship ship = new Ship(100, 100, ShipType.STANDARD);
		ship.setShootingInterval(333);
		ship.setBulletType(BulletType.MULTI);
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.applySlow(ship);
		ship.setSpeed(4);
		status.update();
		check(ship.getSpeed() == 4,
				"Explicit current-speed change remains in effect before restore");
		check(ship.getShootingInterval() == 333,
				"Slow tracking does not alter the current firing interval");

		ship.restoreBaseStats();
		status.update();
		check(ship.getSpeed() == ship.getBaseSpeed(),
				"Explicit base-stat restore resumes base-speed tracking");
		ship.setBaseSpeed(6);
		status.update();
		check(ship.getSpeed() == 6,
				"A base-speed change after restore is applied during slow");
		int positionBeforeMove = ship.getPositionX();
		ship.moveRight();
		check(ship.getPositionX() - positionBeforeMove == 3,
				"Movement uses half of the latest base speed");

		status.advance(PlayerStatus.getSlowDuration());
		status.update();
		check(!status.isSlowActive() && ship.getSpeed() == 6,
				"Slow expiry restores the latest base speed");
		check(ship.getShootingInterval() == ship.getBaseShootingInterval(),
				"Slow expiry leaves the interval selected by explicit base restore");
		check(ship.getCurrentBulletType() == BulletType.MULTI,
				"Base restore and slow expiry preserve the bullet type");
	}
}
