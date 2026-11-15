package engine;

import audio.AudioManager;
import campaign.CampaignStageData;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import entity.ShipType;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import records.RunSession;
import revive.ReviveManager;
import revive.ReviveService;
import screen.GameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignReviveWiringTest {
    @TempDir Path temp;
    private PlayerAssets assets;
    private ShopController shop;
    private boolean muted;

    @BeforeEach
    void setUp() {
        muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(1000, null, null)));
        shop = ShopTestFixtures.controller(assets, new ShopTestFixtures.SaveFile(
                Core.getFileManager(), temp.resolve("assets.properties")));
    }

    @AfterEach
    void restoreAudio() {
        AudioManager.stopBGM();
        AudioManager.setMuted(muted);
    }

    @Test
    void allSevenStagesShareTheSameServiceAndKeepFirstAndThirdStagePayments() throws Exception {
        ReviveService service = service();
        RunSession session = new RunSession("campaign-wiring");
        for (int stage = 1; stage <= CampaignStageData.getTotalStages(); stage++) {
            GameScreen screen = new GameScreen(new GameState(stage, 100, 3, 7, 12),
                    CampaignStageData.getStageSettings(stage), false, 448, 520, 60,
                    ShipType.STANDARD, session, CampaignUpgrades.none());
            screen.setReviveService(service);
            screen.initialize();
            assertSame(service, attachedService(screen));
            if (stage == 1 || stage == 3) {
                assertTrue(service.openOffer());
                assertEquals(stage == 1 ? 50 : 150, service.getNextReviveCost());
                assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
            }
            assertEquals(stage < 3 ? 1 : 0, service.getRemainingRevives());
            assertEquals(100, screen.getGameState().getScore());
            assertEquals(12, screen.getGameState().getShipsDestroyed());
        }
        assertEquals(7, CampaignStageData.getTotalStages());
        assertEquals(800, assets.getBalance());
        assertFalse(service.openOffer());
    }

    @Test
    void aNewCampaignGetsANewServiceWhileThePreviousOneKeepsItsCount() throws Exception {
        ReviveService previous = service();
        assertTrue(previous.openOffer());
        assertEquals(ReviveService.Result.SUCCESS, previous.confirmRevive());
        ReviveService next = service();
        assertNotSame(previous, next);
        assertEquals(1, previous.getRemainingRevives());
        assertEquals(2, next.getRemainingRevives());
        assertEquals(50, next.getNextReviveCost());
        GameScreen screen = legacyScreen();
        screen.setReviveService(next);
        screen.initialize();
        assertSame(next, attachedService(screen));
        assertEquals(2, next.getRemainingRevives());
    }

    @Test
    void nullInjectionCannotClearAnAlreadyAttachedService() throws Exception {
        GameScreen screen = legacyScreen();
        assertNull(attachedService(screen));
        assertThrows(IllegalArgumentException.class, () -> screen.setReviveService(null));
        ReviveService service = service();
        screen.setReviveService(service);
        assertThrows(IllegalArgumentException.class, () -> screen.setReviveService(null));
        assertSame(service, attachedService(screen));
    }

    @Test
    void legacyAndCustomScreensStillInitializeWithoutAReviveService() throws Exception {
        GameScreen legacy = legacyScreen();
        legacy.initialize();
        assertNull(attachedService(legacy));
        WaveDefinition wave = new WaveDefinition("custom-wave",
                new EnemyCell[][] {{new EnemyCell("A", 1)}}, 20, 100, 60, 2000);
        LevelDefinition definition = new LevelDefinition(1, "custom", "Custom", List.of(wave));
        GameScreen custom = GameScreen.forCustomLevel(new GameState(1, 10, 3, 0, 0),
                definition, 1.0, 448, 520, 60);
        custom.initialize();
        assertNull(attachedService(custom));
        assertEquals(ShipType.STANDARD.getMaxLives(), custom.getPlayerStatus().getMaxLives());
        assertEquals(10, custom.getGameState().getScore());
    }

    private ReviveService service() {
        return new ReviveService(new ReviveManager(ReviveManager.Mode.CAMPAIGN),
                shop, () -> SettlementResult.SUCCESS);
    }

    private GameScreen legacyScreen() {
        return new GameScreen(new GameState(1, 0, 3, 0, 0),
                CampaignStageData.getStageSettings(1), false, 448, 520, 60);
    }

    private ReviveService attachedService(GameScreen screen) throws Exception {
        Field field = GameScreen.class.getDeclaredField("reviveService");
        field.setAccessible(true);
        return (ReviveService) field.get(screen);
    }
}
