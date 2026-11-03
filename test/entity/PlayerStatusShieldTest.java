package entity;

/** Deterministic checks for the timed player shield. */
public final class PlayerStatusShieldTest {

	/**
	 * Player status driven by a clock the test moves by hand, so the expiry
	 * boundary is reached without waiting for real time.
	 */
	private static final class FakeClockStatus extends PlayerStatus {

		/** Current value of the fake clock, in milliseconds. */
		private long now;

		private FakeClockStatus(final int initialLives, final int maxLives) {
			super(initialLives, maxLives, true);
			this.now = 1000000L;
		}

		@Override
		long currentTimeMillis() {
			return this.now;
		}

		/**
		 * Moves the fake clock forward.
		 *
		 * @param milliseconds
		 *            Amount of time to advance.
		 */
		private void advance(final long milliseconds) {
			this.now += milliseconds;
		}
	}

	private PlayerStatusShieldTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkDamageWithoutShield();
		checkShieldAbsorbsDamage();
		checkRemainingTime();
		checkExpiry();
		checkRefresh();
		checkClearShield();
		checkInputValidation();
		checkRealClockShield();

		System.out.println("Player shield checks passed.");
	}

	/** Without a shield, a hit still costs a life. */
	private static void checkDamageWithoutShield() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		check(!status.isShieldActive(), "A new status has no shield");
		check(status.takeDamage(1), "Damage applies without a shield");
		check(status.getLives() == 2, "A hit costs one life");
	}

	/** An active shield absorbs every damage source. */
	private static void checkShieldAbsorbsDamage() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		status.activateShield();
		check(status.isShieldActive(), "The shield is active once granted");

		for (int i = 0; i < 3; i++)
			check(!status.takeDamage(1),
					"The shield reports that no damage was applied");
		check(status.getLives() == 3, "The shield keeps every life");

		// Boss bullets carry more than one point of damage.
		check(!status.takeDamage(50), "The shield absorbs boss damage too");
		check(status.getLives() == 3, "Boss damage costs no life either");
	}

	/** The remaining time counts down from the advertised duration. */
	private static void checkRemainingTime() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		check(status.getShieldRemainingMillis() == 0,
				"No shield means no remaining time");

		status.activateShield();
		check(status.getShieldRemainingMillis()
				== PlayerStatus.getShieldDuration(),
				"A new shield reports its full duration");

		status.advance(1000);
		check(status.getShieldRemainingMillis()
				== PlayerStatus.getShieldDuration() - 1000,
				"Remaining time follows the clock");
	}

	/** The shield lasts its full duration, then stops absorbing. */
	private static void checkExpiry() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.activateShield();

		// Still up on the last millisecond before the expiry.
		status.advance(PlayerStatus.getShieldDuration() - 1);
		check(status.isShieldActive(),
				"The shield lasts until its final millisecond");
		check(!status.takeDamage(1), "It still absorbs on that millisecond");

		status.advance(1);
		check(!status.isShieldActive(),
				"The shield is gone once its duration has passed");
		check(status.getShieldRemainingMillis() == 0,
				"An expired shield reports no remaining time");
		check(status.takeDamage(1), "Damage applies again after expiry");
		check(status.getLives() == 2, "The hit after expiry costs a life");
	}

	/** Re-activating restarts the full duration. */
	private static void checkRefresh() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.activateShield();
		status.advance(PlayerStatus.getShieldDuration() - 10);

		status.activateShield();
		check(status.getShieldRemainingMillis()
				== PlayerStatus.getShieldDuration(),
				"Re-activating restarts the full duration");

		// The refreshed shield outlives the original expiry.
		status.advance(100);
		check(status.isShieldActive(),
				"The refreshed shield survives the original expiry");
	}

	/** clearShield ends an active shield at once. */
	private static void checkClearShield() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		status.activateShield();
		status.clearShield();
		check(!status.isShieldActive(), "clearShield ends the shield");
		check(status.takeDamage(1), "Damage applies after clearShield");
		check(status.getLives() == 2, "That hit costs a life");
	}

	/** Invalid damage and a dead player are handled before the shield. */
	private static void checkInputValidation() {
		PlayerStatus status = new FakeClockStatus(1, 3);
		check(!status.takeDamage(0), "Zero damage is ignored");
		check(!status.takeDamage(-1), "Negative damage is ignored");
		check(status.getLives() == 1, "Ignored damage costs no life");

		check(status.takeDamage(5), "Damage beyond the last life applies");
		check(status.getLives() == 0, "Lives never go below zero");
		check(!status.isAlive(), "The player is dead at zero lives");
		check(!status.takeDamage(1), "A dead player takes no more damage");
	}

	/** The real game clock is wired up the same way. */
	private static void checkRealClockShield() {
		PlayerStatus status = new PlayerStatus(3, 3, true);
		status.activateShield();
		int remaining = status.getShieldRemainingMillis();
		check(remaining > 0 && remaining <= PlayerStatus.getShieldDuration(),
				"The real clock gives a shield inside its duration");
		check(!status.takeDamage(1), "The real-clock shield absorbs damage");
		check(status.getLives() == 3, "No life is lost behind it");
	}
}
