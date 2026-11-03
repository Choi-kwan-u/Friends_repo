package screen;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import vfx.ParticleSystem;

import audio.AudioManager;
import records.RunSession;
import records.Achievement;
import records.RecordsService;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.UUID;

import engine.Cooldown;
import engine.ControlsConfig;
import engine.Core;
import engine.DrawManager.SpriteType;
import engine.GameState;
import entity.Bullet;
import entity.BulletPool;
import entity.Entity;
import entity.Ship;
import entity.ZigzagEnemyShip;
import engine.DifficultyAdjustWhilePlaying;

/**
 * Implements the infinite mode game screen: zigzagging enemies keep spawning
 * from the top of the screen until the player runs out of lives or quits.
 */
public class InfiniteGameScreen extends Screen {

	private static final int SEPARATION_LINE_HEIGHT = 40;
	private static final int SIDE_MARGIN = 20;
	private static final int INPUT_DELAY = 6000;
	private static final int ENEMY_WIDTH = 12 * 2;
	private static final int INITIAL_SPAWN_INTERVAL = 1000;
	private static final int MINIMUM_SPAWN_INTERVAL = 300;
	private static final int SPAWN_INTERVAL_STEP = 10;
	private static final int KILLS_PER_SPEED_UP = 20;
	private static final int MAXIMUM_FALL_SPEED = 3;
	private static final int SCREEN_CHANGE_INTERVAL = 1500;
	private static final int ENEMY_BULLET_SPEED = 4;
	private static final double ENEMY_FIRE_CHANCE = 0.003;

	private int getEnemyBulletSpeed() {
		return (int) DifficultyAdjustWhilePlaying.getInstance().adjustEnemyBulletSpeed(ENEMY_BULLET_SPEED);
	}

	private Ship ship;
	private List<ZigzagEnemyShip> enemies;
	private Set<Bullet> bullets;
	private Set<Bullet> enemyBullets;
	private Cooldown spawnCooldown;
	private Cooldown screenFinishedCooldown;
	private int score;
	private int lives;
	/** Lives the player started with. */
	private final int maxLives;
	/** Total bullets shot by the player. */
	private int bulletsShot;
	private int shipsDestroyed;
	private boolean gameOver;
	private RunSession runSession;
	private boolean abandoned;
	private RecordsService recordsService;
	private Achievement visibleAchievement;
	private long achievementNoticeUntil;
	private final GameplayEventPublisher recordsEvents = new GameplayEventPublisher();
	private final Map<Entity,String> defeatIds = new IdentityHashMap<Entity,String>();
	private ParticleSystem particleSystem;
	private long gameStartTime;
	private int lastCountdown = -1;

	public InfiniteGameScreen(final GameState gameState, final int width,
			final int height, final int fps) {
		this(gameState, width, height, fps, null);
	}

	public InfiniteGameScreen(final GameState gameState, final int width,
			final int height, final int fps, final RunSession runSession) {
		super(width, height, fps);
		this.runSession = runSession;
		this.recordsService = Core.getRecordsService();
		this.recordsEvents.subscribe(EnemyDefeatedEvent.class,
				new GameplayEventListener<EnemyDefeatedEvent>() {
			public void onEvent(final EnemyDefeatedEvent event) {
				if (recordsService != null)
					recordsService.enemyDefeated(event.getRunId(), event.getEventId());
			}
		});

		this.score = gameState.getScore();
		this.lives = gameState.getLivesRemaining();
		this.maxLives = this.lives;
		this.bulletsShot = gameState.getBulletsShot();
		this.shipsDestroyed = gameState.getShipsDestroyed();
	}

	public final void initialize() {
		super.initialize();

		this.ship = new Ship(this.width / 2, this.height - 30);
		this.enemies = new ArrayList<ZigzagEnemyShip>();
		this.bullets = new HashSet<Bullet>();
		this.enemyBullets = new HashSet<Bullet>();
		this.spawnCooldown = Core.getCooldown(INITIAL_SPAWN_INTERVAL);
		this.spawnCooldown.reset();
		this.screenFinishedCooldown = Core.getCooldown(SCREEN_CHANGE_INTERVAL);
		this.particleSystem = new ParticleSystem();
		this.gameStartTime = System.currentTimeMillis();
		this.lastCountdown = -1;
		this.inputDelay = Core.getCooldown(INPUT_DELAY);
		this.inputDelay.reset();

		AudioManager.playBGM("audio/bgm/music_game.wav");
	}

	public final int run() {
		try { super.run(); }
		finally { if (runSession != null) runSession.stopActiveSegment(System.nanoTime()); }

		this.logger.info("Infinite mode ended with a score of " + this.score);
		return this.returnCode;
	}

	protected final void update() {
		super.update();
		if (this.recordsService != null) {
			long now = System.currentTimeMillis();
			if (this.visibleAchievement == null || now >= this.achievementNoticeUntil) {
				this.visibleAchievement = this.recordsService.pollNotification();
                if (this.visibleAchievement != null) {
                    AudioManager.playSFX("audio/sfx/achievement_unlock.wav");
                }
				this.achievementNoticeUntil = this.visibleAchievement == null ? 0 : now + 3000;
			}
		}


		//update the current lives remaining to the DifficultyAdjustWhilePlaying class
		DifficultyAdjustWhilePlaying.getInstance().update(this.lives);

		if (this.inputDelay.checkFinished() && !this.gameOver) {
			if (runSession != null) runSession.startActiveSegment(System.nanoTime());
			if (runSession != null && recordsService != null)
				recordsService.activeDuration(runSession.getRunId(), runSession.getActiveDurationMillis(System.nanoTime()));
			if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				this.abandoned = true;
				if (recordsService != null && runSession != null) recordsService.abandonRun(runSession.getRunId());
				if (runSession != null) runSession.stopActiveSegment(System.nanoTime());
				// Leaving with Esc plays the same back sound as the menu screens.
				AudioManager.playSFX("audio/sfx/menu_back.wav");
				this.isRunning = false;
				return;
			}

			updatePlayerControls();
			spawnEnemy();
			moveEnemies();
			shootEnemies();
		}

		this.ship.update();
		manageCollisions();
		cleanBullets();
		this.particleSystem.update();
		draw();

		if (this.lives == 0 && !this.gameOver) {
			this.gameOver = true;
			AudioManager.playSFX("audio/sfx/game_over.wav");
			if (runSession != null) runSession.stopActiveSegment(System.nanoTime());
			this.screenFinishedCooldown.reset();
		}

		if (this.gameOver && this.screenFinishedCooldown.checkFinished())
			this.isRunning = false;
	}

	void updatePlayerControls() {
		if (this.ship.isDestroyed())
			return;

		boolean moveRight = inputManager.isKeyDown(
				ControlsConfig.getKey(ControlsConfig.RIGHT));
		boolean moveLeft = inputManager.isKeyDown(
				ControlsConfig.getKey(ControlsConfig.LEFT));
		boolean isRightBorder = this.ship.getPositionX()
				+ this.ship.getWidth() + this.ship.getSpeed() > this.width - 1;
		boolean isLeftBorder = this.ship.getPositionX()
				- this.ship.getSpeed() < 1;

		if (moveRight && !isRightBorder)
			this.ship.moveRight();
		if (moveLeft && !isLeftBorder)
			this.ship.moveLeft();
		if (inputManager.isKeyDown(ControlsConfig.getKey(ControlsConfig.FIRE))) {
			int fired = this.ship.shoot(this.bullets);
			this.bulletsShot += fired;
			// Play the shot sound only when a bullet was actually fired.
			if (fired > 0)
				AudioManager.playSFX("audio/sfx/player_shoot.wav");
		}
		if (!this.ship.isDestroyed()) {
			// Follow-up bullets of a rapid burst play the same shot sound,
			// so a burst does not sound like a single shot.
			int burstFired = this.ship.updateBurst(this.bullets);
			this.bulletsShot += burstFired;
			if (burstFired > 0)
				AudioManager.playSFX("audio/sfx/player_shoot.wav");
		}
	}

	private void spawnEnemy() {
		if (!this.spawnCooldown.checkFinished())
			return;

		int amplitude = ZigzagEnemyShip.getZigzagAmplitude();
		int minX = SIDE_MARGIN + amplitude;
		int maxX = this.width - SIDE_MARGIN - amplitude - ENEMY_WIDTH;
		int positionX = minX + (int) (Math.random() * (maxX - minX));

		SpriteType spriteType;
		double roll = Math.random();
		if (roll < 0.2)
			spriteType = SpriteType.EnemyShipC1;
		else if (roll < 0.6)
			spriteType = SpriteType.EnemyShipB1;
		else
			spriteType = SpriteType.EnemyShipA1;

		int fallSpeed = Math.min(MAXIMUM_FALL_SPEED,
				1 + this.shipsDestroyed / KILLS_PER_SPEED_UP);

		this.enemies.add(new ZigzagEnemyShip(positionX,
				SEPARATION_LINE_HEIGHT, spriteType, fallSpeed));

		int interval = Math.max(MINIMUM_SPAWN_INTERVAL, INITIAL_SPAWN_INTERVAL
				- this.shipsDestroyed * SPAWN_INTERVAL_STEP);
		this.spawnCooldown = Core.getCooldown(interval);
		this.spawnCooldown.reset();
	}

	private void moveEnemies() {
		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip enemy = iterator.next();
			enemy.zigzagMove();
			enemy.update();
			if (enemy.getPositionY() > this.height)
				iterator.remove();
		}
	}

	private void shootEnemies() {
		for (ZigzagEnemyShip enemy : this.enemies) {
			if (Math.random() < ENEMY_FIRE_CHANCE) {
				this.enemyBullets.add(BulletPool.getBullet(
						enemy.getPositionX() + enemy.getWidth() / 2,
						enemy.getPositionY() + enemy.getHeight(),
						getEnemyBulletSpeed()));
				// Same enemy shot sound as the Campaign formation.
				AudioManager.playSFX("audio/sfx/enemy_shoot.wav");
			}
		}
	}

	private void draw() {
		drawManager.initDrawing(this);

		drawManager.drawEntity(this.ship, this.ship.getPositionX(),
				this.ship.getPositionY());

		for (ZigzagEnemyShip enemy : this.enemies)
			drawManager.drawEntity(enemy, enemy.getPositionX(),
					enemy.getPositionY());

		for (Bullet bullet : this.bullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		for (Bullet bullet : this.enemyBullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		drawManager.drawScore(this, this.score);
		drawManager.drawLives(this, this.lives, this.maxLives);
		try {
			drawManager.drawCredits(this, Core.getAssetHudSnapshot().getCredits());
		} catch (IllegalStateException e) {
			// Assets are not initialized (e.g. screen-only tests): skip credits.
		}
		drawManager.drawHorizontalLine(this, SEPARATION_LINE_HEIGHT - 1);

		if (this.visibleAchievement != null)
			drawManager.drawAchievementNotice(this, this.visibleAchievement);
		drawManager.drawParticles(this.particleSystem);

		if (!this.inputDelay.checkFinished()) {
			int countdown = (int) ((INPUT_DELAY
					- (System.currentTimeMillis() - this.gameStartTime)) / 1000);
			playCountdownSound(countdown);
			drawManager.drawCountDown(this, "Infinite Mode", countdown);
			drawManager.drawHorizontalLine(this,
					this.height / 2 - this.height / 12);
			drawManager.drawHorizontalLine(this,
					this.height / 2 + this.height / 12);
		}
		drawManager.completeDrawing(this);
	}

	private void playCountdownSound(final int countdown) {
		if (countdown == this.lastCountdown)
			return;

		this.lastCountdown = countdown;
		if (countdown == 0)
			AudioManager.playSFX("audio/sfx/countdown_go.wav");
		else if (countdown <= 3)
			AudioManager.playSFX("audio/sfx/countdown_beep.wav");
	}

	private void cleanBullets() {
		Set<Bullet> recyclable = new HashSet<Bullet>();
		for (Bullet bullet : this.bullets) {
			bullet.update();
			if (bullet.getPositionY() < SEPARATION_LINE_HEIGHT
					|| bullet.getPositionY() > this.height)
				recyclable.add(bullet);
		}
		this.bullets.removeAll(recyclable);
		BulletPool.recycle(recyclable);

		Set<Bullet> recyclableEnemy = new HashSet<Bullet>();
		for (Bullet bullet : this.enemyBullets) {
			bullet.update();
			if (bullet.getPositionY() > this.height)
				recyclableEnemy.add(bullet);
		}
		this.enemyBullets.removeAll(recyclableEnemy);
		BulletPool.recycle(recyclableEnemy);
	}

	private void manageCollisions() {
		if (this.gameOver) return;
		Set<Bullet> recyclable = new HashSet<Bullet>();

		for (Bullet bullet : this.bullets) {
			Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
			while (iterator.hasNext()) {
				ZigzagEnemyShip enemy = iterator.next();
				if (checkCollision(bullet, enemy)) {
					this.score += enemy.getPointValue();
					this.shipsDestroyed++;
                    recordEnemyDefeat(enemy);
                    AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
					this.particleSystem.spawnEnemyExplosion(
							enemy.getPositionX() + enemy.getWidth() / 2,
							enemy.getPositionY() + enemy.getHeight() / 2);
					iterator.remove();
					recyclable.add(bullet);
					break;
				}
			}
		}
		this.bullets.removeAll(recyclable);
		BulletPool.recycle(recyclable);

		if (this.gameOver || this.ship.isDestroyed())
			return;

		Iterator<Bullet> bulletIterator = this.enemyBullets.iterator();
		while (bulletIterator.hasNext()) {
			Bullet bullet = bulletIterator.next();
			if (checkCollision(bullet, this.ship)) {
				bulletIterator.remove();
				BulletPool.recycle(Collections.singleton(bullet));
				this.ship.destroy();
				this.ship.triggerDamageFeedback();
				// Hit by an enemy bullet: same hit sound as Campaign mode.
				AudioManager.playSFX("audio/sfx/player_hit.wav");
				this.particleSystem.spawnPlayerExplosion(
						this.ship.getPositionX() + this.ship.getWidth() / 2,
						this.ship.getPositionY() + this.ship.getHeight() / 2);
				this.lives--;
				if (this.lives == 1) {
					AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
				}
				this.logger.info("Hit by enemy bullet, " + this.lives
						+ " lives remaining.");
				return;
			}
		}

		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip rammed = iterator.next();
			if (checkCollision(rammed, this.ship)) {
				iterator.remove();
				// The crashing enemy explodes like an enemy that is shot down.
				AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
				this.particleSystem.spawnEnemyExplosion(
						rammed.getPositionX() + rammed.getWidth() / 2,
						rammed.getPositionY() + rammed.getHeight() / 2);
				this.ship.destroy();
				this.ship.triggerDamageFeedback();
				// Crashed into an enemy: same hit sound as a bullet hit.
				AudioManager.playSFX("audio/sfx/player_hit.wav");
				this.particleSystem.spawnPlayerExplosion(
						this.ship.getPositionX() + this.ship.getWidth() / 2,
						this.ship.getPositionY() + this.ship.getHeight() / 2);
				this.lives--;
				if (this.lives == 1) {
					AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
				}
				this.logger.info("Hit on player ship, " + this.lives
						+ " lives remaining.");
				break;
			}
		}
	}

	private boolean checkCollision(final Entity a, final Entity b) {
		int centerAX = a.getPositionX() + a.getWidth() / 2;
		int centerAY = a.getPositionY() + a.getHeight() / 2;
		int centerBX = b.getPositionX() + b.getWidth() / 2;
		int centerBY = b.getPositionY() + b.getHeight() / 2;

		int maxDistanceX = a.getWidth() / 2 + b.getWidth() / 2;
		int maxDistanceY = a.getHeight() / 2 + b.getHeight() / 2;

		int distanceX = Math.abs(centerAX - centerBX);
		int distanceY = Math.abs(centerAY - centerBY);

		return distanceX < maxDistanceX && distanceY < maxDistanceY;
	}

	public boolean isAbandoned() { return this.abandoned; }

	public final GameState getGameState() {
		return new GameState(1, this.score, this.lives, this.bulletsShot,
				this.shipsDestroyed);
	}

	private void recordEnemyDefeat(final Entity enemy) {
		if (this.runSession == null) return;
		String id = this.defeatIds.get(enemy);
		if (id == null) { id=UUID.randomUUID().toString(); this.defeatIds.put(enemy,id); }
		this.recordsEvents.publish(new EnemyDefeatedEvent(this.runSession.getRunId(),id));
	}

	public GameplayEventPublisher getGameplayEvents() { return this.recordsEvents; }

	public String getActiveRunId() { return this.runSession == null ? null : this.runSession.getRunId(); }
}
