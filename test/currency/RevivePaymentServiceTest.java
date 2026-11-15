
package currency;

import java.io.IOException;

public class RevivePaymentServiceTest {

    public static void main(String[] args) {

        PlayerAssets assets = new PlayerAssets();
        assets.restore(new PlayerAssetsState(100, null, null));

        RevivePaymentService service =
                new RevivePaymentService(assets, state -> { });

        check(service.pay(50)
                == RevivePaymentService.Result.SUCCESS);
        check(assets.getBalance() == 50);

        check(service.pay(150)
                == RevivePaymentService.Result.INSUFFICIENT_FUNDS);
        check(assets.getBalance() == 50);

        check(service.pay(0)
                == RevivePaymentService.Result.INVALID_COST);

        PlayerAssets failingAssets = new PlayerAssets();
        failingAssets.restore(
                new PlayerAssetsState(100, null, null));

        RevivePaymentService failingService =
                new RevivePaymentService(failingAssets, state -> {
                    throw new IOException("Save failed");
                });

        check(failingService.pay(50)
                == RevivePaymentService.Result.SAVE_FAILED);
        check(failingAssets.getBalance() == 100);

        System.out.println("ALL PAYMENT TESTS PASSED!");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Payment test failed!");
        }
    }
}
