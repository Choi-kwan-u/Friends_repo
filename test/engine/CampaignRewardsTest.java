package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import event.EnemyDefeatedEvent;
import event.GameplayEventPublisher;
import event.LevelCompletedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRewardsTest {
    @TempDir Path temp;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private Path save;
    private CampaignRewards rewards;

    @BeforeEach
    void setUp() {
        save = temp.resolve("assets.properties");
        rewards = new CampaignRewards(assets, files, save);
    }

    private void clear(int level, int difficulty) {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(events, level)) {
            events.publish(new LevelCompletedEvent(level, difficulty));
        }
    }

    @Test
    void duplicateForeignAndClosedEventsDoNotPayAgain() throws Exception {
        GameplayEventPublisher events = new GameplayEventPublisher();
        CampaignRewards.StageSubscription subscription = rewards.subscribe(events, 2);
        try (subscription) {
            events.publish(new LevelCompletedEvent(1, GameplayConfig.HARD));
            events.publish(new EnemyDefeatedEvent("run", "enemy"));
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
            events.publish(new LevelCompletedEvent(2, GameplayConfig.NORMAL));
            events.publish(new LevelCompletedEvent(2, GameplayConfig.HARD));
            events.publish(new LevelCompletedEvent(2, GameplayConfig.NORMAL));
            assertEquals(0, assets.getBalance());
            assertEquals(0, rewards.getPendingCurrency());
            assertFalse(Files.exists(save));
        }
        byte[] paid = Files.readAllBytes(save);
        subscription.close();
        events.publish(new LevelCompletedEvent(2, GameplayConfig.HARD));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertArrayEquals(paid, Files.readAllBytes(save));
        assertEquals(100, assets.getBalance());
        assertEquals(100, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void nextStageAndReplayEachEarnTheirOwnBonus() throws Exception {
        clear(1, GameplayConfig.NORMAL);
        clear(2, GameplayConfig.HARD);
        clear(1, GameplayConfig.EASY);
        assertEquals(350, assets.getBalance());
        assertEquals(350, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void noCompletionDoesNotWriteAnEmptySave() {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(events, 1)) {
            events.publish(new EnemyDefeatedEvent("run", "enemy"));
        }
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, assets.getBalance());
        assertFalse(Files.exists(save));
    }

    @Test
    void failedSaveRetryIncludesPurchaseWithoutPayingAgain() throws Exception {
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        clear(1, GameplayConfig.HARD);
        assertEquals(200, assets.getBalance());
        assertEquals(SettlementResult.SAVE_FAILED, rewards.getLastResult());
        assertEquals(SettlementResult.SAVE_FAILED, rewards.retryPendingRewards());

        ShopController shop = ShopTestFixtures.upgradeController(assets,
                new ShopTestFixtures.SaveFile(files, save));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertEquals(100, assets.getBalance());
        Files.delete(blocker);
        Files.delete(save);

        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(100, assets.getBalance());
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(100, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void laterClearSavesPreviouslyPaidRewardAfterSaveFailure() throws Exception {
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        clear(1, GameplayConfig.EASY);
        assertEquals(SettlementResult.SAVE_FAILED, rewards.getLastResult());
        Files.delete(blocker);
        Files.delete(save);
        clear(2, GameplayConfig.NORMAL);
        assertEquals(SettlementResult.SUCCESS, rewards.getLastResult());
        assertEquals(150, assets.getBalance());
        assertEquals(150, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void fullBalanceKeepsLaterStagesAndNewRunsPendingUntilPurchaseMakesRoom() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        files.savePlayerAssets(assets.snapshot(), save);
        byte[] original = Files.readAllBytes(save);
        clear(1, GameplayConfig.EASY);
        clear(2, GameplayConfig.NORMAL);
        clear(1, GameplayConfig.HARD);
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.retryPendingRewards());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertArrayEquals(original, Files.readAllBytes(save));

        ShopController shop = ShopTestFixtures.upgradeController(assets,
                new ShopTestFixtures.SaveFile(files, save));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(2)));
        assertEquals(Integer.MAX_VALUE - 350, assets.getBalance());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertEquals(Integer.MAX_VALUE, files.loadPlayerAssets(save).getBalance());
        assertTrue(files.loadPlayerAssets(save).getUnlockedContentIds()
                .contains(PermanentUpgrade.HULL.getProductId(2)));
    }
}
