package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import engine.GameSettings;
import engine.GameState;
import entity.Bullet;
import entity.BulletPool;

/**
 * Standalone checks that a bullet leaving the screen is retired whichever
 * way it leaves. An aimed enemy shot can travel almost horizontally, so the
 * vertical bounds alone are not enough.
 */
public final class BulletCleanupTest {

	private BulletCleanupTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkHorizontalShotIsRetired(4, "rightwards");
		checkHorizontalShotIsRetired(-4, "leftwards");
		checkBulletOnScreenIsKept();

		System.out.println("Bullet cleanup checks passed.");
	}

	/**
	 * A shot with no vertical speed never meets the vertical bounds. Before
	 * the X axis was checked it stayed in the set for the rest of the level,
	 * drawn and collision-checked every frame and never returned to the pool.
	 */
	private static void checkHorizontalShotIsRetired(final int speedX,
			final String label) throws Exception {
		GameScreen game = newGame();
		Set<Bullet> bullets = bulletsOf(game);
		bullets.clear();

		Bullet shot = BulletPool.getBullet(220, 300, 0, speedX,
				Bullet.Owner.ENEMY);
		bullets.add(shot);

		// Far more frames than it takes to cross a 448 pixel screen.
		for (int i = 0; i < 400 && !bullets.isEmpty(); i++)
			clean(game);

		check(bullets.isEmpty(),
				"A " + label + " shot is retired once it leaves the screen");
	}

	/** A bullet still inside the playfield must not be retired. */
	private static void checkBulletOnScreenIsKept() throws Exception {
		GameScreen game = newGame();
		Set<Bullet> bullets = bulletsOf(game);
		bullets.clear();

		bullets.add(BulletPool.getBullet(220, 300, 0, 0,
				Bullet.Owner.ENEMY));
		clean(game);

		check(bullets.size() == 1, "A bullet on screen is kept");
	}

	private static GameScreen newGame() {
		GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
				new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
		game.initialize();
		return game;
	}

	private static void clean(final GameScreen game) throws Exception {
		Method method = GameScreen.class.getDeclaredMethod("cleanBullets");
		method.setAccessible(true);
		method.invoke(game);
	}

	@SuppressWarnings("unchecked")
	private static Set<Bullet> bulletsOf(final GameScreen game)
			throws Exception {
		Field field = GameScreen.class.getDeclaredField("bullets");
		field.setAccessible(true);
		return (Set<Bullet>) field.get(game);
	}
}
