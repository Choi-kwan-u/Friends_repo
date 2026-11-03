package entity;

import engine.DrawManager.SpriteType;
import java.awt.Color;

/** A red hazard that falls through the player area without dealing damage. */
public final class FallingObstacle extends Entity {

	/** Pixel dimensions of the obstacle's warning sprite. */
	public static final int WIDTH = 26;
	public static final int HEIGHT = 14;
	/** Vertical movement in pixels per game update. */
	public static final int FALL_SPEED = 2;

	public FallingObstacle(final int positionX, final int positionY) {
		super(positionX, positionY, WIDTH, HEIGHT, Color.RED);
		this.spriteType = SpriteType.FallingObstacle;
	}

	/** Advances the obstacle toward the bottom of the playfield. */
	public void update() {
		this.positionY += FALL_SPEED;
	}

	/**
	 * @param screenHeight Bottom edge of the screen.
	 * @return Whether the obstacle's top edge has reached or passed the bottom
	 *         edge of the screen.
	 */
	public boolean hasLeftScreen(final int screenHeight) {
		return this.positionY >= screenHeight;
	}
}
