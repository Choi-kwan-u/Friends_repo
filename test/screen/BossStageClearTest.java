package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;

import engine.GameSettings;
import engine.GameState;
import entity.Boss;
import entity.EnemyShip;
import entity.EnemyShipFormation;

/**
 * Standalone checks for the first completion condition of R4: a boss stage
 * cannot be cleared while its boss is alive.
 *
 * The formation is emptied on purpose, so the only thing left holding the
 * stage open is the boss. Without these checks a boss stage would clear the
 * moment the last ordinary enemy died, and the boss battle could be walked
 * past.
 */
public final class BossStageClearTest {

	private BossStageClearTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		for (int level : GameScreen.BOSS_LEVELS)
			checkABossStageWaitsForItsBoss(level);
		checkAnOrdinaryStageIsNotHeldOpen();

		System.out.println("Boss stage clear checks passed.");
	}

	/**
	 * Plays a boss stage down to its last enemy and then follows the stage
	 * through the three states the boss can leave it in: alive, exploding
	 * and gone.
	 *
	 * @param level
	 *            Boss stage to play.
	 */
	private static void checkABossStageWaitsForItsBoss(final int level)
			throws Exception {
		String stage = "Stage " + level + ": ";
		GameScreen game = newGame(level);
		emptyTheFormation(game);
		check(formationOf(game).isEmpty(),
				stage + "every ordinary enemy is gone");

		// 1. The boss is still alive, so the stage may not end.
		check(!isBossCleared(game),
				stage + "a living boss holds the stage open");
		game.update();
		check(!levelFinished(game),
				stage + "the stage does not clear while the boss is alive");

		// Several frames in a row, in case only the first one is guarded.
		for (int frame = 0; frame < 5; frame++)
			game.update();
		check(!levelFinished(game),
				stage + "the stage stays open frame after frame");

		// 2. The boss has just died and its explosion is still on screen.
		Boss boss = bossOf(game);
		check(boss != null, stage + "a boss was spawned");
		damageBoss(game, boss.getMaxHealth());
		check(boss.isDefeated(), stage + "the boss was defeated");
		check(!isBossCleared(game),
				stage + "the explosion still holds the stage open");
		game.update();
		check(!levelFinished(game),
				stage + "the stage does not clear during the explosion");

		// 3. The explosion has been shown, so nothing is left on the stage.
		finishExplosion(game);
		check(isBossCleared(game),
				stage + "the stage is clear once the boss has gone");
		game.update();
		check(levelFinished(game),
				stage + "the stage ends once the boss has gone");
		check(game.isStageCleared(),
				stage + "the stage counts as cleared, not failed");
	}

	/**
	 * A stage without a boss must not be held open by the boss check, or no
	 * ordinary stage could ever be finished.
	 */
	private static void checkAnOrdinaryStageIsNotHeldOpen() throws Exception {
		int level = ordinaryLevel();
		GameScreen game = newGame(level);

		check(isBossCleared(game),
				"Stage " + level + " has no boss holding it open");
		emptyTheFormation(game);
		game.update();
		check(levelFinished(game) && game.isStageCleared(),
				"Stage " + level + " clears when its enemies are gone");
	}

	/** @return A stage number that is not a boss stage. */
	private static int ordinaryLevel() {
		for (int level = 1;; level++) {
			boolean isBossLevel = false;
			for (int bossLevel : GameScreen.BOSS_LEVELS)
				if (bossLevel == level)
					isBossLevel = true;
			if (!isBossLevel)
				return level;
		}
	}

	/**
	 * Builds a one-enemy stage, with the opening countdown already over so
	 * that a single update is a normal frame of play.
	 *
	 * @param level
	 *            Stage number.
	 * @return Screen ready to be updated.
	 */
	private static GameScreen newGame(final int level) throws Exception {
		GameScreen game = new GameScreen(new GameState(level, 0, 3, 0, 0),
				new GameSettings(1, 1, 60, 2000), false, 448, 520, 60);
		game.initialize();
		// Without this the screen is still counting down and skips its
		// gameplay, including the check being tested here.
		setTime(read(game, "inputDelay"), 0L);
		return game;
	}

	/** Destroys every enemy, the way a player clearing the stage would. */
	private static void emptyTheFormation(final GameScreen game)
			throws Exception {
		EnemyShipFormation formation = formationOf(game);
		Iterator<EnemyShip> enemies = formation.iterator();
		while (enemies.hasNext()) {
			EnemyShip enemy = enemies.next();
			if (!enemy.isDestroyed())
				formation.destroy(enemy);
		}
	}

	/** Marks the explosion as fully shown, instead of waiting for it. */
	private static void finishExplosion(final GameScreen game)
			throws Exception {
		setTime(read(game, "bossExplosionCooldown"), 0L);
	}

	private static void damageBoss(final GameScreen game, final int damage)
			throws Exception {
		Method damageBoss = GameScreen.class.getDeclaredMethod("damageBoss",
				int.class);
		damageBoss.setAccessible(true);
		damageBoss.invoke(game, damage);
	}

	private static boolean isBossCleared(final GameScreen game)
			throws Exception {
		Method isBossCleared = GameScreen.class
				.getDeclaredMethod("isBossCleared");
		isBossCleared.setAccessible(true);
		return ((Boolean) isBossCleared.invoke(game)).booleanValue();
	}

	private static Boss bossOf(final GameScreen game) throws Exception {
		Method getBossOnScreen = GameScreen.class
				.getDeclaredMethod("getBossOnScreen");
		getBossOnScreen.setAccessible(true);
		return (Boss) getBossOnScreen.invoke(game);
	}

	private static EnemyShipFormation formationOf(final GameScreen game)
			throws Exception {
		return (EnemyShipFormation) read(game, "enemyShipFormation");
	}

	private static boolean levelFinished(final GameScreen game)
			throws Exception {
		return ((Boolean) read(game, "levelFinished")).booleanValue();
	}

	/** Puts a cooldown back to its unstarted state, so it reads as over. */
	private static void setTime(final Object cooldown, final long value)
			throws Exception {
		field(cooldown.getClass(), "time").setLong(cooldown, value);
	}

	private static Object read(final Object target, final String name)
			throws Exception {
		return field(target.getClass(), name).get(target);
	}

	private static Field field(final Class<?> owner, final String name)
			throws Exception {
		Class<?> type = owner;
		while (type != null) {
			try {
				Field result = type.getDeclaredField(name);
				result.setAccessible(true);
				return result;
			} catch (NoSuchFieldException exception) {
				type = type.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name + " on " + owner.getName());
	}
}
