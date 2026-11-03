package currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseSystemTest {
    private PlayerAssets assets;
    private ShopCatalog catalog;
    private PurchaseSystem store;

    @BeforeEach
    void setUp() {
        assets = new PlayerAssets();
        assets.addCurrency(1000);
        catalog = new ShopCatalog();
        catalog.registerUpgrade("upgrade", 100);
        catalog.registerUnlockable("stage_02", 500);
        store = new PurchaseSystem(assets, catalog);
    }

    @Test
    void upgradePurchaseGrantsPermanentOwnership() {
        assertEquals(PurchaseResult.SUCCESS, store.purchaseUnlockable("upgrade"));
        assertEquals(900, assets.getBalance());
        assertTrue(assets.isUnlocked("upgrade"));
        assertTrue(assets.snapshot().getItemQuantities().isEmpty());
    }

    @Test
    void permanentUnlockPurchaseRemainsAvailable() {
        assertEquals(PurchaseResult.SUCCESS, store.purchaseUnlockable("stage_02"));
        assertTrue(assets.isUnlocked("stage_02"));
        assertEquals(500, assets.getBalance());
    }

    @Test
    void neitherCategoryCanBeChargedTwice() {
        assertEquals(PurchaseResult.SUCCESS, store.purchaseUnlockable("upgrade"));
        assertEquals(PurchaseResult.SUCCESS, store.purchaseUnlockable("stage_02"));
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, store.purchaseUnlockable("upgrade"));
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, store.purchaseUnlockable("stage_02"));
        assertEquals(400, assets.getBalance());
    }

    @Test
    void insufficientFundsDoNotGrantOwnership() {
        catalog.registerUpgrade("expensive", Integer.MAX_VALUE);
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, store.purchaseUnlockable("expensive"));
        assertEquals(1000, assets.getBalance());
        assertFalse(assets.isUnlocked("expensive"));
    }

    @Test
    void invalidAndUnknownIdsDoNotCharge() {
        assertEquals(PurchaseResult.INVALID_ITEM_ID, store.purchaseUnlockable(null));
        assertEquals(PurchaseResult.INVALID_ITEM_ID, store.purchaseUnlockable("   "));
        assertEquals(PurchaseResult.CATEGORY_MISMATCH, store.purchaseUnlockable("unknown"));
        assertEquals(1000, assets.getBalance());
        assertTrue(assets.snapshot().getUnlockedContentIds().isEmpty());
    }

    @Test
    void failedUnlockRestoresTheDeductedPrice() {
        PlayerAssets rejecting = new PlayerAssets() {
            @Override boolean unlock(String id) { return false; }
        };
        rejecting.addCurrency(1000);
        PurchaseSystem purchases = new PurchaseSystem(rejecting, catalog);
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, purchases.purchaseUnlockable("upgrade"));
        assertEquals(1000, rejecting.getBalance());
        assertFalse(rejecting.isUnlocked("upgrade"));
    }
}
