package shop;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.ShopCatalog.Category;
import engine.Core;
import entity.ShipType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AvailableShipsTest {
    @TempDir Path temp;

    @Test
    void onlySpeedsterRequiresAPurchaseAndBrowsingNeverCreatesASave() {
        PlayerAssets assets = new PlayerAssets();
        ShopTestFixtures.SaveFile save = save();
        ShopController shop = ShopTestFixtures.upgradeController(assets, save);
        List<ShopProduct> ships = shop.getProducts().stream()
                .filter(product -> product.getCategory() == Category.UNLOCKABLE).toList();

        assertEquals(ShipType.values().length, ships.size());
        for (int index = 0; index < ships.size(); index++) {
            ShopProduct product = ships.get(index);
            assertSame(ShipType.values()[index], product.getShipType());
            assertEquals(product.getShipType().getName(), product.getName());
            if (product.getShipType() == ShipType.SPEEDSTER) {
                assertFalse(product.isAvailableByDefault());
                assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
                assertEquals(200, product.getPrice());
                assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, shop.purchase(product.getId()));
                continue;
            }
            assertTrue(product.isAvailableByDefault());
            assertTrue(shop.isShipAvailable(product.getShipType()));
            assertEquals(0, product.getPrice());
            assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(product.getId()));
            assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase(product.getId()));
        }
        assertEquals(0, assets.getBalance());
        assertTrue(assets.snapshot().getUnlockedContentIds().isEmpty());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, shop.getSaveStatus());
        assertFalse(Files.exists(save.getPath()));
        assertFalse(shop.isShipAvailable(null));
    }

    @Test
    void purchasingSpeedsterPersistsHangarAccessWithoutChargingAgain() throws Exception {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(200, null, null)));
        ShopTestFixtures.SaveFile save = save();
        ShopController shop = ShopTestFixtures.upgradeController(assets, save);
        assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase("ship.speedster"));
        assertTrue(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(0, assets.getBalance());

        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(save.load()));
        ShopController reopened = ShopTestFixtures.upgradeController(restored, save);
        assertTrue(reopened.isShipAvailable(ShipType.SPEEDSTER));
        byte[] before = Files.readAllBytes(save.getPath());
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, reopened.purchase("ship.speedster"));
        assertEquals(0, reopened.getBalance());
        assertArrayEquals(before, Files.readAllBytes(save.getPath()));
    }

    @Test
    void failedSaveKeepsSpeedsterUsableAndRetryPreservesTheUnlock() throws Exception {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(200, null, null)));
        ShopTestFixtures.SaveFile save = save();
        Path destination = save.getPath();
        Path blocker = Files.write(temp.resolve("blocker"), new byte[] {1});
        save.setPath(blocker.resolve("assets.properties"));
        ShopController shop = ShopTestFixtures.upgradeController(assets, save);
        assertEquals(PurchaseResult.SUCCESS, shop.purchase("ship.speedster"));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertTrue(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(0, assets.getBalance());
        save.setPath(destination);
        assertTrue(shop.retrySave());
        assertEquals(0, save.load().getBalance());
        assertTrue(save.load().getUnlockedContentIds().contains("ship.speedster"));
    }

    @Test
    void existingOwnershipSurvivesWhileDefaultAvailabilityIsNeverWritten() throws Exception {
        PlayerAssets assets = new PlayerAssets();
        assets.restore(new PlayerAssetsState(1000, Collections.singleton("ship.tank"),
                Collections.singletonMap("legacy.item", 4)));
        ShopTestFixtures.SaveFile save = save();
        save.save(assets.snapshot());
        byte[] before = Files.readAllBytes(save.getPath());
        ShopController shop = ShopTestFixtures.upgradeController(assets, save);

        for (ShopProduct product : shop.getProducts()) {
            if (product.isAvailableByDefault()) shop.purchase(product.getId());
        }
        assertArrayEquals(before, Files.readAllBytes(save.getPath()));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(save.load()));
        assertEquals(900, restored.getBalance());
        assertTrue(restored.isUnlocked("ship.tank"));
        assertFalse(restored.isUnlocked("ship.standard"));
        assertFalse(restored.isUnlocked("ship.speedster"));
        assertEquals(4, restored.snapshot().getItemQuantities().get("legacy.item"));
        ShopController reopened = ShopTestFixtures.upgradeController(restored, save);
        assertEquals(2, reopened.getProducts().stream().filter(ShopProduct::isAvailableByDefault).count());
    }

    @Test
    void pricedShipDefinitionsUseExistingPurchasesWithoutRevokingSavedOwnership() throws Exception {
        PlayerAssets assets = new PlayerAssets();
        assets.restore(new PlayerAssetsState(500, Collections.singleton("ship.tank"), null));
        ShopTestFixtures.SaveFile save = save();
        List<ShopProduct> definitions = Arrays.asList(
                ShopProduct.ship(ShipType.SPEEDSTER, false, 200),
                ShopProduct.ship(ShipType.TANK, false, 300));
        ShopController shop = ShopTestFixtures.controller(assets, save, definitions);

        assertTrue(shop.isShipAvailable(ShipType.TANK));
        assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase("ship.tank"));
        assertEquals(500, assets.getBalance());
        assertFalse(Files.exists(save.getPath()));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase("ship.speedster"));
        assertTrue(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(300, save.load().getBalance());
        assertTrue(save.load().getUnlockedContentIds().containsAll(
                Arrays.asList("ship.tank", "ship.speedster")));
        byte[] written = Files.readAllBytes(save.getPath());
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, shop.purchase("ship.speedster"));
        assertArrayEquals(written, Files.readAllBytes(save.getPath()));
    }

    @Test
    void duplicateShipIdsAndInconsistentAvailabilityPricesAreRejected() {
        ShopProduct available = ShopProduct.ship(ShipType.STANDARD, true, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ShopProduct.ship(null, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ShopProduct.ship(ShipType.STANDARD, true, 100));
        assertThrows(IllegalArgumentException.class,
                () -> ShopProduct.ship(ShipType.STANDARD, false, 0));
        assertThrows(IllegalArgumentException.class, () -> ShopTestFixtures.controller(
                new PlayerAssets(), save(), Arrays.asList(available, available)));
        assertThrows(IllegalArgumentException.class, () -> ShopTestFixtures.controller(
                new PlayerAssets(), save(), Arrays.asList(available,
                        ShopProduct.ship(ShipType.STANDARD, false, 100))));
    }

    private ShopTestFixtures.SaveFile save() {
        return new ShopTestFixtures.SaveFile(Core.getFileManager(), temp.resolve("assets.properties"));
    }
}
