package entity;

import java.awt.Color;

import engine.Cooldown;
import engine.Core;
import engine.GameColors;
import engine.DrawManager.SpriteType;
import event.DefeatedEnemyType;

/**
 * Implements a enemy ship, to be destroyed by the player.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class EnemyShip extends Entity {

	/** Point value of a type A enemy. */
	private static final int A_TYPE_POINTS = 10;
	/** Point value of a type B enemy. */
	private static final int B_TYPE_POINTS = 20;
	/** Point value of a type C enemy. */
	private static final int C_TYPE_POINTS = 30;
	/** Point value of a bonus enemy. */
	private static final int BONUS_TYPE_POINTS = 100;

	/** Cooldown between sprite changes. */
	private Cooldown animationCooldown;
	/** Checks if the ship has been hit by a bullet. */
	private boolean isDestroyed;
	/** Values of the ship, in points, when destroyed. */
	private int pointValue;
	/** Cooldown between this ship's shots. Null if the ship cannot shoot. */
	private Cooldown shootingCooldown;
	/** Remaining hit points; legacy and bonus enemies start with one. */
	private int health = 1;
	/**
	 * Kind of enemy this is, recorded on creation. Destruction swaps the
	 * sprite for an explosion, so the sprite cannot be read back later.
	 */
	private final DefeatedEnemyType defeatedType;

	/**
	 * Constructor, establishes the ship's properties.
	 *
	 * @param positionX
	 *            Initial position of the ship in the X axis.
	 * @param positionY
	 *            Initial position of the ship in the Y axis.
	 * @param spriteType
	 *            Sprite type, image corresponding to the ship.
	 */
	public EnemyShip(final int positionX, final int positionY,
			final SpriteType spriteType) {
     this(positionX, positionY, spriteType, 1);
	}

	/** Creates an enemy with explicit positive initial health. */
	public EnemyShip(final int positionX, final int positionY,
			final SpriteType spriteType, final int health) {
		super(positionX, positionY, 12 * 2, 8 * 2, colorFor(spriteType));
		if (health <= 0)
			throw new IllegalArgumentException("health must be positive");
		this.health = health;
		this.spriteType = spriteType;
		this.defeatedType = classify(spriteType);
		this.animationCooldown = Core.getCooldown(500);
		this.isDestroyed = false;

		switch (this.spriteType) {
		case EnemyShipA1:
		case EnemyShipA2:
			this.pointValue = A_TYPE_POINTS;
			break;
		case EnemyShipB1:
		case EnemyShipB2:
			this.pointValue = B_TYPE_POINTS;
			break;
		case EnemyShipC1:
		case EnemyShipC2:
			this.pointValue = C_TYPE_POINTS;
			break;
		default:
			this.pointValue = 0;
			break;
		}
	}

	/**
	 * Constructor, establishes the ship's properties for a special ship, with
	 * known starting properties.
	 */
	public EnemyShip() {
		 // super(-32, 60, 16 * 2, 7 * 2, Color.RED);
		super(-32, 60, 16 * 2, 7 * 2, GameColors.ENEMY_SPECIAL);
		this.spriteType = SpriteType.EnemyShipSpecial;
		this.isDestroyed = false;
		this.pointValue = BONUS_TYPE_POINTS;
		this.defeatedType = DefeatedEnemyType.BONUS;

	}

	/**
	 * Classifies an enemy from the sprite it is created with. Both animation
	 * frames of a normal enemy give the same classification.
	 *
	 * @param spriteType
	 *            Sprite the enemy is created with.
	 * @return Classification of the enemy, UNKNOWN for an unlisted sprite.
	 */
	private static DefeatedEnemyType classify(final SpriteType spriteType) {
		switch (spriteType) {
		case EnemyShipA1:
		case EnemyShipA2:
			return DefeatedEnemyType.A;
		case EnemyShipB1:
		case EnemyShipB2:
			return DefeatedEnemyType.B;
		case EnemyShipC1:
		case EnemyShipC2:
			return DefeatedEnemyType.C;
		case EnemyShipSpecial:
			return DefeatedEnemyType.BONUS;
		default:
			return DefeatedEnemyType.UNKNOWN;
		}
	}

	/**
	 * Getter for the kind of enemy this is, for a defeat event. It is taken
	 * from the sprite the enemy was created with, so it survives the
	 * animation frames and the explosion applied by {@link #destroy()}.
	 *
	 * @return Classification of this enemy.
	 */
	public final DefeatedEnemyType getDefeatedType() {
		return this.defeatedType;
	}

	private static Color colorFor(final SpriteType spriteType) {
		switch (spriteType) {
			case EnemyShipB1:
			case EnemyShipB2:
				return GameColors.ENEMY_B;
			case EnemyShipC1:
			case EnemyShipC2:
				return GameColors.ENEMY_C;
			default:
				return GameColors.ENEMY_A;
		}
	}

	/**
	 * Getter for the score bonus if this ship is destroyed.
	 *
	 * @return Value of the ship.
	 */
	public final int getPointValue() {
		return this.pointValue;
	}

	/**
	 * Moves the ship the specified distance.
	 *
	 * @param distanceX
	 *            Distance to move in the X axis.
	 * @param distanceY
	 *            Distance to move in the Y axis.
	 */
	public final void move(final int distanceX, final int distanceY) {
		this.positionX += distanceX;
		this.positionY += distanceY;
	}

	/**
	 * Updates attributes, mainly used for animation purposes.
	 */
	public final void update() {
		if (this.animationCooldown.checkFinished()) {
			this.animationCooldown.reset();

			switch (this.spriteType) {
			case EnemyShipA1:
				this.spriteType = SpriteType.EnemyShipA2;
				break;
			case EnemyShipA2:
				this.spriteType = SpriteType.EnemyShipA1;
				break;
			case EnemyShipB1:
				this.spriteType = SpriteType.EnemyShipB2;
				break;
			case EnemyShipB2:
				this.spriteType = SpriteType.EnemyShipB1;
				break;
			case EnemyShipC1:
				this.spriteType = SpriteType.EnemyShipC2;
				break;
			case EnemyShipC2:
				this.spriteType = SpriteType.EnemyShipC1;
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Destroys the ship, causing an explosion.
	 */
	public final void destroy() {
		this.health = 0;
		this.isDestroyed = true;
		this.spriteType = SpriteType.Explosion;
	}

	/** Applies one hit. Returns true only for the hit that destroys this ship. */
	public final boolean hit() {
		if (this.isDestroyed)
			return false;
		if (--this.health == 0) {
			destroy();
			return true;
		}
		return false;
	}

	public final int getHealth() {
		return this.health;
	}

	/**
	 * Checks if the ship has been destroyed.
	 *
	 * @return True if the ship has been destroyed.
	 */
	public final boolean isDestroyed() {
		return this.isDestroyed;
	}
	/** Gives this ship a shooting cooldown, without restarting it. */
	public final void setShootingCooldown(final Cooldown cooldown) {
		this.shootingCooldown = cooldown;
	}

	/** Returns this ship's shooting cooldown, so a replacement can inherit it. */
	public final Cooldown getShootingCooldown() {
		return this.shootingCooldown;
	}

	/** True if this ship is alive, allowed to shoot, and its cooldown is over. */
	public final boolean canShoot() {
		return !this.isDestroyed
				&& this.shootingCooldown != null
				&& this.shootingCooldown.checkFinished();
	}

	/** Restarts this ship's cooldown after it has fired. */
	public final void resetShootingCooldown() {
		if (this.shootingCooldown != null)
			this.shootingCooldown.reset();
	}

	/**
	 * Creates an enemy ship of the given type, for the Level Design system.
	 *
	 * @param type      Type of enemy to create (A, B or C).
	 * @param positionX Initial position in the X axis.
	 * @param positionY Initial position in the Y axis.
	 * @return New enemy ship of the given type.
	 */
	public static EnemyShip createByType(final EnemyType type,
			final int positionX, final int positionY) {
		return createByType(type, positionX, positionY, 1);
	}

	/** Creates a typed enemy with the health specified by a custom level. */
	public static EnemyShip createByType(final EnemyType type,
			final int positionX, final int positionY, final int health) {
		if (type == null)
			throw new IllegalArgumentException("Enemy type cannot be null.");
		return new EnemyShip(positionX, positionY, type.getSpriteType(), health);
	}
}
