package currency;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class PermanentUpgradeTest {
    @ParameterizedTest
    @EnumSource(PermanentUpgrade.class)
    void readsOnlyConsecutiveOwnedTiers(PermanentUpgrade upgrade) {
        PlayerAssetsState incomplete = new PlayerAssetsState(42,
                new HashSet<String>(Arrays.asList(upgrade.getProductId(1),
                        upgrade.getProductId(3), "other.content")), null);
        assertEquals(1, upgrade.getLevel(incomplete));
        assertEquals(0, upgrade.getLevel(new PlayerAssetsState(42,
                Collections.singleton(upgrade.getProductId(3)), null)));
        assertEquals(0, upgrade.getBonus(0));
        assertEquals(3, upgrade.getMaxLevel());
        assertThrows(IllegalArgumentException.class, () -> upgrade.getProductId(0));
        assertThrows(IllegalArgumentException.class, () -> upgrade.getPrice(4));
    }

    @Test
    void prerequisitesRejectSkippedTiersBeforeCharging() {
        ShopCatalog catalog = new ShopCatalog();
        assertFalse(catalog.registerUpgrade("tier2", 250, "tier1"));
        assertTrue(catalog.registerUpgrade("tier1", 100));
        assertTrue(catalog.registerUpgrade("tier2", 250, "tier1"));
        assertTrue(catalog.registerUpgrade("tier3", 500, "tier2"));
        assertFalse(catalog.registerUpgrade("tier1", 100, "tier3"));
        assertFalse(catalog.registerUpgrade("loop", 100, "loop"));
        assertFalse(catalog.registerUpgrade("bad", 10, "missing"));
        PlayerAssets assets = new PlayerAssets();
        assets.addCurrency(1000);
        PurchaseSystem purchases = new PurchaseSystem(assets, catalog);
        assertEquals(PurchaseResult.ITEM_NOT_AVAILABLE, purchases.purchaseUnlockable("tier2"));
        assertEquals(PurchaseResult.ITEM_NOT_AVAILABLE, purchases.purchaseUnlockable("tier3"));
        assertEquals(1000, assets.getBalance());
        // A damaged/imported save containing tier2 must not bypass tier1 either.
        assets.unlock("tier2");
        assertEquals(PurchaseResult.ITEM_NOT_AVAILABLE, purchases.purchaseUnlockable("tier3"));
        assertEquals(PurchaseResult.SUCCESS, purchases.purchaseUnlockable("tier1"));
        assertEquals(PurchaseResult.SUCCESS, purchases.purchaseUnlockable("tier3"));
        assertEquals(400, assets.getBalance());
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, purchases.purchaseUnlockable("tier3"));
        assertEquals(400, assets.getBalance());
    }
}
