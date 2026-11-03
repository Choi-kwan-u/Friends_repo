package records;

import java.util.concurrent.TimeUnit;

/**
 * Tracks active play segments and creates one record when a run finishes.
 */
public final class RunSession {

	/** Unique identifier shared by every level in this run. */
	private final String runId;
	/** Accumulated active duration in nanoseconds. */
	private long activeDurationNanos;
	/** Start time of the current active segment. */
	private long activeSegmentStartNanos;
	/** Whether an active segment is currently being timed. */
	private boolean active;
	/** Completed record, retained so finish is idempotent. */
	private RunRecord completedRecord;

	/**
	 * Creates a session for one complete game run.
	 *
	 * @param runId
	 *            Unique, non-blank run identifier.
	 */
	public RunSession(final String runId) {
		if (runId == null || runId.trim().length() == 0)
			throw new IllegalArgumentException("runId must not be blank.");
		this.runId = runId;
	}

	/**
	 * Starts an active play segment. Repeated start events are ignored.
	 *
	 * @param nowNanos
	 *            Monotonic time, normally from System.nanoTime().
	 */
	public void startActiveSegment(final long nowNanos) {
		if (this.completedRecord == null && !this.active) {
			this.activeSegmentStartNanos = nowNanos;
			this.active = true;
		}
	}

	/**
	 * Stops an active play segment. Repeated stop events are ignored.
	 *
	 * @param nowNanos
	 *            Monotonic time, normally from System.nanoTime().
	 */
	public void stopActiveSegment(final long nowNanos) {
		if (this.completedRecord != null || !this.active)
			return;

		long elapsedNanos = nowNanos - this.activeSegmentStartNanos;
		if (elapsedNanos < 0)
			throw new IllegalArgumentException(
					"nowNanos must not be before the segment start.");
		if (Long.MAX_VALUE - this.activeDurationNanos < elapsedNanos)
			throw new IllegalStateException("Active duration overflow.");

		this.activeDurationNanos += elapsedNanos;
		this.active = false;
	}

	/**
	 * Finishes the run once. Later calls return the original record unchanged.
	 *
	 * @param score
	 *            Final score.
	 * @param enemiesDefeated
	 *            Total enemies defeated.
	 * @param completionTimeMillis
	 *            Completion time in Unix epoch milliseconds.
	 * @return the one completed record for this session.
	 */
	public RunRecord finish(final int score, final int enemiesDefeated,
			final long completionTimeMillis) {
		if (this.completedRecord != null)
			return this.completedRecord;
		if (this.active)
			throw new IllegalStateException(
					"Stop the active segment before finishing the run.");

		this.completedRecord = new RunRecord(this.runId, score,
				enemiesDefeated, TimeUnit.NANOSECONDS.toMillis(
						this.activeDurationNanos), completionTimeMillis);
		return this.completedRecord;
	}

	/** Returns accumulated active time, including a currently active segment. */
	public long getActiveDurationMillis(final long nowNanos) {
		long nanos = this.activeDurationNanos;
		if (this.active && this.completedRecord == null) {
			long elapsed = nowNanos - this.activeSegmentStartNanos;
			if (elapsed > 0 && Long.MAX_VALUE - nanos >= elapsed)
				nanos += elapsed;
		}
		return TimeUnit.NANOSECONDS.toMillis(nanos);
	}

	/** Returns the ID shared by this run's stages. */
	public String getRunId() {
		return this.runId;
	}
}
