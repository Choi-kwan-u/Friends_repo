package records;

import java.util.List;

/**
 * Stores completed run records for the lifetime of the application.
 */
public interface RunRecordRepository {

	/**
	 * Adds a record unless its run identifier has already been stored.
	 *
	 * @param record
	 *            Completed run record.
	 * @return true when the record was added; false when it was a duplicate.
	 */
	boolean add(RunRecord record);

	/** @return the number of unique records stored. */
	int size();

	/** @return a read-only snapshot in insertion order. */
	List<RunRecord> getAll();
}
