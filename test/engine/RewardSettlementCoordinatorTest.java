package engine;

import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.PurchaseSystem;
import currency.RewardSystem;
import currency.ShopCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RewardSettlementCoordinatorTest {

    private PlayerAssets assets;
    private RewardSystem rewards;
    private FileManager fileManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        assets = new PlayerAssets();
        rewards = new RewardSystem(assets);
        fileManager = FileManager.getInstance();
    }

    @Test
    void testSuccessfulSettlementAndSave() throws IOException {
        Path savePath = tempDir.resolve("test_assets.properties");
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);

        assertTrue(rewards.rewardEnemy("TANK")); // Adds 50 pending

        SettlementResult result = coordinator.processSettlement();

        assertEquals(SettlementResult.SUCCESS, result);
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(50, assets.getBalance());
        assertSavedAssets(savePath, assets.snapshot());
    }

    @Test
    void testSettlementFailurePreservesPendingRewards() throws IOException {
        Path savePath = tempDir.resolve("test_assets.properties");
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);

        PlayerAssetsState previousSave = assets.snapshot();
        fileManager.savePlayerAssets(previousSave, savePath);

        // Force an overflow condition in PlayerAssets
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE,
                Collections.<String>emptySet(), Collections.<String, Integer>emptyMap())));
        assertTrue(rewards.rewardEnemy("BASIC")); // 10 pending

        SettlementResult result = coordinator.processSettlement();

        assertEquals(SettlementResult.SETTLEMENT_FAILED, result);
        assertEquals(10, rewards.getPendingCurrency()); // Pending rewards preserved
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertSavedAssets(savePath, previousSave);
    }

    @Test
    void testSaveFailurePreservesSettledAssets() throws IOException {
        Path savePath = createBlockedSavePath();
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);

        assertTrue(rewards.rewardEnemy("TANK"));

        SettlementResult result = coordinator.processSettlement();

        assertEquals(SettlementResult.SAVE_FAILED, result);
        assertEquals(0, rewards.getPendingCurrency()); // Settlement succeeded
        assertEquals(50, assets.getBalance()); // Assets are updated in runtime memory
        assertTrue(Files.isDirectory(savePath));
    }

    @Test
    void testSaveRetryLeavesNewRewardsPending() throws IOException {
        Path savePath = createBlockedSavePath();
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);
        assertTrue(rewards.rewardEnemy("FAST")); // 25 pending
        assertEquals(SettlementResult.SAVE_FAILED, coordinator.processSettlement());
        assertEquals(25, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());

        assertTrue(rewards.rewardEnemy("BASIC")); // New rewards belong to the next settlement
        unblockSavePath(savePath);

        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(25, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        assertSavedAssets(savePath, assets.snapshot());

        // An explicit new settlement still transfers the remaining rewards exactly once.
        assertEquals(SettlementResult.SUCCESS, coordinator.processSettlement());
        assertEquals(35, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertSavedAssets(savePath, assets.snapshot());
    }

    @Test
    void testSaveRetrySucceedsWhenNewRewardsWouldOverflow() throws IOException {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE - 25,
                Collections.<String>emptySet(), Collections.<String, Integer>emptyMap())));
        Path savePath = createBlockedSavePath();
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);
        assertTrue(rewards.rewardEnemy("FAST"));
        assertEquals(SettlementResult.SAVE_FAILED, coordinator.processSettlement());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertTrue(rewards.rewardEnemy("BASIC"));
        unblockSavePath(savePath);

        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        assertSavedAssets(savePath, assets.snapshot());
    }

    @Test
    void testSaveRetryUsesLatestAssetsAfterPermanentPurchases() throws IOException {
        Path savePath = createBlockedSavePath();
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);
        assertTrue(rewards.rewardEnemy("TANK"));
        assertEquals(SettlementResult.SAVE_FAILED, coordinator.processSettlement());

        ShopCatalog catalog = new ShopCatalog();
        assertTrue(catalog.registerUpgrade("test-upgrade", 10));
        assertTrue(catalog.registerUnlockable("test-unlock", 10));
        PurchaseSystem purchases = new PurchaseSystem(assets, catalog);
        assertEquals(PurchaseResult.SUCCESS, purchases.purchaseUnlockable("test-upgrade"));
        assertEquals(PurchaseResult.SUCCESS, purchases.purchaseUnlockable("test-unlock"));
        assertEquals(30, assets.getBalance());
        assertTrue(assets.isUnlocked("test-upgrade"));
        assertTrue(assets.isUnlocked("test-unlock"));
        PlayerAssetsState latestAssets = assets.snapshot();
        unblockSavePath(savePath);

        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(30, assets.getBalance());
        assertTrue(assets.isUnlocked("test-upgrade"));
        assertTrue(assets.isUnlocked("test-unlock"));
        assertEquals(0, rewards.getPendingCurrency());
        assertSavedAssets(savePath, latestAssets);
    }

    @Test
    void testRepeatedSaveFailuresAndRetriesPreserveRewards() throws IOException {
        Path savePath = createBlockedSavePath();
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);
        assertTrue(rewards.rewardEnemy("FAST"));
        assertEquals(SettlementResult.SAVE_FAILED, coordinator.processSettlement());
        assertTrue(rewards.rewardEnemy("BASIC"));

        assertEquals(SettlementResult.SAVE_FAILED, coordinator.retrySave());
        assertEquals(SettlementResult.SAVE_FAILED, coordinator.retrySave());
        assertEquals(25, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        unblockSavePath(savePath);

        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(SettlementResult.SUCCESS, coordinator.retrySave());
        assertEquals(25, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        assertSavedAssets(savePath, assets.snapshot());
    }

    @Test
    void testRepeatedSettlementWithoutNewRewardsDoesNotDuplicateCurrency() throws IOException {
        Path savePath = tempDir.resolve("test_assets.properties");
        RewardSettlementCoordinator coordinator = new RewardSettlementCoordinator(rewards, assets, fileManager, savePath);
        assertTrue(rewards.rewardEnemy("FAST"));

        assertEquals(SettlementResult.SUCCESS, coordinator.processSettlement());
        assertEquals(SettlementResult.SUCCESS, coordinator.processSettlement());
        assertEquals(25, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertSavedAssets(savePath, assets.snapshot());
    }

    private Path createBlockedSavePath() throws IOException {
        Path savePath = Files.createDirectory(tempDir.resolve("test_assets.properties"));
        // A non-empty directory cannot be replaced by the save file.
        Files.createFile(savePath.resolve("blocker"));
        return savePath;
    }

    private void unblockSavePath(Path savePath) throws IOException {
        Files.delete(savePath.resolve("blocker"));
        Files.delete(savePath);
    }

    private void assertSavedAssets(Path savePath, PlayerAssetsState expected) throws IOException {
        assertTrue(Files.isRegularFile(savePath));
        PlayerAssetsState saved = fileManager.loadPlayerAssets(savePath);
        assertEquals(expected.getBalance(), saved.getBalance());
        assertEquals(expected.getUnlockedContentIds(), saved.getUnlockedContentIds());
        assertEquals(expected.getItemQuantities(), saved.getItemQuantities());
    }
}
