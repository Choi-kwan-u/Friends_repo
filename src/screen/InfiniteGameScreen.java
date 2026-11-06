package screen;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
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
import entity.Grenade;
import entity.GrenadeFragment;
import entity.Ship;
import entity.ZigzagEnemyShip;
import entity.item.BuffHud;
import entity.item.ClusterGrenadeItem;
import entity.item.Hyperfire;
import entity.item.Item;
import entity.item.ItemManager;
import entity.item.OrbitalAnnihilator;
import entity.item.ShieldItem;
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
	private static final int INITIAL_SPAWN_INTERVAL = 1300;
	private static final int MINIMUM_SPAWN_INTERVAL = 300;
	private static final int SPAWN_INTERVAL_STEP = 10;
	private static final int KILLS_PER_SPEED_UP = 20;
	private static final int MAXIMUM_FALL_SPEED = 3;
	private static final int SCREEN_CHANGE_INTERVAL = 1500;
	private static final int ENEMY_BULLET_SPEED = 4;
	private static final double ENEMY_FIRE_CHANCE = 0.003;

	/*
	 * Drop table for one roll in [0, 1): Energy Shield 10%, Hyperfire 15%,
	 * Cluster Grenade 5%. Upper bounds are written out, as summing doubles
	 * (0.1 + 0.15) would shift them.
	 */
	private static final double SHIELD_DROP_LIMIT = 0.10;
	private static final double HYPERFIRE_DROP_LIMIT = 0.25;
	private static final double GRENADE_DROP_LIMIT = 0.30;
	/** Energy Shield duration in this mode, in milliseconds. */
	static final long SHIELD_DURATION = 8000;
	/** Hyperfire duration in this mode, in milliseconds. */
	static final long HYPERFIRE_DURATION = 5000;
	/** Fixed altitude (Y) where a grenade explodes if it hits nothing. */
	private static final int GRENADE_DETONATION_Y = 200;
	/** Kills needed to fill the Orbital Annihilator gauge. */
	static final int ANNIHILATOR_KILLS = 30;
	/** Wait after a beam ends before the gauge fills again. */
	static final int ANNIHILATOR_COOLDOWN = 5000;
	/** Time the space key must be held to trigger the Annihilator. */
	private static final int ANNIHILATOR_HOLD_TIME = 1000;
	/** Blink half-period of the full gauge (same as the ready message). */
	static final long GAUGE_BLINK_PERIOD = 300;

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

	/** Falling items and active buffs. */
	private ItemManager itemManager;
	/** Source of the drop rolls (tests in this package can replace it). */
	Random itemDropRandom = new Random();
	/** Grenades launched by the player and not exploded yet. */
	private Set<Grenade> grenades;
	/** Fragments of exploded grenades. */
	private Set<GrenadeFragment> fragments;
	/** Prevents launching several grenades while holding G. */
	private boolean grenadeKeyPressed;
	/** Orbital Annihilator, armed by filling the kill gauge. */
	private OrbitalAnnihilator annihilator;
	/** Kills counted toward the Annihilator gauge. */
	private int annihilatorKills;
	/** Time before which kills do not fill the gauge. */
	private long annihilatorCooldownUntil;
	/** Time the space key started being held, 0 if not held. */
	private long spaceHeldSince;
	/** Best saved Infinite mode score, 0 if there is none yet. */
	private int bestInfiniteScore;

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
		this.itemManager = new ItemManager(this.height);
		this.grenades = new HashSet<Grenade>();
		this.fragments = new HashSet<GrenadeFragment>();
		this.grenadeKeyPressed = false;
		this.annihilator = new OrbitalAnnihilator();
		this.annihilatorKills = 0;
		this.annihilatorCooldownUntil = 0;
		this.spaceHeldSince = 0;
		this.gameStartTime = System.currentTimeMillis();
		this.lastCountdown = -1;
		this.inputDelay = Core.getCooldown(INPUT_DELAY);
		this.bestInfiniteScore = loadBestInfiniteScore();
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
			updateAnnihilatorControls();
			spawnEnemy();
			moveEnemies();
			shootEnemies();
		}

		updateAnnihilator();
		this.ship.update();

		if (this.inputDelay.checkFinished() && !this.gameOver) {
			this.itemManager.update(this.ship);
			if (!this.ship.isDestroyed())
				this.itemManager.checkCollisions(this.ship);
		}

		manageCollisions();
		manageGrenades();
		if (this.annihilator.isFiring())
			manageAnnihilatorBeam();
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
		// The ship cannot shoot while the Annihilator is charging.
		boolean charging = this.annihilator.isCharging();
		if (!charging && inputManager.isKeyDown(
				ControlsConfig.getKey(ControlsConfig.FIRE))) {
			int fired = this.ship.shoot(this.bullets);
			this.bulletsShot += fired;
			// Play the shot sound only when a bullet was actually fired.
			if (fired > 0)
				AudioManager.playSFX("audio/sfx/player_shoot.wav");
		}

		// Cluster Grenade launch with the G key, ignored if G is also bound
		// to movement or firing so one press cannot do both.
		boolean grenadeKeyDown = !isGrenadeKeyBoundElsewhere()
				&& inputManager.isKeyDown(KeyEvent.VK_G);
		if (grenadeKeyDown && !this.grenadeKeyPressed
				&& this.ship.launchGrenade(this.grenades, GRENADE_DETONATION_Y))
			AudioManager.playSFX("audio/sfx/item_grenade_launch.wav");
		this.grenadeKeyPressed = grenadeKeyDown;

		if (!charging && !this.ship.isDestroyed()) {
			// Follow-up bullets of a rapid burst play the same shot sound,
			// so a burst does not sound like a single shot.
			int burstFired = this.ship.updateBurst(this.bullets);
			this.bulletsShot += burstFired;
			if (burstFired > 0)
				AudioManager.playSFX("audio/sfx/player_shoot.wav");
		}
	}

	/** Checks whether the G key is also bound to movement or firing. */
	private boolean isGrenadeKeyBoundElsewhere() {
		return ControlsConfig.getKey(ControlsConfig.LEFT) == KeyEvent.VK_G
				|| ControlsConfig.getKey(ControlsConfig.RIGHT) == KeyEvent.VK_G
				|| ControlsConfig.getKey(ControlsConfig.FIRE) == KeyEvent.VK_G;
	}

	/** Holding space for a moment triggers an armed Annihilator. */
	private void updateAnnihilatorControls() {
		if (this.ship.isDestroyed()) {
			this.annihilator.cancelCharge();
			this.spaceHeldSince = 0;
			return;
		}
		if (!inputManager.isKeyDown(KeyEvent.VK_SPACE)
				|| !this.annihilator.isReady()) {
			this.spaceHeldSince = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (this.spaceHeldSince == 0)
			this.spaceHeldSince = now;
		if (now - this.spaceHeldSince >= ANNIHILATOR_HOLD_TIME
				&& this.annihilator.trigger()) {
			// Charging stops the ship shooting: drop a burst still owed.
			this.ship.cancelBurst();
			this.spaceHeldSince = 0;
		}
	}

	/** Moves the Annihilator through its phases; a finished beam empties the gauge. */
	void updateAnnihilator() {
		boolean wasFiring = this.annihilator.isFiring();
		this.annihilator.update();
		if (wasFiring && !this.annihilator.isFiring()) {
			this.annihilatorKills = 0;
			this.annihilatorCooldownUntil = System.currentTimeMillis()
					+ ANNIHILATOR_COOLDOWN;
		}
	}

	/**
	 * Counts a kill toward the Annihilator gauge. Kills do not count while
	 * the Annihilator is armed, charging or firing, nor during the cooldown
	 * that follows a beam.
	 */
	private void countAnnihilatorKill() {
		if (this.annihilator.isReady() || this.annihilator.isCharging()
				|| this.annihilator.isFiring()
				|| System.currentTimeMillis() < this.annihilatorCooldownUntil)
			return;
		this.annihilatorKills++;
		if (this.annihilatorKills >= ANNIHILATOR_KILLS) {
			this.annihilatorKills = ANNIHILATOR_KILLS;
			this.annihilator.arm();
		}
	}

	/**
	 * Applies the effects of an enemy destroyed by the player (bullet,
	 * grenade fragment or Annihilator beam). The caller removes the enemy.
	 */
	void destroyEnemy(final ZigzagEnemyShip enemy) {
		this.score += enemy.getPointValue();
		this.shipsDestroyed++;
		recordEnemyDefeat(enemy);
		AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
		this.particleSystem.spawnEnemyExplosion(
				enemy.getPositionX() + enemy.getWidth() / 2,
				enemy.getPositionY() + enemy.getHeight() / 2);
		countAnnihilatorKill();
		tryDropItem(enemy);
	}

	/** Rolls the drop table for a destroyed enemy. */
	private void tryDropItem(final Entity enemy) {
		Item item = createDrop(enemy.getPositionX(), enemy.getPositionY(),
				this.itemDropRandom.nextDouble());
		if (item != null)
			this.itemManager.spawnItem(item);
	}

	/**
	 * Picks the item dropped for one roll in [0, 1). One roll per kill, so
	 * a kill drops at most one item.
	 */
	static Item createDrop(final int positionX, final int positionY,
			final double roll) {
		if (roll < SHIELD_DROP_LIMIT)
			return new ShieldItem(positionX, positionY, SHIELD_DURATION, true);
		if (roll < HYPERFIRE_DROP_LIMIT)
			return new Hyperfire(positionX, positionY, HYPERFIRE_DURATION);
		if (roll < GRENADE_DROP_LIMIT)
			return new ClusterGrenadeItem(positionX, positionY);
		return null;
	}

	/** Moves grenades and fragments, and applies their hits. */
	private void manageGrenades() {
		boolean canHit = !this.gameOver;

		Set<Grenade> exploded = new HashSet<Grenade>();
		for (Grenade grenade : this.grenades) {
			grenade.update();
			boolean impact = false;
			if (canHit)
				for (ZigzagEnemyShip enemy : this.enemies)
					if (checkCollision(grenade, enemy)) {
						impact = true;
						break;
					}
			if (impact || grenade.hasReachedAltitude()) {
				exploded.add(grenade);
				this.fragments.addAll(GrenadeFragment.explode(
						grenade.getCenterX(), grenade.getCenterY()));
				AudioManager.playSFX("audio/sfx/item_grenade_explode.wav");
			}
		}
		this.grenades.removeAll(exploded);

		Set<GrenadeFragment> expired = new HashSet<GrenadeFragment>();
		for (GrenadeFragment fragment : this.fragments) {
			fragment.update();
			boolean used = false;
			if (canHit) {
				Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
				while (iterator.hasNext()) {
					ZigzagEnemyShip enemy = iterator.next();
					if (checkCollision(fragment, enemy)) {
						destroyEnemy(enemy);
						iterator.remove();
						used = true;
						break;
					}
				}
			}
			if (used || fragment.isExpired())
				expired.add(fragment);
		}
		this.fragments.removeAll(expired);
	}

	/** The beam destroys enemies and enemy bullets above the ship. */
	private void manageAnnihilatorBeam() {
		if (this.gameOver)
			return;
		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip enemy = iterator.next();
			if (OrbitalAnnihilator.hits(this.ship, enemy)) {
				destroyEnemy(enemy);
				iterator.remove();
			}
		}

		Set<Bullet> neutralized = new HashSet<Bullet>();
		for (Bullet bullet : this.enemyBullets)
			if (OrbitalAnnihilator.hits(this.ship, bullet))
				neutralized.add(bullet);
		this.enemyBullets.removeAll(neutralized);
		BulletPool.recycle(neutralized);
	}

	/** A life is lost: stop a charge and end the active buffs. */
	private void onLifeLost() {
		this.annihilator.cancelCharge();
		this.spaceHeldSince = 0;
		this.itemManager.getBuffManager().resetAllBuffs(this.ship);
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

		if (this.annihilator.isCharging() || this.annihilator.isFiring())
			drawManager.drawOrbitalAnnihilator(this.ship,
					OrbitalAnnihilator.BEAM_WIDTH, SEPARATION_LINE_HEIGHT,
					this.annihilator.isFiring(),
					this.annihilator.getChargeProgress());

		drawManager.drawEntity(this.ship, this.ship.getPositionX(),
				this.ship.getPositionY());
		if (this.ship.isShieldActive() && !this.ship.isDestroyed())
			drawManager.drawShield(this, this.ship);

		for (ZigzagEnemyShip enemy : this.enemies)
			drawManager.drawEntity(enemy, enemy.getPositionX(),
					enemy.getPositionY());

		for (Bullet bullet : this.bullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		for (Bullet bullet : this.enemyBullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		for (Grenade grenade : this.grenades)
			drawManager.drawGrenade(grenade);
		for (GrenadeFragment fragment : this.fragments)
			drawManager.drawGrenadeFragment(fragment);
		this.itemManager.draw(drawManager);

		drawManager.drawScore(this, this.score);
		drawManager.drawInfiniteHighScore(this, this.bestInfiniteScore,
				this.score, isBeatingHighScore());
		drawManager.drawAnnihilatorGauge(this, getAnnihilatorGaugeProgress(),
				false);
		drawManager.drawGrenadeCount(this, this.ship.getGrenadeCount());
		drawManager.drawBuffs(this, BuffHud.buildLines(
				this.itemManager.getBuffManager().getActiveBuffsRemainingTime(),
				System.currentTimeMillis()));
		if (this.annihilator.isReady())
			drawManager.drawAnnihilatorReady(this);
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

	/** Reads the top saved Infinite score; 0 when none can be read. */
	private int loadBestInfiniteScore() {
		try {
			List<engine.Score> scores =
					Core.getFileManager().loadInfiniteHighScores();
			return scores.isEmpty() ? 0 : scores.get(0).getScore();
		} catch (Exception e) {
			this.logger.warning("Couldn't load Infinite mode high scores!");
			return 0;
		}
	}

	/** True once the current score is above the best saved Infinite score. */
	boolean isBeatingHighScore() {
		return this.score > this.bestInfiniteScore;
	}

	float getAnnihilatorGaugeProgress() {
		return getAnnihilatorGaugeProgress(System.currentTimeMillis());
	}

	/**
	 * Gauge fill at the given time: blinks while the Annihilator can be
	 * used, stays full while it charges and fires, drains over the cooldown,
	 * then shows the kills toward the next use.
	 */
	float getAnnihilatorGaugeProgress(final long now) {
		if (this.annihilator.isReady())
			return (now / GAUGE_BLINK_PERIOD) % 2 == 0 ? 1f : 0f;
		if (this.annihilator.isCharging() || this.annihilator.isFiring())
			return 1f;
		long cooldownLeft = this.annihilatorCooldownUntil - now;
		if (cooldownLeft > 0)
			return cooldownLeft / (float) ANNIHILATOR_COOLDOWN;
		return this.annihilatorKills / (float) ANNIHILATOR_KILLS;
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
					destroyEnemy(enemy);
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
				if (this.ship.isShieldActive()) {
					AudioManager.playSFX("audio/sfx/item_shield_block.wav");
					this.logger.info("Shot absorbed by Energy Shield.");
					continue;
				}
				onLifeLost();
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
				if (this.ship.isShieldActive()) {
					// The shield takes the crash: only the enemy is lost.
					AudioManager.playSFX("audio/sfx/item_shield_block.wav");
					this.logger.info("Crash absorbed by Energy Shield.");
					continue;
				}
				onLifeLost();
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
