package screen;

import entity.Ship;
import entity.PlayerStatus;

/** Standalone checks for clamped player-ship movement. */
public final class GameScreenMovementTest {

	private GameScreenMovementTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		final int screenWidth = 800;
		checkMovementAtSpeed(2, screenWidth, "normal speed");
		checkMovementAtSpeed(20, screenWidth, "high speed");
		checkSlowMovementAtBoundaries(screenWidth);

		System.out.println("Game screen movement checks passed.");
	}

	private static void checkSlowMovementAtBoundaries(final int screenWidth) {
		Ship ship = new Ship(400, 100);
		PlayerStatus status = new PlayerStatus(3, 3);
		status.applySlow(ship);
		int rightBoundary = screenWidth - ship.getWidth() - 1;

		for (int i = 0; i < 1000; i++) {
			ship.moveRight();
			ship.setPositionX(GameScreen.clampShipPositionX(
					ship.getPositionX(), ship.getWidth(), screenWidth));
		}
		check(ship.getPositionX() == rightBoundary,
				"Slowed ship reaches but does not pass the right boundary");

		for (int i = 0; i < 1000; i++) {
			ship.moveLeft();
			ship.setPositionX(GameScreen.clampShipPositionX(
					ship.getPositionX(), ship.getWidth(), screenWidth));
		}
		check(ship.getPositionX() == 1,
				"Slowed ship reaches but does not pass the left boundary");
	}

	private static void checkMovementAtSpeed(final int speed,
			final int screenWidth, final String label) {
		Ship ship = new Ship(400, 100);
		ship.setSpeed(speed);
		for (int i = 0; i < 500; i++) {
			ship.moveRight();
			ship.setPositionX(GameScreen.clampShipPositionX(
					ship.getPositionX(), ship.getWidth(), screenWidth));
			check(ship.getPositionX() >= 1
					&& ship.getPositionX() + ship.getWidth() <= screenWidth - 1,
					"Ship stays inside both horizontal boundaries at " + label);
		}
		check(ship.getPositionX() + ship.getWidth() == screenWidth - 1,
				"Ship reaches the right boundary at " + label);

		for (int i = 0; i < 500; i++) {
			ship.moveLeft();
			ship.setPositionX(GameScreen.clampShipPositionX(
					ship.getPositionX(), ship.getWidth(), screenWidth));
			check(ship.getPositionX() >= 1
					&& ship.getPositionX() + ship.getWidth() <= screenWidth - 1,
					"Ship stays inside both horizontal boundaries at " + label);
		}
		check(ship.getPositionX() == 1,
				"Ship reaches the left boundary at " + label);
	}
}
