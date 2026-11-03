package shop;

import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.RewardSystem;
import currency.ShopCatalog.Category;
import engine.Core;
import engine.FileManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShopControllerTest {
    @TempDir
    Path temp;

    private FileManager files;
    private ShopTestFixtures.SaveFile saveFile;
    private Path save;
    private PlayerAssets assets;
    private ShopController shop;

    @BeforeEach
    void setUp() {
        files = Core.getFileManager();
        save = temp.resolve("assets.properties");
        saveFile = new ShopTestFixtures.SaveFile(files, save);
        assets = ShopTestFixtures.fundedAssets();
        shop = ShopTestFixtures.controller(assets, saveFile);
    }

    @Test
    void constructorKeepsAssetsAndSaveFileWithProductionUpgrades() throws Exception {
        Files.write(save, new byte[] { 1, 2, 3 });
        byte[] before = Files.readAllBytes(save);

        ShopController production = new ShopController(assets, files);

        assertEquals(1000, production.snapshot().getBalance());
        assertEquals(6, production.getProducts().size());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, production.getSaveStatus());
        assertArrayEquals(before, Files.readAllBytes(save));
    }

    @Test
    void upgradeUsesCatalogPriceAndCannotBeBoughtAgain() throws Exception {
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(900, saveFile.load().getBalance());
        byte[] saved = Files.readAllBytes(save);

        assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(900, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        assertTrue(saveFile.load().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
        assertArrayEquals(saved, Files.readAllBytes(save));
        assertEquals(ShopController.SaveStatus.SAVED, shop.getSaveStatus());
    }

    @Test
    void unlockIsChargedOnceAndRejectionDoesNotSave() throws Exception {
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(ShopTestFixtures.UNLOCK));
        byte[] saved = Files.readAllBytes(save);

        assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(ShopTestFixtures.UNLOCK));
        assertEquals(500, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UNLOCK));
        assertArrayEquals(saved, Files.readAllBytes(save));
    }

    @Test
    void insufficientFundsAndBadIdsDoNotChangeAssetsOrCreateSave() {
        PlayerAssets empty = new PlayerAssets();
        ShopController poorShop = ShopTestFixtures.controller(empty, saveFile);

        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, poorShop.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, poorShop.purchase(ShopTestFixtures.UNLOCK));
        assertEquals(PurchaseResult.INVALID_ITEM_ID, shop.purchase(null));
        assertEquals(PurchaseResult.INVALID_ITEM_ID, shop.purchase(" "));
        assertEquals(PurchaseResult.CATEGORY_MISMATCH, shop.purchase("UNKNOWN"));
        assertEquals(1000, assets.getBalance());
        assertEquals(0, empty.getBalance());
        assertFalse(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        assertFalse(assets.isUnlocked(ShopTestFixtures.UNLOCK));
        assertFalse(Files.exists(save));
    }

    @Test
    void legacyItemDataIsPreservedButCannotBePurchased() throws Exception {
        assets.restore(new PlayerAssetsState(1000, Collections.<String>emptySet(),
                Collections.singletonMap("legacy.item", Integer.MAX_VALUE)));
        assertEquals(PurchaseResult.CATEGORY_MISMATCH, shop.purchase("legacy.item"));
        assertFalse(Files.exists(save));

        assertEquals(PurchaseResult.SUCCESS, shop.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(900, assets.getBalance());
        assertEquals(Integer.MAX_VALUE, saveFile.load().getItemQuantities().get("legacy.item"));
        assertTrue(saveFile.load().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
    }

    @Test
    void savedPurchasesCanBeRestoredToSharedAssets() throws Exception {
        shop.purchase(ShopTestFixtures.UPGRADE);
        shop.purchase(ShopTestFixtures.UNLOCK);
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(saveFile.load()));
        ShopController restarted = ShopTestFixtures.controller(restored, saveFile);

        assertEquals(400, restarted.snapshot().getBalance());
        assertTrue(restored.isUnlocked(ShopTestFixtures.UNLOCK));
        assertTrue(restored.isUnlocked(ShopTestFixtures.UPGRADE));
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, restarted.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(400, restored.getBalance());
    }

    @Test
    void failedSaveKeepsPurchaseAndRetrySavesLatestAssets() throws Exception {
        saveFile.save(assets.snapshot());
        byte[] previousSave = Files.readAllBytes(save);
        Path blocker = temp.resolve("not-a-directory");
        Files.write(blocker, new byte[] { 1 });
        saveFile.setPath(blocker.resolve("assets.properties"));

        assertEquals(PurchaseResult.SUCCESS, shop.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(900, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertFalse(shop.retrySave());
        assertEquals(PurchaseResult.CATEGORY_MISMATCH, shop.purchase("UNKNOWN"));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertArrayEquals(previousSave, Files.readAllBytes(save));

        RewardSystem rewards = new RewardSystem(assets);
        rewards.rewardEnemy("BASIC");
        assertTrue(rewards.settle());
        int latestBalance = assets.getBalance();
        assertTrue(latestBalance > 900);
        saveFile.setPath(save);

        assertTrue(shop.retrySave());
        assertEquals(ShopController.SaveStatus.SAVED, shop.getSaveStatus());
        assertEquals(latestBalance, saveFile.load().getBalance());
        assertTrue(saveFile.load().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
        byte[] recovered = Files.readAllBytes(save);
        assertTrue(shop.retrySave());
        assertArrayEquals(recovered, Files.readAllBytes(save));
        assertEquals(latestBalance, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
    }

    @Test
    void laterPurchaseAlsoSavesEarlierUnsavedPurchase() throws Exception {
        Path blocker = temp.resolve("blocker");
        Files.write(blocker, new byte[] { 1 });
        saveFile.setPath(blocker.resolve("save"));
        shop.purchase(ShopTestFixtures.UPGRADE);
        saveFile.setPath(save);

        shop.purchase(ShopTestFixtures.UNLOCK);

        assertEquals(ShopController.SaveStatus.SAVED, shop.getSaveStatus());
        PlayerAssetsState saved = saveFile.load();
        assertEquals(400, saved.getBalance());
        assertTrue(saved.getUnlockedContentIds().contains(ShopTestFixtures.UNLOCK));
        assertTrue(saved.getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
    }

    @Test
    void productListAndSnapshotsAreReadOnlyCopies() {
        List<ShopProduct> input = new ArrayList<ShopProduct>(ShopTestFixtures.products());
        ShopController configured = new ShopController(assets, files, input, saveFile);
        input.clear();

        assertEquals(2, configured.getProducts().size());
        assertEquals(100, configured.getProducts().get(0).getPrice());
        assertEquals(ShopTestFixtures.products().get(0).getDescription(),
                configured.getProducts().get(0).getDescription());
        assertThrows(UnsupportedOperationException.class, () -> configured.getProducts().clear());
        PlayerAssetsState before = configured.snapshot();
        configured.purchase(ShopTestFixtures.UPGRADE);
        assertEquals(1000, before.getBalance());
        assertTrue(before.getUnlockedContentIds().isEmpty());
    }

    @Test
    void invalidDependenciesAndProductDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShopController(null, files));
        assertThrows(IllegalArgumentException.class, () -> new ShopController(assets, null));
        assertThrows(IllegalArgumentException.class, () -> new ShopController(assets, files, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopController(assets, files, Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> new ShopProduct(" ", "X", Category.UPGRADE, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShopProduct("x", "", Category.UPGRADE, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShopProduct("x", "X", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShopProduct("x", "X", Category.UPGRADE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopProduct("x", "X", Category.UPGRADE, 1, " "));
        List<ShopProduct> duplicate = new ArrayList<ShopProduct>(ShopTestFixtures.products());
        duplicate.add(new ShopProduct(ShopTestFixtures.UPGRADE, "Duplicate", Category.UNLOCKABLE, 10));

        assertThrows(IllegalArgumentException.class, () -> new ShopController(assets, files, duplicate));
        assertFalse(Files.exists(save));
    }

    @Test
    void controllersKeepTheirSavePathsIndependent() throws Exception {
        ShopTestFixtures.SaveFile otherSave = new ShopTestFixtures.SaveFile(files,
                temp.resolve("other-assets.properties"));
        ShopController other = ShopTestFixtures.controller(ShopTestFixtures.fundedAssets(), otherSave);

        shop.purchase(ShopTestFixtures.UPGRADE);
        other.purchase(ShopTestFixtures.UNLOCK);

        assertEquals(900, saveFile.load().getBalance());
        assertTrue(saveFile.load().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
        assertFalse(saveFile.load().getUnlockedContentIds().contains(ShopTestFixtures.UNLOCK));
        assertEquals(500, otherSave.load().getBalance());
        assertFalse(otherSave.load().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
        assertTrue(otherSave.load().getUnlockedContentIds().contains(ShopTestFixtures.UNLOCK));
    }

    @Test
    void retryWithoutFailedSaveDoesNotWrite() {
        assertTrue(shop.retrySave());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, shop.getSaveStatus());
        assertFalse(Files.exists(save));
    }
}
