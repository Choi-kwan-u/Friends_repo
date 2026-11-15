package shop;

import currency.PlayerAssets;
import currency.PlayerAssetsState;
import engine.Core;
import engine.SettlementResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import revive.ReviveManager;
import revive.ReviveService;
import static org.junit.jupiter.api.Assertions.*;

class RevivePaymentOrderingTest {
    @ParameterizedTest
    @EnumSource(ReviveManager.Mode.class)
    void bothPricesSaveBeforeTheCountChangesAndClosedOffersCannotChargeAgain(ReviveManager.Mode mode) {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(1000, null, null)));
        ReviveManager manager = new ReviveManager(mode);
        List<String> order = new ArrayList<>();
        AtomicInteger usedAtSave = new AtomicInteger();
        ShopController shop = new ShopController(assets, Core.getFileManager(), ShopProducts.availableProducts(), state -> {
            order.add("save"); assertEquals(usedAtSave.get(), manager.getRevivesUsed());
            assertEquals(assets.getBalance(), state.getBalance());
        });
        ReviveService service = new ReviveService(manager, shop, () -> { order.add("settle"); return SettlementResult.SUCCESS; });
        int balance = 1000;
        int[] costs = mode == ReviveManager.Mode.CAMPAIGN ? new int[] {50, 150} : new int[] {20, 50};
        assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
        assertTrue(order.isEmpty());
        for (int used = 0; used < 2; used++) {
            usedAtSave.set(used);
            assertTrue(service.openOffer()); assertTrue(service.openOffer());
            assertEquals(costs[used], service.getNextReviveCost());
            assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
            balance -= costs[used];
            assertEquals(balance, assets.getBalance());
            assertEquals(used + 1, manager.getRevivesUsed());
            assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
        }
        assertEquals(List.of("settle", "save", "settle", "save"), order);
        assertFalse(service.openOffer());
    }

    @ParameterizedTest
    @EnumSource(ReviveManager.Mode.class)
    void settlementMakesCreditsAvailableAndFailedPaymentNeverConsumesAChance(ReviveManager.Mode mode) {
        PlayerAssets assets = new PlayerAssets();
        ReviveManager manager = new ReviveManager(mode);
        int price = manager.getNextReviveCost();
        AtomicBoolean pending = new AtomicBoolean(true), failSave = new AtomicBoolean(true);
        List<String> order = new ArrayList<>();
        ShopController shop = new ShopController(assets, Core.getFileManager(), ShopProducts.availableProducts(), state -> {
            order.add("save"); assertEquals(0, manager.getRevivesUsed());
            assertEquals(0, state.getBalance());
            if (failSave.get()) throw new IOException("injected pre-commit failure");
        });
        ReviveService service = new ReviveService(manager, shop, () -> {
            order.add("settle");
            if (pending.getAndSet(false)) assertTrue(assets.restore(new PlayerAssetsState(price, null, null)));
            return SettlementResult.SUCCESS;
        });
        assertTrue(service.openOffer());
        assertEquals(ReviveService.Result.SAVE_FAILED, service.confirmRevive());
        assertEquals(price, assets.getBalance()); assertEquals(0, manager.getRevivesUsed());
        failSave.set(false);
        assertEquals(ReviveService.Result.SUCCESS, service.confirmRevive());
        assertEquals(0, assets.getBalance()); assertEquals(1, manager.getRevivesUsed());
        assertEquals(List.of("settle", "save", "settle", "save"), order);
        assertEquals(ReviveService.Result.NO_ACTIVE_OFFER, service.confirmRevive());
    }

    @Test
    void simultaneousConfirmsOnOneOfferHaveOnePaymentAndOneSuccessfulCount() throws Exception {
        PlayerAssets assets = new PlayerAssets(); assets.restore(new PlayerAssetsState(100, null, null));
        AtomicInteger saves = new AtomicInteger(), settlements = new AtomicInteger();
        ShopController shop = new ShopController(assets, Core.getFileManager(), ShopProducts.availableProducts(), state -> saves.incrementAndGet());
        ReviveService service = new ReviveService(new ReviveManager(ReviveManager.Mode.ENDLESS), shop,
                () -> { settlements.incrementAndGet(); return SettlementResult.SUCCESS; });
        assertTrue(service.openOffer());
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<ReviveService.Result>) () -> {
                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); return service.confirmRevive();
            };
            var first = workers.submit(task); var second = workers.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            List<ReviveService.Result> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertTrue(results.contains(ReviveService.Result.SUCCESS));
            assertTrue(results.contains(ReviveService.Result.NO_ACTIVE_OFFER));
            assertEquals(80, assets.getBalance()); assertEquals(1, saves.get()); assertEquals(1, settlements.get());
            assertEquals(1, service.getRemainingRevives());
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
