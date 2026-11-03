package engine;

import audio.AudioManager;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import entity.Bullet;
import entity.BulletPool;
import entity.Ship;
import entity.ZigzagEnemyShip;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventPublisher;
import hud.AssetHudSnapshot;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import records.RunSession;
import screen.InfiniteGameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class EndlessRewardIntegrationTest {
    @TempDir Path temp;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private Path save;
    private EndlessRewards endless;
    private CampaignRewards campaign;
    private ShopController shop;
    private Object previousShop;
    private Object previousCampaign;
    private Object previousEndless;
    private Object previousRecords;
    private boolean previousMute;

    @BeforeEach
    void setUp() throws Exception {
        save = temp.resolve("assets.properties");
        assertTrue(assets.restore(new PlayerAssetsState(200, null, null)));
        shop = ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
        campaign = new CampaignRewards(assets, files, save);
        endless = new EndlessRewards(assets, files, save);
        previousShop = swapCore("shopController", shop);
        previousCampaign = swapCore("campaignRewards", campaign);
        previousEndless = swapCore("endlessRewards", endless);
        previousRecords = swapCore("recordsService", null);
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void restoreSession() throws Exception {
        key(false);
        AudioManager.stopBGM();
        AudioManager.setMuted(previousMute);
        swapCore("shopController", previousShop);
        swapCore("campaignRewards", previousCampaign);
        swapCore("endlessRewards", previousEndless);
        swapCore("recordsService", previousRecords);
    }

    private InfiniteGameScreen screen(String runId) {
        return new InfiniteGameScreen(new GameState(1, 0, 1, 0, 0), 448, 520, 60,
                new RunSession(runId));
    }

    @Test
    void actualBulletKillUpdatesTheHudAndDeathSavesOnlyOneCredit() throws Exception {
        InfiniteGameScreen game = screen("endless");
        AssetHudSnapshot before = Core.getAssetHudSnapshot();
        Core.runEndless(game, endless, current -> {
            current.initialize();
            killEnemy(current);
            assertEquals(1, current.getGameState().getShipsDestroyed());
            assertTrue(current.getGameState().getScore() > 1, "Currency is not the score.");
            assertEquals(201, Core.getAssetHudSnapshot().getCredits());
            assertEquals(200, before.getCredits());
            assertEquals(200, assets.getBalance());
            assertFalse(Files.exists(save), "HUD reads do not settle or save live kills.");
            Ship ship = (Ship) get(current, "ship");
            bullets(current, "enemyBullets").add(BulletPool.getBullet(
                    ship.getPositionX() + 4, ship.getPositionY() + 4, 4, Bullet.Owner.ENEMY));
            call(current, "manageCollisions");
            assertEquals(0, current.getGameState().getLivesRemaining());
            assertEquals(1, endless.getPendingCurrency());
        });
        assertEquals(201, assets.getBalance());
        assertEquals(0, endless.getPendingCurrency());
        assertEquals(201, files.loadPlayerAssets(save).getBalance());
        game.getGameplayEvents().publish(new EnemyDefeatedEvent("endless", "late"));
        assertEquals(201, Core.getAssetHudSnapshot().getCredits());
    }

    @Test
    void escapeKeepsEarnedCreditsEvenThoughTheRunIsAbandonedForRecords() throws Exception {
        InfiniteGameScreen game = screen("escape");
        Core.runEndless(game, endless, current -> {
            current.initialize();
            killEnemy(current);
            set(current, "inputDelay", Core.getCooldown(0));
            set(current, "isRunning", true);
            key(true);
            try {
                call(current, "update");
            } finally {
                key(false);
            }
            assertTrue(current.isAbandoned());
            assertEquals(Boolean.FALSE, get(current, "isRunning"));
        });
        assertEquals(201, files.loadPlayerAssets(save).getBalance());
        assertEquals(0, endless.getPendingCurrency());
    }

    @Test
    void exceptionalScreenExitSettlesAndUnsubscribes() throws Exception {
        InfiniteGameScreen game = screen("interrupted");
        assertThrows(IllegalStateException.class, () -> Core.runEndless(game, endless, current -> {
            current.initialize();
            killEnemy(current);
            throw new IllegalStateException("screen stopped");
        }));
        game.getGameplayEvents().publish(new EnemyDefeatedEvent("interrupted", "late"));
        assertEquals(201, files.loadPlayerAssets(save).getBalance());
        assertEquals(0, endless.getPendingCurrency());
    }

    @Test
    void offscreenRemovalAndContactWithoutADefeatEventDoNotPay() {
        Core.runEndless(screen("removal"), endless, current -> {
            current.initialize();
            List<ZigzagEnemyShip> enemies = enemies(current);
            enemies.add(new ZigzagEnemyShip(100, 600, DrawManager.SpriteType.EnemyShipA1, 1));
            call(current, "moveEnemies");
            assertTrue(enemies.isEmpty());
            Ship ship = (Ship) get(current, "ship");
            enemies.add(new ZigzagEnemyShip(ship.getPositionX(), ship.getPositionY(),
                    DrawManager.SpriteType.EnemyShipA1, 1));
            call(current, "manageCollisions");
            assertEquals(0, current.getGameState().getShipsDestroyed());
            assertEquals(0, endless.getPendingCurrency());
        });
        assertEquals(200, assets.getBalance());
        assertFalse(Files.exists(save));
    }

    @Test
    void aPurchaseAndReloadKeepBothModesOnTheSameWallet() throws Exception {
        Core.runEndless(screen("purchase"), endless, current -> {
            current.initialize();
            killEnemy(current);
        });
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(101, Core.getAssetHudSnapshot().getCredits());
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(files.loadPlayerAssets(save)));
        swapCore("shopController", ShopTestFixtures.upgradeController(restored,
                new ShopTestFixtures.SaveFile(files, save)));
        swapCore("campaignRewards", new CampaignRewards(restored, files, save));
        swapCore("endlessRewards", new EndlessRewards(restored, files, save));
        assertEquals(101, Core.getAssetHudSnapshot().getCredits());
        assertTrue(restored.isUnlocked(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void combinedSaveRetryKeepsPurchasesAndRewardsFromBothModes() throws Exception {
        Files.createDirectory(save);
        Path blocker = Files.createFile(save.resolve("blocker"));
        campaign.startRun("campaign");
        GameplayEventPublisher events = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = campaign.subscribe(events, 1)) {
            events.publish(new EnemyDefeatedEvent("campaign", "one", DefeatedEnemyType.A));
        }
        Core.runEndless(screen("endless"), endless, current -> {
            current.initialize();
            killEnemy(current);
        });
        assertEquals(SettlementResult.SAVE_FAILED, campaign.getLastResult());
        assertEquals(SettlementResult.SAVE_FAILED, endless.getLastResult());
        assertEquals(211, assets.getBalance());
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SAVE_FAILED, Core.retryRewards(campaign, endless));
        assertEquals(111, Core.getAssetHudSnapshot().getCredits());
        assertEquals(0, campaign.getPendingCurrency());
        assertEquals(0, endless.getPendingCurrency());
        Files.delete(blocker);
        Files.delete(save);
        assertEquals(SettlementResult.SUCCESS, Core.retryRewards(campaign, endless));
        assertEquals(SettlementResult.SUCCESS, Core.retryRewards(campaign, endless));
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(111, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void bothModesPendingRewardsRemainVisibleAndRetryAfterSpending() throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null)));
        campaign.startRun("campaign");
        GameplayEventPublisher campaignEvents = new GameplayEventPublisher();
        try (CampaignRewards.StageSubscription stage = campaign.subscribe(campaignEvents, 1)) {
            campaignEvents.publish(new EnemyDefeatedEvent("campaign", "one", DefeatedEnemyType.A));
        }
        Core.runEndless(screen("endless"), endless, current -> {
            current.getGameplayEvents().publish(new EnemyDefeatedEvent("endless", "one"));
            current.getGameplayEvents().publish(new EnemyDefeatedEvent("endless", "one"));
        });
        assertEquals((long) Integer.MAX_VALUE + 11, Core.getAssetHudSnapshot().getCredits());
        assertEquals(10, campaign.getPendingCurrency());
        assertEquals(1, endless.getPendingCurrency());
        assertEquals(SettlementResult.SETTLEMENT_FAILED, Core.retryRewards(campaign, endless));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(SettlementResult.SUCCESS, Core.retryRewards(campaign, endless));
        assertEquals(SettlementResult.SUCCESS, Core.retryRewards(campaign, endless));
        assertEquals(0, campaign.getPendingCurrency());
        assertEquals(0, endless.getPendingCurrency());
        assertEquals(Integer.MAX_VALUE - 89, Core.getAssetHudSnapshot().getCredits());
        assertEquals(Integer.MAX_VALUE - 89, files.loadPlayerAssets(save).getBalance());
    }

    private static void killEnemy(InfiniteGameScreen screen) {
        ZigzagEnemyShip enemy = new ZigzagEnemyShip(100, 100, DrawManager.SpriteType.EnemyShipA1, 1);
        enemies(screen).add(enemy);
        bullets(screen, "bullets").add(BulletPool.getBullet(104, 104, -4, Bullet.Owner.PLAYER));
        call(screen, "manageCollisions");
        call(screen, "manageCollisions");
        assertFalse(enemies(screen).contains(enemy));
    }

    @SuppressWarnings("unchecked")
    private static List<ZigzagEnemyShip> enemies(InfiniteGameScreen screen) {
        return (List<ZigzagEnemyShip>) get(screen, "enemies");
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> bullets(InfiniteGameScreen screen, String name) {
        return (Set<Bullet>) get(screen, name);
    }

    private static void key(boolean pressed) {
        KeyEvent event = new KeyEvent(new Canvas(), pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private static Object swapCore(String name, Object value) throws Exception {
        Field field = Core.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Screen lifecycle fields are declared on the superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object target, String name) {
        try { return field(target, name).get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void set(Object target, String name, Object value) {
        try { field(target, name).set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void call(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
