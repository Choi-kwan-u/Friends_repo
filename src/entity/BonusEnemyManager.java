package entity;

import java.util.logging.Logger;

import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

/**
 * Manages the bonus enemy ship that sometimes crosses the top of the screen.
 */
public class BonusEnemyManager {

	/** Minimum time between bonus ship appearances. */
	private static final int BONUS_SHIP_INTERVAL = 20000;
	/** Maximum variance in the time between bonus ship appearances. */
	private static final int BONUS_SHIP_VARIANCE = 10000;
	/** Time until bonus ship explosion disappears. */
	private static final int BONUS_SHIP_EXPLOSION = 500;
	/** Horizontal speed of the bonus ship. */
	private static final int BONUS_SHIP_SPEED = 2;

	/** Bonus enemy ship currently on screen, null if there is none. */
	private EnemyShip bonusShip;
	/** Time until the next bonus ship appears. */
	private Cooldown spawnCooldown;
	/** Time until the bonus ship explosion disappears. */
	private Cooldown explosionCooldown;
	/** Application logger. */
	private Logger logger;

	/**
	 * Constructor, the first bonus ship appears after 10-30 seconds.
	 */
	public BonusEnemyManager() {
		this.spawnCooldown = Core.getVariableCooldown(BONUS_SHIP_INTERVAL,
				BONUS_SHIP_VARIANCE);
		this.spawnCooldown.reset();
		this.explosionCooldown = Core.getCooldown(BONUS_SHIP_EXPLOSION);
		this.logger = Core.getLogger();
	}

	/**
	 * Moves the bonus ship, makes it appear or removes it when it escapes or
	 * its explosion is over. Called once per frame.
	 *
	 * @param screenWidth
	 *            Width of the screen, the ship escapes past it.
	 */
	public final void update(final int screenWidth) {
		if (this.bonusShip != null) {
			if (!this.bonusShip.isDestroyed())
				this.bonusShip.move(BONUS_SHIP_SPEED, 0);
			else if (this.explosionCooldown.checkFinished())
				this.bonusShip = null;
		}
		if (this.bonusShip == null && this.spawnCooldown.checkFinished()) {
			this.bonusShip = new EnemyShip();
			AudioManager.playSFX("audio/sfx/bonus_appear.wav");
			this.spawnCooldown.reset();
			this.logger.info("A special ship appears");
		}
		if (this.bonusShip != null
				&& this.bonusShip.getPositionX() > screenWidth) {
			this.bonusShip = null;
			this.logger.info("The special ship has escaped");
		}
	}

	/**
	 * Destroys the bonus ship after it was hit by a bullet.
	 *
	 * @return Points earned, 0 if there was no bonus ship to destroy.
	 */
	public final int destroy() {
		if (!isActive())
			return 0;
		AudioManager.playSFX("audio/sfx/bonus_explosion.wav");
		this.bonusShip.destroy();
		this.explosionCooldown.reset();
		return this.bonusShip.getPointValue();
	}

	/**
	 * Getter for the bonus ship, used to draw it and check collisions.
	 *
	 * @return Bonus ship, null if there is none on screen.
	 */
	public final EnemyShip getShip() {
		return this.bonusShip;
	}

	/**
	 * Checks if a bonus ship is on screen and can still be hit, for the HUD.
	 *
	 * @return True if a bonus ship is flying and not destroyed.
	 */
	public final boolean isActive() {
		return this.bonusShip != null && !this.bonusShip.isDestroyed();
	}
}
