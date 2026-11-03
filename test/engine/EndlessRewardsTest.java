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
import org.junit.jupiter.params.provider.EnumSource;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class EndlessRewardsTest {
    @TempDir Path temp;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private Path save;
    private EndlessRewards rewards;

    @BeforeEach
    void setUp() {
        save = temp.resolve("assets.properties");
        rewards = new EndlessRewards(assets, files, save);
    }

    @ParameterizedTest
    @EnumSource(DefeatedEnemyType.class)
    void eachEnemyTypePaysOneAfterTheRunWithoutPerKillWrites(DefeatedEnemyType type) throws Exception {
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (EndlessRewards.RunSubscription run = rewards.subscribe(events, "endless")) {
            events.publish(new EnemyDefeatedEvent("endless", "enemy", type));
            assertEquals(1, rewards.getPendingCurrency());
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
        }
        assertEquals(1, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(1, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void legacyDefeatsWorkButDuplicatesForeignRunsAndClearEventsDoNotPayExtra() throws Exception {
        GameplayEventPublisher events = new GameplayEventPublisher();
        EndlessRewards.RunSubscription run = rewards.subscribe(events, "endless");
        try (run) {
            events.publish(new EnemyDefeatedEvent("other", "foreign"));
            events.publish(new LevelCompletedEvent(1, GameplayConfig.HARD));
            events.publish(new EnemyDefeatedEvent("endless", "same"));
            events.publish(new EnemyDefeatedEvent("endless", "same", DefeatedEnemyType.BOSS_B));
            events.publish(new EnemyDefeatedEvent("endless", "second"));
            assertEquals(2, rewards.getPendingCurrency());
        }
        byte[] saved = Files.readAllBytes(save);
        run.close();
        events.publish(new EnemyDefeatedEvent("endless", "late"));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(2, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertArrayEquals(saved, Files.readAllBytes(save));
    }

    @Test
    void sameRunKeepsDeduplicationAndANewRunCanReuseAnEventId() {
        GameplayEventPublisher events = new GameplayEventPublisher();
        for (String runId : new String[] {"first", "first", "second"}) {
            try (EndlessRewards.RunSubscription run = rewards.subscribe(events, runId)) {
                events.publish(new EnemyDefeatedEvent(runId, "same"));
            }
        }
        assertEquals(2, assets.getBalance());
    }

    @Test
    void invalidAndOverlappingSubscriptionsCannotReplaceTheActiveRun() {
        GameplayEventPublisher events = new GameplayEventPublisher();
        assertThrows(NullPointerException.class, () -> rewards.subscribe(null, "run"));
        assertThrows(IllegalArgumentException.class, () -> rewards.subscribe(events, null));
        assertThrows(IllegalArgumentException.class, () -> rewards.subscribe(events, " "));
        try (EndlessRewards.RunSubscription run = rewards.subscribe(events, "first")) {
            assertThrows(IllegalStateException.class, () -> rewards.subscribe(events, "second"));
            events.publish(new EnemyDefeatedEvent("first", "enemy"));
        }
        try (EndlessRewards.RunSubscription run = rewards.subscribe(events, "second")) {
            events.publish(new EnemyDefeatedEvent("first", "late"));
            events.publish(new EnemyDefeatedEvent("second", "enemy"));
        }
        assertEquals(2, assets.getBalance());
    }

    @Test
    void anEmptyRunDoesNotCreateASave() {
        try (EndlessRewards.RunSubscription run = rewards.subscribe(new GameplayEventPublisher(), "empty")) {
            assertEquals(0, rewards.getPendingCurrency());
        }
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertFalse(Files.exists(save));
    }

    @Test
    void fullBalanceKeepsKillsAcrossRunsUntilAShopPurchaseMakesRoom() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        GameplayEventPublisher events = new GameplayEventPublisher();
        for (String runId : new String[] {"first", "second"}) {
            try (EndlessRewards.RunSubscription run = rewards.subscribe(events, runId)) {
                events.publish(new EnemyDefeatedEvent(runId, "enemy"));
            }
        }
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        assertEquals(2, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertFalse(Files.exists(save));
        assertEquals(PurchaseResult.SUCCESS, shop().purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE - 98, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void failedSaveRetryKeepsALaterPurchaseAndNeverRepaysKills() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(80, null, null)));
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (EndlessRewards.RunSubscription run = rewards.subscribe(events, "endless")) {
            for (int index = 0; index < 20; index++) {
                events.publish(new EnemyDefeatedEvent("endless", "enemy-" + index));
            }
        }
        assertEquals(SettlementResult.SAVE_FAILED, rewards.getLastResult());
        assertEquals(100, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(PurchaseResult.SUCCESS, shop().purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SAVE_FAILED, rewards.retryPendingRewards());
        assertEquals(0, assets.getBalance());
        Files.delete(blocker);
        Files.delete(save);
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(0, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void pendingIntegerLimitQueuesAnotherKillWithoutLosingOrDuplicatingIt() throws Exception {
        Field calculation = EndlessRewards.class.getDeclaredField("rewards");
        calculation.setAccessible(true);
        Field pending = RewardSystem.class.getDeclaredField("pendingCurrency");
        pending.setAccessible(true);
        pending.setInt(calculation.get(rewards), Integer.MAX_VALUE);
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (EndlessRewards.RunSubscription run = rewards.subscribe(events, "endless")) {
            events.publish(new EnemyDefeatedEvent("endless", "one"));
            events.publish(new EnemyDefeatedEvent("endless", "one"));
            assertEquals((long) Integer.MAX_VALUE + 1, rewards.getPendingCurrency());
        }
        assertEquals(Integer.MAX_VALUE, assets.getBalance());
        assertEquals(1, rewards.getPendingCurrency());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, rewards.getLastResult());
        assertEquals(PurchaseResult.SUCCESS, shop().purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(Integer.MAX_VALUE - 99, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE - 99, files.loadPlayerAssets(save).getBalance());
    }

    private ShopController shop() {
        return ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
    }
}
