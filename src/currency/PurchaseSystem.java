package currency;

/**
 * Manages purchases of permanent stat upgrades and unlocks.
 *
 * <p>Uses PlayerAssets to access and update the player's currency
 * and item ownership, and ShopCatalog to validate product categories
 * and retrieve prices.</p>
 *
 * <p>Purchase validation and asset updates are handled here.
 * Saving the updated player state is outside this class's
 * responsibility.</p>
 */
public class PurchaseSystem {
    private final PlayerAssets assets;
    private final ShopCatalog catalog;

    public PurchaseSystem(PlayerAssets assets, ShopCatalog catalog) {
        this.assets = assets;
        this.catalog = catalog;
    }

    /**
     * Purchases a permanent unlockable item.
     *
     * <p>Validates the item ID and product category, then checks whether
     * the item is already unlocked. If it is already unlocked, the
     * purchase is rejected without deducting currency. Otherwise, the
     * item price is deducted from the player's currency. If unlocking
     * the item fails, the deducted currency is restored.</p>
     *
     * @param itemId the identifier of the unlockable item to purchase
     * @return SUCCESS if the item is unlocked successfully, or a result
     *         describing the reason for failure
     */
    public PurchaseResult purchaseUnlockable(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return PurchaseResult.INVALID_ITEM_ID;
        }
        if (!catalog.isUnlockable(itemId)) {
            return PurchaseResult.CATEGORY_MISMATCH;
        }

        // Reject if already unlocked without charging
        if (assets.isUnlocked(itemId)) {
            return PurchaseResult.ALREADY_UNLOCKED;
        }

        for (String required = catalog.getRequiredUnlockId(itemId); required != null;
                required = catalog.getRequiredUnlockId(required)) {
            if (!assets.isUnlocked(required)) return PurchaseResult.ITEM_NOT_AVAILABLE;
        }

        int price = catalog.getPrice(itemId);

        if (!assets.deductCurrency(price)) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        // Attempt to grant the unlock. Rollback if unexpected failure occurs.
        if (!assets.unlock(itemId)) {
            assets.addCurrency(price);
            return PurchaseResult.ALREADY_UNLOCKED;
        }

        return PurchaseResult.SUCCESS;
    }
}
