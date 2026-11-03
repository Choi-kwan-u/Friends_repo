package records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory repository that de-duplicates records by run identifier.
 */
public final class InMemoryRunRecordRepository
		implements RunRecordRepository {

	/** Records keyed by run identifier, preserving insertion order. */
	private final Map<String, RunRecord> recordsById =
			new LinkedHashMap<String, RunRecord>();

	/** {@inheritDoc} */
	public synchronized boolean add(final RunRecord record) {
		if (record == null)
			throw new IllegalArgumentException("record must not be null.");

		if (this.recordsById.containsKey(record.getRunId()))
			return false;

		this.recordsById.put(record.getRunId(), record);
		return true;
	}

	/** {@inheritDoc} */
	public synchronized int size() {
		return this.recordsById.size();
	}

	/** {@inheritDoc} */
	public synchronized List<RunRecord> getAll() {
		return Collections.unmodifiableList(
				new ArrayList<RunRecord>(this.recordsById.values()));
	}
}
