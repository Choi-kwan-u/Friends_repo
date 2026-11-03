package entity;

/**
 * Standalone checks that the bonus shield of R5 lasts the three seconds the
 * requirement promises, and that it ends on its own rather than on the first
 * hit.
 *
 * {@link PlayerStatusShieldTest} already covers how the shield behaves, but
 * it measures everything against
 * {@link PlayerStatus#getShieldDuration()}, so it keeps passing whatever that
 * duration is. The figure itself is written out here, in the one place where
 * changing it has to be a deliberate decision.
 */
public final class PlayerStatusShieldDurationTest {

	/** The three seconds R5 commits to, in milliseconds. */
	private static final int THREE_SECONDS = 3000;
	/** Gap between the hits taken while the shield is up. */
	private static final int HIT_INTERVAL = 100;

	/**
	 * Player status driven by a clock the test moves by hand, so the expiry
	 * boundary is reached without waiting three real seconds.
	 */
	private static final class FakeClockStatus extends PlayerStatus {

		/** Current value of the fake clock, in milliseconds. */
		private long now;

		private FakeClockStatus() {
			super(3, 3, true);
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

	private PlayerStatusShieldDurationTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkTheAdvertisedDurationIsThreeSeconds();
		checkTheShieldLastsThreeSeconds();
		checkTheRemainingTimeCountsDownFromThreeSeconds();
		checkTheShieldOutlastsEveryHitInsideThreeSeconds();

		System.out.println("Player shield duration checks passed.");
	}

	/**
	 * The duration the HUD and the rest of the game read. R5 names three
	 * seconds, so this is the number the requirement is judged on.
	 */
	private static void checkTheAdvertisedDurationIsThreeSeconds() {
		check(PlayerStatus.getShieldDuration() == THREE_SECONDS,
				"R5 promises a 3 second shield, but the game offers "
						+ PlayerStatus.getShieldDuration() + " ms");
	}

	/**
	 * The shield is up for three seconds and gone afterwards. Both sides of
	 * the boundary are checked, so a shield that is too short and one that
	 * never ends are both caught.
	 */
	private static void checkTheShieldLastsThreeSeconds() {
		FakeClockStatus status = new FakeClockStatus();
		check(status.activateShield(), "A shield-capable ship raises a shield");

		status.advance(THREE_SECONDS - 1);
		check(status.isShieldActive(),
				"The shield is still up one millisecond before 3 seconds");
		check(!status.takeDamage(1),
				"It still absorbs damage one millisecond before 3 seconds");

		status.advance(1);
		check(!status.isShieldActive(),
				"The shield is gone once 3 seconds have passed");
		check(status.takeDamage(1),
				"Damage applies again once 3 seconds have passed");
		check(status.getLives() == 2,
				"The hit after 3 seconds costs a life");
	}

	/** The gauge the HUD reads starts at three seconds and counts down. */
	private static void checkTheRemainingTimeCountsDownFromThreeSeconds() {
		FakeClockStatus status = new FakeClockStatus();
		status.activateShield();

		check(status.getShieldRemainingMillis() == THREE_SECONDS,
				"A new shield has 3 seconds left, not "
						+ status.getShieldRemainingMillis() + " ms");

		status.advance(1000);
		check(status.getShieldRemainingMillis() == THREE_SECONDS - 1000,
				"A second into the shield, two of the three are left");

		status.advance(2000);
		check(status.getShieldRemainingMillis() == 0,
				"Nothing is left of the shield after 3 seconds");
	}

	/**
	 * R5 asks for a shield that expires on its own after three seconds
	 * rather than on the first hit, so it has to survive a whole stream of
	 * hits spread across those three seconds.
	 */
	private static void checkTheShieldOutlastsEveryHitInsideThreeSeconds() {
		FakeClockStatus status = new FakeClockStatus();
		status.activateShield();

		int hits = 0;
		for (int elapsed = 0; elapsed < THREE_SECONDS;
				elapsed += HIT_INTERVAL) {
			check(status.isShieldActive(),
					"The shield is still up " + elapsed
							+ " ms into its 3 seconds");
			check(!status.takeDamage(1),
					"The hit at " + elapsed + " ms is absorbed");
			hits++;
			status.advance(HIT_INTERVAL);
		}

		check(hits == THREE_SECONDS / HIT_INTERVAL,
				"Three seconds of hits were taken, one every " + HIT_INTERVAL
						+ " ms");
		check(status.getLives() == 3,
				"None of the " + hits + " absorbed hits cost a life");
		check(!status.isShieldActive(),
				"The shield ended on its 3 second timer, not on a hit");
	}
}
