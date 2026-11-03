package screen;

import java.lang.reflect.Method;

import engine.GameSettings;
import engine.GameState;
import entity.Boss;

/**
 * Standalone checks that defeating a boss gives its score exactly once, and
 * that it is counted among the ships destroyed. The score used to stay the
 * same when a boss was defeated, and the count used to ignore the boss
 * although its defeat event already reached the records service — the score
 * screen and the saved run record both read the count, so a boss was missing
 * from both.
 */
public final class BossKillScoreTest {

	private BossKillScoreTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkEachBossTypeHasAScore();
		checkDefeatGivesTheScoreOnce(GameScreen.firstBossLevel(),
				Boss.BossType.BOSS_A);
		for (int level : GameScreen.BOSS_LEVELS)
			if (level != GameScreen.firstBossLevel())
				checkDefeatGivesTheScoreOnce(level, Boss.BossType.BOSS_B);

		System.out.println("Boss kill score checks passed.");
	}

	private static void checkEachBossTypeHasAScore() {
		for (Boss.BossType type : Boss.BossType.values())
			check(type.getPointValue() > 0,
					type + " gives a score when defeated");
		check(Boss.BossType.BOSS_B.getPointValue()
				> Boss.BossType.BOSS_A.getPointValue(),
				"The harder boss is worth more");
	}

	private static void checkDefeatGivesTheScoreOnce(final int level,
			final Boss.BossType expected) throws Exception {
		GameScreen game = new GameScreen(new GameState(level, 0, 3, 0, 0),
				new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
		game.initialize();

		Method damageBoss = GameScreen.class.getDeclaredMethod("damageBoss",
				int.class);
		damageBoss.setAccessible(true);

		int before = game.getGameState().getScore();
		int destroyedBefore = game.getGameState().getShipsDestroyed();
		// One hit short of the boss's health: no score yet.
		damageBoss.invoke(game, expected.getMaxHealth() - 1);
		check(game.getGameState().getScore() == before,
				"Stage " + level + ": no score while the boss is alive");
		check(game.getGameState().getShipsDestroyed() == destroyedBefore,
				"Stage " + level + ": a living boss is not counted");

		damageBoss.invoke(game, 1);
		check(game.getGameState().getScore()
				== before + expected.getPointValue(),
				"Stage " + level + ": defeating " + expected + " gives "
						+ expected.getPointValue() + " points");
		check(game.getGameState().getShipsDestroyed() == destroyedBefore + 1,
				"Stage " + level + ": defeating " + expected + " counts as"
						+ " one ship destroyed");

		damageBoss.invoke(game, 50);
		check(game.getGameState().getScore()
				== before + expected.getPointValue(),
				"Stage " + level + ": hitting a defeated boss gives nothing");
		check(game.getGameState().getShipsDestroyed() == destroyedBefore + 1,
				"Stage " + level + ": hitting a defeated boss does not count"
						+ " again");
	}
}
