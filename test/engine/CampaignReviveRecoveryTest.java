package engine;

import audio.AudioManager;
import boss.BossAttackSystem;
import boss.BossManager;
import campaign.CampaignStageData;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import entity.*;
import entity.item.Hyperfire;
import entity.item.ItemManager;
import entity.item.OrbitalAnnihilator;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.LevelCompletedEvent;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import records.RecordsService;
import records.RunSession;
import revive.ReviveManager;
import revive.ReviveService;
import screen.GameScreen;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class CampaignReviveRecoveryTest {
    @TempDir Path temp;
    private final Map<String, Object> previous = new HashMap<>();
    private PlayerAssets assets;
    private FileManager files;
    private Path save;
    private ShopTestFixtures.SaveFile paymentSave;
    private CampaignRewards rewards;
    private ReviveService service;
    private RecordsService records;
    private GameScreen game;
    private RunSession session;
    private Ship ship;
    private boolean muted;

    @BeforeEach void setUp() throws Exception {
        muted = AudioManager.isMuted(); AudioManager.setMuted(true);
        for (String name : List.of("playerAssets", "shopController", "campaignRewards", "endlessRewards", "recordsService"))
            previous.put(name, get(Core.class, name));
        files = Core.getFileManager(); assets = new PlayerAssets(); save = temp.resolve("assets.properties"); fund(1000);
        paymentSave = new ShopTestFixtures.SaveFile(files, save);
        var shop = ShopTestFixtures.controller(assets, paymentSave);
        rewards = new CampaignRewards(assets, files, save);
        records = new RecordsService(temp.resolve("records.properties"));
        set(Core.class, "playerAssets", assets); set(Core.class, "shopController", shop);
        set(Core.class, "campaignRewards", rewards); set(Core.class, "endlessRewards", new EndlessRewards(assets, files, save));
        set(Core.class, "recordsService", records);
        service = new ReviveService(new ReviveManager(ReviveManager.Mode.CAMPAIGN), shop, Core::settleCreditsForRevive);
        createGame(1, ShipType.STANDARD, CampaignUpgrades.none(), null);
    }

    @AfterEach void cleanUp() throws Exception {
        releaseKeys(); records.close(); AudioManager.stopBGM(); AudioManager.setMuted(muted);
        for (var entry : previous.entrySet()) set(Core.class, entry.getKey(), entry.getValue());
    }

    @Test void aLongOfferPreservesTheWorldAndAllSharedAndBossTimers() throws Exception {
        createGame(7, ShipType.STANDARD, CampaignUpgrades.none(), null);
        EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation"); formation.update();
        List<EnemyShip> enemies = new ArrayList<>(); formation.forEach(enemies::add);
        Cooldown shared = Core.getVariableCooldown(3000, 1000); shared.reset();
        enemies.get(0).setShootingCooldown(shared); enemies.get(1).setShootingCooldown(shared);
        EnemyShip enemy = enemies.get(0); int enemyX = enemy.getPositionX(), enemyY = enemy.getPositionY();
        BossManager manager = (BossManager) get(game, "bossManager"); Boss boss = manager.getBoss();
        boss.takeDamage(137); manager.sync();
        int health = boss.getCurrentHealth(), phase = boss.getPhase(), bossX = boss.getPositionX();
        BossAttackSystem attacks = manager.getAttackSystem();
        Cooldown windup = Core.getCooldown(1500); windup.reset();
        Cooldown buff = Core.getCooldown(3000); buff.reset();
        set(attacks, "windupCooldown", windup); set(attacks, "buffCooldown", buff); set(attacks, "buffMultiplier", 0.4);
        OrbitalAnnihilator laser = (OrbitalAnnihilator) get(game, "annihilator"); laser.arm(); laser.trigger();
        set(laser, "state", enumValue(laser, "state", "FIRING")); set(laser, "phaseStart", System.currentTimeMillis() - 300);
        Cooldown grenade = (Cooldown) get(ship, "grenadeCooldown"); grenade.reset(); ship.addGrenades(1);
        Bullet hazard = BulletPool.getBullet(game.getWidth() / 2, game.getHeight() - 30, 4, Bullet.Owner.ENEMY);
        bullets().add(hazard); session.startActiveSegment(System.nanoTime()); fatal();
        long active = session.getActiveDurationMillis(System.nanoTime());
        Set<Cooldown> timers = timers(formation, manager);
        Map<Cooldown, Long> remaining = new IdentityHashMap<>(); Map<Cooldown, Integer> durations = new IdentityHashMap<>();
        for (Cooldown timer : timers) {
            assertNotEquals(0L, get(timer, "pausedAt")); durations.put(timer, (int) get(timer, "duration"));
            remaining.put(timer, (long) get(timer, "time") == 0 ? Long.MIN_VALUE
                    : (long) get(timer, "time") + durations.get(timer) - (long) get(timer, "pausedAt"));
            if ((long) get(timer, "time") != 0) age(timer, "time", 12000);
            age(timer, "pausedAt", 12000);
        }
        age(game, "reviveOfferStarted", 12000); age(game, "gameStartTime", 12000);
        age(laser, "phaseStart", 12000); age(laser, "pausedAt", 12000); age(grenade, "time", 12000);
        tick(); tick(); assertEquals(active, session.getActiveDurationMillis(System.nanoTime()));
        assertEquals(enemyX, enemy.getPositionX()); assertEquals(enemyY, enemy.getPositionY());
        assertEquals(health, boss.getCurrentHealth()); assertEquals(bossX, boss.getPositionX());
        assertFalse(shared.checkFinished()); assertFalse(windup.checkFinished());
        int projectileX = hazard.getPositionX(), projectileY = hazard.getPositionY(); confirm();
        assertEquals(950, assets.getBalance()); assertEquals(950, files.loadPlayerAssets(save).getBalance());
        assertEquals(2, game.getPlayerStatus().getLives()); assertFalse((boolean) get(game, "reviveOfferOpen"));
        assertSame(ship, get(game, "ship")); assertSame(session, get(game, "runSession"));
        assertSame(formation, get(game, "enemyShipFormation")); assertSame(boss, manager.getBoss());
        assertEquals(health, boss.getCurrentHealth()); assertEquals(phase, boss.getPhase());
        assertEquals(enemyX, enemy.getPositionX()); assertEquals(enemyY, enemy.getPositionY());
        assertEquals(projectileX, hazard.getPositionX()); assertEquals(projectileY, hazard.getPositionY());
        assertTrue(bullets().contains(hazard)); assertEquals(321, game.getGameState().getScore());
        assertEquals(12, game.getGameState().getShipsDestroyed()); assertEquals(active, session.getActiveDurationMillis(System.nanoTime()));
        for (Cooldown timer : timers) {
            assertEquals(0L, get(timer, "pausedAt")); assertEquals(durations.get(timer), get(timer, "duration"));
            if (remaining.get(timer) != Long.MIN_VALUE) {
                long after = (long) get(timer, "time") + durations.get(timer) - System.currentTimeMillis();
                assertTrue(Math.abs(after - remaining.get(timer)) < 300, "Timer changed: " + (after - remaining.get(timer)));
            }
        }
        assertTrue(laser.isFiring()); assertTrue(System.currentTimeMillis() - (long) get(laser, "phaseStart") < 1000);
        assertEquals(0L, get(laser, "pausedAt")); assertFalse(grenade.checkFinished()); assertEquals(1, ship.getGrenadeCount());
        assertTrue(ship.isInvulnerable()); assertFalse(ship.isDestroyed());
        assertTrue(ship.getPositionX() >= 1 && ship.getPositionX() + ship.getWidth() < game.getWidth());
        assertFalse(overlap(ship, hazard)); assertFalse(damage(99)); assertEquals(2, game.getPlayerStatus().getLives());
        age(get(ship, "invulnerabilityCooldown"), "time", 2100);
        assertTrue(damage(1)); assertEquals(1, game.getPlayerStatus().getLives());
    }

    @ParameterizedTest @EnumSource(ShipType.class)
    void hullThreeRecoveryKeepsBaseStatsAndRemovesLostBuffs(ShipType type) throws Exception {
        Set<String> owned = new HashSet<>();
        for (PermanentUpgrade upgrade : PermanentUpgrade.values())
            for (int tier = 1; tier <= 3; tier++) owned.add(upgrade.getProductId(tier));
        assertTrue(assets.restore(new PlayerAssetsState(1000, owned, null)));
        createGame(1, type, CampaignUpgrades.from(assets.snapshot()), null);
        int speed = ship.getBaseSpeed(), interval = ship.getBaseShootingInterval();
        ((ItemManager) get(game, "itemManager")).getBuffManager().addBuff(new Hyperfire(10, 10, 5000), ship);
        game.getPlayerStatus().applySlow(ship); fatal(); assertFalse(ship.isHyperfireEnabled()); confirm();
        assertEquals((type.getMaxLives() + 4) / 2, game.getPlayerStatus().getLives());
        assertEquals(speed, ship.getSpeed()); assertEquals(interval, ship.getShootingInterval());
        assertFalse(game.getPlayerStatus().isSlowActive()); assertFalse(game.getPlayerStatus().isShieldActive());
        assertFalse(ship.isHyperfireEnabled()); assertFalse(ship.isShieldActive());
        assertTrue(ship.isInvulnerable()); assertFalse(ship.isDestroyed()); assertEquals(1, service.getRemainingRevives());
    }

    @Test void heldConfirmCannotBuyOrFireTwiceAndControlsWorkAfterRelease() throws Exception {
        key(KeyEvent.VK_SPACE, true); fatal(); tick(); tick();
        assertEquals(1000, assets.getBalance()); assertEquals(2, service.getRemainingRevives());
        key(KeyEvent.VK_SPACE, false); tick(); key(KeyEvent.VK_SPACE, true); tick();
        assertEquals(950, assets.getBalance()); int shots = game.getGameState().getBulletsShot();
        tick(); tick(); assertEquals(shots, game.getGameState().getBulletsShot());
        int x = ship.getPositionX(); int move = x > game.getWidth() / 2 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        key(move, true); tick(); key(move, false); assertNotEquals(x, ship.getPositionX());
        key(KeyEvent.VK_SPACE, false); tick(); key(KeyEvent.VK_SPACE, true); tick(); key(KeyEvent.VK_SPACE, false);
        assertTrue(game.getGameState().getBulletsShot() > shots);
        assertEquals(950, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
    }

    @Test void insufficientFundsKeepTheOfferPausedWithoutUsingAChance() throws Exception {
        fund(49); fatal(); confirm(); assertTrue(feedback().contains("Not enough Credits"));
        assertEquals(49, assets.getBalance()); assertEquals(2, service.getRemainingRevives());
        assertEquals(0, game.getPlayerStatus().getLives()); assertTrue((boolean) get(game, "reviveOfferOpen"));
        assertNotEquals(0L, get(get(game, "fallingObstacleCooldown"), "pausedAt"));
        key(KeyEvent.VK_ESCAPE, true); tick(); assertTrue((boolean) get(game, "levelFinished"));
        assertEquals(0L, get(get(game, "fallingObstacleCooldown"), "pausedAt"));
    }

    @Test void currentRunPendingCreditsCanPayForRecoveryExactlyOnce() throws Exception {
        fund(0);
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(game.getGameplayEvents(), 1)) {
            rewardKills(5); assertEquals(50, rewards.getPendingCurrency()); fatal(); confirm();
            assertEquals(0, assets.getBalance()); assertEquals(0, rewards.getPendingCurrency());
            assertEquals(2, game.getPlayerStatus().getLives()); assertEquals(1, service.getRemainingRevives());
        }
        assertEquals(0, assets.getBalance()); assertEquals(0, files.loadPlayerAssets(save).getBalance());
    }

    @Test void failedSettlementSaveRetriesWithoutPayingKillsTwice() throws Exception {
        fund(0); Files.delete(save); Files.createDirectory(save); Path blocker = Files.createFile(save.resolve("blocker"));
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(game.getGameplayEvents(), 1)) {
            rewardKills(5); fatal(); key(KeyEvent.VK_ENTER, true); tick();
            assertTrue(feedback().contains("could not be saved"));
            assertEquals(50, assets.getBalance()); assertEquals(0, rewards.getPendingCurrency());
            assertEquals(2, service.getRemainingRevives());
            Files.delete(blocker); Files.delete(save); tick(); assertEquals(50, assets.getBalance());
            key(KeyEvent.VK_ENTER, false); tick(); key(KeyEvent.VK_ENTER, true); tick(); key(KeyEvent.VK_ENTER, false);
            assertEquals(0, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
            assertEquals(2, game.getPlayerStatus().getLives());
        }
    }

    @Test void failedPaymentSaveRollsBackAndRetriesOnlyOnANewPress() throws Exception {
        Path blocked = temp.resolve("blocked"); Files.createDirectory(blocked); Files.createFile(blocked.resolve("blocker"));
        paymentSave.setPath(blocked); fatal(); key(KeyEvent.VK_ENTER, true); tick();
        assertTrue(feedback().contains("could not be saved"));
        assertEquals(1000, assets.getBalance()); assertEquals(1000, files.loadPlayerAssets(save).getBalance());
        assertEquals(2, service.getRemainingRevives()); assertEquals(0, game.getPlayerStatus().getLives());
        paymentSave.setPath(save); tick(); assertEquals(1000, assets.getBalance());
        key(KeyEvent.VK_ENTER, false); tick(); key(KeyEvent.VK_ENTER, true); tick(); key(KeyEvent.VK_ENTER, false);
        assertEquals(950, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
        assertEquals(2, game.getPlayerStatus().getLives());
    }

    @Test void overflowingSettlementCannotChargeOrConsumeAChance() throws Exception {
        fund(Integer.MAX_VALUE);
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(game.getGameplayEvents(), 1)) {
            rewardKills(1); fatal(); confirm(); assertTrue(feedback().contains("settlement failed"));
            assertEquals(Integer.MAX_VALUE, assets.getBalance()); assertEquals(10, rewards.getPendingCurrency());
            assertEquals(2, service.getRemainingRevives()); assertEquals(0, game.getPlayerStatus().getLives());
        }
    }

    @Test void twoRevivesKeepOneRunAndTheThirdDeathEndsNormally() throws Exception {
        AtomicInteger clears = new AtomicInteger();
        game.getGameplayEvents().subscribe(LevelCompletedEvent.class, event -> clears.incrementAndGet());
        for (int death = 0; death < 2; death++) {
            fatal(); confirm(); assertEquals(2, game.getPlayerStatus().getLives());
            assertSame(session, get(game, "runSession")); assertEquals(0, records.getRecordCount());
            age(get(ship, "invulnerabilityCooldown"), "time", 2100);
        }
        assertEquals(800, assets.getBalance()); assertEquals(0, service.getRemainingRevives()); fatal(); advance();
        assertFalse((boolean) get(game, "reviveOfferOpen")); assertTrue((boolean) get(game, "levelFinished"));
        assertEquals(0, clears.get()); assertFalse(game.isStageCleared());
        set(get(game, "screenFinishedCooldown"), "time", System.currentTimeMillis() - 30000); tick();
        assertFalse((boolean) get(game, "isRunning")); var state = game.getGameState();
        var record = session.finish(state.getScore(), state.getShipsDestroyed(), System.currentTimeMillis());
        assertTrue(records.completeRun(record)); assertFalse(records.completeRun(record));
        assertEquals(1, records.getRecordCount()); assertEquals(321, record.getScore());
        assertEquals(12, record.getEnemiesDefeated()); assertEquals(800, files.loadPlayerAssets(save).getBalance());
    }

    @Test void recoveryKeepsTheCurrentWaveRatherThanRestartingTheStage() throws Exception {
        createGame(1, ShipType.STANDARD, CampaignUpgrades.none(),
                List.of(new GameSettings(2, 1, 60, 2000), new GameSettings(1, 1, 60, 2000)));
        EnemyShipFormation first = (EnemyShipFormation) get(game, "enemyShipFormation");
        for (EnemyShip enemy : first) first.destroy(enemy);
        advance(); assertEquals(1, get(game, "currentWave"));
        EnemyShipFormation current = (EnemyShipFormation) get(game, "enemyShipFormation"); fatal(); confirm();
        assertSame(current, get(game, "enemyShipFormation")); assertEquals(1, get(game, "currentWave"));
        assertEquals(1, game.getGameState().getLevel()); assertFalse(game.isStageCleared());
    }

    @Test void recoveryListenerFailureCanRetryWithoutAnotherPaymentOrLifeEvent() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true); AtomicInteger revived = new AtomicInteger();
        game.getPlayerStatus().addListener(event -> {
            if (event.getType() == PlayerStatusEvent.Type.REVIVED) {
                revived.incrementAndGet();
                if (fail.getAndSet(false)) throw new IllegalStateException("injected recovery listener failure");
            }
        });
        fatal(); key(KeyEvent.VK_ENTER, true); tick();
        assertEquals(950, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
        assertTrue(feedback().contains("Revive paid")); assertTrue((boolean) get(game, "reviveOfferOpen"));
        tick(); assertEquals(950, assets.getBalance());
        key(KeyEvent.VK_ENTER, false); tick(); key(KeyEvent.VK_ENTER, true); tick(); key(KeyEvent.VK_ENTER, false);
        assertFalse((boolean) get(game, "reviveOfferOpen")); assertEquals(2, game.getPlayerStatus().getLives());
        assertEquals(1, revived.get()); assertEquals(950, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
        assertFalse(ship.isDestroyed()); assertTrue(ship.isInvulnerable());
    }

    @Test void renderOfferAndSuccessfulRecoveryForVisualReview() throws Exception {
        Path previews = Path.of(System.getProperty("invaders.revivePreviews", temp.toString()));
        Files.createDirectories(previews);
        fatal(); tick();
        javax.imageio.ImageIO.write((java.awt.image.BufferedImage) get(DrawManager.class, "backBuffer"),
                "png", previews.resolve("campaign-offer-enabled.png").toFile());
        confirm();
        javax.imageio.ImageIO.write((java.awt.image.BufferedImage) get(DrawManager.class, "backBuffer"),
                "png", previews.resolve("campaign-recovered.png").toFile());
        assertFalse((boolean) get(game, "reviveOfferOpen"));
        assertEquals(2, game.getPlayerStatus().getLives());
    }

    private void createGame(int stage, ShipType type, CampaignUpgrades upgrades, List<GameSettings> waves) throws Exception {
        releaseKeys(); session = new RunSession("recovery-" + stage + "-" + type);
        records.startRun(session.getRunId()); rewards.startRun(session.getRunId());
        List<GameSettings> settings = waves == null ? List.of(CampaignStageData.getStageSettings(stage)) : waves;
        game = new GameScreen(new GameState(stage, 321, 1, 7, 12), settings, false, 448, 520, 60, type, session, upgrades);
        game.setReviveService(service); game.initialize(); ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0)); set(game, "isRunning", true);
    }

    private void fund(int amount) throws Exception {
        assertTrue(assets.restore(new PlayerAssetsState(amount, null, null)));
        files.savePlayerAssets(assets.snapshot(), save);
    }
    private void rewardKills(int amount) {
        for (int index = 0; index < amount; index++) game.getGameplayEvents().publish(new EnemyDefeatedEvent(
                session.getRunId(), "earned-" + index, DefeatedEnemyType.A));
    }
    private void fatal() throws Exception { assertTrue(damage(game.getPlayerStatus().getLives())); }
    private boolean damage(int amount) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod("damagePlayer", int.class, String.class);
        method.setAccessible(true); return (boolean) method.invoke(game, amount, "recovery test");
    }
    private void tick() throws Exception { invoke("update"); }
    private void advance() throws Exception { invoke("advanceStageIfReady"); }
    private void invoke(String name) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(game);
    }
    private void confirm() throws Exception {
        key(KeyEvent.VK_ENTER, false); tick(); key(KeyEvent.VK_ENTER, true); tick(); key(KeyEvent.VK_ENTER, false);
    }
    private String feedback() throws Exception { return String.join(" ", (String[]) get(game, "reviveFeedback")); }
    private Set<Cooldown> timers(EnemyShipFormation formation, BossManager manager) throws Exception {
        Set<Cooldown> timers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String name : List.of("inputDelay", "fallingObstacleCooldown", "bossExplosionCooldown")) timers.add((Cooldown) get(game, name));
        timers.add((Cooldown) get(formation, "shootingCooldown"));
        for (EnemyShip enemy : formation) {
            timers.add((Cooldown) get(enemy, "animationCooldown")); timers.add(enemy.getShootingCooldown());
        }
        Object bonus = get(game, "bonusEnemyManager");
        timers.add((Cooldown) get(bonus, "spawnCooldown")); timers.add((Cooldown) get(bonus, "explosionCooldown"));
        Object attacks = manager.getAttackSystem();
        for (String name : List.of("attackCooldown", "windupCooldown", "buffCooldown")) timers.add((Cooldown) get(attacks, name));
        boss.BossAttackConfig config = (boss.BossAttackConfig) get(attacks, "config");
        timers.add((Cooldown) get(config.getSpecialAttack(), "cooldown")); timers.remove(null); return timers;
    }
    @SuppressWarnings("unchecked") private Set<Bullet> bullets() throws Exception { return (Set<Bullet>) get(game, "bullets"); }
    private void releaseKeys() { for (int code = 0; code < 256; code++) key(code, false); }
    private void key(int code, boolean pressed) {
        var event = new KeyEvent(new Canvas(), pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event); else Core.getInputManager().keyReleased(event);
    }
    static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static Object get(Object target, String name) throws Exception { return field(target, name).get(target instanceof Class ? null : target); }
    static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target instanceof Class ? null : target, value); }
    static void age(Object target, String name, long millis) throws Exception { set(target, name, (long) get(target, name) - millis); }
    @SuppressWarnings({"rawtypes", "unchecked"}) static Object enumValue(Object target, String name, String value) throws Exception { return Enum.valueOf((Class) field(target, name).getType(), value); }
    private boolean overlap(Entity a, Entity b) { return a.getPositionX() < b.getPositionX() + b.getWidth() && a.getPositionX() + a.getWidth() > b.getPositionX() && a.getPositionY() < b.getPositionY() + b.getHeight() && a.getPositionY() + a.getHeight() > b.getPositionY(); }
}
