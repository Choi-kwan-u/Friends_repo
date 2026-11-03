package engine;

import audio.AudioManager;
import campaign.CampaignStageData;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PurchaseResult;
import entity.Boss;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.ShipType;
import event.LevelCompletedEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import screen.GameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRewardIntegrationTest {
    @TempDir Path temp;
    private int oldDifficulty;
    private boolean oldMute;
    private final PlayerAssets assets = new PlayerAssets();
    private final FileManager files = Core.getFileManager();
    private Path save;
    private CampaignRewards rewards;

    @BeforeEach
    void setUp() {
        oldDifficulty = GameplayConfig.getDifficulty();
        oldMute = AudioManager.isMuted();
        GameplayConfig.setDifficulty(GameplayConfig.NORMAL);
        AudioManager.setMuted(true);
        save = temp.resolve("assets.properties");
        rewards = new CampaignRewards(assets, files, save);
    }

    @AfterEach
    void restoreSettings() {
        GameplayConfig.setDifficulty(oldDifficulty);
        AudioManager.stopBGM();
        AudioManager.setMuted(oldMute);
    }

    private GameScreen stage(int level, int waves) {
        GameSettings[] settings = new GameSettings[waves];
        Arrays.fill(settings, new GameSettings(1, 1, 60, 2000));
        return new GameScreen(new GameState(level, 0, 3, 0, 0),
                Arrays.asList(settings), false, 448, 520, 60);
    }

    private void clearWave(GameScreen screen) {
        EnemyShipFormation formation = (EnemyShipFormation) field(screen, "enemyShipFormation");
        List<EnemyShip> enemies = new ArrayList<EnemyShip>();
        for (EnemyShip enemy : formation) enemies.add(enemy);
        for (EnemyShip enemy : enemies) formation.destroy(enemy);
        advance(screen);
    }

    private static Object field(GameScreen screen, String name) {
        try {
            Field field = GameScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(screen);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void advance(GameScreen screen) {
        try {
            Method completion = GameScreen.class.getDeclaredMethod("advanceStageIfReady");
            completion.setAccessible(true);
            completion.invoke(screen);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,50", "1,100", "2,200"})
    void coreSavesActualClearAfterGameScreenReturnsUsingCapturedDifficulty(int difficulty, int amount)
            throws Exception {
        GameplayConfig.setDifficulty(difficulty);
        GameScreen game = stage(1, 1);
        GameplayConfig.setDifficulty((difficulty + 1) % 3);
        Core.runCampaignStage(game, rewards, screen -> {
            screen.initialize();
            clearWave(screen);
            assertTrue(screen.isStageCleared());
            assertEquals(0, assets.getBalance());
            assertEquals(0, rewards.getPendingCurrency());
            assertFalse(Files.exists(save), "Clear rewards wait until the game screen finishes.");
            advance(screen);
        });
        assertEquals(amount, assets.getBalance());
        assertEquals(amount, files.loadPlayerAssets(save).getBalance());
        game.getGameplayEvents().publish(new LevelCompletedEvent(1, GameplayConfig.HARD));
        assertEquals(amount, assets.getBalance());
    }

    @Test
    void incompleteWaveAndDeathDoNotPay() {
        Core.runCampaignStage(stage(1, 2), rewards, screen -> {
            screen.initialize();
            clearWave(screen);
            assertFalse(screen.isStageCleared());
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
            screen.getPlayerStatus().takeDamage(screen.getPlayerStatus().getLives());
            clearWave(screen);
            assertFalse(screen.isStageCleared());
        });
        assertEquals(0, assets.getBalance());
        assertFalse(Files.exists(save));
    }

    @Test
    void finalWavePaysAndCreditsBuyUpgradeThatSurvivesReload() throws Exception {
        ShopController shop = ShopTestFixtures.upgradeController(assets,
                new ShopTestFixtures.SaveFile(files, save));
        Core.runCampaignStage(stage(1, 2), rewards, screen -> {
            screen.initialize();
            clearWave(screen);
            assertEquals(0, shop.snapshot().getBalance());
            clearWave(screen);
            advance(screen);
            assertEquals(0, shop.snapshot().getBalance());
        });
        assertEquals(100, shop.snapshot().getBalance());
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        PlayerAssets reloaded = new PlayerAssets();
        assertTrue(reloaded.restore(files.loadPlayerAssets(save)));
        assertEquals(0, reloaded.getBalance());
        assertEquals(ShipType.STANDARD.getMaxLives() + 1,
                CampaignUpgrades.from(reloaded.snapshot()).getMaxLives(ShipType.STANDARD));
    }

    @Test
    void finalBossMustBeDefeatedBeforeReward() throws Exception {
        Core.runCampaignStage(stage(CampaignStageData.getTotalStages(), 1), rewards, screen -> {
            screen.initialize();
            clearWave(screen);
            assertEquals(0, assets.getBalance());
            assertFalse(Files.exists(save));
            try {
                Object manager = field(screen, "bossManager");
                Boss boss = (Boss) manager.getClass().getMethod("getBoss").invoke(manager);
                assertNotNull(boss);
                Method damage = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
                damage.setAccessible(true);
                damage.invoke(screen, boss.getMaxHealth());
                Field cooldown = GameScreen.class.getDeclaredField("bossExplosionCooldown");
                cooldown.setAccessible(true);
                cooldown.set(screen, Core.getCooldown(0));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            advance(screen);
            assertTrue(screen.isStageCleared());
            assertEquals(0, assets.getBalance());
        });
        assertEquals(100, assets.getBalance());
        assertEquals(100, files.loadPlayerAssets(save).getBalance());
    }

    @Test
    void earlyReturnAndRunnerFailureReleaseListenerWithoutReward() {
        GameScreen early = stage(1, 1);
        Core.runCampaignStage(early, rewards, GameScreen::initialize);
        early.getGameplayEvents().publish(new LevelCompletedEvent(1, GameplayConfig.NORMAL));
        GameScreen failed = stage(1, 1);
        assertThrows(IllegalStateException.class, () -> Core.runCampaignStage(failed, rewards,
                screen -> { throw new IllegalStateException("runner failed"); }));
        failed.getGameplayEvents().publish(new LevelCompletedEvent(1, GameplayConfig.NORMAL));
        assertEquals(0, assets.getBalance());
        assertFalse(Files.exists(save));
    }

    @Test
    void customStageStillDoesNotEarnCampaignBonus() {
        WaveDefinition wave = new WaveDefinition("last",
                new EnemyCell[][] {{new EnemyCell("A", 1)}}, 20, 100, 60, 2000);
        LevelDefinition level = new LevelDefinition(1, "campaign-stage-01", "Custom",
                Collections.singletonList(wave));
        Core.runCustomLevel(level, 1.0, 448, 520, 60, screen -> {
            screen.initialize();
            clearWave(screen);
            assertTrue(screen.isStageCleared());
        });
        assertEquals(SettlementResult.SUCCESS, rewards.retryPendingRewards());
        assertEquals(0, assets.getBalance());
        assertFalse(Files.exists(save));
    }
}
