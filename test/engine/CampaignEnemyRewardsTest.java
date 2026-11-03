package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.RewardSystem;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventPublisher;
import event.LevelCompletedEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignEnemyRewardsTest {
    @TempDir Path temp;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private Path save;
    private CampaignRewards rewards;

    @BeforeEach
    void setUp() {
        save = temp.resolve("assets.properties");
        rewards = new CampaignRewards(assets, files, save);
        rewards.startRun("campaign");
    }

    private void defeat(GameplayEventPublisher events, String id, DefeatedEnemyType type) {
        events.publish(new EnemyDefeatedEvent("campaign", id, type));
    }

    @ParameterizedTest
    @CsvSource({"A,10", "B,15", "C,20", "BONUS,20", "BOSS_A,50", "BOSS_B,50"})
    void eachTypeIsVisibleWhilePlayingAndSavedAfterAnUnclearedStage(
            DefeatedEnemyType type, int amount) throws Exception {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1)) {
            defeat(events, "one", type);
            assertEquals(amount, rewards.getPendingCurrency());
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
        }
        assertEquals(amount, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(amount, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void classificationIsAuthoritativeAndTheIdRemainsOpaque() {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1)) {
            defeat(events, "BOSS_B:not-a-uuid", DefeatedEnemyType.A);
            defeat(events, "BOSS_B:not-a-uuid", DefeatedEnemyType.BOSS_B);
            events.publish(new EnemyDefeatedEvent("campaign", "A:legacy"));
            assertEquals(10, rewards.getPendingCurrency());
        }
        assertEquals(10, assets.getBalance());
    }

    @Test
    void duplicatesStayIgnoredAcrossStagesButANewRunCanReuseAnId() {
        GameplayEventPublisher first = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(first, 1)) {
            defeat(first, "same", DefeatedEnemyType.A);
            defeat(first, "same", DefeatedEnemyType.A);
        }
        rewards.startRun("campaign");
        GameplayEventPublisher second = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(second, 2)) {
            defeat(second, "same", DefeatedEnemyType.A);
            defeat(second, "next", DefeatedEnemyType.B);
        }
        assertEquals(25, assets.getBalance());

        rewards.startRun("new-run");
        GameplayEventPublisher nextRun = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(nextRun, 1)) {
            defeat(nextRun, "late", DefeatedEnemyType.BOSS_B);
            nextRun.publish(new EnemyDefeatedEvent("new-run", "same", DefeatedEnemyType.A));
        }
        assertEquals(35, assets.getBalance());
    }

    @Test
    void unknownForeignAndUnstartedRunsDoNotPayOrCreateASave() {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1)) {
            defeat(events, "unknown", DefeatedEnemyType.UNKNOWN);
            events.publish(new EnemyDefeatedEvent("other-run", "foreign", DefeatedEnemyType.A));
        }
        CampaignRewards unstarted = new CampaignRewards(assets, files, save);
        try (CampaignRewards.StageSubscription stage = unstarted.subscribe(events, 1)) {
            defeat(events, "unstarted", DefeatedEnemyType.A);
        }
        assertEquals(0, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertFalse(Files.exists(save));
        assertThrows(IllegalArgumentException.class, () -> rewards.startRun(null));
        assertThrows(IllegalArgumentException.class, () -> rewards.startRun(" "));
    }

    @Test
    void completionKeepsItsBonusHiddenAndStopsFurtherDefeats() throws Exception {
        GameplayEventPublisher events = new GameplayEventPublisher();
        CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1);
        try (stage) {
            defeat(events, "last-enemy", DefeatedEnemyType.C);
            events.publish(new LevelCompletedEvent(1, GameplayConfig.NORMAL));
            events.publish(new LevelCompletedEvent(1, GameplayConfig.HARD));
            defeat(events, "late", DefeatedEnemyType.BOSS_B);
            assertEquals(20, rewards.getPendingCurrency());
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
        }
        stage.close();
        defeat(events, "after-exit", DefeatedEnemyType.BOSS_B);
        events.publish(new LevelCompletedEvent(1, GameplayConfig.HARD));
        assertEquals(120, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(120, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void anOldSubscriptionCannotPayAfterANewRunStarts() {
        GameplayEventPublisher old = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(old, 1)) {
            rewards.startRun("new-run");
            defeat(old, "old-run", DefeatedEnemyType.A);
            old.publish(new EnemyDefeatedEvent("new-run", "new-run", DefeatedEnemyType.A));
        }
        assertEquals(0, assets.getBalance());
        assertFalse(Files.exists(save));
    }

    @Test
    void saveRetryPreservesAPurchaseWithoutPayingDefeatsAgain() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(80, null, null)));
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1)) {
            defeat(events, "bonus", DefeatedEnemyType.BONUS);
        }
        assertEquals(SettlementResult.SAVE_FAILED, rewards.getLastResult());
        assertEquals(100, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        ShopController shop = shop();
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(0, assets.getBalance());
        assertEquals(SettlementResult.SAVE_FAILED, rewards.retryPendingRewards());
        Files.delete(blocker);
        Files.delete(save);
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(0, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void fullBalanceKeepsDefeatsAndClearRewardsAcrossNewRuns() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        GameplayEventPublisher first = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(first, 1)) {
            defeat(first, "enemy", DefeatedEnemyType.A);
            first.publish(new LevelCompletedEvent(1, GameplayConfig.EASY));
        }
        assertEquals(60, rewards.getPendingCurrency());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        rewards.startRun("new-run");
        GameplayEventPublisher next = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(next, 1)) {
            next.publish(new EnemyDefeatedEvent("new-run", "enemy", DefeatedEnemyType.A));
        }
        assertEquals(70, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertFalse(Files.exists(save));
        assertEquals(PurchaseResult.SUCCESS, shop().purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE - 30, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void aFullPendingAmountQueuesTheNextDefeatInsteadOfLosingIt() throws Exception {
        Field calculation = CampaignRewards.class.getDeclaredField("rewards");
        calculation.setAccessible(true);
        RewardSystem policy = (RewardSystem) calculation.get(rewards);
        assertTrue(policy.setEnemyReward("BOSS_A", Integer.MAX_VALUE));
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = rewards.subscribe(events, 1)) {
            defeat(events, "boss", DefeatedEnemyType.BOSS_A);
            defeat(events, "queued", DefeatedEnemyType.A);
            defeat(events, "queued", DefeatedEnemyType.A);
            assertEquals((long) Integer.MAX_VALUE + 10, rewards.getPendingCurrency());
            assertEquals(0, assets.getBalance());
        }
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        assertEquals(PurchaseResult.SUCCESS, shop().purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(Integer.MAX_VALUE - 90, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE - 90, files.loadPlayerAssets(save).getBalance());
    }

    private ShopController shop() {
        return ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
    }
}
