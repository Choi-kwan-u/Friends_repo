package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import event.LevelCompletedEvent;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import hud.AssetHudSnapshot;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import screen.GameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CoreAssetHudTest {
    @TempDir Path temp;
    private final FileManager files = Core.getFileManager();
    private final PlayerAssets assets = new PlayerAssets();
    private Field controllerField;
    private Object previousController;
    private Field rewardsField;
    private Object previousRewards;
    private Path save;
    private ShopController shop;
    private CampaignRewards rewards;

    @BeforeEach
    void setUp() throws Exception {
        controllerField = Core.class.getDeclaredField("shopController");
        controllerField.setAccessible(true);
        previousController = controllerField.get(null);
        rewardsField = Core.class.getDeclaredField("campaignRewards");
        rewardsField.setAccessible(true);
        previousRewards = rewardsField.get(null);
        save = temp.resolve("assets.properties");
        shop = ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
        controllerField.set(null, shop);
        rewards = new CampaignRewards(assets, files, save);
        rewardsField.set(null, rewards);
    }

    @AfterEach
    void restoreController() throws Exception {
        controllerField.set(null, previousController);
        rewardsField.set(null, previousRewards);
    }

    private void clearStage(int difficulty) {
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(1, 1, 60, 2000), false, 448, 520, 60);
        Core.runCampaignStage(game, rewards, screen -> {
            long beforeClear = Core.getAssetHudSnapshot().getCredits();
            screen.getGameplayEvents().publish(new LevelCompletedEvent(1, difficulty));
            screen.getGameplayEvents().publish(new LevelCompletedEvent(1, difficulty));
            assertEquals(beforeClear, Core.getAssetHudSnapshot().getCredits(),
                    "The current stage's clear bonus must stay out of the in-game HUD.");
        });
    }

    @Test
    void emptySessionReadsDoNotCreateASave() {
        assertEquals(0, Core.getAssetHudSnapshot().getCredits());
        assertEquals(0, Core.getAssetHudSnapshot().getCredits());
        assertSame(shop, Core.getShopController());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, shop.getSaveStatus());
        assertFalse(Files.exists(save));
    }

    @Test
    void liveDefeatsRemainInTheTotalWhenABalanceLimitDelaysSettlement() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        rewards.startRun("campaign");
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(1, 1, 60, 2000), false, 448, 520, 60);
        Core.runCampaignStage(game, rewards, screen -> {
            screen.getGameplayEvents().publish(new EnemyDefeatedEvent("campaign", "one", DefeatedEnemyType.A));
            assertEquals((long) Integer.MAX_VALUE + 10, Core.getAssetHudSnapshot().getCredits());
            screen.getGameplayEvents().publish(new LevelCompletedEvent(1, GameplayConfig.EASY));
            assertEquals((long) Integer.MAX_VALUE + 10, Core.getAssetHudSnapshot().getCredits());
        });
        assertEquals((long) Integer.MAX_VALUE + 60, Core.getAssetHudSnapshot().getCredits());
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals((long) Integer.MAX_VALUE - 40, Core.getAssetHudSnapshot().getCredits());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals((long) Integer.MAX_VALUE - 40, Core.getAssetHudSnapshot().getCredits());
        assertEquals(Integer.MAX_VALUE - 40, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void clearRewardPurchaseAndReloadUseTheSameBalance() throws Exception {
        AssetHudSnapshot before = Core.getAssetHudSnapshot();
        clearStage(GameplayConfig.HARD);
        AssetHudSnapshot earned = Core.getAssetHudSnapshot();
        assertEquals(200, earned.getCredits());
        assertEquals(0, before.getCredits());
        assertEquals(200, files.loadPlayerAssets(save).getBalance());

        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(100, Core.getAssetHudSnapshot().getCredits());
        assertEquals(200, earned.getCredits());

        PlayerAssets restored = new PlayerAssets();
        ShopController reopened = Core.initializeShop(restored, files, files.loadPlayerAssets(save));
        controllerField.set(null, reopened);
        rewards = new CampaignRewards(restored, files, save);
        rewardsField.set(null, rewards);
        assertEquals(100, Core.getAssetHudSnapshot().getCredits());
        assertTrue(restored.isUnlocked(PermanentUpgrade.HULL.getProductId(1)));
        clearStage(GameplayConfig.NORMAL);
        assertEquals(200, Core.getAssetHudSnapshot().getCredits());
        assertEquals(100, shop.getBalance(), "The HUD must read the reopened session.");
    }

    @Test
    void rejectedPurchasesDoNotChangeTheHudBalanceOrSave() throws Exception {
        String hull = PermanentUpgrade.HULL.getProductId(1);
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, shop.purchase(hull));
        assertEquals(0, Core.getAssetHudSnapshot().getCredits());
        assertFalse(Files.exists(save));
        clearStage(GameplayConfig.HARD);
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(hull));
        byte[] saved = Files.readAllBytes(save);

        assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(hull));
        assertEquals(100, Core.getAssetHudSnapshot().getCredits());
        assertArrayEquals(saved, Files.readAllBytes(save));
    }

    @Test
    void pendingAndQueuedRewardsStayInTheTotalAcrossSettlement() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        clearStage(GameplayConfig.EASY);
        assertEquals((long) Integer.MAX_VALUE + 50, Core.getAssetHudSnapshot().getCredits());
        clearStage(GameplayConfig.NORMAL);
        clearStage(GameplayConfig.HARD);
        AssetHudSnapshot pending = Core.getAssetHudSnapshot();
        assertEquals((long) Integer.MAX_VALUE + 350, pending.getCredits());
        assertEquals(Integer.MAX_VALUE, shop.getBalance());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        assertFalse(Files.exists(save));

        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(2)));
        byte[] saved = Files.readAllBytes(save);
        assertEquals(Integer.MAX_VALUE - 350, shop.getBalance());
        assertEquals(Integer.MAX_VALUE, Core.getAssetHudSnapshot().getCredits());
        assertEquals(350, rewards.getPendingCurrency(), "Reading the HUD must not settle rewards.");
        assertArrayEquals(saved, Files.readAllBytes(save));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE, Core.getAssetHudSnapshot().getCredits());
        assertEquals((long) Integer.MAX_VALUE + 350, pending.getCredits());
        assertEquals(Integer.MAX_VALUE, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void queuedRewardsStayVisibleWhenASaveStopsSettlementPartway() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        clearStage(GameplayConfig.EASY);
        clearStage(GameplayConfig.NORMAL);
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(2)));
        long total = (long) Integer.MAX_VALUE - 200;
        assertEquals(total, Core.getAssetHudSnapshot().getCredits());

        assertEquals(SettlementResult.SAVE_FAILED, rewards.retryPendingRewards());
        assertEquals(Integer.MAX_VALUE - 300, shop.getBalance());
        assertEquals(100, rewards.getPendingCurrency());
        assertEquals(total, Core.getAssetHudSnapshot().getCredits());
        Files.delete(blocker);
        Files.delete(save);
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(total, Core.getAssetHudSnapshot().getCredits());
        assertEquals(total, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void failedSavesKeepLiveBalanceAndRetriesDoNotPayOrChargeAgain() throws Exception {
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        clearStage(GameplayConfig.HARD);
        assertEquals(SettlementResult.SAVE_FAILED, rewards.getLastResult());
        assertEquals(200, Core.getAssetHudSnapshot().getCredits());
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(100, Core.getAssetHudSnapshot().getCredits());
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertTrue(Files.exists(blocker));
        Files.delete(blocker);
        Files.delete(save);

        assertTrue(shop.retrySave());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertTrue(shop.retrySave());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(100, Core.getAssetHudSnapshot().getCredits());
        assertEquals(100, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void loadingLegacySaveExposesBalanceWithoutRewritingIt() throws Exception {
        files.savePlayerAssets(new PlayerAssetsState(700,
                Collections.singleton(PermanentUpgrade.HULL.getProductId(1)),
                Collections.singletonMap("legacy.item", 4)), save);
        byte[] saved = Files.readAllBytes(save);
        ShopController loaded = Core.initializeShop(assets, files, files.loadPlayerAssets(save));
        controllerField.set(null, loaded);

        assertEquals(700, Core.getAssetHudSnapshot().getCredits());
        assertEquals(700, Core.getAssetHudSnapshot().getCredits());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, loaded.getSaveStatus());
        assertArrayEquals(saved, Files.readAllBytes(save));
        assertEquals(4, assets.snapshot().getItemQuantities().get("legacy.item"));
    }
}
