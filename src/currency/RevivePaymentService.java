
package currency;

import java.io.IOException;

public final class RevivePaymentService {

    public enum Result {
        SUCCESS,
        INVALID_COST,
        INSUFFICIENT_FUNDS,
        SAVE_FAILED
    }

    @FunctionalInterface
    public interface Saver {
        void save(PlayerAssetsState state) throws IOException;
    }

    private final PlayerAssets assets;
    private final Saver saver;

    public RevivePaymentService(PlayerAssets assets, Saver saver) {
        if (assets == null || saver == null) {
            throw new IllegalArgumentException(
                    "Assets and saver cannot be null.");
        }

        this.assets = assets;
        this.saver = saver;
    }

    public synchronized Result pay(int cost) {
        if (cost <= 0) {
            return Result.INVALID_COST;
        }

        if (!assets.canAfford(cost)) {
            return Result.INSUFFICIENT_FUNDS;
        }

        PlayerAssetsState before = assets.snapshot();

        if (!assets.deductCurrency(cost)) {
            return Result.INSUFFICIENT_FUNDS;
        }

        try {
            saver.save(assets.snapshot());
            return Result.SUCCESS;

        } catch (IOException | SecurityException e) {
            if (!assets.restore(before)) {
                throw new IllegalStateException(
                        "Failed to restore player assets.", e);
            }
            return Result.SAVE_FAILED;
        }
    }
}
