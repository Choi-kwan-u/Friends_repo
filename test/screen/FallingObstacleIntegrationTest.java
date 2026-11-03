package screen;

import engine.DrawManager.SpriteType;
import engine.GameSettings;
import engine.GameState;
import entity.FallingObstacle;
import entity.PlayerStatus;
import entity.Ship;
import entity.ShipType;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import event.GameplayEventListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import entity.PlayerStatusEvent;

/** Integration checks for falling obstacles and the existing B5 slow API. */
public final class FallingObstacleIntegrationTest {

	private FallingObstacleIntegrationTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkDedicatedObstacleSpriteResource();
		checkObstacleMovementAndSpawn();
		checkObstacleCollisionAppliesSlowOnlyOnce();
		checkDestroyedShipCollisionOnlyConsumesObstacle();
		checkBothShieldsBlockSlow();
		checkEnergyShieldClearsActiveSlowExactlyOnce();
		checkSpawnFrameDoesNotCollideWithChameleonShip();
		checkObstacleRendersBehindShipAndShield();
		checkShipMovementTypesAndChameleon();
		checkWaveTransitionClearsObstacles();
		System.out.println("Falling obstacle integration checks passed.");
	}

	/** Checks the sprite kind and exact number of bits consumed by the loader. */
	private static void checkDedicatedObstacleSpriteResource() throws Exception {
		FallingObstacle obstacle = new FallingObstacle(10, 10);
		check(obstacle.getSpriteType() == SpriteType.FallingObstacle,
				"The hazard uses its dedicated sprite type");
		java.io.InputStream input = FallingObstacleIntegrationTest.class
				.getClassLoader().getResourceAsStream("graphics");
		check(input != null, "The graphics resource is on the test classpath");
		int bitCount = 0;
		int value;
		try {
			while ((value = input.read()) != -1)
				if (value == '0' || value == '1')
					bitCount++;
		} finally {
			input.close();
		}
		check(bitCount == 1108,
				"All existing and new sprite pixels are present (expected 1108, got "
						+ bitCount + ")");
	}

	private static void checkObstacleMovementAndSpawn() throws Exception {
		GameScreen game = newGame(ShipType.STANDARD);
		makeReady(game, "inputDelay");
		makeReady(game, "fallingObstacleCooldown");
		updateObstacles(game);

		Object spawnCooldown = read(game, "fallingObstacleCooldown");
		int spawnDelay = field(spawnCooldown.getClass(), "duration")
				.getInt(spawnCooldown);
		check(spawnDelay >= 15000 && spawnDelay <= 20000,
				"Obstacle spawn timing stays within the configured range");
		Set<FallingObstacle> obstacles = obstacles(game);
		check(obstacles.size() == 1,
				"An expired spawn timer creates one obstacle");
		check(field(spawnCooldown.getClass(), "time")
				.getLong(spawnCooldown) == 0L,
				"Spawning does not restart the post-removal cooldown");
		FallingObstacle obstacle = obstacles.iterator().next();
		check(obstacle.getPositionX() >= 1
				&& obstacle.getPositionX() + obstacle.getWidth()
						<= game.getWidth() - 1,
				"A new obstacle starts within the horizontal playfield");
		int initialY = obstacle.getPositionY();
		makeReady(game, "fallingObstacleCooldown");
		updateObstacles(game);
		check(obstacles.size() == 1,
				"At most one obstacle can be active at a time");
		check(obstacle.getPositionY() == initialY + FallingObstacle.FALL_SPEED,
				"An obstacle moves down at its configured speed");

		obstacle.setPositionY(game.getHeight());
		updateObstacles(game);
		check(obstacles.isEmpty(),
				"An obstacle is removed after leaving the bottom of the screen");
		assertFreshSpawnDelay(game,
				"Leaving the screen starts a fresh obstacle delay");
		updateObstacles(game);
		check(obstacles.isEmpty(),
				"An obstacle does not respawn immediately after leaving the screen");
		check(obstacle.getColor().equals(java.awt.Color.RED),
				"The obstacle uses the red hazard color");
	}

	private static void checkObstacleCollisionAppliesSlowOnlyOnce()
			throws Exception {
		GameScreen game = newGame(ShipType.STANDARD);
		makeReady(game, "inputDelay");
		Ship ship = ship(game);
		PlayerStatus status = game.getPlayerStatus();
		final List<PlayerStatusEvent> statusEvents =
				new ArrayList<PlayerStatusEvent>();
		game.getGameplayEvents().subscribe(PlayerStatusEvent.class,
				new GameplayEventListener<PlayerStatusEvent>() {
					@Override
					public void onEvent(final PlayerStatusEvent event) {
						statusEvents.add(event);
					}
				});
		int lives = status.getLives();
		long feedbackTime = field(ship.getClass(), "damageFeedbackStartTime")
				.getLong(ship);
		FallingObstacle obstacle = new FallingObstacle(
				ship.getPositionX(), ship.getPositionY());
		obstacles(game).add(obstacle);

		collideObstacles(game);
		check(obstacles(game).isEmpty(),
				"A colliding obstacle disappears immediately");
		assertFreshSpawnDelay(game,
				"Collision starts a fresh obstacle delay");
		check(status.isSlowActive(),
				"A colliding obstacle applies the existing slow status");
		check(statusEvents.size() == 1
				&& statusEvents.get(0).getType()
						== PlayerStatusEvent.Type.SLOW_STARTED,
				"GameScreen forwards the slow-start event to the gameplay bus");
		check(status.getLives() == lives && !ship.isDestroyed()
				&& !ship.isInvulnerable(),
				"An obstacle neither damages nor destroys the player ship");
		check(field(ship.getClass(), "damageFeedbackStartTime").getLong(ship)
				== feedbackTime,
				"An obstacle does not trigger hit feedback or invulnerability");
		collideObstacles(game);
		check(status.getLives() == lives,
				"A consumed obstacle cannot affect the player twice");

		// Set a short remaining time without sleeping; the next obstacle must
		// refresh the existing timer instead of stacking another slowdown.
		setLong(status, "slowExpiryTime", System.currentTimeMillis() + 100L);
		statusEvents.clear();
		obstacles(game).add(new FallingObstacle(
				ship.getPositionX(), ship.getPositionY()));
		collideObstacles(game);
		check(status.getSlowRemainingMillis() > 1900,
				"A later collision refreshes the two-second slow duration");
		check(statusEvents.size() == 1
				&& statusEvents.get(0).getType()
						== PlayerStatusEvent.Type.SLOW_REFRESHED,
				"GameScreen forwards exactly one slow-refresh event");
	}

	private static void assertFreshSpawnDelay(final GameScreen game,
			final String message) throws Exception {
		Object cooldown = read(game, "fallingObstacleCooldown");
		long startedAt = field(cooldown.getClass(), "time").getLong(cooldown);
		int duration = field(cooldown.getClass(), "duration")
				.getInt(cooldown);
		check(startedAt != 0L && duration >= 15000 && duration <= 20000,
				message);
	}

	private static void checkDestroyedShipCollisionOnlyConsumesObstacle()
			throws Exception {
		GameScreen game = newGame(ShipType.STANDARD);
		makeReady(game, "inputDelay");
		Ship ship = ship(game);
		PlayerStatus status = game.getPlayerStatus();
		final List<PlayerStatusEvent> events =
				new ArrayList<PlayerStatusEvent>();
		game.getGameplayEvents().subscribe(PlayerStatusEvent.class,
				new GameplayEventListener<PlayerStatusEvent>() {
					@Override
					public void onEvent(final PlayerStatusEvent event) {
						events.add(event);
					}
				});

		ship.destroy();
		check(ship.isDestroyed() && ship.isInvulnerable() && status.isAlive(),
				"The recovery test starts with a living, recently hit player");
		obstacles(game).add(new FallingObstacle(
				ship.getPositionX(), ship.getPositionY()));
		collideObstacles(game);
		check(obstacles(game).isEmpty(),
				"A collision during hit recovery still consumes the obstacle");
		check(!status.isSlowActive(),
				"A collision during hit recovery does not apply slow");
		check(countSlowEvents(events, PlayerStatusEvent.Type.SLOW_STARTED) == 0
				&& countSlowEvents(events,
						PlayerStatusEvent.Type.SLOW_REFRESHED) == 0,
				"A collision during hit recovery emits no slow start or refresh");

		setLong(read(ship, "destructionCooldown"), "time", 0L);
		check(!ship.isDestroyed() && ship.isInvulnerable(),
				"Regaining the controls leaves the protection running");

		setLong(read(ship, "invulnerabilityCooldown"), "time", 0L);
		check(!ship.isInvulnerable(),
				"The recovery cooldown can be ended deterministically in the test");
		obstacles(game).add(new FallingObstacle(
				ship.getPositionX(), ship.getPositionY()));
		collideObstacles(game);
		check(obstacles(game).isEmpty() && status.isSlowActive(),
				"A collision after hit recovery applies slow normally");
		check(countSlowEvents(events, PlayerStatusEvent.Type.SLOW_STARTED) == 1
				&& countSlowEvents(events,
						PlayerStatusEvent.Type.SLOW_REFRESHED) == 0,
				"A post-recovery collision emits exactly one slow-start event");
	}

	private static void checkBothShieldsBlockSlow() throws Exception {
		GameScreen timedShieldGame = newGame(ShipType.TANK);
		makeReady(timedShieldGame, "inputDelay");
		PlayerStatus timedShieldStatus = timedShieldGame.getPlayerStatus();
		int timedShieldLives = timedShieldStatus.getLives();
		timedShieldStatus.activateShield();
		obstacles(timedShieldGame).add(new FallingObstacle(
				ship(timedShieldGame).getPositionX(),
				ship(timedShieldGame).getPositionY()));
		collideObstacles(timedShieldGame);
		check(obstacles(timedShieldGame).isEmpty()
				&& !timedShieldStatus.isSlowActive()
				&& timedShieldStatus.getLives() == timedShieldLives,
				"PlayerStatus shield consumes the obstacle without a penalty");

		GameScreen itemShieldGame = newGame(ShipType.TANK);
		makeReady(itemShieldGame, "inputDelay");
		Ship shieldedShip = ship(itemShieldGame);
		int itemShieldLives = itemShieldGame.getPlayerStatus().getLives();
		shieldedShip.setShieldActive(true);
		obstacles(itemShieldGame).add(new FallingObstacle(
				shieldedShip.getPositionX(), shieldedShip.getPositionY()));
		collideObstacles(itemShieldGame);
		check(obstacles(itemShieldGame).isEmpty()
				&& !itemShieldGame.getPlayerStatus().isSlowActive()
				&& itemShieldGame.getPlayerStatus().getLives()
						== itemShieldLives,
				"Energy Shield consumes the obstacle without a penalty");
	}

	private static void checkEnergyShieldClearsActiveSlowExactlyOnce()
			throws Exception {
		GameScreen game = newGame(ShipType.TANK);
		makeReady(game, "inputDelay");
		triggerObstacleSlow(game);
		Ship ship = ship(game);
		PlayerStatus status = game.getPlayerStatus();
		final List<PlayerStatusEvent> events =
				new ArrayList<PlayerStatusEvent>();
		game.getGameplayEvents().subscribe(PlayerStatusEvent.class,
				new GameplayEventListener<PlayerStatusEvent>() {
					@Override
					public void onEvent(final PlayerStatusEvent event) {
						events.add(event);
					}
				});

		((ItemManager) read(game, "itemManager")).spawnItem(
				new ShieldItem(ship.getPositionX(), ship.getPositionY()));
		game.update();
		check(!status.isSlowActive(),
				"Energy Shield activation immediately clears the active slow");
		int position = ship.getPositionX();
		ship.moveRight();
		check(ship.getPositionX() == position + ship.getBaseSpeed(),
				"Clearing the slow restores normal movement speed");
		check(countSlowEvents(events, PlayerStatusEvent.Type.SLOW_ENDED) == 1,
				"Energy Shield activation publishes one SLOW_ENDED event");

		game.update();
		status.clearSlowDebuff();
		check(countSlowEvents(events, PlayerStatusEvent.Type.SLOW_ENDED) == 1,
				"Repeated updates and clears do not duplicate SLOW_ENDED");

		GameScreen unslowedGame = newGame(ShipType.STANDARD);
		makeReady(unslowedGame, "inputDelay");
		final List<PlayerStatusEvent> unslowedEvents =
				new ArrayList<PlayerStatusEvent>();
		unslowedGame.getGameplayEvents().subscribe(
				PlayerStatusEvent.class,
				new GameplayEventListener<PlayerStatusEvent>() {
					@Override
					public void onEvent(final PlayerStatusEvent event) {
						unslowedEvents.add(event);
					}
				});
		Ship unslowedShip = ship(unslowedGame);
		((ItemManager) read(unslowedGame, "itemManager")).spawnItem(
				new ShieldItem(unslowedShip.getPositionX(),
						unslowedShip.getPositionY()));
		unslowedGame.update();
		check(countSlowEvents(unslowedEvents,
				PlayerStatusEvent.Type.SLOW_ENDED) == 0,
				"Energy Shield without an active slow emits no end event");
	}

	/** A newly created hazard cannot hit a ship overlapping its spawn point. */
	private static void checkSpawnFrameDoesNotCollideWithChameleonShip()
			throws Exception {
		GameScreen game = newGame(ShipType.STANDARD);
		makeReady(game, "inputDelay");
		makeReady(game, "fallingObstacleCooldown");
		Ship ship = ship(game);
		ship.activateChameleonHull();

		long seed = 713L;
		Random expectedRandom = new Random(seed);
		int expectedX = 1 + expectedRandom.nextInt(game.getWidth()
				- FallingObstacle.WIDTH - 1);
		((Random) read(game, "obstacleRandom")).setSeed(seed);
		ship.setPositionX(expectedX);
		ship.setPositionY(40);

		check(updateObstacleFrame(game),
				"The expired timer creates an obstacle on this frame");
		FallingObstacle obstacle = obstacles(game).iterator().next();
		check(obstacle.getPositionY() == 40,
				"A new obstacle remains at its spawn position for its first frame");
		check(obstacles(game).size() == 1
				&& !game.getPlayerStatus().isSlowActive(),
				"Spawn-frame overlap neither consumes the obstacle nor applies slow");

		check(!updateObstacleFrame(game),
				"The following frame advances the existing obstacle");
		check(obstacles(game).isEmpty()
				&& game.getPlayerStatus().isSlowActive(),
				"Normal collision handling resumes on the next frame");
	}

	/** The hazard must not cover opaque ship pixels or the shield outline. */
	private static void checkObstacleRendersBehindShipAndShield()
			throws Exception {
		GameScreen game = newGame(ShipType.TANK);
		Ship ship = ship(game);
		ship.setPositionX(150);
		ship.setPositionY(220);
		ship.setShieldActive(true);
		int x = ship.getPositionX();
		int y = ship.getPositionY();

		invoke(game, "draw");
		BufferedImage withoutObstacle = copyBackBuffer(game);
		ship.setShieldActive(false);
		invoke(game, "draw");
		BufferedImage withoutShield = copyBackBuffer(game);
		ship.setShieldActive(true);
		obstacles(game).add(new FallingObstacle(x, y));
		invoke(game, "draw");
		BufferedImage withShipOverlap = copyBackBuffer(game);

		Map<?, ?> sprites = (Map<?, ?>) field(
				read(game, "drawManager").getClass(), "spriteMap")
				.get(null);
		boolean[][] shipSprite = (boolean[][]) sprites.get(SpriteType.Ship);
		boolean[][] obstacleSprite = (boolean[][]) sprites.get(
				SpriteType.FallingObstacle);
		int shipPixelsChecked = 0;
		for (int i = 0; i < obstacleSprite.length; i++) {
			for (int j = 0; j < obstacleSprite[i].length; j++) {
				if (!obstacleSprite[i][j])
					continue;
				int pixelX = x + i * 2;
				int pixelY = y + j * 2;
				if (i < shipSprite.length && j < shipSprite[i].length
						&& shipSprite[i][j]) {
					shipPixelsChecked++;
					check(withShipOverlap.getRGB(pixelX, pixelY)
							== withoutObstacle.getRGB(pixelX, pixelY),
							"The obstacle cannot cover an overlapping ship pixel");
				}
			}
		}
		check(shipPixelsChecked > 0,
				"The render test covers overlapping ship sprite pixels");

		obstacles(game).clear();
		int shieldOffsetX = 0;
		int shieldOffsetY = 0;
		int expectedShieldOverlap = 0;
		for (int offsetX = -32; offsetX <= 32
				&& expectedShieldOverlap == 0; offsetX++) {
			for (int offsetY = -32; offsetY <= 32
					&& expectedShieldOverlap == 0; offsetY++) {
				int overlap = countShieldHaloObstaclePixels(withoutObstacle,
						withoutShield, obstacleSprite, x + offsetX, y + offsetY,
						x, y,
						ship.getWidth(), ship.getHeight());
				if (overlap > 0) {
					shieldOffsetX = offsetX;
					shieldOffsetY = offsetY;
					expectedShieldOverlap = overlap;
				}
			}
		}
		check(expectedShieldOverlap > 0,
				"The test finds obstacle pixels overlapping the shield outline");
		obstacles(game).add(new FallingObstacle(x + shieldOffsetX,
				y + shieldOffsetY));
		invoke(game, "draw");
		BufferedImage withShieldOverlap = copyBackBuffer(game);
		check(countPreservedShieldHaloObstaclePixels(withoutObstacle,
				withoutShield, withShieldOverlap, obstacleSprite,
				x + shieldOffsetX, y + shieldOffsetY, x, y,
				ship.getWidth(), ship.getHeight()) == expectedShieldOverlap,
				"The obstacle cannot cover the overlapping shield ring");
	}

	private static int countShieldHaloObstaclePixels(
			final BufferedImage withShield, final BufferedImage withoutShield,
			final boolean[][] obstacleSprite, final int x, final int y,
			final int shipX, final int shipY,
			final int shipWidth, final int shipHeight) {
		int count = 0;
		for (int i = 0; i < obstacleSprite.length; i++)
			for (int j = 0; j < obstacleSprite[i].length; j++) {
				int pixelX = x + i * 2;
				int pixelY = y + j * 2;
				boolean outsideShip = pixelX < shipX
						|| pixelX >= shipX + shipWidth
						|| pixelY < shipY
						|| pixelY >= shipY + shipHeight;
				if (obstacleSprite[i][j] && outsideShip
						&& withShield.getRGB(pixelX, pixelY)
								!= withoutShield.getRGB(pixelX, pixelY))
					count++;
			}
		return count;
	}

	private static int countPreservedShieldHaloObstaclePixels(
			final BufferedImage baseline, final BufferedImage withoutShield,
			final BufferedImage rendered,
			final boolean[][] obstacleSprite, final int x, final int y,
			final int shipX, final int shipY,
			final int shipWidth, final int shipHeight) {
		int count = 0;
		for (int i = 0; i < obstacleSprite.length; i++)
			for (int j = 0; j < obstacleSprite[i].length; j++) {
				int pixelX = x + i * 2;
				int pixelY = y + j * 2;
				boolean outsideShip = pixelX < shipX
						|| pixelX >= shipX + shipWidth
						|| pixelY < shipY
						|| pixelY >= shipY + shipHeight;
				if (obstacleSprite[i][j] && outsideShip
						&& baseline.getRGB(pixelX, pixelY)
								!= withoutShield.getRGB(pixelX, pixelY)) {
					count++;
					check(rendered.getRGB(pixelX, pixelY)
							== baseline.getRGB(pixelX, pixelY),
							"The obstacle cannot cover the overlapping shield ring");
				}
			}
		return count;
	}

	private static BufferedImage copyBackBuffer(final GameScreen game)
			throws Exception {
		Object manager = read(game, "drawManager");
		return (BufferedImage) field(manager.getClass(), "backBuffer")
				.get(null);
	}

	private static int countSlowEvents(
			final List<PlayerStatusEvent> events,
			final PlayerStatusEvent.Type type) {
		int count = 0;
		for (PlayerStatusEvent event : events)
			if (event.getType() == type)
				count++;
		return count;
	}

	private static void checkShipMovementTypesAndChameleon()
			throws Exception {
		checkOneMovement(ShipType.STANDARD, 1, false);
		checkOneMovement(ShipType.SPEEDSTER, 2, false);
		checkOneMovement(ShipType.TANK, 0, false);
		checkOneMovement(ShipType.SPEEDSTER, 4, true);
		GameScreen tankGame = newGame(ShipType.TANK);
		triggerObstacleSlow(tankGame);
		Ship tank = ship(tankGame);
		int originalX = tank.getPositionX();
		tank.moveRight();
		tank.moveRight();
		check(tank.getPositionX() == originalX + 1,
				"TANK accumulates its fractional half-speed movement");
	}

	private static void checkOneMovement(final ShipType type,
			final int expectedPixels, final boolean chameleon) throws Exception {
		GameScreen game = newGame(type);
		triggerObstacleSlow(game);
		Ship ship = ship(game);
		if (chameleon)
			ship.activateChameleonHull();
		int originalX = ship.getPositionX();
		ship.moveRight();
		check(ship.getPositionX() == originalX + expectedPixels,
				type + (chameleon ? " Chameleon" : "")
						+ " moves with its expected slowed distance");
	}

	private static void checkWaveTransitionClearsObstacles()
			throws Exception {
		List<GameSettings> waves = new ArrayList<GameSettings>();
		waves.add(new GameSettings(5, 4, 60, 2000));
		waves.add(new GameSettings(5, 4, 60, 2000));
		GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
				waves, false, 448, 520, 60, ShipType.STANDARD);
		game.initialize();
		obstacles(game).add(new FallingObstacle(20, 40));
		invoke(game, "startNextWave");
		check(obstacles(game).isEmpty(),
				"A wave transition clears obstacles from the previous wave");
	}

	private static void triggerObstacleSlow(final GameScreen game)
			throws Exception {
		makeReady(game, "inputDelay");
		Ship ship = ship(game);
		obstacles(game).add(new FallingObstacle(
				ship.getPositionX(), ship.getPositionY()));
		collideObstacles(game);
		check(game.getPlayerStatus().isSlowActive(),
				"The obstacle collision starts the slow effect");
	}

	private static GameScreen newGame(final ShipType type) {
		GameScreen game = new GameScreen(
				new GameState(1, 0, type.getMaxLives(), 0, 0),
				new GameSettings(5, 4, 60, 2000), false,
				448, 520, 60, type);
		game.initialize();
		return game;
	}

	@SuppressWarnings("unchecked")
	private static Set<FallingObstacle> obstacles(final GameScreen game)
			throws Exception {
		return (Set<FallingObstacle>) read(game, "fallingObstacles");
	}

	private static Ship ship(final GameScreen game) throws Exception {
		return (Ship) read(game, "ship");
	}

	private static Object read(final Object target, final String name)
			throws Exception {
		return field(target.getClass(), name).get(target);
	}

	private static void makeReady(final GameScreen game, final String name)
			throws Exception {
		Object cooldown = read(game, name);
		setLong(cooldown, "time", 0L);
	}

	private static void setLong(final Object target, final String name,
			final long value) throws Exception {
		field(target.getClass(), name).setLong(target, value);
	}

	private static void updateObstacles(final GameScreen game)
			throws Exception {
		invoke(game, "updateFallingObstacles");
	}

	private static boolean updateObstacleFrame(final GameScreen game)
			throws Exception {
		boolean spawned = ((Boolean) invokeResult(game,
				"updateFallingObstacles")).booleanValue();
		if (!spawned)
			collideObstacles(game);
		return spawned;
	}

	private static void collideObstacles(final GameScreen game)
			throws Exception {
		invoke(game, "manageFallingObstacleCollisions");
	}

	private static void invoke(final Object target, final String name)
			throws Exception {
		Method method = target.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private static Object invokeResult(final Object target, final String name)
			throws Exception {
		Method method = target.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		return method.invoke(target);
	}

	private static Field field(Class<?> type, final String name)
			throws Exception {
		while (type != null) {
			try {
				Field result = type.getDeclaredField(name);
				result.setAccessible(true);
				return result;
			} catch (NoSuchFieldException exception) {
				type = type.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}
}
