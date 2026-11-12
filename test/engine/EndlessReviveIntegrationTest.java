package engine;

import audio.AudioManager;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import entity.Bullet;
import entity.BulletPool;
import entity.Entity;
import entity.GrenadeFragment;
import entity.Ship;
import entity.ZigzagEnemyShip;
import entity.item.Hyperfire;
import entity.item.ItemManager;
import entity.item.OrbitalAnnihilator;
import event.EnemyDefeatedEvent;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import records.RunSession;
import records.Achievement;
import records.RecordsService;
import revive.ReviveManager;
import revive.ReviveService;
import screen.InfiniteGameScreen;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated end-to-end checks using the actual Core settlement/payment path. */
class EndlessReviveIntegrationTest {
    @TempDir Path temp;
    PlayerAssets assets;
    FileManager files;
    Path save;
    ShopTestFixtures.SaveFile paymentSave;
    EndlessRewards rewards;
    RunSession session;
    InfiniteGameScreen game;
    ReviveService service;
    ReviveManager manager;
    Ship ship;
    private final java.util.Map<String, Object> previousCore = new java.util.HashMap<>();
    private boolean previousMute;
    private RecordsService records;

    @BeforeEach void setup() throws Exception {
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        for (String name : List.of("playerAssets", "shopController", "campaignRewards", "endlessRewards", "recordsService"))
            previousCore.put(name, get(Core.class, name));
        assets = new PlayerAssets();
        assets.restore(new PlayerAssetsState(100, Set.of("owned"), Map.of("legacy", 3)));
        files = Core.getFileManager();
        save = temp.resolve("wallet.properties");
        paymentSave = new ShopTestFixtures.SaveFile(files, save);
        ShopController shop = ShopTestFixtures.upgradeController(assets, paymentSave);
        rewards = new EndlessRewards(assets, files, save);
        set(Core.class, "playerAssets", assets);
        set(Core.class, "shopController", shop);
        set(Core.class, "campaignRewards", new CampaignRewards(assets, files, save));
        set(Core.class, "endlessRewards", rewards);
        set(Core.class, "recordsService", null);
        manager = new ReviveManager(ReviveManager.Mode.ENDLESS);
        service = new ReviveService(manager, shop, Core::settleCreditsForRevive);
        session = new RunSession("endless-revive-" + temp.getFileName());
        game = new InfiniteGameScreen(new GameState(1, 1234, 1, 12, 42), 448, 520, 60, session);
        game.setReviveService(service);
        game.initialize();
        ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));
        set(game, "isRunning", true);
        for (int k = 0; k < 256; k++) key(k, false);
        session.startActiveSegment(System.nanoTime());
    }

    @AfterEach void cleanup() throws Exception {
        for (int k = 0; k < 256; k++) key(k, false);
        if (records != null) records.close();
        AudioManager.stopBGM();
        AudioManager.setMuted(previousMute);
        for (var entry : previousCore.entrySet()) set(Core.class, entry.getKey(), entry.getValue());
    }

    @Test void fatalFrameStopsBeforeGrenadeKillsAndEscapeWinsOverConfirm() throws Exception {
        ZigzagEnemyShip survivor = new ZigzagEnemyShip(100, 200, DrawManager.SpriteType.EnemyShipA1, 1);
        enemies().add(survivor);
        @SuppressWarnings("unchecked") Set<GrenadeFragment> fragments = (Set<GrenadeFragment>) get(game, "fragments");
        fragments.add(new GrenadeFragment(110, 210, 0));
        int score = game.getGameState().getScore();
        bullets("enemyBullets").add(BulletPool.getBullet(ship.getPositionX() + 4, ship.getPositionY() + 4, 4, Bullet.Owner.ENEMY));
        update();
        assertEquals(true, get(game, "reviveOfferOpen"));
        assertTrue(enemies().contains(survivor));
        assertEquals(score, game.getGameState().getScore());
        assertEquals(42, game.getGameState().getShipsDestroyed());
        assertEquals(1, fragments.size());
        key(KeyEvent.VK_ESCAPE, true); key(KeyEvent.VK_ENTER, true); update();
        assertEquals(true, get(game, "gameOver"));
        assertEquals(100, assets.getBalance()); assertEquals(0, manager.getRevivesUsed());
    }

    @Test void fatalHitPausesWorldHeldFireCannotBuyAndSameRunResumesSafely() throws Exception {
        assets.restore(new PlayerAssetsState(0, Set.of("owned"), Map.of("legacy", 3)));
        try (EndlessRewards.RunSubscription subscription = rewards.subscribe(game.getGameplayEvents(), session.getRunId())) {
            for (int i = 0; i < 20; i++)
                game.getGameplayEvents().publish(new EnemyDefeatedEvent(session.getRunId(), "kill-" + i));
            ItemManager items = (ItemManager) get(game, "itemManager");
            items.getBuffManager().addBuff(new Hyperfire(10, 10, 5000), ship);
            assertTrue(ship.isHyperfireEnabled());
            ZigzagEnemyShip survivor = new ZigzagEnemyShip(200, 470, DrawManager.SpriteType.EnemyShipA1, 3);
            enemies().add(survivor);
            survivor.update();
            Cooldown spawn = (Cooldown) get(game, "spawnCooldown");
            spawn.reset();
            Cooldown grenadeCooldown = (Cooldown) get(ship, "grenadeCooldown");
            grenadeCooldown.reset();
            ship.addGrenades(1);
            OrbitalAnnihilator laser = (OrbitalAnnihilator) get(game, "annihilator");
            laser.arm(); laser.trigger();
            set(laser, "state", enumValue(laser, "state", "FIRING"));
            set(laser, "phaseStart", System.currentTimeMillis() - 300);
            set(game, "annihilatorCooldownUntil", System.currentTimeMillis() + 4000);
            set(game, "lastSurvivalTick", System.currentTimeMillis() - 250);
            key(KeyEvent.VK_SPACE, true);
            fatalBullet();
            assertTrue(ship.isDestroyed());
            assertFalse(ship.isHyperfireEnabled());
            update();
            assertEquals(true, get(game, "reviveOfferOpen"));
            assertEquals(false, get(game, "gameOver"));
            long active = session.getActiveDurationMillis(System.nanoTime());
            int score = game.getGameState().getScore();
            int x = survivor.getPositionX(), y = survivor.getPositionY();
            int bullets = bullets("enemyBullets").size();
            for (int i = 0; i < 4; i++) update();
            assertEquals(0, manager.getRevivesUsed());
            assertEquals(20, rewards.getPendingCurrency());
            assertEquals(score, game.getGameState().getScore());
            assertEquals(x, survivor.getPositionX()); assertEquals(y, survivor.getPositionY());
            assertEquals(bullets, bullets("enemyBullets").size());
            assertEquals(active, session.getActiveDurationMillis(System.nanoTime()));
            // Simulate a 12-second offer without sleeping: age the wall timers together.
            age(game, "reviveOfferStarted", 12000);
            age(game, "lastSurvivalTick", 12000);
            age(game, "annihilatorCooldownUntil", 12000);
            age(spawn, "time", 12000);
            age(grenadeCooldown, "time", 12000);
            age(get(survivor, "animationCooldown"), "time", 12000);
            age(laser, "phaseStart", 12000);
            key(KeyEvent.VK_SPACE, false); update();
            key(KeyEvent.VK_SPACE, true); update();
            assertEquals(3, game.getGameState().getLivesRemaining());
            assertEquals(1, manager.getRevivesUsed());
            assertEquals(1, service.getRemainingRevives()); assertEquals(50, service.getNextReviveCost());
            assertEquals(0, assets.getBalance()); assertEquals(0, files.loadPlayerAssets(save).getBalance());
            assertEquals(0, rewards.getPendingCurrency());
            assertSame(ship, get(game, "ship")); assertSame(session, get(game, "runSession"));
            assertEquals(42, game.getGameState().getShipsDestroyed());
            assertEquals(12, game.getGameState().getBulletsShot());
            assertEquals(score, game.getGameState().getScore());
            assertEquals(3, ((Number) call(game, "getSpeedLevel")).intValue());
            assertSame(survivor, enemies().get(0)); assertEquals(y, survivor.getPositionY());
            assertFalse(ship.isDestroyed()); assertTrue(ship.isInvulnerable());
            assertEquals(1, ship.getGrenadeCount());
            assertFalse(spawn.checkFinished()); assertFalse(grenadeCooldown.checkFinished());
            assertTrue(laser.isFiring());
            assertTrue(System.currentTimeMillis() - (long) get(laser, "phaseStart") < 1000);
            assertTrue(System.currentTimeMillis() - (long) get(game, "lastSurvivalTick") < 1000);
            assertTrue((long) get(game, "annihilatorCooldownUntil") > System.currentTimeMillis());
            assertTrue(ship.getPositionX() >= 1 && ship.getPositionX() + ship.getWidth() < game.getWidth());
            assertEquals(490, ship.getPositionY());
            assertFalse(overlap(ship, survivor));
            // Protection applies to both bullets and contact, with controls ready immediately.
            enemies().add(new ZigzagEnemyShip(ship.getPositionX(), ship.getPositionY(), DrawManager.SpriteType.EnemyShipB1, 1));
            bullets("enemyBullets").add(BulletPool.getBullet(ship.getPositionX() + 4, ship.getPositionY() + 4, 4, Bullet.Owner.ENEMY));
            call(game, "manageCollisions"); assertEquals(3, game.getGameState().getLivesRemaining());
            int startX = ship.getPositionX();
            int direction = startX > game.getWidth() / 2 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
            key(direction, true); call(game, "updatePlayerControls"); key(direction, false);
            assertNotEquals(startX, ship.getPositionX());
            assertEquals(12, game.getGameState().getBulletsShot(), "Confirm still held cannot fire");
            key(KeyEvent.VK_SPACE, false); call(game, "releaseReviveHeldKeys");
            key(KeyEvent.VK_SPACE, true); call(game, "updatePlayerControls"); key(KeyEvent.VK_SPACE, false);
            assertEquals(13, game.getGameState().getBulletsShot());
            age(get(ship, "invulnerabilityCooldown"), "time", 2100);
            assertFalse(ship.isInvulnerable(), "Fresh protection expires normally");
            assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
        }
        assertEquals(0, assets.getBalance(), "Closing reward subscription cannot pay kills twice");
        session.stopActiveSegment(System.nanoTime());
        var record = session.finish(game.getGameState().getScore(), 42, System.currentTimeMillis());
        assertSame(record, session.finish(0, 0, 0));
        assertEquals(session.getRunId(), record.getRunId());
    }

    @Test void insufficientFundsRetriesOnlyAfterReleaseAndGiveUpCompletesNormally() throws Exception {
        assets.restore(new PlayerAssetsState(19, null, null));
        fatalBullet(); update(); tap(KeyEvent.VK_ENTER);
        assertEquals(true, get(game, "reviveOfferOpen"));
        assertTrue(feedback().contains("Not enough Credits"));
        for (int i = 0; i < 5; i++) update();
        assertEquals(19, assets.getBalance()); assertEquals(0, manager.getRevivesUsed());
        tap(KeyEvent.VK_DOWN); tap(KeyEvent.VK_ENTER);
        assertEquals(true, get(game, "gameOver")); assertFalse(game.isAbandoned());
        assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
        age(get(game, "screenFinishedCooldown"), "time", 1600); update();
        assertEquals(false, get(game, "isRunning"));
    }

    @Test void fullBalanceSettlementFailureRetainsPendingCreditsAndEscapeDeclines() throws Exception {
        assets.restore(new PlayerAssetsState(Integer.MAX_VALUE, null, null));
        try (EndlessRewards.RunSubscription subscription = rewards.subscribe(game.getGameplayEvents(), session.getRunId())) {
            game.getGameplayEvents().publish(new EnemyDefeatedEvent(session.getRunId(), "pending"));
            fatalBullet(); update(); tap(KeyEvent.VK_ENTER);
            assertTrue(feedback().contains("settlement failed"));
            assertEquals(Integer.MAX_VALUE, assets.getBalance()); assertEquals(1, rewards.getPendingCurrency());
            assertEquals(0, manager.getRevivesUsed());
            tap(KeyEvent.VK_ESCAPE);
            assertEquals(true, get(game, "gameOver")); assertFalse(game.isAbandoned());
        }
    }

    @Test void settlementSaveFailureNeverChargesAndRetryDoesNotRepayPendingKills() throws Exception {
        assets.restore(new PlayerAssetsState(0, null, null));
        Files.createDirectory(save); Path blocker = Files.createFile(save.resolve("blocker"));
        try (EndlessRewards.RunSubscription subscription = rewards.subscribe(game.getGameplayEvents(), session.getRunId())) {
            for (int i = 0; i < 20; i++) game.getGameplayEvents().publish(new EnemyDefeatedEvent(session.getRunId(), "p" + i));
            fatalBullet(); update(); key(KeyEvent.VK_ENTER, true); update();
            assertTrue(feedback().contains("could not be saved"));
            assertEquals(20, assets.getBalance()); assertEquals(0, rewards.getPendingCurrency());
            assertEquals(0, manager.getRevivesUsed()); assertEquals(0, game.getGameState().getLivesRemaining());
            Files.delete(blocker); Files.delete(save);
            update(); update();
            assertEquals(20, assets.getBalance(), "Held key must not retry after storage recovers");
            key(KeyEvent.VK_ENTER, false); update(); key(KeyEvent.VK_ENTER, true); update(); key(KeyEvent.VK_ENTER, false);
            assertEquals(3, game.getGameState().getLivesRemaining());
            assertEquals(0, assets.getBalance()); assertEquals(1, manager.getRevivesUsed());
            assertEquals(0, files.loadPlayerAssets(save).getBalance());
        }
    }

    @Test void paymentSaveFailureRollsBackOwnershipBalanceAndCountThenRetriesOnce() throws Exception {
        files.savePlayerAssets(assets.snapshot(), save);
        Path blocked = temp.resolve("payment-blocked"); Files.createDirectory(blocked);
        Files.createFile(blocked.resolve("blocker")); paymentSave.setPath(blocked);
        fatalBullet(); update(); key(KeyEvent.VK_ENTER, true); update();
        assertTrue(feedback().contains("could not be saved"));
        assertEquals(100, assets.getBalance()); assertEquals(100, files.loadPlayerAssets(save).getBalance());
        assertEquals(0, manager.getRevivesUsed()); assertEquals(true, get(game, "reviveOfferOpen"));
        assertEquals(Set.of("owned"), assets.snapshot().getUnlockedContentIds());
        assertEquals(Map.of("legacy", 3), assets.snapshot().getItemQuantities());
        paymentSave.setPath(save); update(); assertEquals(100, assets.getBalance());
        key(KeyEvent.VK_ENTER, false); update(); tap(KeyEvent.VK_ENTER);
        assertEquals(80, assets.getBalance()); assertEquals(80, files.loadPlayerAssets(save).getBalance());
        assertEquals(1, manager.getRevivesUsed());
    }

    @Test void secondReviveCostsFiftyAndThirdDeathEndsWithoutOffer() throws Exception {
        fatalBullet(); update(); tap(KeyEvent.VK_ENTER);
        assertEquals(80, assets.getBalance());
        set(game, "lives", 1); age(get(ship, "invulnerabilityCooldown"), "time", 2100);
        enemies().add(new ZigzagEnemyShip(ship.getPositionX(), ship.getPositionY(), DrawManager.SpriteType.EnemyShipA1, 1));
        call(game, "manageCollisions"); assertEquals(0, game.getGameState().getLivesRemaining());
        update(); assertEquals(50, service.getNextReviveCost()); tap(KeyEvent.VK_SPACE);
        assertEquals(30, assets.getBalance()); assertEquals(2, manager.getRevivesUsed());
        set(game, "lives", 1); age(get(ship, "invulnerabilityCooldown"), "time", 2100);
        fatalBullet(); update();
        assertEquals(false, get(game, "reviveOfferOpen")); assertEquals(true, get(game, "gameOver"));
        assertEquals(30, assets.getBalance()); assertEquals(0, service.getRemainingRevives());
    }

    @Test void noServiceStillEndsNormally() throws Exception {
        set(game, "reviveService", null); fatalBullet(); update();
        assertEquals(true, get(game, "gameOver")); assertEquals(false, get(game, "reviveOfferOpen"));
    }

    @Test void loggingFailureAfterAtomicCommitCannotRollBackAPaidRevive() throws Exception {
        Handler failure = new Handler() {
            public void publish(LogRecord record) { throw new SecurityException("Injected logging failure"); }
            public void flush() {} public void close() {}
        };
        assertTrue(service.openOffer());
        Core.getLogger().addHandler(failure);
        try {
            assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
        } finally { Core.getLogger().removeHandler(failure); }
        assertEquals(80, assets.getBalance()); assertEquals(80, files.loadPlayerAssets(save).getBalance());
        assertEquals(1, manager.getRevivesUsed());
    }

    @Test void modalTextFitsAndGenerateVisualPreviews() throws Exception {
        String[][] messages = {
            {}, {"Not enough Credits.", "No payment made. Retry or give up."},
            {"Credit settlement failed.", "Rewards kept pending. No payment made."},
            {"Credits could not be saved.", "No payment made. Retry or give up."}
        };
        String[] names = {"offer", "insufficient", "settlement", "save-error"};
        Font font = (Font) get(DrawManager.class, "fontRegular");
        var g = new BufferedImage(448, 520, BufferedImage.TYPE_INT_RGB).createGraphics();
        FontMetrics metrics = g.getFontMetrics(font);
        for (String text : new String[] {"Balance: 2147483647 Credits", "Earned Credits settle on confirm.",
                "Up/Down: choose  Enter/Space: confirm", "Esc: give up  |  Release to retry"})
            assertTrue(metrics.stringWidth(text) <= 392, text + " is clipped: " + metrics.stringWidth(text));
        for (int i = 0; i < messages.length; i++) {
            for (String text : messages[i]) assertTrue(metrics.stringWidth(text) <= 392, text + " is clipped");
            Core.getDrawManager().initDrawing(game);
            Core.getDrawManager().drawReviveOffer(game, 20, 2, 2147483647, i == 2, messages[i]);
            BufferedImage preview = (BufferedImage) get(DrawManager.class, "backBuffer");
            ImageIO.write(preview, "png", Path.of(System.getProperty("invaders.revivePreviews", temp.toString()), names[i] + ".png").toFile());
            Core.getDrawManager().completeDrawing(game);
        }
        g.dispose();
    }

    @Test void revivesKeepOneRecordAndDoNotRepeatAchievementsOrRewards() throws Exception {
        records = new RecordsService(temp.resolve("records.properties"));
        set(Core.class, "recordsService", records);
        set(game, "recordsService", records);
        set(game, "score", 0);
        set(game, "shipsDestroyed", 0);
        records.startRun(session.getRunId());
        try (EndlessRewards.RunSubscription subscription = rewards.subscribe(
                game.getGameplayEvents(), session.getRunId())) {
            for (int i = 0; i < 10; i++) defeatEnemy();
            assertEquals(10, rewards.getPendingCurrency());
            assertEquals(0, records.getRecordCount());
            Long firstUnlock = records.getUnlockTime(Achievement.FIRST_ENEMY);
            assertNotNull(firstUnlock);
            fatalBullet(); update(); tap(KeyEvent.VK_ENTER);
            assertEquals(90, assets.getBalance());
            assertEquals(0, records.getRecordCount(), "A revive must not complete the run");
            for (int i = 0; i < 10; i++) defeatEnemy();
            assertNotNull(records.getUnlockTime(Achievement.TWENTY_ENEMIES));
            assertEquals(firstUnlock, records.getUnlockTime(Achievement.FIRST_ENEMY));
            assertEquals(2, records.getCurrentRunUnlocks().size());
            set(game, "lives", 1);
            age(get(ship, "invulnerabilityCooldown"), "time", 2100);
            fatalBullet(); update(); tap(KeyEvent.VK_ENTER);
            assertEquals(50, assets.getBalance());
            assertEquals(0, rewards.getPendingCurrency());
            assertEquals(0, records.getRecordCount());
            set(game, "lives", 1);
            age(get(ship, "invulnerabilityCooldown"), "time", 2100);
            fatalBullet(); update();
            assertEquals(true, get(game, "gameOver"));
            age(get(game, "screenFinishedCooldown"), "time", 1600); update();
            assertEquals(false, get(game, "isRunning"));
            assertFalse(game.isAbandoned());
            var state = game.getGameState();
            assertEquals(20, state.getShipsDestroyed());
            assertEquals(200, state.getScore());
            var completed = session.finish(state.getScore(), state.getShipsDestroyed(), System.currentTimeMillis());
            assertTrue(records.completeRun(completed));
            assertFalse(records.completeRun(completed));
            assertEquals(1, records.getRecordCount());
            assertEquals(session.getRunId(), records.getTopRuns().get(0).getRunId());
            assertEquals(3, records.getCurrentRunUnlocks().size());
            // A duplicated defeat event remains deduplicated after both revives.
            var ids = ((java.util.Map<?, ?>) get(game, "defeatIds")).values();
            game.getGameplayEvents().publish(new EnemyDefeatedEvent(session.getRunId(), (String) ids.iterator().next()));
            assertEquals(0, rewards.getPendingCurrency());
        }
        assertEquals(50, assets.getBalance());
        assertEquals(50, files.loadPlayerAssets(save).getBalance());
        records.close();
        assertNull(records.getSaveError());
        RecordsService reloaded = new RecordsService(temp.resolve("records.properties"));
        try {
            assertEquals(1, reloaded.getRecordCount());
            assertEquals(20, reloaded.getMostEnemiesDefeated());
            assertEquals(200, reloaded.getHighestScore());
            assertNotNull(reloaded.getUnlockTime(Achievement.FIRST_ENEMY));
            assertNotNull(reloaded.getUnlockTime(Achievement.TWENTY_ENEMIES));
            assertNotNull(reloaded.getUnlockTime(Achievement.FIRST_RUN));
        } finally {
            reloaded.close();
        }
    }

    @Test void giveUpKeepsTheRunEligibleForCompletionAndANewRunGetsFreshChances() throws Exception {
        records = new RecordsService(temp.resolve("give-up-records.properties"));
        set(game, "recordsService", records);
        records.startRun(session.getRunId());
        fatalBullet(); update(); tap(KeyEvent.VK_ESCAPE);
        assertFalse(game.isAbandoned());
        assertEquals(0, records.getRecordCount());
        var state = game.getGameState();
        assertTrue(records.completeRun(session.finish(state.getScore(), state.getShipsDestroyed(), System.currentTimeMillis())));
        assertEquals(1, records.getRecordCount());
        assertEquals(100, assets.getBalance());
        ReviveService nextRun = new ReviveService(new ReviveManager(ReviveManager.Mode.ENDLESS),
                Core.getShopController(), Core::settleCreditsForRevive);
        assertEquals(2, nextRun.getRemainingRevives());
        assertEquals(20, nextRun.getNextReviveCost());
    }

    private void defeatEnemy() throws Exception {
        ZigzagEnemyShip enemy = new ZigzagEnemyShip(100, 100, DrawManager.SpriteType.EnemyShipA1, 1);
        enemies().add(enemy);
        bullets("bullets").add(BulletPool.getBullet(104, 104, -4, Bullet.Owner.PLAYER));
        call(game, "manageCollisions");
        call(game, "manageCollisions");
        assertFalse(enemies().contains(enemy));
    }

    private void fatalBullet() throws Exception {
        bullets("enemyBullets").add(BulletPool.getBullet(ship.getPositionX() + 4, ship.getPositionY() + 4, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");
        assertEquals(0, game.getGameState().getLivesRemaining());
    }
    private String feedback() throws Exception { return String.join(" ", (String[]) get(game, "reviveFeedback")); }
    private void update() throws Exception { call(game, "update"); }
    private void tap(int code) throws Exception { key(code, false); update(); key(code, true); update(); key(code, false); }
    @SuppressWarnings("unchecked") private List<ZigzagEnemyShip> enemies() throws Exception { return (List<ZigzagEnemyShip>) get(game, "enemies"); }
    @SuppressWarnings("unchecked") private Set<Bullet> bullets(String name) throws Exception { return (Set<Bullet>) get(game, name); }
    static void key(int code, boolean pressed) {
        KeyEvent e = new KeyEvent(new Canvas(), pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(e); else Core.getInputManager().keyReleased(e);
    }
    static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    static Object get(Object target, String name) throws Exception { return field(target, name).get(target instanceof Class ? null : target); }
    static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target instanceof Class ? null : target, value); }
    static void age(Object target, String name, long millis) throws Exception { set(target, name, (long) get(target, name) - millis); }
    static Object call(Object target, String name) throws Exception { Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target); }
    @SuppressWarnings({"rawtypes", "unchecked"}) static Object enumValue(Object target, String name, String value) throws Exception { return Enum.valueOf((Class) field(target, name).getType(), value); }
    static boolean overlap(Entity a, Entity b) { return a.getPositionX() < b.getPositionX() + b.getWidth() && a.getPositionX() + a.getWidth() > b.getPositionX() && a.getPositionY() < b.getPositionY() + b.getHeight() && a.getPositionY() + a.getHeight() > b.getPositionY(); }
}
