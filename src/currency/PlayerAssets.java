package currency;

import java.util.Collections;
import java.util.Map;

/** Shared balance and permanent ownership for purchases, rewards and saving. */
public class PlayerAssets {
    private CurrencyBalance currencyBalance = new CurrencyBalance();
    private UnlockManager unlockManager = new UnlockManager();
    // Preserve old save fields without exposing an inventory purchase or use path.
    private Map<String, Integer> legacyItemQuantities = Collections.emptyMap();

    public int getBalance() {
        return currencyBalance.getBalance();
    }

    public boolean canAfford(int amount) {
        return currencyBalance.canAfford(amount);
    }

    public boolean isUnlocked(String contentId) {
        return unlockManager.isUnlocked(contentId);
    }

    boolean addCurrency(int amount) {
        return currencyBalance.add(amount);
    }

    boolean deductCurrency(int amount) {
        return currencyBalance.deduct(amount);
    }

    boolean unlock(String contentId) {
        return unlockManager.unlock(contentId);
    }

    public PlayerAssetsState snapshot() {
        return new PlayerAssetsState(getBalance(), unlockManager.copyUnlockedContentIds(),
                legacyItemQuantities);
    }

    /** Restore the validated snapshot atomically, retaining legacy save data. */
    public boolean restore(PlayerAssetsState state) {
        if (state == null) return false;
        CurrencyBalance restoredBalance;
        try {
            restoredBalance = new CurrencyBalance(state.getBalance());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        UnlockManager restoredUnlocks = new UnlockManager();
        for (String id : state.getUnlockedContentIds()) {
            if (!restoredUnlocks.unlock(id)) return false;
        }
        currencyBalance = restoredBalance;
        unlockManager = restoredUnlocks;
        legacyItemQuantities = state.getItemQuantities();
        return true;
    }
}
