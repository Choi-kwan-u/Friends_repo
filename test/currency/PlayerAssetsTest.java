package currency;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAssetsTest {
    @Test
    void startsWithEmptyAssets() {
        PlayerAssets assets = new PlayerAssets();
        assertEquals(0, assets.getBalance());
        assertFalse(assets.canAfford(1));
        assertFalse(assets.isUnlocked("BGM_01"));
        assertTrue(assets.snapshot().getUnlockedContentIds().isEmpty());
        assertTrue(assets.snapshot().getItemQuantities().isEmpty());
    }

    @Test
    void managesEachAssetIndependently() {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.addCurrency(100));
        assertTrue(assets.canAfford(100));
        assertTrue(assets.deductCurrency(30));
        assertTrue(assets.unlock("BGM_01"));
        assertEquals(70, assets.getBalance());
        assertTrue(assets.isUnlocked("BGM_01"));
        assertFalse(assets.snapshot().getItemQuantities().containsKey("BOMB"));
    }

    @Test
    void rejectedCurrencyOperationsPreserveAllAssets() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState before = assets.snapshot();

        assertFalse(assets.addCurrency(0));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.addCurrency(-1));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.addCurrency(Integer.MAX_VALUE));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.canAfford(0));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.deductCurrency(-1));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.deductCurrency(101));
        assertStateEquals(before, assets.snapshot());
    }

    @Test
    void rejectedUnlocksPreserveAllAssets() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState before = assets.snapshot();

        assertFalse(assets.unlock("BGM_01"));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.unlock(null));
        assertStateEquals(before, assets.snapshot());
        assertFalse(assets.unlock("  "));
        assertStateEquals(before, assets.snapshot());
    }

    @Test
    void legacyItemDataSurvivesBalanceAndOwnershipChanges() {
        PlayerAssets assets = populatedAssets();
        assertTrue(assets.deductCurrency(30));
        assertTrue(assets.unlock("upgrade.hull.1"));
        assertEquals(Collections.singletonMap("BOMB", 2), assets.snapshot().getItemQuantities());
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(assets.snapshot()));
        assertEquals(70, restored.getBalance());
        assertTrue(restored.isUnlocked("upgrade.hull.1"));
        assertEquals(Collections.singletonMap("BOMB", 2), restored.snapshot().getItemQuantities());
    }

    @Test
    void snapshotCollectionsAreUnmodifiable() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState snapshot = assets.snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getUnlockedContentIds().add("STAGE_02"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getItemQuantities().put("BOMB", 99));

        assertStateEquals(populatedState(), snapshot);
        assertStateEquals(populatedState(), assets.snapshot());
    }

    @Test
    void snapshotIsIndependentOfLaterAssetChanges() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState snapshot = assets.snapshot();

        assertTrue(assets.addCurrency(50));
        assertTrue(assets.unlock("STAGE_02"));

        assertEquals(150, assets.getBalance());
        assertTrue(assets.isUnlocked("STAGE_02"));
        assertStateEquals(populatedState(), snapshot);
    }

    @Test
    void restoreReplacesAllExistingAssets() {
        PlayerAssetsState snapshot = populatedState();
        PlayerAssets target = new PlayerAssets();
        assertTrue(target.restore(new PlayerAssetsState(999, Collections.singleton("OLD_STAGE"),
                Collections.singletonMap("OLD_ITEM", 4))));

        assertTrue(target.restore(snapshot));

        assertStateEquals(snapshot, target.snapshot());
        assertFalse(target.isUnlocked("OLD_STAGE"));
        assertFalse(target.snapshot().getItemQuantities().containsKey("OLD_ITEM"));
    }

    @Test
    void restoredAssetsDoNotModifySnapshotOrSource() {
        PlayerAssets source = populatedAssets();
        PlayerAssetsState snapshot = source.snapshot();
        PlayerAssets target = new PlayerAssets();
        assertTrue(target.restore(snapshot));

        assertTrue(target.deductCurrency(10));
        assertTrue(target.unlock("STAGE_02"));

        assertEquals(90, target.getBalance());
        assertTrue(target.isUnlocked("STAGE_02"));
        assertEquals(2, target.snapshot().getItemQuantities().get("BOMB"));
        assertStateEquals(populatedState(), snapshot);
        assertStateEquals(populatedState(), source.snapshot());
    }

    @Test
    void sameSnapshotCanBeRestoredAgainAfterChanges() {
        PlayerAssetsState snapshot = populatedState();
        PlayerAssets target = new PlayerAssets();
        assertTrue(target.restore(snapshot));
        assertTrue(target.deductCurrency(10));
        assertTrue(target.unlock("STAGE_02"));

        assertTrue(target.restore(snapshot));

        assertStateEquals(snapshot, target.snapshot());
    }

    @Test
    void nullRestorePreservesAllAssets() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState before = assets.snapshot();
        assertFalse(assets.restore(null));
        assertStateEquals(before, assets.snapshot());
    }

    @Test
    void emptyRestoreClearsEveryAsset() {
        PlayerAssets assets = populatedAssets();
        PlayerAssetsState empty = new PlayerAssets().snapshot();
        assertTrue(assets.restore(empty));
        assertStateEquals(empty, assets.snapshot());
    }

    @Test
    void restoreAcceptsMaximumBalanceAndQuantity() {
        PlayerAssets assets = new PlayerAssets();
        PlayerAssetsState maximum = new PlayerAssetsState(Integer.MAX_VALUE,
                Collections.singleton("BGM_01"),
                Collections.singletonMap("BOMB", Integer.MAX_VALUE));
        assertTrue(assets.restore(maximum));
        assertStateEquals(maximum, assets.snapshot());
        assertFalse(assets.addCurrency(1));
        assertStateEquals(maximum, assets.snapshot());
    }

    private PlayerAssets populatedAssets() {
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(populatedState()));
        return assets;
    }

    private PlayerAssetsState populatedState() {
        return new PlayerAssetsState(100, Collections.singleton("BGM_01"),
                Collections.singletonMap("BOMB", 2));
    }

    private void assertStateEquals(PlayerAssetsState expected, PlayerAssetsState actual) {
        assertEquals(expected.getBalance(), actual.getBalance());
        assertEquals(expected.getUnlockedContentIds(), actual.getUnlockedContentIds());
        assertEquals(expected.getItemQuantities(), actual.getItemQuantities());
    }
}
