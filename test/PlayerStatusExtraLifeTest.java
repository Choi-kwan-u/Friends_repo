import java.lang.reflect.Field;

import engine.GameSettings;
import engine.GameState;
import entity.PlayerStatus;
import entity.ShipType;
import screen.GameScreen;

/** Standalone regression checks for B3 extra-life behavior. */
public final class PlayerStatusExtraLifeTest {

	private PlayerStatusExtraLifeTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static PlayerStatus getPlayerStatus(final GameScreen screen)
			throws Exception {
		Field playerStatusField = GameScreen.class
				.getDeclaredField("playerStatus");
		playerStatusField.setAccessible(true);
		return (PlayerStatus) playerStatusField.get(screen);
	}

	private static GameScreen createGameScreen(final GameState gameState,
			final boolean bonusLife, final ShipType shipType) {
		return new GameScreen(gameState,
				new GameSettings(5, 4, 60, 2000), bonusLife,
				800, 600, 60, shipType);
	}

	private static void checkShipLifeLimits() throws Exception {
		ShipType[] shipTypes = { ShipType.STANDARD, ShipType.SPEEDSTER,
				ShipType.TANK };
		int[] expectedMaxLives = { 4, 3, 5 };
		for (int i = 0; i < shipTypes.length; i++) {
			GameScreen screen = createGameScreen(
					new GameState(1, 0, 3, 0, 0), false, shipTypes[i]);
			PlayerStatus status = getPlayerStatus(screen);
			check(status.getMaxLives() == expectedMaxLives[i],
					"Selected ship maxLives reaches PlayerStatus");
			check(status.getLives() == Math.min(3, expectedMaxLives[i]),
					"Initial lives respect the selected ship limit");
		}
	}

	private static void checkGainLifeAndCap() {
		PlayerStatus standard = new PlayerStatus(1, ShipType.STANDARD.getMaxLives());
		check(standard.gainLife() && standard.getLives() == 2,
				"STANDARD gains one life from 1/4 to 2/4");
		check(standard.gainLife() && standard.getLives() == 3,
				"STANDARD gains one life from 2/4 to 3/4");
		check(standard.gainLife() && standard.getLives() == 4,
				"STANDARD gains one life from 3/4 to 4/4");
		check(!standard.gainLife() && standard.getLives() == 4,
				"STANDARD cannot exceed 4/4");

		PlayerStatus speedster = new PlayerStatus(1,
				ShipType.SPEEDSTER.getMaxLives());
		check(speedster.gainLife() && speedster.getLives() == 2,
				"SPEEDSTER gains a life up to 2/3");
		check(speedster.gainLife() && speedster.getLives() == 3,
				"SPEEDSTER gains a life up to 3/3");
		check(!speedster.gainLife() && speedster.getLives() == 3,
				"SPEEDSTER cannot exceed 3/3");

		PlayerStatus tank = new PlayerStatus(1, ShipType.TANK.getMaxLives());
		for (int expectedLives = 2; expectedLives <= 5; expectedLives++)
			check(tank.gainLife() && tank.getLives() == expectedLives,
					"TANK gains lives up to 5/5");
		check(!tank.gainLife() && tank.getLives() == 5,
				"TANK cannot exceed 5/5");
	}

	private static void checkBonusLifeAndLevelPersistence() {
		GameScreen bonusLevel = createGameScreen(
				new GameState(3, 0, 1, 10, 2), true, ShipType.STANDARD);
		GameState afterBonus = bonusLevel.getGameState();
		check(afterBonus.getLivesRemaining() == 2,
				"Existing level bonus grants exactly one life");

		GameScreen nextLevel = createGameScreen(
				new GameState(4, afterBonus.getScore(),
						afterBonus.getLivesRemaining(),
						afterBonus.getBulletsShot(),
						afterBonus.getShipsDestroyed()),
				false, ShipType.STANDARD);
		check(nextLevel.getGameState().getLivesRemaining() == 2,
				"Gained life persists through the next GameState and level");

		GameScreen fullLives = createGameScreen(
				new GameState(3, 0, 4, 0, 0), true, ShipType.STANDARD);
		check(fullLives.getGameState().getLivesRemaining() == 4,
				"Bonus life at the maximum does not exceed the cap");

		GameScreen speedsterBonus = createGameScreen(
				new GameState(3, 0, 1, 0, 0), true, ShipType.SPEEDSTER);
		check(speedsterBonus.getGameState().getLivesRemaining() == 2,
				"Existing bonus respects SPEEDSTER maxLives");
	}

	private static void checkDamageAndNoRevival() {
		PlayerStatus alive = new PlayerStatus(2, 3);
		check(alive.takeDamage(1) && alive.getLives() == 1,
				"Damage still removes a life");
		check(alive.takeDamage(1) && alive.getLives() == 0
				&& !alive.isAlive(),
				"Damage to the last life preserves game-over state");
		check(!alive.gainLife() && alive.getLives() == 0,
				"A player with zero lives cannot be revived");
	}

	public static void main(final String[] args) throws Exception {
		checkShipLifeLimits();
		checkGainLifeAndCap();
		checkBonusLifeAndLevelPersistence();
		checkDamageAndNoRevival();
		System.out.println("Player status extra-life checks passed.");
	}
}
