package entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the player's discrete lives and damage processing. The current game
 * does not model a separate health pool; one accepted hit consumes one life.
 *
 * A timed shield can absorb incoming damage, and a timed slow can affect ship
 * movement. These statuses are held here so damage and effect timing have one
 * owner and the Gameplay HUD can read their remaining time.
 *
 * Every change is announced to the registered
 * {@link PlayerStatusListener}s, so the Gameplay HUD and Visual Effect
 * System can react without polling.
 */
public class PlayerStatus {

	/** Duration of a shield, in milliseconds. */
	private static final int SHIELD_DURATION = 3000;
	/** Duration of the slow debuff, in milliseconds. */
	private static final int SLOW_DURATION = 2000;

	/** Result of an attempt to apply the slow debuff. */
	public enum SlowApplicationResult {
		/** A new slow debuff was applied. */
		STARTED,
		/** The active slow debuff's duration was restarted. */
		REFRESHED,
		/** The active shield prevented the slow debuff. */
		BLOCKED_BY_SHIELD,
		/** The player has no remaining lives. */
		PLAYER_DEAD
	}

	/** Current number of lives. */
	private int lives;
	/** Maximum number of lives for the selected game setup. */
	private final int maxLives;
	/**
	 * Point in time the shield lasts until. The shield is active only while
	 * the current time is before it, so a stale value from an expired shield
	 * is harmless. Never compare this field against zero to decide whether a
	 * shield is up; call {@link #isShieldActive()}.
	 */
	private long shieldExpiryTime;
	/**
	 * Whether the selected ship may hold a shield. Ships that cannot are
	 * refused by {@link #activateShield()}. This covers only the timed shield
	 * held here; the Energy Shield item lives on {@link Ship} and is guarded
	 * separately in {@link Ship#setShieldActive(boolean)}.
	 */
	private final boolean shieldCapable;
	/**
	 * Whether a shield was up the last time the clock was looked at. Keeps
	 * {@link PlayerStatusEvent.Type#SHIELD_ENDED} to one event per shield,
	 * since nothing counts the shield down frame by frame.
	 */
	private boolean shieldAnnounced;
	/** Point in time the current slow debuff expires. */
	private long slowExpiryTime;
	/** Ship whose movement is currently affected by the slow debuff. */
	private Ship slowedShip;
	/** Whether the ship's current speed was following its base speed. */
	private boolean slowFollowsBaseSpeed;
	/** Last base speed observed while the slow debuff was active. */
	private int slowTrackedBaseSpeed;
	/** Last current speed observed while the slow debuff was active. */
	private int slowTrackedCurrentSpeed;
	/** Last explicit base-stat restore observed for the slowed ship. */
	private long slowTrackedBaseStatsRestoreVersion;
	/** Listeners from the other systems. */
	private final List<PlayerStatusListener> listeners =
			new ArrayList<PlayerStatusListener>();

	/**
	 * Creates a valid player-life state for a ship that cannot hold a
	 * shield. A ship has to be declared shield-capable explicitly with
	 * {@link #PlayerStatus(int, int, boolean)}, so a caller that forgets the
	 * capability never hands every ship a shield.
	 *
	 * @param initialLives
	 *            Initial number of lives.
	 * @param maxLives
	 *            Maximum number of lives.
	 */
	public PlayerStatus(final int initialLives, final int maxLives) {
		this(initialLives, maxLives, false);
	}

	/**
	 * Creates a valid player-life state for a ship that may or may not be
	 * allowed to hold a shield.
	 *
	 * @param initialLives
	 *            Initial number of lives.
	 * @param maxLives
	 *            Maximum number of lives.
	 * @param shieldCapable
	 *            Whether the selected ship may hold a shield at all.
	 */
	public PlayerStatus(final int initialLives, final int maxLives,
			final boolean shieldCapable) {
		this.shieldCapable = shieldCapable;
		if (maxLives <= 0)
			throw new IllegalArgumentException(
					"Maximum lives must be positive.");
		if (initialLives < 0 || initialLives > maxLives)
			throw new IllegalArgumentException(
					"Initial lives must be between zero and the maximum.");

		this.lives = initialLives;
		this.maxLives = maxLives;
	}

	/**
	 * Registers a listener. Registering the same listener twice has no
	 * effect, so it never receives an event twice.
	 *
	 * @param listener
	 *            Listener to add.
	 */
	public final void addListener(final PlayerStatusListener listener) {
		if (listener != null && !this.listeners.contains(listener))
			this.listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 *
	 * @param listener
	 *            Listener to remove.
	 */
	public final void removeListener(final PlayerStatusListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Sends an event to all listeners.
	 *
	 * @param event
	 *            Event to send.
	 */
	private void publish(final PlayerStatusEvent event) {
		// Copy so that a listener may add or remove one during the call.
		for (PlayerStatusListener listener
				: new ArrayList<PlayerStatusListener>(this.listeners))
			listener.onPlayerStatusEvent(event);
	}

	/**
	 * Updates timed player effects. The shield is held as an expiry time rather
	 * than counted down frame by frame, so something has to look at the clock
	 * for its end to be announced; {@link screen.GameScreen} calls this once per
	 * frame.
	 */
	public final void update() {
		announceExpiredShield();
		updateSlowDebuff();
	}

	/**
	 * Applies or refreshes a two-second, 50-percent movement slow. An active
	 * shield blocks a new slow and also clears an existing one on the next
	 * status update. Successful starts and refreshes are published to the
	 * registered status listeners.
	 *
	 * @param ship
	 *            Player ship to affect.
	 * @return Whether the effect started, refreshed, or was blocked.
	 * @throws IllegalArgumentException
	 *             If {@code ship} is {@code null}.
	 */
	public final SlowApplicationResult applySlow(final Ship ship) {
		if (ship == null)
			throw new IllegalArgumentException("Ship cannot be null.");

		announceExpiredShield();
		updateSlowDebuff();
		if (!isAlive())
			return SlowApplicationResult.PLAYER_DEAD;
		if (isShieldActive())
			return SlowApplicationResult.BLOCKED_BY_SHIELD;

		boolean refreshed = isSlowActive() && this.slowedShip == ship;
		if (this.slowedShip != null && this.slowedShip != ship)
			endSlowDebuff();
		if (!refreshed) {
			this.slowedShip = ship;
			this.slowTrackedBaseSpeed = ship.getBaseSpeed();
			this.slowTrackedCurrentSpeed = ship.getSpeed();
			this.slowTrackedBaseStatsRestoreVersion =
					ship.getBaseStatsRestoreVersion();
			this.slowFollowsBaseSpeed =
					ship.getSpeed() == this.slowTrackedBaseSpeed;
			ship.setSlowDebuffActive(true);
		}
		this.slowExpiryTime = currentTimeMillis() + SLOW_DURATION;
		publish(refreshed ? PlayerStatusEvent.slowRefreshed(this)
				: PlayerStatusEvent.slowStarted(this));
		return refreshed ? SlowApplicationResult.REFRESHED
				: SlowApplicationResult.STARTED;
	}

	/** @return Whether the slow debuff is currently active. */
	public final boolean isSlowActive() {
		return isSlowActiveAt(currentTimeMillis());
	}

	/**
	 * Reports how much slow duration is left.
	 *
	 * @return Milliseconds remaining, or zero when inactive.
	 */
	public final int getSlowRemainingMillis() {
		return getSlowRemainingMillisAt(currentTimeMillis());
	}

	/** Checks the logical slow state without mutating it or publishing events. */
	final boolean isSlowActiveAt(final long now) {
		return this.slowedShip != null && isAlive()
				&& !isShieldActiveAt(now)
				&& now < this.slowExpiryTime;
	}

	/** Returns remaining slow time using the same instant as the state check. */
	final int getSlowRemainingMillisAt(final long now) {
		if (!isSlowActiveAt(now))
			return 0;
		return (int) (this.slowExpiryTime - now);
	}

	/** Ends the active movement-only slow, if one is present. */
	public final void clearSlowDebuff() {
		endSlowDebuff();
	}

	/** @return Full duration of the slow debuff, in milliseconds. */
	public static int getSlowDuration() {
		return SLOW_DURATION;
	}

	/** Synchronizes slow state with life, shield, base speed, and expiry. */
	private void updateSlowDebuff() {
		if (this.slowedShip == null)
			return;

		if (!isAlive() || isShieldActive()) {
			endSlowDebuff();
			return;
		}

		if (this.slowedShip.getBaseStatsRestoreVersion()
				!= this.slowTrackedBaseStatsRestoreVersion) {
			this.slowTrackedBaseStatsRestoreVersion =
					this.slowedShip.getBaseStatsRestoreVersion();
			this.slowTrackedBaseSpeed = this.slowedShip.getBaseSpeed();
			this.slowTrackedCurrentSpeed = this.slowedShip.getSpeed();
			this.slowFollowsBaseSpeed = true;
			this.slowedShip.resetSlowMovementRemainders();
		} else if (this.slowFollowsBaseSpeed) {
			if (this.slowedShip.getSpeed()
					!= this.slowTrackedCurrentSpeed) {
				// A separate current-stat change takes precedence over the base.
				this.slowFollowsBaseSpeed = false;
				this.slowTrackedCurrentSpeed = this.slowedShip.getSpeed();
				this.slowedShip.resetSlowMovementRemainders();
			} else if (this.slowedShip.getBaseSpeed()
					!= this.slowTrackedBaseSpeed) {
				this.slowTrackedBaseSpeed = this.slowedShip.getBaseSpeed();
				this.slowedShip.setSpeed(this.slowTrackedBaseSpeed);
				this.slowTrackedCurrentSpeed = this.slowTrackedBaseSpeed;
				this.slowedShip.resetSlowMovementRemainders();
			}
		} else if (this.slowedShip.getSpeed()
				!= this.slowTrackedCurrentSpeed) {
			this.slowTrackedCurrentSpeed = this.slowedShip.getSpeed();
			this.slowedShip.resetSlowMovementRemainders();
		}

		if (currentTimeMillis() >= this.slowExpiryTime)
			endSlowDebuff();
	}

	/** Ends the movement-only modifier without touching the firing interval. */
	private void endSlowDebuff() {
		boolean wasActive = this.slowedShip != null;
		if (wasActive) {
			if (this.slowFollowsBaseSpeed
					&& this.slowedShip.getSpeed() == this.slowTrackedBaseSpeed)
				this.slowedShip.setSpeed(this.slowedShip.getBaseSpeed());
			this.slowedShip.setSlowDebuffActive(false);
		}
		this.slowedShip = null;
		this.slowExpiryTime = 0;
		this.slowFollowsBaseSpeed = false;
		if (wasActive)
			publish(PlayerStatusEvent.slowEnded(this));
	}

	/**
	 * Sends {@link PlayerStatusEvent.Type#SHIELD_ENDED} once, the first time
	 * the clock is read after the shield has run out. Called before anything
	 * that depends on the shield being up, so the end of a shield is never
	 * announced after the hit that got through it.
	 */
	private void announceExpiredShield() {
		if (!this.shieldAnnounced || isShieldActive())
			return;

		// Cleared before publishing, so a listener that calls back in does
		// not see the same expiry a second time.
		this.shieldAnnounced = false;
		publish(PlayerStatusEvent.shieldEnded(this));
	}

	/**
	 * Processes incoming damage and reduces lives. An active shield absorbs
	 * the hit, so no life is lost and the caller is told that no damage was
	 * applied. Every damage source reaches this method, so the shield covers
	 * enemy bullets and boss bullets alike.
	 *
	 * @param damage
	 *            Amount of damage to apply.
	 * @return Whether damage was applied.
	 */
	/**
	 * Announces a hit that a protection outside this class absorbed, such as
	 * the Energy Shield item held by {@link Ship}. No life is taken; this
	 * only tells the listeners, so that a blocked hit is reported the same
	 * way whichever shield blocked it.
	 *
	 * @param damage
	 *            Amount of damage that was absorbed.
	 */
	public final void announceDamageBlocked(final int damage) {
		if (damage <= 0)
			return;
		publish(PlayerStatusEvent.damageBlocked(this, damage));
	}

	public final boolean takeDamage(final int damage) {
		if (damage <= 0 || !isAlive())
			return false;

		announceExpiredShield();
		if (isShieldActive()) {
			publish(PlayerStatusEvent.damageBlocked(this, damage));
			return false;
		}

		this.lives = Math.max(0, this.lives - damage);
		publish(PlayerStatusEvent.damageTaken(this, damage));
		if (!isAlive())
			updateSlowDebuff();
		return true;
	}

	/**
	 * Grants one life when the player is alive and below the configured maximum.
	 * A player with no lives cannot be revived; the game-over flow has already
	 * ended level progression at that point.
	 *
	 * @return Whether a life was added.
	 */
	public final boolean gainLife() {
		if (!isAlive() || this.lives >= this.maxLives)
			return false;

		this.lives++;
		publish(PlayerStatusEvent.lifeGained(this));
		return true;
	}

	/**
	 * Grants a shield, or refreshes the one already active. The player takes
	 * no damage until it expires. A refresh is announced as its own kind of
	 * event, so the HUD can restart a gauge rather than start a second one.
	 */
	public final boolean activateShield() {
		if (!this.shieldCapable)
			return false;

		announceExpiredShield();

		boolean refreshed = isShieldActive();
		this.shieldExpiryTime = currentTimeMillis() + SHIELD_DURATION;
		this.shieldAnnounced = true;
		publish(refreshed ? PlayerStatusEvent.shieldRefreshed(this)
				: PlayerStatusEvent.shieldStarted(this));
		return true;
	}

	/**
	 * Reports whether the selected ship may hold a shield at all.
	 *
	 * @return True if a shield can be raised.
	 */
	public final boolean isShieldCapable() {
		return this.shieldCapable;
	}

	/**
	 * Reads the clock the shield is measured against. Tests override this to
	 * reach the expiry boundary without waiting. A pause feature would also
	 * hook in here, since the wall clock keeps running while the game is
	 * paused.
	 *
	 * @return Current time in milliseconds.
	 */
	long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	/** Ends an active shield immediately, if there is one. */
	public final void clearShield() {
		boolean wasActive = isShieldActive();

		this.shieldExpiryTime = 0;
		this.shieldAnnounced = false;
		if (wasActive)
			publish(PlayerStatusEvent.shieldEnded(this));
	}

	/** @return Whether a shield is currently absorbing damage. */
	public final boolean isShieldActive() {
		return isShieldActiveAt(currentTimeMillis());
	}

	/** Checks shield activity at a caller-supplied instant without side effects. */
	final boolean isShieldActiveAt(final long now) {
		return this.shieldExpiryTime > now;
	}

	/**
	 * Reports how much of the shield is left, for HUD gauges.
	 *
	 * @return Milliseconds left on the active shield, or zero when no shield
	 *         is active.
	 */
	public final int getShieldRemainingMillis() {
		long remaining = this.shieldExpiryTime - currentTimeMillis();
		return remaining > 0 ? (int) remaining : 0;
	}

	/** @return Full duration of a shield, in milliseconds, for HUD gauges. */
	public static int getShieldDuration() {
		return SHIELD_DURATION;
	}

	/** @return Current number of lives. */
	public final int getLives() {
		return this.lives;
	}

	/** @return Maximum number of lives. */
	public final int getMaxLives() {
		return this.maxLives;
	}

	/** @return Whether at least one life remains. */
	public final boolean isAlive() {
		return this.lives > 0;
	}
}
