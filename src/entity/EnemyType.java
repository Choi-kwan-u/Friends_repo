package entity;

import engine.DrawManager.SpriteType;

/**
 * Types of enemy ships that the Level Design system can spawn.
 */
public enum EnemyType {
	/** Weakest enemy type. */
	A(SpriteType.EnemyShipA1),
	/** Medium enemy type. */
	B(SpriteType.EnemyShipB1),
	/** Strongest enemy type. */
	C(SpriteType.EnemyShipC1);

	/** Sprite used to draw this type of enemy. */
	private final SpriteType spriteType;

	EnemyType(final SpriteType spriteType) {
		this.spriteType = spriteType;
	}

	/**
	 * Returns the sprite used to draw this type of enemy.
	 *
	 * @return Sprite type.
	 */
	public SpriteType getSpriteType() {
		return this.spriteType;
	}
}
