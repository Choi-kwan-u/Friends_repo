package engine;

import audio.AudioManager;
import boss.BossManager;
import campaign.CampaignStageData;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.RewardSystem;
import entity.*;
import event.EnemyDefeatedEvent;
import event.LevelCompletedEvent;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import records.Achievement;
import records.RecordsService;
import records.RunSession;
import revive.ReviveManager;
import revive.ReviveService;
import screen.GameScreen;
import screen.InfiniteGameScreen;
import screen.ScoreScreen;
import screen.Screen;
import shop.ShopProduct;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class ReviveModesScenarioTest {
    @TempDir Path temp;
    private final Map<String, Object> previous = new HashMap<>();
    private PlayerAssets assets;
    private FileManager files;
    private Path save;
    private CampaignRewards campaign;
    private EndlessRewards endless;
    private RecordsService records;
    private RewardSystem rewardRules;
    private boolean muted;
    private String previousStorage;

    private record Context(ReviveManager.Mode mode, RunSession session, ReviveService revive,
            CampaignUpgrades upgrades) { }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    @BeforeEach void setUp() throws Exception {
        muted = AudioManager.isMuted(); AudioManager.setMuted(true);
        previousStorage = System.getProperty("invaders.storageDir");
        System.setProperty("invaders.storageDir", temp.resolve("storage").toString());
        for (String name : List.of("playerAssets", "shopController", "campaignRewards", "endlessRewards", "recordsService"))
            previous.put(name, get(Core.class, name));
        Set<String> owned = new HashSet<>();
        for (int tier = 1; tier <= 3; tier++) owned.add(PermanentUpgrade.HULL.getProductId(tier));
        assets = new PlayerAssets(); assertTrue(assets.restore(new PlayerAssetsState(1000, owned, Map.of("legacy", 2))));
        files = Core.getFileManager(); save = temp.resolve("assets.properties"); files.savePlayerAssets(assets.snapshot(), save);
        var shop = ShopTestFixtures.upgradeController(assets, new ShopTestFixtures.SaveFile(files, save));
        campaign = new CampaignRewards(assets, files, save); endless = new EndlessRewards(assets, files, save);
        records = new RecordsService(temp.resolve("records.properties")); rewardRules = new RewardSystem(new PlayerAssets());
        set(Core.class, "playerAssets", assets); set(Core.class, "shopController", shop);
        set(Core.class, "campaignRewards", campaign); set(Core.class, "endlessRewards", endless);
        set(Core.class, "recordsService", records); releaseKeys();
    }

    @AfterEach void cleanUp() throws Exception {
        releaseKeys(); records.close(); AudioManager.stopBGM(); AudioManager.setMuted(muted);
        for (var entry : previous.entrySet()) set(Core.class, entry.getKey(), entry.getValue());
        if (previousStorage == null) System.clearProperty("invaders.storageDir");
        else System.setProperty("invaders.storageDir", previousStorage);
    }

    @ParameterizedTest @EnumSource(ShipType.class)
    void campaignKeepsOneRunAcrossAllSevenStagesWithRevivesInStagesOneAndThree(ShipType type) throws Exception {
        var shop = Core.getShopController();
        if (!shop.isShipAvailable(type)) {
            String id = shop.getProducts().stream().filter(product -> product.getShipType() == type)
                    .map(ShopProduct::getId).findFirst().orElseThrow();
            assertEquals(PurchaseResult.SUCCESS, shop.purchase(id));
        }
        int startingMoney = assets.getBalance(); Context run = begin(ReviveManager.Mode.CAMPAIGN);
        GameState state = new GameState(1, 0, run.upgrades().getMaxLives(type), 0, 0);
        AtomicInteger earned = new AtomicInteger(), defeats = new AtomicInteger(), clears = new AtomicInteger();
        Set<String> defeatIds = new HashSet<>(); Long firstEnemyTime = null; GameScreen last = null;
        for (int level = 1; level <= CampaignStageData.getTotalStages(); level++) {
            int stage = level;
            boolean bonus = stage % 3 == 0 && state.getLivesRemaining() < run.upgrades().getMaxLives(type);
            GameScreen game = campaignScreen(run, state, type, bonus); last = game;
            game.getGameplayEvents().subscribe(EnemyDefeatedEvent.class, event -> {
                assertEquals(run.session().getRunId(), event.getRunId()); assertTrue(defeatIds.add(event.getEventId()));
                defeats.incrementAndGet(); earned.addAndGet(rewardRules.getEnemyReward(event.getEnemyType().name()));
            });
            game.getGameplayEvents().subscribe(LevelCompletedEvent.class, event -> {
                clears.incrementAndGet(); earned.addAndGet(rewardRules.getLevelReward(event.getDifficulty()));
            });
            runScreen(game, () -> {
                prepare(game, run);
                assertSame(run.revive(), get(game, "reviveService"));
                assertEquals(type.getMaxLives() + 3, game.getPlayerStatus().getMaxLives());
                assertEquals(type.getSpeed(), ((Ship) get(game, "ship")).getBaseSpeed());
                if (stage == 1 || stage == 3) {
                    if (stage == 1) for (int killed = 0; killed < 5; killed++) killOne(game);
                    Boss boss = ((BossManager) get(game, "bossManager")).getBoss();
                    if (boss != null) invoke(game, "damageBoss", new Class<?>[] {int.class}, 137);
                    int health = boss == null ? 0 : boss.getCurrentHealth();
                    Object formation = get(game, "enemyShipFormation");
                    fatal(game); revive(game, run, stage == 1 ? 50 : 150, (type.getMaxLives() + 4) / 2);
                    assertSame(formation, get(game, "enemyShipFormation"));
                    if (boss != null) assertEquals(health, boss.getCurrentHealth());
                    assertEquals(stage == 1 ? 1 : 0, run.revive().getRemainingRevives());
                    tick(game);
                } else assertEquals(stage < 3 ? 1 : 0, run.revive().getRemainingRevives());
                clearCampaignStage(game);
            });
            assertEquals(stage, clears.get());
            if (firstEnemyTime == null) firstEnemyTime = records.getUnlockTime(Achievement.FIRST_ENEMY);
            assertNotNull(firstEnemyTime); assertEquals(firstEnemyTime, records.getUnlockTime(Achievement.FIRST_ENEMY));
            state = new GameState(stage + 1, game.getGameState().getScore(), game.getGameState().getLivesRemaining(),
                    game.getGameState().getBulletsShot(), game.getGameState().getShipsDestroyed());
            assertEquals(0, records.getRecordCount());
        }
        assertNotNull(last); assertEquals(8, state.getLevel()); assertTrue(state.getLivesRemaining() > 0);
        assertEquals(7, clears.get()); assertEquals(defeats.get(), state.getShipsDestroyed());
        assertEquals(startingMoney + earned.get() - 200, assets.getBalance()); assertEquals(0, run.revive().getRemainingRevives());
        complete(run, last); assertEquals(1, records.getRecordCount());
        assertEquals(state.getScore(), records.getHighestScore()); assertEquals(state.getShipsDestroyed(), records.getMostEnemiesDefeated());
        assertEquals(records.getCurrentRunUnlocks().size(), new HashSet<>(records.getCurrentRunUnlocks()).size());
        assertNotNull(records.getUnlockTime(Achievement.FIRST_RUN)); assertNotNull(records.getUnlockTime(Achievement.TWENTY_ENEMIES));
        assertEquals(1, results(state, false, KeyEvent.VK_ESCAPE));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.ENGINE.getProductId(1)));
        assertEquals(startingMoney + earned.get() - 300, files.loadPlayerAssets(save).getBalance());
        assertTrue(shop.isShipAvailable(type)); assertEquals(Map.of("legacy", 2), shop.snapshot().getItemQuantities());
        assertReloadedRecords(1);
    }

    @ParameterizedTest @ValueSource(ints = {3, 7})
    void bothBossTypesKeepDamagedHealthAndPhaseAcrossPaidHullThreeRecovery(int level) throws Exception {
        Context run = begin(ReviveManager.Mode.CAMPAIGN);
        GameScreen game = campaignScreen(run, new GameState(level, 700, 8, 10, 3), ShipType.TANK, false);
        runScreen(game, () -> {
            prepare(game, run); BossManager manager = (BossManager) get(game, "bossManager"); Boss boss = manager.getBoss();
            invoke(game, "damageBoss", new Class<?>[] {int.class}, boss.getMaxHealth() / 2 + 1);
            int health = boss.getCurrentHealth(), phase = boss.getPhase(); Object formation = get(game, "enemyShipFormation");
            fatal(game); revive(game, run, 50, 4);
            assertSame(boss, manager.getBoss()); assertEquals(health, boss.getCurrentHealth()); assertEquals(phase, boss.getPhase());
            assertSame(formation, get(game, "enemyShipFormation")); assertEquals(level, state(game).getLevel());
            fatal(game); giveUp(game); assertFalse(game.isStageCleared());
        });
        complete(run, game); assertEquals(1, records.getRecordCount()); assertEquals(1, run.revive().getRemainingRevives());
        assertEquals(700, records.getHighestScore()); assertEquals(1, results(state(game), false, KeyEvent.VK_ESCAPE));
        assertReloadedRecords(1);
    }

    @Test void endlessPaysTwentyThenFiftyAndFinalDeathKeepsOneRecordAndAllEarnedCredits() throws Exception {
        Context run = begin(ReviveManager.Mode.ENDLESS); InfiniteGameScreen game = (InfiniteGameScreen) firstScreen(run);
        runScreen(game, () -> {
            prepare(game, run);
            for (int killed = 0; killed < 20; killed++) killOne(game);
            Long first = records.getUnlockTime(Achievement.FIRST_ENEMY);
            assertNotNull(first); assertNotNull(records.getUnlockTime(Achievement.TWENTY_ENEMIES));
            fatal(game); revive(game, run, 20, 3);
            for (int killed = 0; killed < 5; killed++) killOne(game);
            fatal(game); revive(game, run, 50, 3);
            assertEquals(first, records.getUnlockTime(Achievement.FIRST_ENEMY));
            fatal(game); assertEquals(true, get(game, "gameOver")); finish(game);
            assertFalse(game.isAbandoned()); assertEquals(25, game.getGameState().getShipsDestroyed());
        });
        assertEquals(955, assets.getBalance()); assertEquals(0, run.revive().getRemainingRevives());
        complete(run, game); assertEquals(1, records.getRecordCount()); assertEquals(25, records.getMostEnemiesDefeated());
        assertEquals(1, results(state(game), true, KeyEvent.VK_ESCAPE));
        assertEquals(955, files.loadPlayerAssets(save).getBalance()); assertReloadedRecords(1);
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void decliningInEitherModeRetainsEarnedCreditsAndUsesTheFailedResultPath(ReviveManager.Mode mode) throws Exception {
        Context run = begin(mode); Screen game = firstScreen(run); long[] earned = {0};
        runScreen(game, () -> {
            prepare(game, run); killOne(game); earned[0] = Core.getAssetHudSnapshot().getCredits() - 1000;
            fatal(game); giveUp(game); assertEquals(2, run.revive().getRemainingRevives());
            assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, run.revive().confirmRevive());
            if (game instanceof InfiniteGameScreen infinite) assertFalse(infinite.isAbandoned());
            else assertFalse(((GameScreen) game).isStageCleared());
        });
        assertEquals(1000 + earned[0], assets.getBalance()); complete(run, game);
        assertEquals(1, records.getRecordCount()); assertEquals(1, state(game).getShipsDestroyed());
        assertEquals(1, results(state(game), mode == ReviveManager.Mode.ENDLESS, KeyEvent.VK_ESCAPE));
        assertEquals(assets.getBalance(), files.loadPlayerAssets(save).getBalance()); assertReloadedRecords(1);
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void resultScreenReplayStartsAFreshRunWithTwoChancesAndCorrectUpgradeScope(ReviveManager.Mode mode) throws Exception {
        Context old = begin(mode); Screen game = firstScreen(old);
        runScreen(game, () -> {
            prepare(game, old);
            for (int used = 0; used < 2; used++) {
                fatal(game); revive(game, old, mode == ReviveManager.Mode.CAMPAIGN
                        ? used == 0 ? 50 : 150 : used == 0 ? 20 : 50,
                        mode == ReviveManager.Mode.CAMPAIGN ? 4 : 3);
            }
            fatal(game); finish(game);
        });
        complete(old, game); assertEquals(0, old.revive().getRemainingRevives());
        Long firstRunTime = records.getUnlockTime(Achievement.FIRST_RUN);
        assertEquals(mode == ReviveManager.Mode.CAMPAIGN ? 2 : 8,
                results(state(game), mode == ReviveManager.Mode.ENDLESS, KeyEvent.VK_SPACE));
        assertEquals(PurchaseResult.SUCCESS, Core.getShopController().purchase(PermanentUpgrade.ENGINE.getProductId(1)));
        assertEquals(ShipType.STANDARD.getSpeed(), ((Ship) get(game, "ship")).getBaseSpeed());
        int moneyBefore = assets.getBalance();
        Context fresh = begin(mode); Screen replay = firstScreen(fresh);
        assertNotEquals(old.session().getRunId(), fresh.session().getRunId());
        assertNotSame(old.revive(), fresh.revive()); assertEquals(2, fresh.revive().getRemainingRevives());
        assertEquals(mode == ReviveManager.Mode.CAMPAIGN ? 50 : 20, fresh.revive().getNextReviveCost());
        long[] earned = {0};
        runScreen(replay, () -> {
            prepare(replay, fresh); assertSame(fresh.revive(), get(replay, "reviveService"));
            assertEquals(ShipType.STANDARD.getSpeed() + (mode == ReviveManager.Mode.CAMPAIGN ? 1 : 0),
                    ((Ship) get(replay, "ship")).getBaseSpeed());
            killOne(replay); earned[0] = Core.getAssetHudSnapshot().getCredits() - moneyBefore;
            fatal(replay); assertEquals(true, get(replay, "reviveOfferOpen")); giveUp(replay);
        });
        complete(fresh, replay); assertEquals(2, records.getRecordCount());
        assertEquals(firstRunTime, records.getUnlockTime(Achievement.FIRST_RUN));
        assertEquals(0, old.revive().getRemainingRevives()); assertEquals(2, fresh.revive().getRemainingRevives());
        assertEquals(moneyBefore + earned[0], assets.getBalance());
        assertEquals(1, results(state(replay), mode == ReviveManager.Mode.ENDLESS, KeyEvent.VK_ESCAPE));
        assertReloadedRecords(2);
    }

    @Test void campaignEndlessAndNewCampaignShareWalletAndProfileButKeepIndependentRunLimits() throws Exception {
        Context first = begin(ReviveManager.Mode.CAMPAIGN); Screen campaignGame = firstScreen(first);
        runScreen(campaignGame, () -> {
            prepare(campaignGame, first); killOne(campaignGame); fatal(campaignGame);
            revive(campaignGame, first, 50, 4); fatal(campaignGame); giveUp(campaignGame);
        });
        complete(first, campaignGame); int afterCampaign = assets.getBalance();
        Long firstEnemyTime = records.getUnlockTime(Achievement.FIRST_ENEMY);
        assertEquals(1, results(state(campaignGame), false, KeyEvent.VK_ESCAPE));
        Context second = begin(ReviveManager.Mode.ENDLESS); Screen endlessGame = firstScreen(second);
        runScreen(endlessGame, () -> {
            prepare(endlessGame, second); for (int killed = 0; killed < 20; killed++) killOne(endlessGame);
            fatal(endlessGame); revive(endlessGame, second, 20, 3); fatal(endlessGame); giveUp(endlessGame);
        });
        complete(second, endlessGame); assertEquals(afterCampaign, assets.getBalance());
        assertEquals(1, first.revive().getRemainingRevives()); assertEquals(1, second.revive().getRemainingRevives());
        assertEquals(firstEnemyTime, records.getUnlockTime(Achievement.FIRST_ENEMY));
        assertNotNull(records.getUnlockTime(Achievement.TWENTY_ENEMIES));
        assertEquals(1, results(state(endlessGame), true, KeyEvent.VK_ESCAPE));
        Context third = begin(ReviveManager.Mode.CAMPAIGN); Screen newCampaign = firstScreen(third);
        runScreen(newCampaign, () -> {
            prepare(newCampaign, third); assertEquals(2, third.revive().getRemainingRevives());
            assertEquals(50, third.revive().getNextReviveCost()); fatal(newCampaign); giveUp(newCampaign);
        });
        complete(third, newCampaign); assertEquals(3, records.getRecordCount());
        assertEquals(afterCampaign, assets.getBalance());
        assertEquals(1, first.revive().getRemainingRevives()); assertEquals(2, third.revive().getRemainingRevives());
        assertEquals(3, records.getTopRuns().stream().map(record -> record.getRunId()).distinct().count());
        assertEquals(1, results(state(newCampaign), false, KeyEvent.VK_ESCAPE)); assertReloadedRecords(3);
    }

    private Context begin(ReviveManager.Mode mode) {
        String id = "scenario-" + mode + "-" + UUID.randomUUID();
        RunSession session = new RunSession(id); records.startRun(id);
        if (mode == ReviveManager.Mode.CAMPAIGN) campaign.startRun(id);
        return new Context(mode, session, new ReviveService(new ReviveManager(mode),
                Core.getShopController(), Core::settleCreditsForRevive), CampaignUpgrades.from(assets.snapshot()));
    }

    private GameScreen campaignScreen(Context run, GameState state, ShipType type, boolean bonus) {
        GameScreen screen = new GameScreen(state, CampaignStageData.getStageSettings(state.getLevel()), bonus,
                448, 520, 60, type, run.session(), run.upgrades());
        screen.setReviveService(run.revive()); return screen;
    }

    private Screen firstScreen(Context run) {
        if (run.mode() == ReviveManager.Mode.CAMPAIGN)
            return campaignScreen(run, new GameState(1, 0, run.upgrades().getMaxLives(ShipType.STANDARD), 0, 0), ShipType.STANDARD, false);
        InfiniteGameScreen screen = new InfiniteGameScreen(new GameState(1, 0, 3, 0, 0), 448, 520, 60, run.session());
        screen.setReviveService(run.revive()); return screen;
    }

    private void prepare(Screen screen, Context run) throws Exception {
        releaseKeys(); screen.initialize(); set(screen, "inputDelay", Core.getCooldown(0));
        set(screen, "isRunning", true); run.session().startActiveSegment(System.nanoTime());
    }

    private void runScreen(Screen screen, Checked work) {
        if (screen instanceof GameScreen campaignScreen)
            Core.runCampaignStage(campaignScreen, campaign, current -> checked(work));
        else Core.runEndless((InfiniteGameScreen) screen, endless, current -> checked(work));
    }

    private void checked(Checked work) {
        try { work.run(); } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private GameState state(Screen screen) {
        return screen instanceof GameScreen game ? game.getGameState() : ((InfiniteGameScreen) screen).getGameState();
    }

    private void fatal(Screen screen) throws Exception {
        Ship ship = (Ship) get(screen, "ship");
        set(get(ship, "invulnerabilityCooldown"), "time", System.currentTimeMillis() - 30000);
        if (screen instanceof GameScreen game)
            assertEquals(true, invoke(game, "damagePlayer", new Class<?>[] {int.class, String.class},
                    game.getPlayerStatus().getLives(), "scenario fatal hit"));
        else {
            while (state(screen).getLivesRemaining() > 0) {
                set(get(ship, "invulnerabilityCooldown"), "time", System.currentTimeMillis() - 30000);
                enemyBullets(screen).add(BulletPool.getBullet(ship.getPositionX() + 4, ship.getPositionY() + 4, 4, Bullet.Owner.ENEMY));
                invoke(screen, "manageCollisions");
            }
            tick(screen);
        }
        assertEquals(0, state(screen).getLivesRemaining());
    }

    private void revive(Screen screen, Context run, int cost, int expectedLives) throws Exception {
        assertEquals(true, get(screen, "reviveOfferOpen"));
        GameState before = state(screen); Ship ship = (Ship) get(screen, "ship");
        long credits = Core.getAssetHudSnapshot().getCredits(); int recordsBefore = records.getRecordCount();
        assertEquals(cost, run.revive().getNextReviveCost());
        tap(screen, KeyEvent.VK_ENTER);
        assertEquals(false, get(screen, "reviveOfferOpen"));
        assertEquals(expectedLives, state(screen).getLivesRemaining());
        assertEquals(before.getScore(), state(screen).getScore());
        assertEquals(before.getShipsDestroyed(), state(screen).getShipsDestroyed());
        assertEquals(credits - cost, assets.getBalance());
        assertSame(run.session(), get(screen, "runSession")); assertSame(ship, get(screen, "ship"));
        assertFalse(ship.isDestroyed()); assertTrue(ship.isInvulnerable()); assertEquals(recordsBefore, records.getRecordCount());
    }

    private void giveUp(Screen screen) throws Exception {
        tap(screen, KeyEvent.VK_DOWN); tap(screen, KeyEvent.VK_ENTER);
        assertEquals(false, get(screen, "reviveOfferOpen")); finish(screen);
    }

    private void finish(Screen screen) throws Exception {
        if (screen instanceof GameScreen) {
            invoke(screen, "advanceStageIfReady"); assertEquals(true, get(screen, "levelFinished"));
        }
        set(get(screen, "screenFinishedCooldown"), "time", System.currentTimeMillis() - 30000);
        screen.run(); assertEquals(false, get(screen, "isRunning"));
    }

    private void complete(Context run, Screen screen) {
        GameState state = state(screen); run.session().stopActiveSegment(System.nanoTime());
        var record = run.session().finish(state.getScore(), state.getShipsDestroyed(), System.currentTimeMillis());
        assertTrue(records.completeRun(record)); assertFalse(records.completeRun(record));
    }

    private int results(GameState state, boolean infinite, int keyCode) throws Exception {
        releaseKeys(); ScoreScreen score = new ScoreScreen(448, 520, 60, state, infinite, records.getCurrentRunUnlocks());
        assertEquals(state.getScore(), get(score, "score"));
        assertEquals(state.getShipsDestroyed(), get(score, "shipsDestroyed"));
        assertEquals(state.getLivesRemaining() == 0, get(score, "stageFailed"));
        assertEquals(!infinite && state.getLevel() > 7 && state.getLivesRemaining() > 0, get(score, "gameCleared"));
        set(score, "inputDelay", Core.getCooldown(0)); key(keyCode, true);
        try { return score.run(); } finally { key(keyCode, false); }
    }

    private void killOne(Screen screen) throws Exception {
        if (screen instanceof GameScreen game) {
            EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation");
            EnemyShip target = null;
            for (EnemyShip enemy : formation) if (!enemy.isDestroyed()) { target = enemy; break; }
            assertNotNull(target);
            bullets(screen).add(BulletPool.getBullet(target.getPositionX() + 4, target.getPositionY() + 4, -4, Bullet.Owner.PLAYER));
            invoke(screen, "manageCollisions"); assertTrue(target.isDestroyed());
        } else {
            var enemy = new ZigzagEnemyShip(100, 100, DrawManager.SpriteType.EnemyShipA1, 1);
            enemies((InfiniteGameScreen) screen).add(enemy);
            bullets(screen).add(BulletPool.getBullet(104, 104, -4, Bullet.Owner.PLAYER));
            invoke(screen, "manageCollisions"); assertFalse(enemies((InfiniteGameScreen) screen).contains(enemy));
        }
    }

    private void clearCampaignStage(GameScreen game) throws Exception {
        EnemyShipFormation formation = (EnemyShipFormation) get(game, "enemyShipFormation");
        while (formation.iterator().hasNext() && hasLiving(formation)) killOne(game);
        Boss boss = ((BossManager) get(game, "bossManager")).getBoss();
        if (boss != null) {
            invoke(game, "damageBoss", new Class<?>[] {int.class}, Integer.MAX_VALUE);
            invoke(game, "damageBoss", new Class<?>[] {int.class}, Integer.MAX_VALUE);
            set(get(game, "bossExplosionCooldown"), "time", System.currentTimeMillis() - 30000);
        }
        invoke(game, "advanceStageIfReady"); invoke(game, "advanceStageIfReady");
        assertTrue(game.isStageCleared()); finish(game);
    }

    private boolean hasLiving(EnemyShipFormation formation) {
        for (EnemyShip enemy : formation) if (!enemy.isDestroyed()) return true;
        return false;
    }

    private void assertReloadedRecords(int expected) {
        records.close(); RecordsService reloaded = new RecordsService(temp.resolve("records.properties"));
        try {
            assertEquals(expected, reloaded.getRecordCount()); assertNull(reloaded.getLoadWarning());
            assertEquals(records.getHighestScore(), reloaded.getHighestScore());
            assertEquals(records.getMostEnemiesDefeated(), reloaded.getMostEnemiesDefeated());
        }
        finally { reloaded.close(); }
    }

    private void tick(Screen screen) throws Exception { invoke(screen, "update"); }
    private void tap(Screen screen, int key) throws Exception {
        key(key, false); tick(screen); key(key, true); tick(screen); key(key, false);
    }
    private void releaseKeys() { for (int code = 0; code < 256; code++) key(code, false); }
    private void key(int code, boolean pressed) {
        var event = new KeyEvent(new Canvas(), pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event); else Core.getInputManager().keyReleased(event);
    }
    @SuppressWarnings("unchecked") private Set<Bullet> bullets(Screen screen) throws Exception {
        return (Set<Bullet>) get(screen, "bullets");
    }
    @SuppressWarnings("unchecked") private Set<Bullet> enemyBullets(Screen screen) throws Exception { return (Set<Bullet>) get(screen, "enemyBullets"); }
    @SuppressWarnings("unchecked") private List<ZigzagEnemyShip> enemies(InfiniteGameScreen screen) throws Exception {
        return (List<ZigzagEnemyShip>) get(screen, "enemies");
    }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target instanceof Class ? null : target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target instanceof Class ? null : target, value); }
    private static Object invoke(Object target, String name) throws Exception { return invoke(target, name, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
}
