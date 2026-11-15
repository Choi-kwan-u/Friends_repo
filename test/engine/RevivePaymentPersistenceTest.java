package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import entity.ShipType;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventPublisher;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import revive.ReviveManager;
import revive.ReviveService;
import shop.ShopController;
import shop.ShopProduct;
import shop.ShopProducts;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

public class RevivePaymentPersistenceTest {
    @TempDir Path temp;
    private final Map<String, Object> previous = new HashMap<>();
    private PlayerAssets assets;
    private FileManager files;
    private Path save;
    private ShopTestFixtures.SaveFile shopSave;
    private ShopController shop;
    private CampaignRewards campaign;
    private EndlessRewards endless;

    @BeforeEach void setUp() throws Exception {
        for (String name : new String[] {"playerAssets", "shopController", "campaignRewards", "endlessRewards"})
            previous.put(name, coreField(name).get(null));
        assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(1000, Set.of("legacy-owned"), Map.of("legacy", 3))));
        files = Core.getFileManager(); save = temp.resolve("assets.properties");
        files.savePlayerAssets(assets.snapshot(), save);
        shopSave = new ShopTestFixtures.SaveFile(files, save);
        shop = ShopTestFixtures.upgradeController(assets, shopSave);
        campaign = new CampaignRewards(assets, files, save); endless = new EndlessRewards(assets, files, save);
        coreField("playerAssets").set(null, assets); coreField("shopController").set(null, shop);
        coreField("campaignRewards").set(null, campaign); coreField("endlessRewards").set(null, endless);
    }

    @AfterEach void restoreCore() throws Exception {
        for (var entry : previous.entrySet()) coreField(entry.getKey()).set(null, entry.getValue());
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void bothPricesCurrentRunCreditsAndShopUnlocksSurviveAFreshJvm(ReviveManager.Mode mode) throws Exception {
        String run = "persist-" + mode;
        GameplayEventPublisher events = new GameplayEventPublisher();
        ReviveService service = service(mode);
        int first = service.getNextReviveCost();
        String hull = PermanentUpgrade.HULL.getProductId(1);
        String speedster = shop.getProducts().stream().filter(product -> product.getShipType() == ShipType.SPEEDSTER)
                .map(ShopProduct::getId).findFirst().orElseThrow();
        assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
        try (AutoCloseable subscription = subscribe(mode, run, events)) {
            earnFirstPrice(mode, run, events);
            assertEquals(first, pending(mode));
            assertTrue(service.openOffer()); assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
            assertEquals(1000, assets.getBalance()); assertEquals(0, pending(mode));
            assertEquals(PurchaseResult.SUCCESS, shop.purchase(hull));
            assertEquals(PurchaseResult.SUCCESS, shop.purchase(speedster));
            assertTrue(shop.isShipAvailable(ShipType.SPEEDSTER));
            assertEquals(700, assets.getBalance());
            assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(hull));
            assertEquals(700, assets.getBalance());
            assertTrue(service.openOffer());
            int second = mode == ReviveManager.Mode.CAMPAIGN ? 150 : 50;
            assertEquals(second, service.getNextReviveCost());
            assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
            assertEquals(700 - second, assets.getBalance());
            assertEquals(0, service.getRemainingRevives()); assertFalse(service.openOffer());
            events.publish(new EnemyDefeatedEvent(run, "enemy-0", DefeatedEnemyType.A));
            assertEquals(0, pending(mode));
            assertEquals(SettlementResult.SUCCESS, Core.settleCreditsForRevive());
            assertEquals(SettlementResult.SUCCESS, Core.settleCreditsForRevive());
        }
        int expected = mode == ReviveManager.Mode.CAMPAIGN ? 550 : 650;
        assertEquals(expected, assets.getBalance());
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(expected, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().containsAll(Set.of("legacy-owned", hull, speedster)));
        assertEquals(Map.of("legacy", 3), saved.getItemQuantities());
        reloadInFreshJvm(expected, speedster);
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void aFailedReviveDoesNotLoseAnEarlierUnsavedShopPurchase(ReviveManager.Mode mode) throws Exception {
        Path blocked = temp.resolve("blocked"); Files.createDirectory(blocked);
        Files.createFile(blocked.resolve("blocker")); shopSave.setPath(blocked);
        String hull = PermanentUpgrade.HULL.getProductId(1);
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(hull));
        assertEquals(900, assets.getBalance()); assertTrue(assets.isUnlocked(hull));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        ReviveService service = service(mode); int cost = service.getNextReviveCost();
        assertTrue(service.openOffer()); assertEquals(ReviveService.Result.SAVE_FAILED, service.confirmRevive());
        assertEquals(900, assets.getBalance()); assertTrue(assets.isUnlocked(hull));
        assertEquals(2, service.getRemainingRevives());
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertEquals(1000, files.loadPlayerAssets(save).getBalance());
        shopSave.setPath(save); assertTrue(shop.retrySave());
        assertEquals(900, assets.getBalance());
        assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
        assertEquals(900 - cost, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
        assertEquals(ShopController.SaveStatus.SAVED, shop.getSaveStatus());
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(900 - cost, saved.getBalance()); assertTrue(saved.getUnlockedContentIds().contains(hull));
        assertEquals(Map.of("legacy", 3), saved.getItemQuantities());
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void rewardSaveRetryUsesTheLatestShopStateAndCannotRepayEarnedCredits(ReviveManager.Mode mode) throws Exception {
        String run = "retry-" + mode;
        GameplayEventPublisher events = new GameplayEventPublisher(); ReviveService service = service(mode);
        int first = service.getNextReviveCost();
        try (AutoCloseable subscription = subscribe(mode, run, events)) {
            earnFirstPrice(mode, run, events);
            Path blocker = blockRewardSave();
            assertTrue(service.openOffer()); assertEquals(ReviveService.Result.SAVE_FAILED, service.confirmRevive());
            assertEquals(1000 + first, assets.getBalance()); assertEquals(0, pending(mode));
            assertEquals(2, service.getRemainingRevives());
            shopSave.setPath(temp.resolve("shop-progress.properties"));
            assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
            assertEquals(900 + first, assets.getBalance());
            assertEquals(ReviveService.Result.SAVE_FAILED, service.confirmRevive());
            assertEquals(900 + first, assets.getBalance()); assertEquals(2, service.getRemainingRevives());
            unblockRewardSave(blocker); shopSave.setPath(save);
            assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
            assertEquals(900, assets.getBalance()); assertEquals(1, service.getRemainingRevives());
            assertEquals(SettlementResult.SUCCESS, Core.settleCreditsForRevive());
            assertEquals(SettlementResult.SUCCESS, Core.settleCreditsForRevive());
        }
        PlayerAssetsState saved = files.loadPlayerAssets(save);
        assertEquals(900, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(Map.of("legacy", 3), saved.getItemQuantities());
    }

    @ParameterizedTest @EnumSource(ReviveManager.Mode.class)
    void decliningAfterASettlementSaveFailureRetainsCreditsWithoutCharging(ReviveManager.Mode mode) throws Exception {
        String run = "decline-" + mode;
        GameplayEventPublisher events = new GameplayEventPublisher(); ReviveService service = service(mode);
        int first = service.getNextReviveCost();
        try (AutoCloseable subscription = subscribe(mode, run, events)) {
            earnFirstPrice(mode, run, events); Path blocker = blockRewardSave();
            assertTrue(service.openOffer()); assertEquals(ReviveService.Result.SAVE_FAILED, service.confirmRevive());
            service.decline(); assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
            assertEquals(1000 + first, assets.getBalance()); assertEquals(2, service.getRemainingRevives());
            unblockRewardSave(blocker);
            assertEquals(SettlementResult.SUCCESS, Core.settleCreditsForRevive());
            assertEquals(1000 + first, files.loadPlayerAssets(save).getBalance());
        }
        assertEquals(1000 + first, assets.getBalance());
        assertEquals(2, service.getRemainingRevives());
    }

    private Path blockRewardSave() throws Exception {
        Files.delete(save); Files.createDirectory(save);
        return Files.createFile(save.resolve("blocker"));
    }
    private void unblockRewardSave(Path blocker) throws Exception {
        Files.delete(blocker); Files.delete(save);
    }

    private ReviveService service(ReviveManager.Mode mode) {
        return new ReviveService(new ReviveManager(mode), shop, Core::settleCreditsForRevive);
    }
    private AutoCloseable subscribe(ReviveManager.Mode mode, String run, GameplayEventPublisher events) {
        if (mode == ReviveManager.Mode.CAMPAIGN) { campaign.startRun(run); return campaign.subscribe(events, 1); }
        return endless.subscribe(events, run);
    }
    private long pending(ReviveManager.Mode mode) {
        return mode == ReviveManager.Mode.CAMPAIGN ? campaign.getPendingCurrency() : endless.getPendingCurrency();
    }
    private void earnFirstPrice(ReviveManager.Mode mode, String run, GameplayEventPublisher events) {
        int count = mode == ReviveManager.Mode.CAMPAIGN ? 5 : 20;
        for (int index = 0; index < count; index++) {
            var event = new EnemyDefeatedEvent(run, "enemy-" + index, DefeatedEnemyType.A);
            events.publish(event); events.publish(event);
        }
        events.publish(new EnemyDefeatedEvent("foreign-run", "foreign", DefeatedEnemyType.A));
    }
    private void reloadInFreshJvm(int balance, String speedsterId) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classpath = System.getProperty("invaders.test.classpath", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                "-Duser.home=" + temp, "-Dinvaders.storageDir=" + temp,
                "-cp", classpath, ReloadProbe.class.getName(), save.toString(), Integer.toString(balance), speedsterId)
                .redirectErrorStream(true).start();
        if (!child.waitFor(20, TimeUnit.SECONDS)) { child.destroyForcibly(); fail("Wallet reload JVM timed out"); }
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, child.exitValue(), output);
        assertTrue(output.contains("RELOADED_OK " + balance), output);
    }
    private Field coreField(String name) throws Exception {
        Field field = Core.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    /** Launched in a separate JVM so no parent wallet/controller can leak into the reload. */
    public static final class ReloadProbe {
        public static void main(String[] args) throws Exception {
            FileManager files = Core.getFileManager();
            PlayerAssets restored = new PlayerAssets();
            ShopController shop = Core.initializeShop(restored, files,
                    files.loadPlayerAssets(Path.of(args[0])), ShopProducts.availableProducts());
            if (shop.getBalance() != Integer.parseInt(args[1])
                    || !shop.isShipAvailable(ShipType.SPEEDSTER)
                    || !restored.isUnlocked(args[2])
                    || !restored.isUnlocked("legacy-owned")
                    || PermanentUpgrade.HULL.getLevel(shop.snapshot()) != 1
                    || !Integer.valueOf(3).equals(shop.snapshot().getItemQuantities().get("legacy")))
                throw new AssertionError("Reloaded wallet/ownership differs from the committed state");
            System.out.println("RELOADED_OK " + shop.getBalance());
        }
    }
}
