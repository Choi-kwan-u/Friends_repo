package vfx;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Creates, updates and removes explosion particles.
 */
public class ParticleSystem {

	private static final Color[] FIRE_COLORS = {
			Color.ORANGE, Color.YELLOW, Color.RED };

	private final List<Particle> particles = new ArrayList<Particle>();
	private final Random random = new Random();

	/** Small explosion: 10-15 particles, gone within 0.5 s. */
	public void spawnEnemyExplosion(final int centerX, final int centerY) {
		int count = 10 + random.nextInt(6); // 10..15
		spawn(centerX, centerY, count, 2, 2.0, 500);
	}

	/** Big explosion: 20-30 particles, about 1 s. */
	public void spawnPlayerExplosion(final int centerX, final int centerY) {
		int count = 20 + random.nextInt(11); // 20..30
		spawn(centerX, centerY, count, 3, 3.0, 1000);
	}

	private void spawn(final int centerX, final int centerY, final int count,
			final int size, final double maxSpeed, final long lifetimeMs) {
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * 2 * Math.PI; // any direction
			double speed = 0.5 + random.nextDouble() * maxSpeed;
			Color color = FIRE_COLORS[random.nextInt(FIRE_COLORS.length)];
			particles.add(new Particle(centerX, centerY,
					Math.cos(angle) * speed, Math.sin(angle) * speed,
					color, size, lifetimeMs));
		}
	}

	/** Moves all particles and removes expired ones. Call once per frame. */
	public void update() {
		Iterator<Particle> it = particles.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.update();
			if (p.isExpired())
				it.remove();
		}
	}

	public List<Particle> getParticles() {
		return particles;
	}

	public boolean isEmpty() {
		return particles.isEmpty();
	}
}
