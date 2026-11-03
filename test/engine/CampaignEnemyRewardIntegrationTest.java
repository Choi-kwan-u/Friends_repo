package engine;

import audio.AudioManager;
import boss.BossManager;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import hud.AssetHudSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import records.RunSession;
import screen.GameScreen;
import screen.InfiniteGameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignEnemyRewardIntegrationTest {
    @TempDir Path temp;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private final RunSession run = new RunSession("campaign");
    private Path save;
    private CampaignRewards rewards;
    private ShopController shop;
    private Object previousShop;
    private Object previousRewards;
    private int previousDifficulty;
    private boolean previousMute;

    @BeforeEach
    void setUp() throws Exception {
        previousDifficulty = GameplayConfig.getDifficulty();
        previousMute = AudioManager.isMuted();
        GameplayConfig.setDifficulty(GameplayConfig.NORMAL);
        AudioManager.setMuted(true);
        save = temp.resolve("assets.properties");
        assertTrue(assets.restore(new PlayerAssetsState(200, null, null)));
        shop = ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
        rewards = new CampaignRewards(assets, files, save);
        rewards.startRun(run.getRunId());
        previousShop = swapCore("shopController", shop);
        previousRewards = swapCore("campaignRewards", rewards);
    }

    @AfterEach
    void restoreSession() throws Exception {
        swapCore("shopController", previousShop);
        swapCore("campaignRewards", previousRewards);
        GameplayConfig.setDifficulty(previousDifficulty);
        AudioManager.stopBGM();
        AudioManager.setMuted(previousMute);
    }

    private GameScreen stage(int level, int waves) {
        GameSettings[] settings = new GameSettings[waves];
        Arrays.fill(settings, new GameSettings(1, 1, 1, 100000));
        return new GameScreen(new GameState(level, 0, 3, 0, 0), Arrays.asList(settings),
                false, 448, 520, 60, run);
    }

    @Test
    void liveDefeatsAppearInHudBeforeSettlementAndClearBonusWaitsForScreenExit() throws Exception {
        AssetHudSnapshot before = Core.getAssetHudSnapshot();
        AssetHudSnapshot[] whilePlaying = new AssetHudSnapshot[1];
        Core.runCampaignStage(stage(1, 1), rewards, screen -> {
            screen.initialize();
            defeatOnlyEnemy(screen);
            assertEquals(200, assets.getBalance());
            assertEquals(20, rewards.getPendingCurrency());
            whilePlaying[0] = Core.getAssetHudSnapshot();
            assertEquals(220, whilePlaying[0].getCredits());
            assertEquals(220, Core.getAssetHudSnapshot().getCredits());
            assertFalse(Files.exists(save), "HUD reads must not save or settle rewards.");
            call(screen, "advanceStageIfReady");
            assertTrue(screen.isStageCleared());
            assertEquals(220, Core.getAssetHudSnapshot().getCredits(),
                    "The stage-clear bonus is hidden during the end delay.");
            assertEquals(200, assets.getBalance());
        });
        assertEquals(320, Core.getAssetHudSnapshot().getCredits());
        assertEquals(0, rewards.getPendingCurrency());
        assertEquals(320, files.loadPlayerAssets(save).getBalance());
        assertEquals(200, before.getCredits());
        assertEquals(220, whilePlaying[0].getCredits());

        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        PlayerAssets reloaded = new PlayerAssets();
        assertTrue(reloaded.restore(files.loadPlayerAssets(save)));
        swapCore("shopController", ShopTestFixtures.upgradeController(reloaded,
                new ShopTestFixtures.SaveFile(files, save)));
        swapCore("campaignRewards", new CampaignRewards(reloaded, files, save));
        assertEquals(220, Core.getAssetHudSnapshot().getCredits());
        assertTrue(reloaded.isUnlocked(PermanentUpgrade.HULL.getProductId(1)));
    }

    @Test
    void failureStillSavesEarnedDefeatsWithoutAClearBonus() throws Exception {
        Core.runCampaignStage(stage(1, 2), rewards, screen -> {
            screen.initialize();
            defeatOnlyEnemy(screen);
            screen.getPlayerStatus().takeDamage(screen.getPlayerStatus().getLives());
            call(screen, "advanceStageIfReady");
            assertFalse(screen.isStageCleared());
            assertEquals(220, Core.getAssetHudSnapshot().getCredits());
        });
        assertEquals(220, assets.getBalance());
        assertEquals(220, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void anInterruptedRunnerSettlesEarnedDefeatsAndUnsubscribes() throws Exception {
        GameScreen game = stage(1, 2);
        assertThrows(IllegalStateException.class, () -> Core.runCampaignStage(game, rewards, screen -> {
            screen.initialize();
            defeatOnlyEnemy(screen);
            throw new IllegalStateException("screen stopped");
        }));
        assertEquals(220, assets.getBalance());
        game.getGameplayEvents().publish(new EnemyDefeatedEvent("campaign", "late", DefeatedEnemyType.BOSS_B));
        assertEquals(220, Core.getAssetHudSnapshot().getCredits());
        assertEquals(220, files.loadPlayerAssets(save).getBalance());
    }

    @ParameterizedTest
    @CsvSource({"3,50", "5,50"})
    void bossRewardAppearsBeforeCompletionAndPaysOnlyOnce(int level, int bossReward) throws Exception {
        Core.runCampaignStage(stage(level, 1), rewards, screen -> {
            screen.initialize();
            defeatOnlyEnemy(screen);
            BossManager manager = (BossManager) get(screen, "bossManager");
            int health = manager.getBoss().getMaxHealth();
            damageBoss(screen, health);
            damageBoss(screen, health);
            manager.sync();
            assertEquals(220 + bossReward, Core.getAssetHudSnapshot().getCredits());
            assertEquals(200, assets.getBalance());
            set(screen, "bossExplosionCooldown", Core.getCooldown(0));
            call(screen, "advanceStageIfReady");
            assertTrue(screen.isStageCleared());
            assertEquals(220 + bossReward, Core.getAssetHudSnapshot().getCredits());
        });
        assertEquals(320 + bossReward, assets.getBalance());
        assertEquals(320 + bossReward, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void customAndInfiniteEventsNeverReachCampaignRewards() {
        WaveDefinition wave = new WaveDefinition("one", new EnemyCell[][] {{new EnemyCell("A", 1)}},
                20, 100, 60, 2000);
        LevelDefinition custom = new LevelDefinition(1, "campaign-stage-01", "Custom",
                Collections.singletonList(wave));
        Core.runCustomLevel(custom, 1.0, 448, 520, 60, screen -> {
            screen.initialize();
            defeatOnlyEnemy(screen);
            screen.getGameplayEvents().publish(new EnemyDefeatedEvent("campaign", "custom", DefeatedEnemyType.A));
            call(screen, "advanceStageIfReady");
            assertTrue(screen.isStageCleared());
        });
        InfiniteGameScreen infinite = new InfiniteGameScreen(new GameState(1, 0, 3, 0, 0),
                448, 520, 60, run);
        infinite.getGameplayEvents().publish(new EnemyDefeatedEvent("campaign", "infinite", DefeatedEnemyType.A));
        infinite.getGameplayEvents().publish(new EnemyDefeatedEvent("campaign", "legacy"));
        assertEquals(200, Core.getAssetHudSnapshot().getCredits());
        assertEquals(0, rewards.getPendingCurrency());
        assertFalse(Files.exists(save));
    }

    @SuppressWarnings("unchecked")
    private static void defeatOnlyEnemy(GameScreen screen) {
        EnemyShipFormation formation = (EnemyShipFormation) get(screen, "enemyShipFormation");
        for (EnemyShip enemy : formation) {
            if (enemy.isDestroyed()) continue;
            Set<Bullet> bullets = (Set<Bullet>) get(screen, "bullets");
            bullets.add(BulletPool.getBullet(enemy.getPositionX() + 4, enemy.getPositionY() + 4, -4));
            call(screen, "manageCollisions");
            assertTrue(enemy.isDestroyed());
            return;
        }
        fail("No enemy was available for the collision check.");
    }

    private static Object swapCore(String name, Object value) throws Exception {
        Field field = Core.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void call(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void damageBoss(GameScreen screen, int amount) {
        try {
            Method method = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
            method.setAccessible(true);
            method.invoke(screen, amount);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
