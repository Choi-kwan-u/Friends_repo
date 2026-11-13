
package revive;

import currency.RevivePaymentService;
import engine.SettlementResult;
import shop.ShopController;

import java.util.function.Supplier;

public final class ReviveService {

    public enum Result {
        SUCCESS,
        NO_ACTIVE_OFFER,
        NO_REVIVES_LEFT,
        INSUFFICIENT_FUNDS,
        SETTLEMENT_FAILED,
        SAVE_FAILED
    }

    private final ReviveManager manager;
    private final ShopController shop;
    private final Supplier<SettlementResult> settleCredits;

    private boolean offerOpen;

    public ReviveService(ReviveManager manager,
                         ShopController shop,
                         Supplier<SettlementResult> settleCredits) {

        if (manager == null || shop == null || settleCredits == null) {
            throw new IllegalArgumentException(
                    "Revive dependencies cannot be null.");
        }

        this.manager = manager;
        this.shop = shop;
        this.settleCredits = settleCredits;
    }

    /** Read-only offer data; these accessors never settle, save or charge. */
    public synchronized int getNextReviveCost() {
        return manager.getNextReviveCost();
    }

    public synchronized int getRemainingRevives() {
        return manager.getRemainingRevives();
    }

    public synchronized int getBalance() {
        return shop.getBalance();
    }

    public synchronized boolean openOffer() {
        if (!manager.hasRevivesLeft()) {
            return false;
        }

        offerOpen = true;
        return true;
    }

    public synchronized Result confirmRevive() {
        if (!offerOpen) {
            return Result.NO_ACTIVE_OFFER;
        }

        if (!manager.hasRevivesLeft()) {
            return Result.NO_REVIVES_LEFT;
        }

        // Settlement may add earned Credits to memory before its save fails.
        // Leave the offer and revive count intact; retry the coordinator, never
        // restore the older pre-settlement balance or charge on a failed save.
        SettlementResult settlement = settleCredits.get();
        if (settlement == null) {
            return Result.SETTLEMENT_FAILED;
        }

        if (settlement == SettlementResult.SETTLEMENT_FAILED) {
            return Result.SETTLEMENT_FAILED;
        }

        if (settlement == SettlementResult.SAVE_FAILED) {
            return Result.SAVE_FAILED;
        }

        int cost = manager.getNextReviveCost();

        if (!manager.canRevive(shop.getBalance())) {
            return Result.INSUFFICIENT_FUNDS;
        }

        RevivePaymentService.Result payment =
                shop.payForRevive(cost);

        if (payment == RevivePaymentService.Result.INSUFFICIENT_FUNDS) {
            return Result.INSUFFICIENT_FUNDS;
        }

        if (payment == RevivePaymentService.Result.SAVE_FAILED) {
            return Result.SAVE_FAILED;
        }

        if (payment != RevivePaymentService.Result.SUCCESS) {
            throw new IllegalStateException(
                    "Unexpected revive payment result.");
        }

        manager.recordSuccessfulRevive();
        offerOpen = false;

        return Result.SUCCESS;
    }

    public synchronized void decline() {
        offerOpen = false;
    }
}
