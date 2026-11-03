package engine;

import currency.PlayerAssets;
import currency.RewardSystem;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Fixed Endless kill rewards for one application session, on the gameplay thread. */
final class EndlessRewards {
    static final int CREDITS_PER_DEFEAT = 1;
    private static final String DEFEAT_REWARD = "ENDLESS_DEFEAT";
    private static final String QUEUED_REWARD = "ENDLESS_QUEUED";
    private final RewardSystem rewards;
    private final RewardSettlementCoordinator settlement;
    private final Set<String> seenDefeats = new HashSet<String>();
    private String lastRunId;
    private RunSubscription active;
    private long queuedCredits;
    private SettlementResult lastResult = SettlementResult.SUCCESS;

    EndlessRewards(PlayerAssets assets, FileManager files) {
        this(assets, files, null);
    }

    EndlessRewards(PlayerAssets assets, FileManager files, Path savePath) {
        rewards = new RewardSystem(assets);
        rewards.setEnemyReward(DEFEAT_REWARD, CREDITS_PER_DEFEAT);
        settlement = new RewardSettlementCoordinator(rewards, assets, files, savePath);
    }

    RunSubscription subscribe(GameplayEventPublisher events, String runId) {
        Objects.requireNonNull(events, "events");
        if (runId == null || runId.trim().isEmpty()) {
            throw new IllegalArgumentException("Endless run ID must not be blank.");
        }
        if (active != null) {
            throw new IllegalStateException("An Endless reward subscription is already active.");
        }
        if (!runId.equals(lastRunId)) {
            lastRunId = runId;
            seenDefeats.clear();
        }
        active = new RunSubscription(events, runId);
        return active;
    }

    SettlementResult getLastResult() {
        return lastResult;
    }

    /** Includes live kills; reading never settles or saves. */
    long getPendingCurrency() {
        return rewards.getPendingCurrency() + queuedCredits;
    }

    /** Retains unpaid kills and retries the latest shared assets after a failed save. */
    SettlementResult retryPendingRewards() {
        if (rewards.getPendingCurrency() > 0) {
            lastResult = settlement.processSettlement();
            if (lastResult != SettlementResult.SUCCESS) return lastResult;
        }
        while (queuedCredits > 0) {
            int batch = (int) Math.min(queuedCredits, Integer.MAX_VALUE);
            rewards.setEnemyReward(QUEUED_REWARD, batch);
            if (!rewards.rewardEnemy(QUEUED_REWARD)) {
                lastResult = SettlementResult.SETTLEMENT_FAILED;
                return lastResult;
            }
            queuedCredits -= batch;
            lastResult = settlement.processSettlement();
            if (lastResult != SettlementResult.SUCCESS) return lastResult;
        }
        if (lastResult == SettlementResult.SAVE_FAILED) {
            lastResult = settlement.retrySave();
        }
        return lastResult;
    }

    /** A screen owns one subscription; death, Escape and exceptional exits all close it. */
    final class RunSubscription implements AutoCloseable, GameplayEventListener<EnemyDefeatedEvent> {
        private final GameplayEventPublisher events;
        private final String runId;
        private boolean closed;

        private RunSubscription(GameplayEventPublisher events, String runId) {
            this.events = events;
            this.runId = runId;
            events.subscribe(EnemyDefeatedEvent.class, this);
        }

        @Override
        public void onEvent(EnemyDefeatedEvent event) {
            if (closed || !runId.equals(event.getRunId())
                    || !seenDefeats.add(event.getEventId())) return;
            // Type is deliberately ignored: legacy and typed defeats earn the same amount.
            if (!rewards.rewardEnemy(DEFEAT_REWARD)) {
                queuedCredits += CREDITS_PER_DEFEAT;
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            events.unsubscribe(EnemyDefeatedEvent.class, this);
            active = null;
            SettlementResult result = retryPendingRewards();
            if (result != SettlementResult.SUCCESS) {
                Core.getLogger().warning("Endless reward needs retry: " + result);
            }
        }
    }
}
