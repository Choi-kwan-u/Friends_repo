package currency;

/**
 * Represents the possible outcomes of an item purchase operation.
 * Each value indicates whether the purchase succeeded or why it was rejected.
 */
public enum PurchaseResult {
    /** Indicates that the purchase completed successfully. */
    SUCCESS,
    /** Indicates that the item ID is null or blank. */
    INVALID_ITEM_ID,
    /** Indicates that the requested item is not available for purchase. */
    ITEM_NOT_AVAILABLE,
    /** Indicates that the item's category does not match the required category. */
    CATEGORY_MISMATCH,
    /** Indicates that the player does not have enough funds for the purchase. */
    INSUFFICIENT_FUNDS,
    /** Indicates that the content has already been unlocked. */
    ALREADY_UNLOCKED
}
