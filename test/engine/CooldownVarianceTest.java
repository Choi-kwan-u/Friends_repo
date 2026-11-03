package engine;

import java.lang.reflect.Field;

/** Deterministic checks for variable cooldown duration boundaries. */
public final class CooldownVarianceTest {

	private CooldownVarianceTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static void checkDuration(final int milliseconds,
			final int variance, final double randomValue, final int expected) {
		check(Cooldown.calculateDuration(milliseconds, variance, randomValue)
				== expected, "Unexpected duration at sample " + randomValue);
	}

	private static int getDuration(final Cooldown cooldown) throws Exception {
		Field duration = Cooldown.class.getDeclaredField("duration");
		duration.setAccessible(true);
		return duration.getInt(cooldown);
	}

	private static long getStartTime(final Cooldown cooldown) throws Exception {
		Field time = Cooldown.class.getDeclaredField("time");
		time.setAccessible(true);
		return time.getLong(cooldown);
	}

	private static void setStartTime(final Cooldown cooldown,
			final long startTime) throws Exception {
		Field time = Cooldown.class.getDeclaredField("time");
		time.setAccessible(true);
		time.setLong(cooldown, startTime);
	}

	private static void checkInvalidRange(final int milliseconds,
			final int variance) {
		try {
			new Cooldown(milliseconds, variance);
			throw new AssertionError("Invalid cooldown range was accepted.");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	public static void main(final String[] args) throws Exception {
		checkDuration(1000, 200, 0.0, 800);
		checkDuration(1000, 200, 0.5, 1000);
		checkDuration(1000, 200, 1.0, 1200);
		checkDuration(2000, 400, 0.0, 1600);
		checkDuration(2000, 400, 1.0, 2400);
		checkDuration(750, 0, 0.0, 750);
		checkDuration(750, 0, 0.5, 750);
		checkDuration(750, 0, 1.0, 750);

		checkInvalidRange(0, 0);
		checkInvalidRange(-1, 0);
		checkInvalidRange(1000, -1);
		checkInvalidRange(1000, 1000);
		checkInvalidRange(Integer.MAX_VALUE, 1);

		Cooldown cooldown = new Cooldown(1000, 200);
		setStartTime(cooldown, 1L);
		cooldown.reset();
		long startTime = getStartTime(cooldown);
		int duration = getDuration(cooldown);
		check(startTime > 1L, "Reset updates the cooldown start time");
		check(duration >= 800 && duration <= 1200,
				"Reset selects a duration inside the configured inclusive range");

		Cooldown fixedCooldown = new Cooldown(750);
		fixedCooldown.reset();
		check(getDuration(fixedCooldown) == 750,
				"Reset preserves a fixed cooldown duration");

		System.out.println("Cooldown variance checks passed.");
	}
}
