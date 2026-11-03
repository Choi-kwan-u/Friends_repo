package records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable persistence boundary for records and achievement unlock times. */
public final class ProfileSnapshot {
    private final List<RunRecord> records;
    private final Map<String, Long> unlockTimes;

    public ProfileSnapshot(final List<RunRecord> records,
            final Map<String, Long> unlockTimes) {
        this.records = Collections.unmodifiableList(
                new ArrayList<RunRecord>(records));
        this.unlockTimes = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(unlockTimes));
    }

    public List<RunRecord> getRecords() { return this.records; }
    public Map<String, Long> getUnlockTimes() { return this.unlockTimes; }
}
