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
import entity.ZigzagEnemyShip.Pattern;
import vfx.Icon;
import hud.OneUpHud;
import entity.item.BuffHud;
import entity.item.ClusterGrenadeItem;
import entity.item.ExtraLifeItem;
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
	/** Chance per frame that a B-type enemy fires its three-way spread. */
	private static final double SPREAD_FIRE_CHANCE = 0.002;
	/** Sideways speed of the side bullets of a spread. */
	private static final int SPREAD_SPEED_X = 2;
	/*
	 * Spawn table for one roll in [0, 1): charger 10%, formation 10%, then a
	 * single enemy.
	 */
	static final double CHARGER_LIMIT = 0.10;
	static final double FORMATION_LIMIT = 0.20;
	/** Enemies in one formation. */
	static final int FORMATION_SIZE = 5;
	/** Horizontal distance between two enemies of a formation. */
	private static final int FORMATION_SPACING = 40;
	/** Vertical step between the rows of a V formation. */
	private static final int FORMATION_V_STEP = 20;
	/** The spawn interval after a formation is this many times longer. */
	private static final int FORMATION_INTERVAL_FACTOR = 2;
	/** Frames a warning charger stays shown or hidden while blinking. */
	private static final int CHARGE_BLINK_FRAMES = 6;
	/** Score step between two extra life drops. */
	static final int EXTRA_LIFE_SCORE_STEP = 1000;
	/** Most lives the player can hold in this mode. */
	static final int MAX_LIVES = 5;
	/** Blink half-period of the invulnerable ship, in milliseconds. */
	static final long INVULNERABLE_BLINK_PERIOD = 100;

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
	/** Hearts falling toward the player. */
	private List<ExtraLifeItem> lifeItems;
	/** Score at which the next heart drops. */
	private int nextLifeScore;
	/** "+1UP" popups shown when a heart gives a life. */
	private OneUpHud oneUpHud;
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
		this.lifeItems = new ArrayList<ExtraLifeItem>();
		this.oneUpHud = new OneUpHud();
		this.nextLifeScore = (this.score / EXTRA_LIFE_SCORE_STEP + 1)
				* EXTRA_LIFE_SCORE_STEP;
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
			updateLifeItems();
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
		tryDropExtraLife();
	}

	/**
	 * Drops one heart from the top of the screen, at a random X, for each
	 * score step reached.
	 */
	private void tryDropExtraLife() {
		while (this.score >= this.nextLifeScore) {
			int heartWidth = Icon.LIFE.getWidth() * ExtraLifeItem.SCALE;
			int maxX = this.width - SIDE_MARGIN - heartWidth;
			int positionX = SIDE_MARGIN
					+ (int) (Math.random() * (maxX - SIDE_MARGIN));
			this.lifeItems.add(new ExtraLifeItem(positionX,
					SEPARATION_LINE_HEIGHT));
			this.nextLifeScore += EXTRA_LIFE_SCORE_STEP;
		}
	}

	/** Moves the hearts and gives a life for each one the ship catches. */
	private void updateLifeItems() {
		Iterator<ExtraLifeItem> iterator = this.lifeItems.iterator();
		while (iterator.hasNext()) {
			ExtraLifeItem heart = iterator.next();
			heart.update();
			if (heart.getPositionY() > this.height) {
				iterator.remove();
			} else if (!this.ship.isDestroyed()
					&& checkCollision(heart, this.ship)) {
				iterator.remove();
				AudioManager.playSFX("audio/sfx/item_pickup.wav");
				if (gainLife())
					this.oneUpHud.show(
							this.ship.getPositionX() + this.ship.getWidth() / 2,
							this.ship.getPositionY() - 6);
			}
		}
	}

	/**
	 * Adds a life, up to MAX_LIVES.
	 *
	 * @return True if a life was added.
	 */
	boolean gainLife() {
		if (this.lives >= MAX_LIVES)
			return false;
		this.lives++;
		// Out of danger: back to the normal music.
		if (this.lives == 2)
			AudioManager.playBGM("audio/bgm/music_game.wav");
		this.logger.info("Extra life, " + this.lives + " lives remaining.");
		return true;
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

		boolean formation = spawnFromRoll(Math.random());

		int interval = Math.max(MINIMUM_SPAWN_INTERVAL, INITIAL_SPAWN_INTERVAL
				- this.shipsDestroyed * SPAWN_INTERVAL_STEP);
		// A formation brings several enemies at once: wait longer after it.
		if (formation)
			interval *= FORMATION_INTERVAL_FACTOR;
		this.spawnCooldown = Core.getCooldown(interval);
		this.spawnCooldown.reset();
	}

	/**
	 * Spawns what one roll in [0, 1) picks: a charger, a formation, or a
	 * single enemy whose type sets its movement and attack.
	 *
	 * @return True if a formation was spawned.
	 */
	boolean spawnFromRoll(final double roll) {
		int fallSpeed = Math.min(MAXIMUM_FALL_SPEED,
				1 + this.shipsDestroyed / KILLS_PER_SPEED_UP);

		if (roll < CHARGER_LIMIT) {
			this.enemies.add(ZigzagEnemyShip.charger(
					randomSpawnX(Pattern.CHARGER, 0), SEPARATION_LINE_HEIGHT,
					fallSpeed, this.height / 3));
			return false;
		}
		if (roll < FORMATION_LIMIT) {
			spawnFormation(fallSpeed, Math.random() < 0.5);
			return true;
		}

		// Single enemy: C 20%, B 40%, A 40% of the remaining rolls.
		double typeRoll = (roll - FORMATION_LIMIT) / (1 - FORMATION_LIMIT);
		SpriteType spriteType;
		Pattern pattern;
		if (typeRoll < 0.2) {
			spriteType = SpriteType.EnemyShipC1;
			pattern = Pattern.WAVE;
		} else if (typeRoll < 0.6) {
			spriteType = SpriteType.EnemyShipB1;
			pattern = Pattern.STRAIGHT;
		} else {
			spriteType = SpriteType.EnemyShipA1;
			pattern = Pattern.ZIGZAG;
		}
		this.enemies.add(new ZigzagEnemyShip(randomSpawnX(pattern, 0),
				SEPARATION_LINE_HEIGHT, spriteType, fallSpeed, pattern,
				Math.random() < 0.5 ? 1 : -1));
		return false;
	}

	/**
	 * Spawns FORMATION_SIZE zigzagging A-type enemies that keep their shape:
	 * a horizontal line, or a V with its center in front.
	 */
	private void spawnFormation(final int fallSpeed, final boolean vShape) {
		int span = (FORMATION_SIZE - 1) * FORMATION_SPACING;
		int leftX = randomSpawnX(Pattern.ZIGZAG, span);
		int direction = Math.random() < 0.5 ? 1 : -1;
		int middle = FORMATION_SIZE / 2;
		for (int i = 0; i < FORMATION_SIZE; i++) {
			int offsetY = vShape
					? (middle - Math.abs(i - middle)) * FORMATION_V_STEP : 0;
			this.enemies.add(new ZigzagEnemyShip(
					leftX + i * FORMATION_SPACING,
					SEPARATION_LINE_HEIGHT + offsetY, SpriteType.EnemyShipA1,
					fallSpeed, Pattern.ZIGZAG, direction));
		}
	}

	/**
	 * Picks a spawn X so the pattern never swings off screen.
	 *
	 * @param extraWidth
	 *            Extra width to the right (a formation's span).
	 */
	private int randomSpawnX(final Pattern pattern, final int extraWidth) {
		int amplitude = ZigzagEnemyShip.getAmplitude(pattern);
		int minX = SIDE_MARGIN + amplitude;
		int maxX = this.width - SIDE_MARGIN - amplitude - ENEMY_WIDTH
				- extraWidth;
		return minX + (int) (Math.random() * Math.max(1, maxX - minX));
	}

	private void moveEnemies() {
		int targetX = this.ship.getPositionX() + this.ship.getWidth() / 2;
		int targetY = this.ship.getPositionY() + this.ship.getHeight() / 2;
		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip enemy = iterator.next();
			enemy.advance(targetX, targetY);
			enemy.update();
			// A dashing charger can also leave through a side.
			if (enemy.getPositionY() > this.height
					|| enemy.getPositionX() < -enemy.getWidth()
					|| enemy.getPositionX() > this.width)
				iterator.remove();
		}
	}

	private void shootEnemies() {
		for (ZigzagEnemyShip enemy : this.enemies)
			if (Math.random() < fireChance(enemy.getPattern()))
				fireEnemy(enemy);
	}

	/** Chance per frame that an enemy with this pattern shoots. */
	static double fireChance(final Pattern pattern) {
		switch (pattern) {
		case STRAIGHT:
			return SPREAD_FIRE_CHANCE;
		case CHARGER:
			return 0;
		default:
			return ENEMY_FIRE_CHANCE;
		}
	}

	/**
	 * Fires the shot of the enemy's type: A straight down, B a three-way
	 * spread, C aimed at the player. Chargers do not shoot.
	 */
	void fireEnemy(final ZigzagEnemyShip enemy) {
		int x = enemy.getPositionX() + enemy.getWidth() / 2;
		int y = enemy.getPositionY() + enemy.getHeight();
		int speed = getEnemyBulletSpeed();

		switch (enemy.getPattern()) {
		case CHARGER:
			return;
		case STRAIGHT:
			for (int speedX = -SPREAD_SPEED_X; speedX <= SPREAD_SPEED_X;
					speedX += SPREAD_SPEED_X)
				addEnemyBullet(x, y, speed, speedX);
			break;
		case WAVE:
			addEnemyBullet(x, y, speed, aimedSpeedX(x, y, speed));
			break;
		default:
			addEnemyBullet(x, y, speed, 0);
			break;
		}
		// Same enemy shot sound as the Campaign formation.
		AudioManager.playSFX("audio/sfx/enemy_shoot.wav");
	}

	/** Sideways speed that sends a bullet from (x, y) toward the player. */
	private int aimedSpeedX(final int x, final int y, final int speed) {
		int dx = this.ship.getPositionX() + this.ship.getWidth() / 2 - x;
		int dy = this.ship.getPositionY() - y;
		if (dy <= 0)
			return 0;
		int speedX = (int) Math.round(dx / (double) dy * speed);
		return Math.max(-speed, Math.min(speed, speedX));
	}

	private void addEnemyBullet(final int x, final int y, final int speedY,
			final int speedX) {
		this.enemyBullets.add(BulletPool.getBullet(x, y, speedY, speedX,
				Bullet.Owner.ENEMY));
	}

	private void draw() {
		drawManager.initDrawing(this);

		if (this.annihilator.isCharging() || this.annihilator.isFiring())
			drawManager.drawOrbitalAnnihilator(this.ship,
					OrbitalAnnihilator.BEAM_WIDTH, SEPARATION_LINE_HEIGHT,
					this.annihilator.isFiring(),
					this.annihilator.getChargeProgress());

		if (isShipVisible(System.currentTimeMillis()))
			drawManager.drawEntity(this.ship, this.ship.getPositionX(),
					this.ship.getPositionY());
		if (this.ship.isShieldActive() && !this.ship.isDestroyed())
			drawManager.drawShield(this, this.ship);

		for (ZigzagEnemyShip enemy : this.enemies) {
			// A charger about to dash blinks as a warning.
			if (enemy.isChargeWarning()
					&& (enemy.getWarningFrame() / CHARGE_BLINK_FRAMES) % 2 == 1)
				continue;
			drawManager.drawEntity(enemy, enemy.getPositionX(),
					enemy.getPositionY());
		}

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
		for (ExtraLifeItem heart : this.lifeItems)
			drawManager.drawIcon(Icon.LIFE, heart.getPositionX(),
					heart.getPositionY(), ExtraLifeItem.SCALE);
		drawManager.drawOneUps(this.oneUpHud, this);

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

	/**
	 * The ship blinks while it is invulnerable after a hit, once it can move
	 * again. While stunned it is always drawn.
	 */
	boolean isShipVisible(final long now) {
		if (this.ship.isDestroyed() || !this.ship.isInvulnerable())
			return true;
		return (now / INVULNERABLE_BLINK_PERIOD) % 2 == 0;
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
			// Spread and aimed shots can also leave through a side.
			if (bullet.getPositionY() > this.height
					|| bullet.getPositionX() < -bullet.getWidth()
					|| bullet.getPositionX() > this.width)
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

		// After a hit the ship cannot be hit again for the whole recovery
		// (2 s), not only while it is stunned (1 s).
		if (this.gameOver || this.ship.isInvulnerable())
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
