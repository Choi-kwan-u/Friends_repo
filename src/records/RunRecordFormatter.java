package records;

import java.util.Locale;

/**
 * Produces a stable, human-readable representation of a completed run.
 */
public final class RunRecordFormatter {

	/** Constructor, not called. */
	private RunRecordFormatter() {
	}

	/**
	 * Formats every field required by feature 3.1.
	 *
	 * @param record
	 *            Record to format.
	 * @return a multi-line summary.
	 */
	public static String format(final RunRecord record) {
		if (record == null)
			throw new IllegalArgumentException("record must not be null.");

		String lineSeparator = System.getProperty("line.separator");
		String seconds = String.format(Locale.ROOT, "%.3f",
				record.getActiveDurationMillis() / 1000.0);
		return "Run ID: " + record.getRunId() + lineSeparator
				+ "Score: " + record.getScore() + lineSeparator
				+ "Enemies defeated: " + record.getEnemiesDefeated()
				+ lineSeparator + "Active duration: "
				+ record.getActiveDurationMillis() + " ms (" + seconds
				+ " s)" + lineSeparator + "Completion time: "
				+ record.getCompletionTimeMillis();
	}
}
