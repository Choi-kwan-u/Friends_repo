package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import campaign.CampaignStageData;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.GameplayConfig;
import entity.Boss;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.PlayerStatus;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.LevelCompletedEvent;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import records.RunSession;

/** Standalone checks for level completion event publication. */
public final class LevelCompletedEventTest {
	public static void main(final String[] args) throws Exception {
		final int savedDifficulty = GameplayConfig.getDifficulty();
		try {
			clearPublishesOnce();
			replayPublishesAgain();
			onlyFinalWavePublishes();
			failureDoesNotPublish();
			finalBossStageNeedsBossDefeat();
			eachDifficultyIsDeliveredUnchanged();
			difficultyIsCapturedAtStart();
			customLevelDoesNotPublish();
			enemyDefeatPrecedesCompletion();
		} finally {
			GameplayConfig.setDifficulty(savedDifficulty);
		}
		System.out.println("Level completed event checks passed.");
	}

	private static void clearPublishesOnce() throws Exception {
		GameScreen screen = newScreen(2, wave(1));
		List<LevelCompletedEvent> events = listen(screen);
		clearFormation(screen);
		screen.advanceStageIfReady();
		screen.advanceStageIfReady();
		check(events.size() == 1, "Stage clear must publish exactly once");
		check(events.get(0).getLevelId() == 2,
				"Event must report the completed stage");
	}

	private static void replayPublishesAgain() throws Exception {
		for (int attempt = 0; attempt < 2; attempt++) {
			GameScreen screen = newScreen(2, wave(1));
			List<LevelCompletedEvent> events = listen(screen);
			clearFormation(screen);
			screen.advanceStageIfReady();
			check(events.size() == 1,
					"Each attempt must publish its own completion");
		}
	}

	private static void onlyFinalWavePublishes() throws Exception {
		GameScreen screen = newScreen(1, wave(1), wave(2));
		List<LevelCompletedEvent> events = listen(screen);
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(events.isEmpty(),
				"A wave clear must not publish a level completion");
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(events.size() == 1, "The final wave clear must publish once");
	}

	private static void failureDoesNotPublish() throws Exception {
		GameScreen screen = newScreen(1, wave(1));
		List<LevelCompletedEvent> events = listen(screen);
		PlayerStatus status = (PlayerStatus) get(screen, "playerStatus");
		while (status.isAlive())
			status.takeDamage(1);
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(events.isEmpty(), "A failed stage must not publish a completion");
		check(!screen.isStageCleared(), "Simultaneous death must fail the stage");
	}

	private static void finalBossStageNeedsBossDefeat() throws Exception {
		int finalStage = CampaignStageData.getTotalStages();
		GameScreen screen = newScreen(finalStage, wave(1));
		List<LevelCompletedEvent> events = listen(screen);
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(events.isEmpty(),
				"A formation clear without the boss defeat must not publish");
		defeatBoss(screen);
		screen.advanceStageIfReady();
		check(events.size() == 1, "The boss defeat must complete the stage");
		check(events.get(0).getLevelId() == finalStage,
				"The final campaign stage must be reported");
	}

	private static void eachDifficultyIsDeliveredUnchanged() throws Exception {
		int[] difficulties = { GameplayConfig.EASY, GameplayConfig.NORMAL,
				GameplayConfig.HARD };
		for (int difficulty : difficulties) {
			GameplayConfig.setDifficulty(difficulty);
			GameScreen screen = newScreen(1, wave(1));
			List<LevelCompletedEvent> events = listen(screen);
			clearFormation(screen);
			screen.advanceStageIfReady();
			check(events.get(0).getDifficulty() == difficulty,
					"Difficulty " + difficulty + " must be delivered unchanged");
		}
	}

	private static void difficultyIsCapturedAtStart() throws Exception {
		GameplayConfig.setDifficulty(GameplayConfig.HARD);
		GameScreen screen = newScreen(1, wave(1));
		List<LevelCompletedEvent> events = listen(screen);
		GameplayConfig.setDifficulty(GameplayConfig.EASY);
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(events.get(0).getDifficulty() == GameplayConfig.HARD,
				"A later preference change must not alter the reported difficulty");
	}

	private static void customLevelDoesNotPublish() throws Exception {
		WaveDefinition wave = new WaveDefinition("wave-01",
				new EnemyCell[][] { { new EnemyCell("A", 1) } },
				20, 100, 60, 2000);
		LevelDefinition level = new LevelDefinition(1, "custom-01", "Custom",
				Collections.singletonList(wave));
		GameScreen screen = GameScreen.forCustomLevel(
				new GameState(1, 0, 3, 0, 0), level, 1.0, 448, 520, 60);
		screen.initialize();
		List<LevelCompletedEvent> events = listen(screen);
		clearFormation(screen);
		screen.advanceStageIfReady();
		check(screen.isStageCleared(),
				"Test setup: the custom stage must be cleared");
		check(events.isEmpty(),
				"A custom level must not publish a campaign completion");
	}

	private static void enemyDefeatPrecedesCompletion() throws Exception {
		GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0),
				new GameSettings(1, 1, 1000, 10000), false, 448, 520, 60,
				new RunSession("order-check"));
		screen.initialize();
		final List<String> order = new ArrayList<String>();
		screen.getGameplayEvents().subscribe(EnemyDefeatedEvent.class,
				new GameplayEventListener<EnemyDefeatedEvent>() {
					public void onEvent(final EnemyDefeatedEvent event) {
						order.add("defeat");
					}
				});
		screen.getGameplayEvents().subscribe(LevelCompletedEvent.class,
				new GameplayEventListener<LevelCompletedEvent>() {
					public void onEvent(final LevelCompletedEvent event) {
						order.add("completed");
					}
				});
		EnemyShipFormation formation =
				(EnemyShipFormation) get(screen, "enemyShipFormation");
		EnemyShip enemy = formation.iterator().next();
		@SuppressWarnings("unchecked")
		Set<Bullet> bullets = (Set<Bullet>) get(screen, "bullets");
		bullets.add(BulletPool.getBullet(enemy.getPositionX() + 4,
				enemy.getPositionY() + 4, -4));
		call(screen, "manageCollisions");
		screen.advanceStageIfReady();
		check(order.equals(Arrays.asList("defeat", "completed")),
				"The final enemy defeat must precede completion: " + order);
	}

	private static GameSettings wave(final int width) {
		return new GameSettings(width, 1, 60, 2000);
	}

	private static GameScreen newScreen(final int level,
			final GameSettings... waves) {
		GameScreen screen = new GameScreen(new GameState(level, 0, 3, 0, 0),
				Arrays.asList(waves), false, 448, 520, 60);
		screen.initialize();
		return screen;
	}

	private static List<LevelCompletedEvent> listen(final GameScreen screen) {
		final List<LevelCompletedEvent> events =
				new ArrayList<LevelCompletedEvent>();
		screen.getGameplayEvents().subscribe(LevelCompletedEvent.class,
				new GameplayEventListener<LevelCompletedEvent>() {
					public void onEvent(final LevelCompletedEvent event) {
						events.add(event);
					}
				});
		return events;
	}

	private static void clearFormation(final GameScreen screen)
			throws Exception {
		EnemyShipFormation formation =
				(EnemyShipFormation) get(screen, "enemyShipFormation");
		List<EnemyShip> enemies = new ArrayList<EnemyShip>();
		for (EnemyShip enemy : formation)
			enemies.add(enemy);
		for (EnemyShip enemy : enemies)
			formation.destroy(enemy);
	}

	private static void defeatBoss(final GameScreen screen) throws Exception {
		Object manager = get(screen, "bossManager");
		Boss boss = (Boss) manager.getClass().getMethod("getBoss")
				.invoke(manager);
		check(boss != null, "Test setup: the final stage must spawn a boss");
		Method damage = GameScreen.class.getDeclaredMethod("damageBoss",
				int.class);
		damage.setAccessible(true);
		damage.invoke(screen, boss.getMaxHealth());
		Field explosion = GameScreen.class
				.getDeclaredField("bossExplosionCooldown");
		explosion.setAccessible(true);
		explosion.set(screen, Core.getCooldown(0));
	}

	private static Object get(final Object target, final String name)
			throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void call(final Object target, final String name)
			throws Exception {
		Method method = target.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private LevelCompletedEventTest() {
	}
}
