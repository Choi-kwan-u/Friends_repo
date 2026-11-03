package shop;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import engine.Core;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PermanentUpgradesTest {
    @TempDir Path temp;
    private PlayerAssets assets;
    private ShopTestFixtures.SaveFile save;
    private ShopController shop;

    @BeforeEach
    void setUp() {
        assets = new PlayerAssets();
        assets.restore(new PlayerAssetsState(3000, Collections.singleton("legacy.ship"),
                Collections.singletonMap("legacy.item", 4)));
        save = new ShopTestFixtures.SaveFile(Core.getFileManager(), temp.resolve("assets.properties"));
        shop = ShopTestFixtures.upgradeController(assets, save);
    }

    @Test
    void buysAllThreeTracksInOrderAndRestoresThemWithoutLosingOtherAssets() throws Exception {
        int expectedBalance = 3000;
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            for (int level = 1; level <= upgrade.getMaxLevel(); level++) {
                assertEquals(6, shop.getProducts().size());
                assertEquals(upgrade.getProductId(level),
                        shop.getProducts().get(upgrade.ordinal()).getId());
                assertEquals(PurchaseResult.SUCCESS, shop.purchase(upgrade.getProductId(level)));
                expectedBalance -= upgrade.getPrice(level);
                assertEquals(expectedBalance, assets.getBalance());
                assertEquals(level, upgrade.getLevel(save.load()));
                byte[] written = Files.readAllBytes(save.getPath());
                assertEquals(PurchaseResult.ALREADY_UNLOCKED,
                        shop.purchase(upgrade.getProductId(level)));
                assertArrayEquals(written, Files.readAllBytes(save.getPath()));
            }
            assertTrue(shop.getProducts().get(upgrade.ordinal()).getName().endsWith("MAX"));
        }
        assertEquals(450, expectedBalance);
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(save.load()));
        ShopController reopened = ShopTestFixtures.upgradeController(restored, save);
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            assertEquals(3, upgrade.getLevel(reopened.snapshot()));
        }
        assertEquals(450, restored.getBalance());
        assertTrue(restored.isUnlocked("legacy.ship"));
        assertEquals(4, restored.snapshot().getItemQuantities().get("legacy.item"));
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, reopened.getSaveStatus());
    }

    @Test
    void skippedTierAndInsufficientFundsLeaveProgressAndSaveUntouched() {
        assertEquals(PurchaseResult.ITEM_NOT_AVAILABLE,
                shop.purchase(PermanentUpgrade.HULL.getProductId(2)));
        assertEquals(3000, assets.getBalance());
        assets.restore(new PlayerAssetsState(99, null, null));
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS,
                shop.purchase(PermanentUpgrade.HULL.getProductId(1)));
        assertEquals(99, assets.getBalance());
        assertEquals(0, PermanentUpgrade.HULL.getLevel(assets.snapshot()));
        assertEquals(PermanentUpgrade.HULL.getProductId(1), shop.getProducts().get(0).getId());
        assertFalse(Files.exists(save.getPath()));
    }

    @Test
    void saveRetryPersistsLatestTierWithoutBuyingItAgain() throws Exception {
        Path destination = save.getPath();
        Path blocker = temp.resolve("not-a-directory");
        Files.write(blocker, new byte[] {1});
        save.setPath(blocker.resolve("assets.properties"));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.ENGINE.getProductId(1)));
        assertEquals(PurchaseResult.SUCCESS, shop.purchase(PermanentUpgrade.ENGINE.getProductId(2)));
        assertEquals(ShopController.SaveStatus.FAILED, shop.getSaveStatus());
        assertEquals(2650, assets.getBalance());
        assertFalse(shop.retrySave());
        save.setPath(destination);
        assertTrue(shop.retrySave());
        assertEquals(2, PermanentUpgrade.ENGINE.getLevel(save.load()));
        assertEquals(2650, save.load().getBalance());
        byte[] recovered = Files.readAllBytes(destination);
        assertTrue(shop.retrySave());
        assertArrayEquals(recovered, Files.readAllBytes(destination));
    }
}
