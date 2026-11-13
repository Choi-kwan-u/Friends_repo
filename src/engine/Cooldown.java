package engine;

/**
 * Imposes a cooldown period between two actions.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Cooldown {

	/** Cooldown duration. */
	private int milliseconds;
	/** Maximum difference between durations. */
	private int variance;
	/** Duration of this run, varies between runs if variance > 0. */
	private int duration;
	/** Beginning time. */
	private long time;
	/** Frozen clock instant, or zero while running. */
	private long pausedAt;

	/**
	 * Constructor, established the time until the action can be performed
	 * again.
	 *
	 * @param milliseconds
	 *            Time until cooldown period is finished.
	 * @throws IllegalArgumentException
	 *             If the cooldown duration is negative.
	 */
	protected Cooldown(final int milliseconds) {
		if (milliseconds < 0)
			throw new IllegalArgumentException(
					"Cooldown duration must not be negative.");
		this.milliseconds = milliseconds;
		this.variance = 0;
		this.duration = milliseconds;
		this.time = 0;
	}

	/**
	 * Constructor, established the time until the action can be performed
	 * again, with a variation of +/- variance.
	 *
	 * @param milliseconds
	 *            Base time until cooldown period is finished.
	 * @param variance
	 *            Maximum variation on either side of the base duration.
	 * @throws IllegalArgumentException
	 *             If the duration is negative, the variance is negative,
	 *             not less than the duration, or would exceed the integer range.
	 */
	protected Cooldown(final int milliseconds, final int variance) {
		if (milliseconds < 0)
			throw new IllegalArgumentException(
					"Cooldown duration must not be negative.");
		if (variance < 0 || variance >= milliseconds
				|| variance > Integer.MAX_VALUE - milliseconds)
			throw new IllegalArgumentException(
					"Cooldown variance must be non-negative, less than its duration, "
							+ "and within the integer duration range.");
		this.milliseconds = milliseconds;
		this.variance = variance;
		this.duration = milliseconds;
		this.time = 0;
	}

	/**
	 * Checks if the cooldown is finished.
	 *
	 * @return Cooldown state.
	 */
	public final boolean checkFinished() {
		if ((this.time == 0)
				|| this.time + this.duration < currentTimeMillis())
			return true;
		return false;
	}

	/**
	 * Changes a fixed cooldown's duration without restarting its timer. The new
	 * duration applies from the original start time, so shortening can make an
	 * active cooldown finish immediately and extending can keep it active longer.
	 * The change is not measured from the time this method is called.
	 *
	 * @param milliseconds
	 *            New non-negative cooldown duration in milliseconds.
	 * @throws IllegalArgumentException
	 *             If the duration is negative.
	 * @throws IllegalStateException
	 *             If this cooldown has variable duration.
	 */
	public final void setDuration(final int milliseconds) {
		if (milliseconds < 0)
			throw new IllegalArgumentException(
					"Cooldown duration must not be negative.");
		if (this.variance != 0)
			throw new IllegalStateException(
					"Cannot change a variable cooldown duration.");

		this.milliseconds = milliseconds;
		this.duration = milliseconds;
	}

	/**
	 * Freezes or resumes this timer without resetting its remaining wait or
	 * rerolling variance. Repeated calls are harmless for shared timers.
	 */
	public final void setPaused(final boolean paused) {
		if (paused && this.pausedAt == 0) {
			this.pausedAt = System.currentTimeMillis();
		} else if (!paused && this.pausedAt != 0) {
			excludePause(Math.max(0, System.currentTimeMillis() - this.pausedAt));
			this.pausedAt = 0;
		}
	}

	private long currentTimeMillis() {
		return this.pausedAt == 0 ? System.currentTimeMillis() : this.pausedAt;
	}

	/** Excludes a completed gameplay pause without rerolling a variable duration. */
	public final void excludePause(final long pausedMillis) {
		if (pausedMillis < 0)
			throw new IllegalArgumentException("Pause must be non-negative.");
		if (this.time != 0)
			this.time += pausedMillis;
	}

	/**
	 * Restarts the cooldown.
	 */
	public final void reset() {
		this.time = currentTimeMillis();
		if (this.variance != 0)
			this.duration = calculateDuration(this.milliseconds, this.variance,
					Math.random());
	}

	/**
	 * Calculates a duration within the inclusive base +/- variance range.
	 *
	 * @param milliseconds Base duration.
	 * @param variance Maximum variation on either side of the base duration.
	 * @param randomValue Random sample from 0.0 through 1.0, inclusive.
	 * @return A duration from milliseconds - variance through
	 *         milliseconds + variance.
	 */
	static int calculateDuration(final int milliseconds, final int variance,
			final double randomValue) {
		long range = 2L * variance + 1L;
		long offset = (long) (randomValue * range);
		if (offset >= range)
			offset = range - 1L;
		return (int) ((long) milliseconds - variance + offset);
	}
}
