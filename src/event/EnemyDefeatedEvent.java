package event;
/** One defeated enemy; source event IDs must remain stable across redelivery. */
public final class EnemyDefeatedEvent implements GameplayEvent {
    private final String runId;
    private final String eventId;
    private final DefeatedEnemyType enemyType;
    /** Leaves the defeated enemy unclassified, for callers that supply no type. */
    public EnemyDefeatedEvent(final String runId, final String eventId) {
        this(runId, eventId, DefeatedEnemyType.UNKNOWN);
    }
    /** Carries the classification recorded when the defeated enemy was created. */
    public EnemyDefeatedEvent(final String runId, final String eventId,
            final DefeatedEnemyType enemyType) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        if (enemyType == null)
            throw new IllegalArgumentException("enemyType is required.");
        this.runId=runId; this.eventId=eventId; this.enemyType=enemyType;
    }
    public String getRunId() { return this.runId; }
    public String getEventId() { return this.eventId; }
    /** Reward category of this defeat, never null. */
    public DefeatedEnemyType getEnemyType() { return this.enemyType; }
}
