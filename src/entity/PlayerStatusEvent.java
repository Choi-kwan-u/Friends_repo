package entity;

import event.GameplayEvent;

/**
 * Player status event sent to other systems. Every event carries the lives
 * left, the maximum lives and the shield time remaining, so a listener can
 * redraw the whole player part of the HUD from one event without reading the
 * status back.
 *
 * Player Status &amp; Buff/Debuff 1.
 */
public final class PlayerStatusEvent implements GameplayEvent {

	/** Kinds of player status events. */
	public enum Type {
		/** A hit got through and cost the player at least one life. */
		DAMAGE_TAKEN("PlayerDamaged"),
		/** A hit was absorbed by the shield, so no life was lost. */
		DAMAGE_BLOCKED("PlayerDamageBlocked"),
		/** The player gained a life. */
		LIFE_GAINED("PlayerLifeGained"),
		/** A shield was raised while none was up. */
		SHIELD_STARTED("PlayerShieldStarted"),
		/** A shield was granted again while one was already up, which
		 * restarts its full duration. */
		SHIELD_REFRESHED("PlayerShieldRefreshed"),
		/** The shield ran out, or was ended by hand. */
		SHIELD_ENDED("PlayerShieldEnded"),
		/** A movement slow started. */
		SLOW_STARTED("PlayerSlowStarted"),
		/** An active movement slow was refreshed. */
		SLOW_REFRESHED("PlayerSlowRefreshed"),
		/** A movement slow ended or was cleared. */
		SLOW_ENDED("PlayerSlowEnded");

		/** Name of the event as written in the log. */
		private final String eventName;

		Type(final String eventName) {
			this.eventName = eventName;
		}

		public String getEventName() {
			return this.eventName;
		}
	}

	/** Kind of event. */
	private final Type type;
	/** Lives left after the event. */
	private final int lives;
	/** Maximum number of lives. */
	private final int maxLives;
	/** Shield time left after the event, in milliseconds. */
	private final int shieldRemainingMillis;
	/** Damage carried by the hit, for DAMAGE_TAKEN and DAMAGE_BLOCKED. */
	private final int damageAmount;
	/** Whether a slow was active at this event's snapshot. */
	private final boolean slowActive;
	/** Slow time left at this event's snapshot, in milliseconds. */
	private final int slowRemainingMillis;

	private PlayerStatusEvent(final Type type, final PlayerStatus status,
			final int damageAmount) {
		this.type = type;
		this.lives = status.getLives();
		this.maxLives = status.getMaxLives();
		this.shieldRemainingMillis = status.getShieldRemainingMillis();
		this.damageAmount = damageAmount;
		long now = status.currentTimeMillis();
		this.slowActive = type != Type.SLOW_ENDED
				&& status.isSlowActiveAt(now);
		this.slowRemainingMillis = this.slowActive
				? status.getSlowRemainingMillisAt(now) : 0;
	}

	static PlayerStatusEvent damageTaken(final PlayerStatus status,
			final int damage) {
		return new PlayerStatusEvent(Type.DAMAGE_TAKEN, status, damage);
	}

	static PlayerStatusEvent damageBlocked(final PlayerStatus status,
			final int damage) {
		return new PlayerStatusEvent(Type.DAMAGE_BLOCKED, status, damage);
	}

	static PlayerStatusEvent lifeGained(final PlayerStatus status) {
		return new PlayerStatusEvent(Type.LIFE_GAINED, status, 0);
	}

	static PlayerStatusEvent shieldStarted(final PlayerStatus status) {
		return new PlayerStatusEvent(Type.SHIELD_STARTED, status, 0);
	}

	static PlayerStatusEvent shieldRefreshed(final PlayerStatus status) {
		return new PlayerStatusEvent(Type.SHIELD_REFRESHED, status, 0);
	}

	static PlayerStatusEvent shieldEnded(final PlayerStatus status) {
		return new PlayerStatusEvent(Type.SHIELD_ENDED, status, 0);
	}

	static PlayerStatusEvent slowStarted(final PlayerStatus status) {
		return slowEvent(Type.SLOW_STARTED, status);
	}

	static PlayerStatusEvent slowRefreshed(final PlayerStatus status) {
		return slowEvent(Type.SLOW_REFRESHED, status);
	}

	static PlayerStatusEvent slowEnded(final PlayerStatus status) {
		return new PlayerStatusEvent(Type.SLOW_ENDED, status, 0);
	}

	private static PlayerStatusEvent slowEvent(final Type type,
			final PlayerStatus status) {
		return new PlayerStatusEvent(type, status, 0);
	}

	public Type getType() {
		return this.type;
	}

	/** @return Lives left after the event. */
	public int getLives() {
		return this.lives;
	}

	/** @return Maximum number of lives, for a lives gauge. */
	public int getMaxLives() {
		return this.maxLives;
	}

	/**
	 * @return Shield time left after the event, in milliseconds, or zero when
	 *         no shield is up. Compare against
	 *         {@link PlayerStatus#getShieldDuration()} for a gauge.
	 */
	public int getShieldRemainingMillis() {
		return this.shieldRemainingMillis;
	}

	/**
	 * @return Damage carried by the hit, for {@link Type#DAMAGE_TAKEN} and
	 *         {@link Type#DAMAGE_BLOCKED}, and zero for every other kind.
	 */
	public int getDamageAmount() {
		return this.damageAmount;
	}

	/**
	 * @return Whether slow was active when this event was created, regardless
	 *         of the event type. Always false for {@link Type#SLOW_ENDED}.
	 */
	public boolean isSlowActive() {
		return this.slowActive;
	}

	/**
	 * @return Slow time left at this event's snapshot, in milliseconds, or zero
	 *         when slow was inactive or had ended.
	 */
	public int getSlowRemainingMillis() {
		return this.slowRemainingMillis;
	}

	/**
	 * @return Whether the player still has a life after the event. False only
	 *         on the {@link Type#DAMAGE_TAKEN} that spent the last one.
	 */
	public boolean isAlive() {
		return this.lives > 0;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(this.type.getEventName())
				.append(" [lives ").append(this.lives).append('/')
				.append(this.maxLives);
		if (this.type == Type.DAMAGE_TAKEN
				|| this.type == Type.DAMAGE_BLOCKED)
			text.append(", damage ").append(this.damageAmount);
		if (this.shieldRemainingMillis > 0)
			text.append(", shield ").append(this.shieldRemainingMillis)
					.append("ms");
		if (this.slowActive)
			text.append(", slow ").append(this.slowRemainingMillis)
				.append("ms");
		return text.append(']').toString();
	}
}
