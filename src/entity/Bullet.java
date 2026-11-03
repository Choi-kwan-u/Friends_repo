package entity;

import java.awt.Color;

import engine.DrawManager.SpriteType;

/**
 * Implements a bullet that moves vertically up or down.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Bullet extends Entity {

	/** Side that fired a bullet. */
	public enum Owner {
		/** Fired by the player ship. */
		PLAYER,
		/** Fired by an enemy ship or a boss. */
		ENEMY
	}

	/**
	 * Speed of the bullet, positive or negative depending on direction -
	 * positive is down.
	 */
	private int speed;
	private int speedX;
	/**
	 * Side that fired this bullet. Kept separately from the speed, because an
	 * aimed enemy shot can travel sideways or upwards and must still count as
	 * an enemy bullet.
	 */
	private Owner owner;

	/**
	 * Constructor, establishes the bullet's properties.
	 *
	 * @param positionX
	 *            Initial position of the bullet in the X axis.
	 * @param positionY
	 *            Initial position of the bullet in the Y axis.
	 * @param speed
	 *            Speed of the bullet, positive or negative depending on
	 *            direction - positive is down.
	 */
	public Bullet(final int positionX, final int positionY, final int speed) {
		this(positionX, positionY, speed,
				speed < 0 ? Owner.PLAYER : Owner.ENEMY);
	}

	/**
	 * Constructor, establishes the bullet's properties and who fired it.
	 *
	 * @param positionX
	 *            Initial position of the bullet in the X axis.
	 * @param positionY
	 *            Initial position of the bullet in the Y axis.
	 * @param speed
	 *            Speed of the bullet, positive or negative depending on
	 *            direction - positive is down.
	 * @param owner
	 *            Side that fired the bullet.
	 */
	public Bullet(final int positionX, final int positionY, final int speed,
			final Owner owner) {
		super(positionX, positionY, 3 * 2, 5 * 2, Color.WHITE);

		this.speed = speed;
		this.owner = owner;
		setSprite();
	}

	/**
	 * Getter for the side that fired this bullet.
	 *
	 * @return Owner of the bullet.
	 */
	public final Owner getOwner() {
		return this.owner;
	}

	/**
	 * Setter for the side that fired this bullet. Set on every bullet handed
	 * out by the pool, so a recycled bullet never keeps its previous owner.
	 *
	 * @param owner
	 *            Side that fired the bullet.
	 */
	public final void setOwner(final Owner owner) {
		this.owner = owner;
	}

	/**
	 * Checks whether an enemy or a boss fired this bullet.
	 *
	 * @return True if the bullet belongs to the enemy side.
	 */
	public final boolean isEnemyBullet() {
		return this.owner == Owner.ENEMY;
	}

	/**
	 * Sets correct sprite for the bullet, based on speed.
	 */
	public final void setSprite() {
		// Driven by the owner, not the direction: an aimed enemy shot may
		// travel upwards and must still look like an enemy bullet.
		if (this.owner == Owner.PLAYER)
			this.spriteType = SpriteType.Bullet;
		else
			this.spriteType = SpriteType.EnemyBullet;
	}

	/**
	 * Updates the bullet's position.
	 */
	public final void update() {
		this.positionY += this.speed;
		this.positionX += this.speedX;
	}

	/**
	 * Setter of the speed of the bullet.
	 *
	 * @param speed
	 *            New speed of the bullet.
	 */
	public final void setSpeed(final int speed) {
		this.speed = speed;
	}
	public final void setSpeedX(final int speedX) {
		this.speedX = speedX;
	}

	/**
	 * Getter for the speed of the bullet.
	 *
	 * @return Speed of the bullet.
	 */
	public final int getSpeed() {
		return this.speed;
	}
}
