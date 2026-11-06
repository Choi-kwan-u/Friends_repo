package entity;

import java.awt.Color;

import engine.DrawManager.SpriteType;

/**
 * Implements an enemy ship of the infinite mode. It falls down the screen
 * following one movement pattern: zigzag (default), straight, wide wave, or
 * charge (stops, warns, then dashes at the player).
 */
public class ZigzagEnemyShip extends EnemyShip {

	/** Movement patterns of the infinite mode enemies. */
	public enum Pattern {
		/** Swings sideways in a short zigzag while falling. */
		ZIGZAG,
		/** Falls straight down, faster. */
		STRAIGHT,
		/** Falls slowly along a wide sine wave. */
		WAVE,
		/** Stops, blinks as a warning, then dashes at the player. */
		CHARGER
	}

	/** Distance, in pixels, the ship travels sideways before turning back. */
	private static final int ZIGZAG_AMPLITUDE = 40;
	/** Sideways speed, in pixels per frame. */
	private static final int LATERAL_SPEED = 2;
	/** Scale applied to the downwards speed. */
	private static final double FALL_SPEED_SCALE = 0.8;
	/** Extra fall speed scale of the straight pattern. */
	static final double STRAIGHT_SPEED_SCALE = 1.5;
	/** Fall speed scale of the wave pattern. */
	static final double WAVE_SPEED_SCALE = 0.8;
	/** Sideways distance of the wave pattern from its center. */
	static final int WAVE_AMPLITUDE = 90;
	/** Frames for one full wave (2.5 s at 60 fps). */
	static final int WAVE_PERIOD_FRAMES = 150;
	/** Frames the charger blinks before dashing (0.6 s at 60 fps). */
	static final int CHARGE_WARNING_FRAMES = 36;
	/** Dash speed of the charger, in pixels per frame. */
	static final double CHARGE_SPEED = 6;
	/** Points given by a charger. */
	public static final int CHARGER_POINTS = 50;
	/** Color of the charger, to tell it apart from the other enemies. */
	static final Color CHARGER_COLOR = new Color(170, 70, 255);

	/** Charger phases. */
	private enum ChargeState { DESCENDING, WARNING, DASHING }

	/** Movement pattern of this ship. */
	private final Pattern pattern;
	/** Position in the x-axis the zigzag or wave is centered on. */
	private int originX;
	/** Downwards speed, in pixels per frame. */
	private int fallSpeed;
	/** Fraction of a pixel of downwards movement not yet applied. */
	private double fallRemainder;
	/** Current sideways direction, 1 is right and -1 is left. */
	private int direction;
	/** Frames since the ship appeared (wave) or started warning (charger). */
	private int frame;

	/** Height where a charger stops before its dash. */
	private int chargeStopY;
	private ChargeState chargeState;
	/** Exact position and velocity of a dashing charger. */
	private double exactX;
	private double exactY;
	private double dashSpeedX;
	private double dashSpeedY;

	/**
	 * Constructor, establishes a zigzagging ship.
	 *
	 * @param positionX
	 *            Initial position of the ship in the X axis, also the center of
	 *            the zigzag.
	 * @param positionY
	 *            Initial position of the ship in the Y axis.
	 * @param spriteType
	 *            Sprite type, image corresponding to the ship.
	 * @param fallSpeed
	 *            Downwards speed, in pixels per frame.
	 */
	public ZigzagEnemyShip(final int positionX, final int positionY,
			final SpriteType spriteType, final int fallSpeed) {
		this(positionX, positionY, spriteType, fallSpeed, Pattern.ZIGZAG,
				Math.random() < 0.5 ? 1 : -1);
	}

	/**
	 * Constructor with an explicit pattern and first sideways direction.
	 *
	 * @param direction
	 *            First sideways direction of a zigzag: 1 right, -1 left.
	 */
	public ZigzagEnemyShip(final int positionX, final int positionY,
			final SpriteType spriteType, final int fallSpeed,
			final Pattern pattern, final int direction) {
		super(positionX, positionY, spriteType);

		this.pattern = pattern;
		this.originX = positionX;
		this.fallSpeed = fallSpeed;
		this.direction = direction >= 0 ? 1 : -1;
	}

	/**
	 * Creates a charger: it falls to stopY, blinks, then dashes at the
	 * player.
	 *
	 * @param stopY
	 *            Height where it stops before the dash.
	 */
	public static ZigzagEnemyShip charger(final int positionX,
			final int positionY, final int fallSpeed, final int stopY) {
		ZigzagEnemyShip ship = new ZigzagEnemyShip(positionX, positionY,
				SpriteType.EnemyShipC1, fallSpeed, Pattern.CHARGER, 1);
		ship.chargeStopY = stopY;
		ship.chargeState = ChargeState.DESCENDING;
		ship.setColor(CHARGER_COLOR);
		ship.setPointValue(CHARGER_POINTS);
		return ship;
	}

	/**
	 * Advances the ship one frame, following its pattern.
	 *
	 * @param targetX
	 *            Center of the player ship in the X axis, aimed at by a
	 *            charger.
	 * @param targetY
	 *            Center of the player ship in the Y axis.
	 */
	public final void advance(final int targetX, final int targetY) {
		switch (this.pattern) {
		case STRAIGHT:
			fall(STRAIGHT_SPEED_SCALE, 0);
			break;
		case WAVE:
			waveMove();
			break;
		case CHARGER:
			chargeMove(targetX, targetY);
			break;
		default:
			zigzagMove();
			break;
		}
	}

	/**
	 * Advances the ship one frame, falling down and swinging sideways.
	 */
	public final void zigzagMove() {
		if (Math.abs(this.positionX + this.direction * LATERAL_SPEED
				- this.originX) > ZIGZAG_AMPLITUDE)
			this.direction = -this.direction;

		fall(1, this.direction * LATERAL_SPEED);
	}

	private void waveMove() {
		this.frame++;
		int waveX = this.originX + (int) Math.round(WAVE_AMPLITUDE
				* Math.sin(2 * Math.PI * this.frame / WAVE_PERIOD_FRAMES));
		fall(WAVE_SPEED_SCALE, waveX - this.positionX);
	}

	private void chargeMove(final int targetX, final int targetY) {
		switch (this.chargeState) {
		case DESCENDING:
			fall(1, 0);
			if (this.positionY >= this.chargeStopY) {
				this.chargeState = ChargeState.WARNING;
				this.frame = 0;
			}
			break;
		case WARNING:
			if (++this.frame >= CHARGE_WARNING_FRAMES)
				startDash(targetX, targetY);
			break;
		default:
			this.exactX += this.dashSpeedX;
			this.exactY += this.dashSpeedY;
			this.positionX = (int) Math.round(this.exactX);
			this.positionY = (int) Math.round(this.exactY);
			break;
		}
	}

	/** Locks the dash direction toward where the player is now. */
	private void startDash(final int targetX, final int targetY) {
		this.chargeState = ChargeState.DASHING;
		this.exactX = this.positionX;
		this.exactY = this.positionY;
		double dx = targetX - (this.positionX + this.width / 2.0);
		double dy = targetY - (this.positionY + this.height / 2.0);
		double length = Math.sqrt(dx * dx + dy * dy);
		if (length < 1) {
			this.dashSpeedX = 0;
			this.dashSpeedY = CHARGE_SPEED;
		} else {
			this.dashSpeedX = dx / length * CHARGE_SPEED;
			this.dashSpeedY = dy / length * CHARGE_SPEED;
		}
	}

	/** Moves down at fallSpeed times FALL_SPEED_SCALE times scale. */
	private void fall(final double scale, final int distanceX) {
		// Positions are whole pixels, so the scaled speed is accumulated.
		double fall = this.fallSpeed * FALL_SPEED_SCALE * scale
				+ this.fallRemainder;
		int fallStep = (int) fall;
		this.fallRemainder = fall - fallStep;

		move(distanceX, fallStep);
	}

	/** True while a charger is blinking before its dash. */
	public final boolean isChargeWarning() {
		return this.pattern == Pattern.CHARGER
				&& this.chargeState == ChargeState.WARNING;
	}

	/** True once a charger has started its dash. */
	public final boolean isDashing() {
		return this.pattern == Pattern.CHARGER
				&& this.chargeState == ChargeState.DASHING;
	}

	/** Frames spent in the current warning phase. */
	public final int getWarningFrame() {
		return this.frame;
	}

	public final Pattern getPattern() {
		return this.pattern;
	}

	/**
	 * Getter for the amplitude of the zigzag.
	 *
	 * @return Sideways distance the ship covers from its center.
	 */
	public static int getZigzagAmplitude() {
		return ZIGZAG_AMPLITUDE;
	}

	/**
	 * Sideways distance a pattern covers from its center.
	 *
	 * @param pattern
	 *            Movement pattern.
	 * @return Amplitude in pixels.
	 */
	public static int getAmplitude(final Pattern pattern) {
		switch (pattern) {
		case ZIGZAG:
			return ZIGZAG_AMPLITUDE;
		case WAVE:
			return WAVE_AMPLITUDE;
		default:
			return 0;
		}
	}
}
