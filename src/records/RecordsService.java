package records;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;

/** Coordinates run records, achievements, notifications, and local saving. */
public final class RecordsService {
    private final ProfileStore store;
    private final ExecutorService writer;
    private final Map<String, RunRecord> records = new LinkedHashMap<String, RunRecord>();
    private final Map<String, Long> unlockTimes = new LinkedHashMap<String, Long>();
    private final Deque<Achievement> notifications = new ArrayDeque<Achievement>();
    private final List<Achievement> currentRunUnlocks = new ArrayList<Achievement>();
    private final Set<String> seenDefeatEvents = new HashSet<String>();
    private String activeRunId;
    private int activeRunDefeats;
    private boolean writesBlocked;
    private boolean closed;
    private final String loadWarning;
    private volatile String saveError;
    private long saveRevision;

    public RecordsService(final Path file) {
        this.store = new ProfileStore(file);
		this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
			public Thread newThread(final Runnable runnable) {
				Thread thread = new Thread(runnable, "records-profile-writer");
				thread.setDaemon(true);
				return thread;
			}
		});
        try {
            ProfileSnapshot snapshot = this.store.load();
            for (RunRecord record : snapshot.getRecords())
                this.records.put(record.getRunId(), record);
            this.unlockTimes.putAll(snapshot.getUnlockTimes());
        } catch (IOException exception) {
            this.writesBlocked = true;
            this.saveError = "Profile load failed; existing files are protected: "
                    + exception.getMessage();
        }
        this.loadWarning = this.store.getLoadWarning();
    }

    public synchronized void startRun(final String runId) {
        if (this.closed) throw new IllegalStateException("Records service is closed.");
        if (runId == null || runId.trim().isEmpty()) throw new IllegalArgumentException("runId must not be blank.");
        this.activeRunId = runId;
        this.activeRunDefeats = 0;
        this.seenDefeatEvents.clear();
        this.currentRunUnlocks.clear();
    }

    public synchronized void abandonRun(final String runId) {
        if (runId != null && runId.equals(this.activeRunId)) {
            this.activeRunId = null;
            this.seenDefeatEvents.clear();
        }
    }

    /** Accepts each source event once; safe for a future shared enemy event hook. */
    public synchronized boolean enemyDefeated(final String runId,
            final String eventId) {
        if (this.closed || runId == null || !runId.equals(this.activeRunId)
                || eventId == null || eventId.trim().length() == 0
                || !this.seenDefeatEvents.add(eventId))
            return false;
        this.activeRunDefeats++;
        unlock(Achievement.FIRST_ENEMY, System.currentTimeMillis());
        if (this.activeRunDefeats >= 20)
            unlock(Achievement.TWENTY_ENEMIES, System.currentTimeMillis());
        return true;
    }

    public synchronized void activeDuration(final String runId,
            final long durationMillis) {
        if (!this.closed && runId != null && runId.equals(this.activeRunId)
                && durationMillis >= 60000L)
            unlock(Achievement.SIXTY_SECONDS, System.currentTimeMillis());
    }

    public synchronized boolean completeRun(final RunRecord record) {
        if (this.closed || record == null || this.records.containsKey(record.getRunId()))
            return false;
        boolean firstRun = this.records.isEmpty();
        int previousBest = Integer.MIN_VALUE;
        for (RunRecord prior : this.records.values())
            previousBest = Math.max(previousBest, prior.getScore());
        // Every save containing a completion achievement also contains its run.
        this.records.put(record.getRunId(), record);
        if (record.getEnemiesDefeated() >= 1) unlock(Achievement.FIRST_ENEMY, record.getCompletionTimeMillis());
        if (record.getEnemiesDefeated() >= 20) unlock(Achievement.TWENTY_ENEMIES, record.getCompletionTimeMillis());
        if (record.getActiveDurationMillis() >= 60000L) unlock(Achievement.SIXTY_SECONDS, record.getCompletionTimeMillis());
        if (firstRun)
            unlock(Achievement.FIRST_RUN, record.getCompletionTimeMillis());
        if (!firstRun && record.getScore() > previousBest)
            unlock(Achievement.BEAT_HIGH_SCORE, record.getCompletionTimeMillis());
        if (record.getRunId().equals(this.activeRunId))
            this.activeRunId = null;
        enqueueSave();
        return true;
    }

    private void unlock(final Achievement achievement, final long timeMillis) {
        if (this.unlockTimes.containsKey(achievement.getId()))
            return;
        this.unlockTimes.put(achievement.getId(), Long.valueOf(timeMillis));
        this.currentRunUnlocks.add(achievement);
        this.notifications.addLast(achievement);
        enqueueSave();
    }


    private ProfileSnapshot snapshot() {
        return new ProfileSnapshot(new ArrayList<RunRecord>(this.records.values()),
                this.unlockTimes);
    }

    public synchronized Achievement pollNotification() {
        return this.notifications.pollFirst();
    }

    public synchronized List<Achievement> getCurrentRunUnlocks() {
        return Collections.unmodifiableList(
                new ArrayList<Achievement>(this.currentRunUnlocks));
    }

    public synchronized List<RunRecord> getTopRuns() {
        List<RunRecord> result = new ArrayList<RunRecord>(this.records.values());
        Collections.sort(result, new Comparator<RunRecord>() {
            public int compare(final RunRecord first, final RunRecord second) {
                int scoreOrder = Integer.compare(second.getScore(), first.getScore());
                if (scoreOrder != 0)
                    return scoreOrder;
                int timeOrder = Long.compare(first.getCompletionTimeMillis(),
                        second.getCompletionTimeMillis());
                return timeOrder != 0 ? timeOrder
                        : first.getRunId().compareTo(second.getRunId());
            }
        });
        if (result.size() > 10)
            result = new ArrayList<RunRecord>(result.subList(0, 10));
        return Collections.unmodifiableList(result);
    }

    public synchronized int getHighestScore() {
        int result = 0;
        if (!this.records.isEmpty())
            result = Integer.MIN_VALUE;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getScore());
        return result;
    }

    public synchronized int getMostEnemiesDefeated() {
        int result = 0;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getEnemiesDefeated());
        return result;
    }

    public synchronized long getLongestSurvivalMillis() {
        long result = 0;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getActiveDurationMillis());
        return result;
    }

    public synchronized Long getUnlockTime(final Achievement achievement) {
        return this.unlockTimes.get(achievement.getId());
    }

    public synchronized int getRecordCount() { return this.records.size(); }
    public synchronized boolean isWritesBlocked() { return this.writesBlocked; }
    public String getSaveError() { return this.saveError; }
    public String getLoadWarning() { return this.loadWarning; }

    public void close() {
        synchronized (this) {
            this.closed = true;
            this.writer.shutdown();
        }
        try {
            if (!this.writer.awaitTermination(10, TimeUnit.SECONDS)) {
                this.writer.shutdownNow();
                this.saveError = "Profile save did not finish before shutdown.";
            }
        } catch (InterruptedException exception) {
            this.writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void enqueueSave() {
        if (this.writesBlocked || this.closed) return;
        final ProfileSnapshot state=snapshot();
        final long revision=++this.saveRevision;
        this.saveError="Saving profile...";
        this.writer.execute(new Runnable() {
            public void run() {
                String error=null;
                try { store.write(state); }
                catch (IOException exception) { error="Save failed; previous data was preserved: "+exception.getMessage(); }
                synchronized (RecordsService.this) { if (revision==saveRevision) saveError=error; }
            }
        });
    }
}
