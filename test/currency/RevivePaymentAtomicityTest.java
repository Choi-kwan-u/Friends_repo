package currency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RevivePaymentAtomicityTest {
    @ParameterizedTest
    @CsvSource({
        "0,1,INSUFFICIENT_FUNDS,0", "19,20,INSUFFICIENT_FUNDS,19", "20,20,SUCCESS,0",
        "49,50,INSUFFICIENT_FUNDS,49", "50,50,SUCCESS,0", "150,150,SUCCESS,0",
        "200,150,SUCCESS,50", "2147483647,2147483647,SUCCESS,0",
        "2147483647,20,SUCCESS,2147483627", "100,0,INVALID_COST,100",
        "100,-1,INVALID_COST,100", "100,-2147483648,INVALID_COST,100",
        "100,2147483647,INSUFFICIENT_FUNDS,100"
    })
    void rejectsInvalidOrUnaffordableCostsWithoutSavingAndNeverGoesNegative(
            int initial, int cost, RevivePaymentService.Result expected, int remaining) {
        PlayerAssets assets = assets(initial);
        AtomicInteger saves = new AtomicInteger();
        RevivePaymentService payment = new RevivePaymentService(assets, state -> saves.incrementAndGet());
        assertEquals(expected, payment.pay(cost));
        assertEquals(remaining, assets.getBalance());
        assertTrue(assets.getBalance() >= 0);
        assertEquals(expected == RevivePaymentService.Result.SUCCESS ? 1 : 0, saves.get());
        assertOwnership(assets.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "security"})
    void failedSaveRestoresTheFullSnapshotAndANewAttemptChargesOnlyOnce(String failure) {
        PlayerAssets assets = assets(100);
        List<PlayerAssetsState> attempted = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        RevivePaymentService payment = new RevivePaymentService(assets, state -> {
            attempted.add(state);
            if (attempts.getAndIncrement() == 0) {
                if (failure.equals("io")) throw new IOException("injected pre-commit failure");
                throw new SecurityException("injected pre-commit access denial");
            }
        });
        assertEquals(RevivePaymentService.Result.SAVE_FAILED, payment.pay(50));
        assertEquals(100, assets.getBalance());
        assertOwnership(assets.snapshot());
        assertEquals(RevivePaymentService.Result.SUCCESS, payment.pay(50));
        assertEquals(50, assets.getBalance());
        assertEquals(2, attempts.get());
        assertEquals(List.of(50, 50), attempted.stream().map(PlayerAssetsState::getBalance).toList());
        for (PlayerAssetsState snapshot : attempted) assertOwnership(snapshot);
    }

    @Test
    void theSavedSnapshotCannotChangeWithLaterRuntimeAssets() {
        PlayerAssets assets = assets(100);
        List<PlayerAssetsState> saved = new ArrayList<>();
        assertEquals(RevivePaymentService.Result.SUCCESS,
                new RevivePaymentService(assets, saved::add).pay(20));
        assertTrue(assets.restore(new PlayerAssetsState(0, null, null)));
        assertEquals(80, saved.get(0).getBalance());
        assertOwnership(saved.get(0));
    }

    private PlayerAssets assets(int amount) {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(amount, Set.of("owned"), Map.of("legacy", 3))));
        return assets;
    }

    private void assertOwnership(PlayerAssetsState snapshot) {
        assertEquals(Set.of("owned"), snapshot.getUnlockedContentIds());
        assertEquals(Map.of("legacy", 3), snapshot.getItemQuantities());
    }
}
