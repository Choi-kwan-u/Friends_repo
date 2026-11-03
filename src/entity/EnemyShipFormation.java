package entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import engine.DifficultyAdjustWhilePlaying;
import screen.Screen;
import level.WaveDefinition;
import level.EnemyCell;
import engine.Cooldown;
import engine.Core;
import engine.DrawManager;
import engine.DrawManager.SpriteType;
import engine.GameSettings;
import audio.AudioManager;

/**
 * Groups enemy ships into a formation that moves together.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class EnemyShipFormation implements Iterable<EnemyShip> {

	/** Distance between ships. */
	private static final int SEPARATION_DISTANCE = 40;
	/** Proportion of C-type ships. */
	private static final double PROPORTION_C = 0.2;
	/** Proportion of B-type ships. */
	private static final double PROPORTION_B = 0.4;
	/** Lateral step of the classic pattern, on the opening stage. */
	private static final int X_SPEED = 2;
	/** Downwards step of the classic pattern, on the opening stage. */
	private static final int Y_SPEED = 2;
	/** Speed of the bullets shot by the members. */
	private static final int BULLET_SPEED = 3;
	/** Proportion of differences between shooting times. */
	private static final double SHOOTING_VARIANCE = .2;
	/** Margin on the sides of the screen. */
	private static final int SIDE_MARGIN = 20;
	/** Margin on the bottom of the screen. */
	private static final int BOTTOM_MARGIN = 80;
	/** Distance to go down each pass. */
	private static final int DESCENT_DISTANCE = 20;
	/** Minimum speed allowed. */
	private static final int MINIMUM_SPEED = 10;
	/** Horizontal step of the wave movement, on the opening stage. */
	private static final int WAVE_X_SPEED = 2;
	/** Maximum vertical amplitude of the wave. */
	private static final int WAVE_AMPLITUDE = 25;
	/**
	 * Phase the vertical oscillation advances per pixel travelled sideways,
	 * so its shape does not depend on how long the step is.
	 */
	private static final double WAVE_PHASE_PER_PIXEL = 0.025;

	/** DrawManager instance. */
	private DrawManager drawManager;
	/** Application logger. */
	private Logger logger;
	/** Screen to draw ships on. */
	private Screen screen;

	/** Available movement patterns for enemy formations. */
    private enum MovementPattern {
        CLASSIC,
        WAVE_BOUNCE,
        DIAGONAL_BOUNCE
    }

	/** Current movement pattern. */
    private MovementPattern movementPattern = MovementPattern.CLASSIC;
	/** Horizontal direction of the wave: 1 = right, -1 = left. */
	private int waveDirection = 1;
	/** Current phase of the wave. */
	private double wavePhase = -Math.PI / 2;
	/** Reference vertical position the wave oscillates around. */
	private int waveBaseY;
    /** Horizontal and vertical speed of the diagonal movement. */
    private static final int DIAGONAL_SPEED = 4;
    /**
     * Frames between steps, for every movement pattern. It stays at this
     * value on every stage and however many enemies are left, so the path
     * reads as a line rather than a jump; the step length is what carries
     * the speed instead.
     */
    private static final int STEP_INTERVAL = 2;
    /**
     * Largest step of any pattern. Without it a nearly cleared formation
     * would jump far enough in one step to read as a teleport.
     */
    private static final int MAX_STEP = 16;
    /**
     * The movementSpeed a full formation has on the opening stage, which is
     * the pace the patterns were tuned at. The step is anchored to it so
     * that stage one keeps its base step, while a harder stage or a
     * thinned-out formation still changes it.
     */
    private static final int REFERENCE_SPEED = 70;
    /** Current segment of the diamond loop (0 to 3). */
    private int diagonalPhase = 0;
    /** Initial vertical position of the diamond loop. */
    private int diagonalBaseY;
	/** List of enemy ships forming the formation. */
	private List<List<EnemyShip>> enemyShips;
	/** Minimum time between shots. */
	private Cooldown shootingCooldown;
	/** Number of ships in the formation - horizontally. */
	private int nShipsWide;
	/** Number of ships in the formation - vertically. */
	private int nShipsHigh;
	/** Time between shots. */
	private int shootingInterval;
	/** Variance in the time between shots. */
	private int shootingVariance;
	/** Initial ship speed. */
	private int baseSpeed;
	/** Speed of the ships. */
	private int movementSpeed;
	/** Current direction the formation is moving on. */
	private Direction currentDirection;
	/** Direction the formation was moving previously. */
	private Direction previousDirection;
	/** Interval between movements, in frames. */
	private int movementInterval;
	/** Total width of the formation. */
	private int width;
	/** Total height of the formation. */
	private int height;
	/** Position in the x-axis of the upper left corner of the formation. */
	private int positionX;
	/** Position in the y-axis of the upper left corner of the formation. */
	private int positionY;
	/** Width of one ship. */
	private int shipWidth;
	/** Height of one ship. */
	private int shipHeight;
	/** List of ships that are able to shoot. */
	private List<EnemyShip> shooters;
	/** Number of not destroyed ships. */
	private int shipCount;
	//lowers the speed of enemy bullets by half when the temporary difficulty adjustment is active
private int getBulletSpeed() {
    return (int)DifficultyAdjustWhilePlaying.getInstance().adjustEnemyBulletSpeed(BULLET_SPEED);
}
	/** Actual occupied cells at the start, independent of grid capacity. */
	private int initialShipCount;

	/** Directions the formation can move. */
	private enum Direction {
		/** Movement to the right side of the screen. */
		RIGHT,
		/** Movement to the left side of the screen. */
		LEFT,
		/** Movement to the bottom of the screen. */
		DOWN
	};

/**
	 * Constructor compatible with default callers using level 1.
	 *
	 * @param gameSettings Current game settings.
	 */
	public EnemyShipFormation(final GameSettings gameSettings) {
		this(gameSettings, 1, null);
	}

	/**
	 * Creates an enemy formation with a movement pattern based on the level.
	 *
	 * @param gameSettings Current game settings.
	 * @param level Current game level.
	 */
	public EnemyShipFormation(final GameSettings gameSettings, final int level) {
		this(gameSettings, level, null);
	}

	/**
	 * Uses the full grid while taking runtime timing from adjusted settings.
	 *
	 * @param gameSettings Current game settings.
	 * @param wave Custom wave definition.
	 */
	public EnemyShipFormation(final GameSettings gameSettings, final WaveDefinition wave) {
		this(gameSettings, 1, wave);
	}

	/**
	 * Full constructor supporting both level-based movement pattern and custom wave definition.
	 *
	 * @param gameSettings Current game settings.
	 * @param level Current game level.
	 * @param wave Custom wave definition, or null for default settings.
	 * @throws IllegalArgumentException If the wave grid geometry mismatches gameSettings
	 *                                  or initial position is invalid.
	 */
	public EnemyShipFormation(final GameSettings gameSettings, final int level, final WaveDefinition wave) {
		if (wave != null && (wave.getRows() != gameSettings.getFormationHeight()
				|| wave.getColumns() != gameSettings.getFormationWidth()
				|| wave.getInitialX() != gameSettings.getInitialX()
				|| wave.getInitialY() != gameSettings.getInitialY()))
			throw new IllegalArgumentException("Wave grid and runtime settings must share geometry");

		// Select the movement pattern according to the current level.
		MovementPattern[] patterns = MovementPattern.values();
		this.movementPattern = patterns[floorMod(level - 1, patterns.length)];
		this.drawManager = Core.getDrawManager();
		this.logger = Core.getLogger();
		this.enemyShips = new ArrayList<List<EnemyShip>>();
		this.currentDirection = Direction.RIGHT;
		this.movementInterval = 0;
		this.nShipsWide = gameSettings.getFormationWidth();
		this.nShipsHigh = gameSettings.getFormationHeight();
		this.shootingInterval = gameSettings.getShootingFrecuency();
		this.shootingVariance = (int) (gameSettings.getShootingFrecuency()
				* SHOOTING_VARIANCE);
		this.baseSpeed = gameSettings.getBaseSpeed();
		this.movementSpeed = this.baseSpeed;
		this.positionX = gameSettings.getInitialX();
		this.positionY = gameSettings.getInitialY();
		if (this.positionX < SIDE_MARGIN || this.positionY < 0
				|| this.positionY % DESCENT_DISTANCE != 0)
			throw new IllegalArgumentException("Initial formation position must "
					+ "have X >= " + SIDE_MARGIN + " and non-negative Y divisible by "
					+ DESCENT_DISTANCE + ": (" + positionX + "," + positionY + ")");
		this.waveBaseY = this.positionY;
        // Initial position of the diagonal movement.
        this.diagonalBaseY = this.positionY;
		this.shooters = new ArrayList<EnemyShip>();
		EnemyType enemyType;

		this.logger.info("Initializing " + nShipsWide + "x" + nShipsHigh
				+ " ship formation in (" + positionX + "," + positionY + ")");

		// Each sub-list is a column on the formation.
		for (int i = 0; i < this.nShipsWide; i++)
			this.enemyShips.add(new ArrayList<EnemyShip>());

		for (int columnIndex = 0; columnIndex < this.nShipsWide; columnIndex++) {
			List<EnemyShip> column = this.enemyShips.get(columnIndex);
			for (int row = 0; row < this.nShipsHigh; row++) {
				int health = 1;
				if (wave != null) {
					EnemyCell cell = wave.getCell(row, columnIndex);
					if (cell == null)
						continue;
					enemyType = enemyTypeFor(cell.getEnemyType());
					health = cell.getHealth();
				} else if (row / (float) this.nShipsHigh < PROPORTION_C) {
					enemyType = EnemyType.C;
				} else if (row / (float) this.nShipsHigh < PROPORTION_B + PROPORTION_C) {
					enemyType = EnemyType.B;
				} else {
					enemyType = EnemyType.A;
				}
				column.add(EnemyShip.createByType(enemyType,
						positionX + SEPARATION_DISTANCE * columnIndex,
						positionY + SEPARATION_DISTANCE * row, health));
				this.shipCount++;
			}
		}
		this.initialShipCount = this.shipCount;
		this.shipWidth = 24;
		this.shipHeight = 16;

		this.width = (this.nShipsWide - 1) * SEPARATION_DISTANCE
				+ this.shipWidth;
		this.height = (this.nShipsHigh - 1) * SEPARATION_DISTANCE
				+ this.shipHeight;

		for (List<EnemyShip> column : this.enemyShips) {
			if (column.isEmpty())
				continue;
			EnemyShip shooter = column.get(column.size() - 1);
			shooter.setShootingCooldown(createShooterCooldown());
			this.shooters.add(shooter);
		}
	}

	/** Creates a cooldown for one shooter, based on the level's settings. */
	private Cooldown createShooterCooldown() {
		return Core.getVariableCooldown(this.shootingInterval / 2,
				this.shootingVariance / 2);
	}

	/**
	 * Calculates a wave movement with horizontal bouncing, keeping the
	 * formation inside the screen boundaries. The wave oscillates around the
	 * initial vertical position the level settings asked for.
	 *
	 * @return Movement to apply on the X and Y axes.
	 */
	private int[] moveWaveBounce() {
		int minX = SIDE_MARGIN;
		int maxX = screen.getWidth() - SIDE_MARGIN - this.width;
		int minY = this.waveBaseY;
		int maxY = screen.getHeight() - BOTTOM_MARGIN - this.height;

		int nextX = this.positionX
				+ scaledStep(WAVE_X_SPEED) * this.waveDirection;
		if (nextX >= maxX) {
			nextX = maxX;
			this.waveDirection = -1;
		} else if (nextX <= minX) {
			nextX = minX;
			this.waveDirection = 1;
		}

		this.wavePhase += WAVE_PHASE_PER_PIXEL
				* Math.abs(nextX - this.positionX);

		int amplitude = Math.min(WAVE_AMPLITUDE,
				Math.max(0, (maxY - minY) / 2));
		int centerY = Math.max(minY,
				Math.min(this.waveBaseY + amplitude, maxY - amplitude));
		int nextY = centerY
				+ (int) Math.round(amplitude * Math.sin(this.wavePhase));
		nextY = Math.max(minY, Math.min(nextY, maxY));

		// cleanUp() re-anchors positionY to the topmost surviving ship, so a
		// cleared row leaves the formation far from its curve. Without a
		// limit the next frame drags every ship back in one move, which the
		// player sees as a jump. The diagonal pattern already moves at most
		// one step towards its target; the wave does the same here.
		int step = scaledStep(WAVE_X_SPEED);
		int movementY = nextY - this.positionY;
		if (movementY > step)
			movementY = step;
		else if (movementY < -step)
			movementY = -step;

		return new int[] { nextX - this.positionX, movementY };
	}


    /**
     * Distance the diagonal pattern covers in one step. The interval between
     * steps stays at {@link #STEP_INTERVAL} frames so the path stays
     * smooth, so the step itself is what carries the speed: it grows as
     * movementSpeed falls, which is how the stage setting and the speed-up
     * as enemies are defeated reach this pattern. The fixed step it used
     * before ignored both.
     *
     * <p>Anchored so that a full formation on the opening stage keeps the
     * step of {@link #DIAGONAL_SPEED} it has today, and capped so a nearly
     * cleared formation does not jump across the screen.</p>
     *
     * @return Step length in pixels.
     */
    private int diagonalStep() {
        return scaledStep(DIAGONAL_SPEED);
    }

    /**
     * Scales a step by the formation's current pace. Every pattern moves
     * every {@link #STEP_INTERVAL} frames so it stays smooth, and the step
     * is what carries the stage setting and the speed-up as enemies are
     * defeated: it is {@code baseStep} for a full formation on the opening
     * stage, longer when the stage is harder or the formation is thinner,
     * and capped by {@link #MAX_STEP}.
     *
     * @param baseStep
     *            Step, in pixels, of a full formation on the opening stage.
     * @return Step length in pixels.
     */
    private int scaledStep(final int baseStep) {
        int scaled = baseStep * REFERENCE_SPEED
                / Math.max(1, this.movementSpeed);
        return Math.max(1, Math.min(MAX_STEP, scaled));
    }

    /**
     * The remainder of a division, never negative, for any sign of the
     * value. Java 7 has no Math.floorMod and the README asks for Java 7, so
     * the identity is written out rather than called.
     *
     * @param value
     *            Value to divide.
     * @param modulus
     *            Positive divisor.
     * @return Remainder in the range zero to modulus - 1.
     */
    private static int floorMod(final int value, final int modulus) {
        return (value % modulus + modulus) % modulus;
    }

    /**
     * Moves the formation along a diamond-shaped loop.
     * The path adapts to the formation's size.
     *
     * @return Movement on the X and Y axes.
     */
    private int[] moveDiagonalBounce() {
        final int step = diagonalStep();
        int minX = SIDE_MARGIN;
        int maxX = screen.getWidth() - SIDE_MARGIN - this.width;
        int minY = Math.max(0, this.diagonalBaseY);
        int maxY = screen.getHeight() - BOTTOM_MARGIN - this.height;

        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);

        // Adapt the diamond to the available space.
        int radiusX = Math.min(80, (maxX - minX) / 2);
        int radiusY = Math.min(40, (maxY - minY) / 2);

        int centerX = minX + (maxX - minX) / 2;
        int centerY = minY + (maxY - minY) / 2;

        // Diamond vertices: top, right, bottom, left.
        int[] pointsX = {
                centerX,
                centerX + radiusX,
                centerX,
                centerX - radiusX
        };

        int[] pointsY = {
                centerY - radiusY,
                centerY,
                centerY + radiusY,
                centerY
        };

        // Current destination vertex.
        int targetX = pointsX[this.diagonalPhase];
        int targetY = pointsY[this.diagonalPhase];

        int dx = targetX - this.positionX;
        int dy = targetY - this.positionY;

        double distance = Math.hypot(dx, dy);

        // Move towards the destination without jumping.
        int movementX;
        int movementY;

        if (distance <= step) {
            movementX = dx;
            movementY = dy;

            // Move to the next vertex.
            this.diagonalPhase = (this.diagonalPhase + 1) % 4;
        } else {
            movementX = (int) Math.round(
                    dx / distance * step);
            movementY = (int) Math.round(
                    dy / distance * step);
        }

        // Keep the formation inside the screen.
        int nextX = Math.max(minX,
                Math.min(this.positionX + movementX, maxX));
        int nextY = Math.max(minY,
                Math.min(this.positionY + movementY, maxY));

        return new int[] {
                nextX - this.positionX,
                nextY - this.positionY
        };
    }

	/**
	 * Associates the formation to a given screen.
	 *
	 * @param newScreen
	 *            Screen to attach.
	 * @throws IllegalArgumentException If the formation exceeds the screen's
	 *            right or bottom margin.
	 */
	public final void attach(final Screen newScreen) {
		if ((long) positionX + this.width > newScreen.getWidth() - SIDE_MARGIN
				|| (long) positionY + this.height
						> newScreen.getHeight() - BOTTOM_MARGIN)
			throw new IllegalArgumentException("Formation does not fit within "
					+ "the screen margins: (" + positionX + "," + positionY + "), "
					+ this.width + "x" + this.height + " on "
					+ newScreen.getWidth() + "x" + newScreen.getHeight());
		screen = newScreen;
	}

	/**
	 * Draws every individual component of the formation.
	 */
	public final void draw() {
		for (List<EnemyShip> column : this.enemyShips)
			for (EnemyShip enemyShip : column)
				drawManager.drawEntity(enemyShip, enemyShip.getPositionX(),
						enemyShip.getPositionY());
	}

	/**
	 * Updates the position of the ships.
	 */
	public final void update() {
		if(this.shootingCooldown == null) {
			this.shootingCooldown = Core.getVariableCooldown(shootingInterval,
					shootingVariance);
			this.shootingCooldown.reset();
		}

		// Keep updating a defeated formation so its explosions can be removed
		// while the screen is still waiting for a boss to be defeated.
		if (this.initialShipCount == 0)
			return;
		cleanUp();

		int movementX = 0;
		int movementY = 0;
		double remainingProportion = (double) this.shipCount
				/ this.initialShipCount;
		this.movementSpeed = (int) (Math.pow(remainingProportion, 2)
				* this.baseSpeed);
		this.movementSpeed += MINIMUM_SPEED;

        movementInterval++;

        // Every pattern steps at the same short interval, which keeps the
        // movement smooth. movementSpeed is not thrown away with the long
        // delay it used to set: scaledStep() reads it and lengthens the
        // step instead, so the stage setting and the speed-up as enemies
        // are defeated reach each pattern through the distance covered per
        // step rather than through the pause between steps.
        final int movementDelay = STEP_INTERVAL;

        if (movementInterval >= movementDelay) {
            movementInterval = 0;

            if (this.movementPattern == MovementPattern.WAVE_BOUNCE) {
                int[] waveMovement = moveWaveBounce();
                movementX = waveMovement[0];
                movementY = waveMovement[1];
            } else if (this.movementPattern == MovementPattern.DIAGONAL_BOUNCE) {
                int[] diagonalMovement = moveDiagonalBounce();
                movementX = diagonalMovement[0];
                movementY = diagonalMovement[1];
            } else {

			boolean isAtBottom = positionY
					+ this.height > screen.getHeight() - BOTTOM_MARGIN;
			boolean isAtRightSide = positionX
					+ this.width >= screen.getWidth() - SIDE_MARGIN;
			boolean isAtLeftSide = positionX <= SIDE_MARGIN;
			boolean isAtHorizontalAltitude = positionY % DESCENT_DISTANCE == 0;

			if (currentDirection == Direction.DOWN) {
				if (isAtHorizontalAltitude)
					if (previousDirection == Direction.RIGHT) {
						currentDirection = Direction.LEFT;
						this.logger.info("Formation now moving left 1");
					} else {
						currentDirection = Direction.RIGHT;
						this.logger.info("Formation now moving right 2");
					}
			} else if (currentDirection == Direction.LEFT) {
				if (isAtLeftSide)
					if (!isAtBottom) {
						previousDirection = currentDirection;
						currentDirection = Direction.DOWN;
						this.logger.info("Formation now moving down 3");
					} else {
						currentDirection = Direction.RIGHT;
						this.logger.info("Formation now moving right 4");
					}
			} else {
				if (isAtRightSide)
					if (!isAtBottom) {
						previousDirection = currentDirection;
						currentDirection = Direction.DOWN;
						this.logger.info("Formation now moving down 5");
					} else {
						currentDirection = Direction.LEFT;
						this.logger.info("Formation now moving left 6");
					}
			}

			// The step is cut short rather than overshooting a side or a
			// descent altitude, so the formation lands exactly on them.
			if (currentDirection == Direction.RIGHT)
				movementX = Math.min(scaledStep(X_SPEED), Math.max(1,
						screen.getWidth() - SIDE_MARGIN
								- (positionX + this.width)));
			else if (currentDirection == Direction.LEFT)
				movementX = -Math.min(scaledStep(X_SPEED),
						Math.max(1, positionX - SIDE_MARGIN));
			else
				movementY = Math.min(scaledStep(Y_SPEED), DESCENT_DISTANCE
						- floorMod(positionY, DESCENT_DISTANCE));
			}

			positionX += movementX;
			positionY += movementY;

			// Cleans explosions.
			List<EnemyShip> destroyed;
			for (List<EnemyShip> column : this.enemyShips) {
				destroyed = new ArrayList<EnemyShip>();
				for (EnemyShip ship : column) {
					if (ship != null && ship.isDestroyed()) {
						destroyed.add(ship);
						this.logger.info("Removed enemy "
								+ column.indexOf(ship) + " from column "
								+ this.enemyShips.indexOf(column));
					}
				}
				column.removeAll(destroyed);
			}

			for (List<EnemyShip> column : this.enemyShips)
				for (EnemyShip enemyShip : column) {
					enemyShip.move(movementX, movementY);
					enemyShip.update();
				}
		}
	}

	/**
	 * Cleans empty columns, adjusts the width and height of the formation.
	 */
	private void cleanUp() {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		Iterator<List<EnemyShip>> columns = this.enemyShips.iterator();
		while (columns.hasNext()) {
			List<EnemyShip> column = columns.next();
			if (column.isEmpty()) {
				columns.remove();
				continue;
			}
			for (EnemyShip enemy : column) {
				if (enemy.isDestroyed())
					continue;
				minX = Math.min(minX, enemy.getPositionX());
				minY = Math.min(minY, enemy.getPositionY());
				maxX = Math.max(maxX, enemy.getPositionX() + enemy.getWidth());
				maxY = Math.max(maxY, enemy.getPositionY() + enemy.getHeight());
			}
		}
		if (minX != Integer.MAX_VALUE) {
			this.positionX = minX;
			this.positionY = minY;
			this.width = maxX - minX;
			this.height = maxY - minY;
		}
	}

	/**
	 * Shoots a bullet downwards.
	 *
	 * @param bullets
	 *            Bullets set to add the bullet being shot.
	 */
	public final void shoot(final Set<Bullet> bullets) {
		shoot(bullets, null);
	}

	/**
	 * Shoots a bullet downwards, aimed at the player when one is given.
	 *
	 * @param bullets
	 *            Bullets set to add the bullet being shot.
	 * @param player
	 *            Player ship to aim at, or null to shoot straight down.
	 */
	public final void shoot(final Set<Bullet> bullets, final Ship player) {
		if (this.shootingCooldown == null
				|| !this.shootingCooldown.checkFinished())
			return;

		// Only ships in the bottom row shoot, and each has its own cooldown.
		List<EnemyShip> readyShooters = new ArrayList<EnemyShip>();
		for (EnemyShip shooter : this.shooters)
			if (shooter.canShoot())
				readyShooters.add(shooter);
		if (readyShooters.isEmpty())
			return;

		int index = (int) (Math.random() * readyShooters.size());
		EnemyShip shooter = readyShooters.get(index);
		this.shootingCooldown.reset();
		shooter.resetShootingCooldown();

		int enemyX = shooter.getPositionX() + shooter.width / 2;
		int enemyY = shooter.getPositionY();

		if (player == null) {



			bullets.add(BulletPool.getBullet(enemyX, enemyY, getBulletSpeed(),
					Bullet.Owner.ENEMY));
			AudioManager.playSFX("audio/sfx/enemy_shoot.wav");
			return;
		}

		// Aim the bullet at the player instead of straight down.
		int playerX = player.getPositionX() + player.width / 2;
		int playerY = player.getPositionY() + player.height / 2;
		double dx = playerX - enemyX;
		double dy = playerY - enemyY;
		double distance = Math.sqrt(dx * dx + dy * dy);

		int speedX = 0;
		int speedY = getBulletSpeed(); // Default to straight down if distance is zero.
		if (distance > 0) {
			speedX = (int) Math.round((dx / distance) * getBulletSpeed());
			speedY = (int) Math.round((dy / distance) * getBulletSpeed());
		}

		// An aimed shot can travel sideways or upwards, so the owner is set
		// explicitly rather than inferred from the speed.
		bullets.add(BulletPool.getBullet(enemyX, enemyY, speedY, speedX,
				Bullet.Owner.ENEMY));
		AudioManager.playSFX("audio/sfx/enemy_shoot.wav");
	}

	/**
	 * Destroys a ship.
	 *
	 * @param destroyedShip
	 *            Ship to be destroyed.
	 */
	public final void destroy(final EnemyShip destroyedShip) {
		if (destroyedShip == null || destroyedShip.isDestroyed() || !contains(destroyedShip))
			return;
		for (List<EnemyShip> column : this.enemyShips)
			for (int i = 0; i < column.size(); i++)
				if (column.get(i).equals(destroyedShip)) {
					column.get(i).destroy();
					this.logger.info("Destroyed ship in ("
							+ this.enemyShips.indexOf(column) + "," + i + ")");
				}

		// Updates the list of ships that can shoot the player.
		if (this.shooters.contains(destroyedShip)) {
			int destroyedShipIndex = this.shooters.indexOf(destroyedShip);
			int destroyedShipColumnIndex = -1;

			for (List<EnemyShip> column : this.enemyShips)
				if (column.contains(destroyedShip)) {
					destroyedShipColumnIndex = this.enemyShips.indexOf(column);
					break;
				}

			EnemyShip nextShooter = getNextShooter(this.enemyShips
					.get(destroyedShipColumnIndex));

			if (nextShooter != null) {
				nextShooter.setShootingCooldown(
						destroyedShip.getShootingCooldown());
				this.shooters.set(destroyedShipIndex, nextShooter);
			} else {
				this.shooters.remove(destroyedShipIndex);
				this.logger.info("Shooters list reduced to "
						+ this.shooters.size() + " members.");
			}
		}

		this.shipCount--;
	}

	/** One bullet damages once; kill counting and shooter promotion happen on death only. */
	public final boolean hit(final EnemyShip enemy) {
		if (enemy == null || enemy.isDestroyed() || !contains(enemy))
			return false;
		if (enemy.getHealth() > 1) {
			enemy.hit();
			return false;
		}
		destroy(enemy);
		return true;
	}

	private boolean contains(final EnemyShip enemy) {
		for (List<EnemyShip> column : this.enemyShips)
			if (column.contains(enemy))
				return true;
		return false;
	}

	/** Runtime adapter for the enemy families currently implemented in this checkout. */
	public static SpriteType spriteFor(final String identifier) {
		return enemyTypeFor(identifier).getSpriteType();
	}

	private static EnemyType enemyTypeFor(final String identifier) {
		if ("A".equals(identifier)) return EnemyType.A;
		if ("B".equals(identifier)) return EnemyType.B;
		if ("C".equals(identifier)) return EnemyType.C;
		throw new IllegalArgumentException("Unsupported gameplay enemy identifier: " + identifier);
	}

	/** Validates full grid placement, including empty leading/trailing rows and columns. */
	public static void validateWave(final WaveDefinition wave, final int screenWidth,
			final int screenHeight) {
		if (wave.getInitialX() < SIDE_MARGIN || wave.getInitialY() < 40
				|| wave.getInitialY() % DESCENT_DISTANCE != 0)
			throw new IllegalArgumentException("Formation requires X >= 20 and Y >= 40 aligned to 20 pixels");
		long width = (wave.getColumns() - 1L) * SEPARATION_DISTANCE + 24;
		long height = (wave.getRows() - 1L) * SEPARATION_DISTANCE + 16;
		if (wave.getInitialX() + width > screenWidth - SIDE_MARGIN
				|| wave.getInitialY() + height > screenHeight - BOTTOM_MARGIN)
			throw new IllegalArgumentException("Formation grid exceeds screen margins");
		for (int row = 0; row < wave.getRows(); row++)
			for (int column = 0; column < wave.getColumns(); column++) {
				EnemyCell cell = wave.getCell(row, column);
				if (cell != null) {
					try {
						spriteFor(cell.getEnemyType());
					} catch (IllegalArgumentException invalid) {
						throw new IllegalArgumentException("formation.grid[" + row + "]["
								+ column + "].enemyType: " + invalid.getMessage(), invalid);
					}
				}
			}
	}

	/**
	 * Destroys a ship that touched the player, removing it from the shooters
	 * in the same frame.
	 *
	 * @param enemyShip
	 *            Ship that collided with the player.
	 * @return True if the ship was destroyed now, false if it already was.
	 */
	public final boolean destroyOnPlayerCollision(final EnemyShip enemyShip) {
		if (enemyShip == null || enemyShip.isDestroyed())
			return false;
		destroy(enemyShip);
		return true;
	}

	/**
	 * Returns the number of enemy ships still alive, for the HUD.
	 *
	 * @return Remaining enemy ships.
	 */
	public final int getRemainingCount() {
		return this.shipCount;
	}

	/**
	 * Returns the number of enemy ships at the start of the level.
	 *
	 * @return Total enemy ships.
	 */
	public final int getTotalCount() {
		return this.initialShipCount;
	}

	/**
	 * Returns the number of enemy ships destroyed in this level.
	 *
	 * @return Destroyed enemy ships.
	 */
	public final int getDestroyedCount() {
		return getTotalCount() - this.shipCount;
	}

	/**
	 * Returns the points the player can still earn from alive ships.
	 *
	 * @return Remaining points.
	 */
	public final int getRemainingPoints() {
		int points = 0;
		for (List<EnemyShip> column : this.enemyShips)
			for (EnemyShip ship : column)
				if (!ship.isDestroyed())
					points += ship.getPointValue();
		return points;
	}

	/**
	 * Gets the ship on a given column that will be in charge of shooting.
	 *
	 * @param column
	 *            Column to search.
	 * @return New shooter ship.
	 */
	public final EnemyShip getNextShooter(final List<EnemyShip> column) {
		Iterator<EnemyShip> iterator = column.iterator();
		EnemyShip nextShooter = null;
		while (iterator.hasNext()) {
			EnemyShip checkShip = iterator.next();
			if (checkShip != null && !checkShip.isDestroyed())
				nextShooter = checkShip;
		}

		return nextShooter;
	}

	/**
	 * Returns an iterator over the ships in the formation.
	 *
	 * @return Iterator over the enemy ships.
	 */
	@Override
	public final Iterator<EnemyShip> iterator() {
		Set<EnemyShip> enemyShipsList = new HashSet<EnemyShip>();

		for (List<EnemyShip> column : this.enemyShips)
			for (EnemyShip enemyShip : column)
				enemyShipsList.add(enemyShip);

		return enemyShipsList.iterator();
	}

	/**
	 * Checks if there are any ships remaining.
	 *
	 * @return True when all ships have been destroyed.
	 */
	public final boolean isEmpty() {
		return this.shipCount <= 0;
	}
}
