package vfx;

import java.awt.Color;

/**
 * A single explosion particle that moves in a straight line and
 * disappears after a fixed lifetime.
 */
public class Particle {

	private double x;
	private double y;
	private final double speedX;
	private final double speedY;
	private final Color color;
	private final int size;
	private final long birthTime;
	private final long lifetimeMs;

	public Particle(final double x, final double y, final double speedX,
			final double speedY, final Color color, final int size,
			final long lifetimeMs) {
		this.x = x;
		this.y = y;
		this.speedX = speedX;
		this.speedY = speedY;
		this.color = color;
		this.size = size;
		this.lifetimeMs = lifetimeMs;
		this.birthTime = System.currentTimeMillis();
	}

	/** Moves the particle one frame. */
	public void update() {
		this.x += this.speedX;
		this.y += this.speedY;
	}

	/** @return true once the particle has lived longer than its lifetime. */
	public boolean isExpired() {
		return System.currentTimeMillis() - this.birthTime >= this.lifetimeMs;
	}

	public int getX() { return (int) this.x; }
	public int getY() { return (int) this.y; }
	public int getSize() { return this.size; }
	public Color getColor() { return this.color; }
}
