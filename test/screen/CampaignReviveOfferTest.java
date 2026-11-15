package screen;

import audio.AudioManager;
import campaign.CampaignStageData;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import engine.Core;
import engine.Cooldown;
import engine.DrawManager;
import engine.GameState;
import engine.SettlementResult;
import entity.Boss;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.Ship;
import event.LevelCompletedEvent;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import records.RecordsService;
import records.RunSession;
import revive.ReviveManager;
import revive.ReviveService;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignReviveOfferTest {
    @TempDir Path temp;
    private PlayerAssets assets;
    private ReviveService service;
    private AtomicInteger settlements;
    private RecordsService records;
    private Object previousRecords;
    private boolean muted;
    private GameScreen game;
    private RunSession session;
    private Path save;

    @BeforeEach
    void setUp() throws Exception {
        muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(1000, null, null)));
        save = temp.resolve("assets.properties");
        var shop = ShopTestFixtures.controller(assets,
                new ShopTestFixtures.SaveFile(Core.getFileManager(), save));
        settlements = new AtomicInteger();
        service = new ReviveService(new ReviveManager(ReviveManager.Mode.CAMPAIGN), shop, () -> {
            settlements.incrementAndGet();
            return SettlementResult.SUCCESS;
        });
        records = new RecordsService(temp.resolve("records.properties"));
        previousRecords = get(Core.class, "recordsService");
        set(Core.class, "recordsService", records);
        session = new RunSession("campaign-offer-" + temp.getFileName());
        records.startRun(session.getRunId());
        game = new GameScreen(new GameState(7, 123, 1, 4, 5),
                CampaignStageData.getStageSettings(7), false, 448, 520, 60,
                entity.ShipType.STANDARD, session);
        game.setReviveService(service);
        game.initialize();
        game.isRunning = true;
        releaseKeys();
    }

    @AfterEach
    void cleanUp() throws Exception {
        releaseKeys();
        records.close();
        set(Core.class, "recordsService", previousRecords);
        AudioManager.stopBGM();
        AudioManager.setMuted(muted);
    }

    @Test
    void deathPausesTheSameStageBossAndWorldWithoutCompletingTheRun() throws Exception {
        Boss boss = ((boss.BossManager) get(game, "bossManager")).getBoss();
        boss.takeDamage(47);
        int health = boss.getCurrentHealth();
        int bossX = boss.getPositionX();
        EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation");
        EnemyShip enemy = formation.iterator().next();
        int enemyX = enemy.getPositionX();
        int enemyY = enemy.getPositionY();
        Bullet bullet = BulletPool.getBullet(40, 200, -4, Bullet.Owner.PLAYER);
        bullets().add(bullet);
        session.startActiveSegment(System.nanoTime());
        assertTrue(game.damagePlayer(1, "offer test"));
        assertEquals(true, get(game, "reviveOfferOpen"));
        assertEquals(false, get(game, "levelFinished"));
        assertFalse(game.isStageCleared());
        long duration = session.getActiveDurationMillis(System.nanoTime());
        // Even an already expired finish timer must not close a pending offer.
        Cooldown finished = (Cooldown) get(game, "screenFinishedCooldown");
        finished.reset();
        set(finished, "time", System.currentTimeMillis() - 30000);
        for (int i = 0; i < 8; i++) {
            game.update();
            game.advanceStageIfReady();
        }
        assertTrue(game.isRunning);
        assertEquals(health, boss.getCurrentHealth());
        assertEquals(bossX, boss.getPositionX());
        assertEquals(enemyX, enemy.getPositionX());
        assertEquals(enemyY, enemy.getPositionY());
        assertEquals(200, bullet.getPositionY());
        assertSame(formation, get(game, "enemyShipFormation"));
        assertSame(session, get(game, "runSession"));
        assertEquals(123, game.getGameState().getScore());
        assertEquals(5, game.getGameState().getShipsDestroyed());
        assertEquals(0, get(game, "currentWave"));
        assertEquals(0, records.getRecordCount());
        assertEquals(duration, session.getActiveDurationMillis(System.nanoTime()));
    }

    @Test
    void heldFireCannotBuyAndOneFreshConfirmChargesOnce() throws Exception {
        key(KeyEvent.VK_SPACE, true);
        game.damagePlayer(1, "held fire");
        for (int i = 0; i < 5; i++) game.update();
        key(KeyEvent.VK_SPACE, false); game.update();
        key(KeyEvent.VK_ENTER, true);
        for (int i = 0; i < 5; i++) game.update();
        assertEquals(1, settlements.get());
        assertEquals(950, assets.getBalance());
        assertEquals(1, service.getRemainingRevives());
        assertEquals(150, service.getNextReviveCost());
        assertTrue(Files.exists(save));
        assertEquals(false, get(game, "reviveOfferOpen"));
        assertEquals(2, game.getPlayerStatus().getLives());
    }

    @Test
    void giveUpDeclinesTheOfferAndEndsThroughTheNormalFailedStagePath() throws Exception {
        game.damagePlayer(1, "give up");
        tap(KeyEvent.VK_DOWN);
        assertEquals(true, get(game, "reviveGiveUpSelected"));
        tap(KeyEvent.VK_ENTER);
        assertEquals(false, get(game, "reviveOfferOpen"));
        assertEquals(true, get(game, "levelFinished"));
        assertFalse(game.isStageCleared());
        assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
        assertEquals(1000, assets.getBalance());
        set(get(game, "screenFinishedCooldown"), "time", System.currentTimeMillis() - 30000);
        game.update();
        assertFalse(game.isRunning);
        assertTrue(records.enemyDefeated(session.getRunId(), "still-same-run"),
                "Declining must not abandon the run");
        var state = game.getGameState();
        assertTrue(records.completeRun(session.finish(state.getScore(),
                state.getShipsDestroyed(), System.currentTimeMillis())));
        assertEquals(1, records.getRecordCount());
    }

    @Test
    void escapeWinsOverSimultaneousConfirmAndHoldingEscapeAtDeathNeedsRelease() throws Exception {
        key(KeyEvent.VK_ESCAPE, true);
        game.damagePlayer(1, "escape");
        game.update();
        assertEquals(true, get(game, "reviveOfferOpen"));
        key(KeyEvent.VK_ESCAPE, false); game.update();
        key(KeyEvent.VK_ESCAPE, true); key(KeyEvent.VK_ENTER, true);
        game.update();
        assertEquals(true, get(game, "levelFinished"));
        assertEquals(0, settlements.get());
        assertEquals(2, service.getRemainingRevives());
    }

    @Test
    void exhaustedChancesEndNormallyWithoutOpeningAnOffer() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertTrue(service.openOffer());
            assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
        }
        assertEquals(800, assets.getBalance());
        game.damagePlayer(1, "exhausted");
        game.advanceStageIfReady();
        assertEquals(false, get(game, "reviveOfferOpen"));
        assertEquals(true, get(game, "levelFinished"));
        assertEquals(800, assets.getBalance());
        assertEquals(2, settlements.get());
    }

    @Test
    void legacyAndCustomScreensRetainTheirExistingDeathFlow() throws Exception {
        GameScreen legacy = new GameScreen(new GameState(1, 0, 1, 0, 0),
                CampaignStageData.getStageSettings(1), false, 448, 520, 60);
        legacy.initialize();
        assertTrue(legacy.damagePlayer(1, "legacy"));
        legacy.advanceStageIfReady();
        assertEquals(true, get(legacy, "levelFinished"));
        assertEquals(false, get(legacy, "reviveOfferOpen"));
        WaveDefinition wave = new WaveDefinition("wave", new EnemyCell[][] {{new EnemyCell("A", 1)}},
                20, 100, 60, 2000);
        GameScreen custom = GameScreen.forCustomLevel(new GameState(1, 0, 1, 0, 0),
                new LevelDefinition(1, "custom", "Custom", List.of(wave)), 1.0, 448, 520, 60);
        custom.setReviveService(service);
        custom.initialize();
        assertTrue(custom.damagePlayer(1, "custom"));
        custom.advanceStageIfReady();
        assertEquals(true, get(custom, "levelFinished"));
        assertEquals(false, get(custom, "reviveOfferOpen"));
        assertEquals(0, settlements.get());
    }

    @Test
    void aFatalBulletStopsLaterKillsInTheSameCollisionPass() throws Exception {
        Ship ship = (Ship) get(game, "ship");
        EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation");
        EnemyShip enemy = formation.iterator().next();
        Set<Bullet> ordered = new LinkedHashSet<Bullet>();
        ordered.add(BulletPool.getBullet(ship.getPositionX() + 4, ship.getPositionY() + 4,
                4, Bullet.Owner.ENEMY));
        ordered.add(BulletPool.getBullet(enemy.getPositionX() + 4, enemy.getPositionY() + 4,
                -4, Bullet.Owner.PLAYER));
        set(game, "bullets", ordered);
        game.update();
        assertEquals(true, get(game, "reviveOfferOpen"));
        assertFalse(enemy.isDestroyed());
        assertEquals(123, game.getGameState().getScore());
        assertEquals(5, game.getGameState().getShipsDestroyed());
        assertEquals(1, ordered.size());
    }

    @Test
    void zeroLivesTakePriorityOverAnEmptyWaveAndNeverPublishAClearReward() throws Exception {
        AtomicInteger clears = new AtomicInteger();
        game.getGameplayEvents().subscribe(LevelCompletedEvent.class, event -> clears.incrementAndGet());
        EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation");
        for (EnemyShip enemy : formation) formation.destroy(enemy);
        game.getPlayerStatus().takeDamage(1);
        game.advanceStageIfReady();
        assertEquals(true, get(game, "reviveOfferOpen"));
        assertFalse(game.isStageCleared());
        assertEquals(0, clears.get());
        assertEquals(0, get(game, "currentWave"));
    }

    @Test
    void campaignAndEndlessRenderTheirOwnLabelsUsingTheSharedModal() throws Exception {
        game.damagePlayer(1, "render");
        game.update();
        Path previews = Path.of(System.getProperty("invaders.revivePreviews", temp.toString()));
        Files.createDirectories(previews);
        ImageIO.write((BufferedImage) get(DrawManager.class, "backBuffer"), "png",
                previews.resolve("campaign-offer.png").toFile());
        var g = new BufferedImage(448, 520, BufferedImage.TYPE_INT_RGB).createGraphics();
        FontMetrics metrics = g.getFontMetrics((Font) get(DrawManager.class, "fontRegular"));
        for (String line : new String[] {"Campaign paused", "Earned Credits settle on confirm.",
                "Revive paid. Recovery needs retry.", "Retry to resume. No extra payment."})
            assertTrue(metrics.stringWidth(line) <= 392, line);
        Core.getDrawManager().initDrawing(game);
        Core.getDrawManager().drawReviveOffer(game, 20, 2, 1000, false, new String[0]);
        ImageIO.write((BufferedImage) get(DrawManager.class, "backBuffer"), "png",
                previews.resolve("endless-offer.png").toFile());
        Core.getDrawManager().completeDrawing(game);
        g.dispose();
    }

    private void tap(int code) {
        key(code, false); game.update();
        key(code, true); game.update();
        key(code, false);
    }

    private void releaseKeys() {
        for (int code = 0; code < 256; code++) key(code, false);
    }

    private void key(int code, boolean down) {
        var event = new KeyEvent(new Canvas(), down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    @SuppressWarnings("unchecked")
    private Set<Bullet> bullets() throws Exception {
        return (Set<Bullet>) get(game, "bullets");
    }

    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
                type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Lifecycle fields belong to Screen.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object object, String name) throws Exception {
        return field(object, name).get(object instanceof Class ? null : object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object, name).set(object instanceof Class ? null : object, value);
    }
}
