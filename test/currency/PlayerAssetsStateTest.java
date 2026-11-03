package currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAssetsStateTest {

    @Test
    @DisplayName("Valid state creation should retain correct values")
    void testValidStateCreation() {
        Set<String> unlocks = new HashSet<>();
        unlocks.add("SHIP_SKIN_BLUE");
        unlocks.add("BGM_03");

        Map<String, Integer> items = new HashMap<>();
        items.put("BOMB", 2);
        items.put("GRENADE", 4);

        PlayerAssetsState state = new PlayerAssetsState(1250, unlocks, items);

        assertEquals(1250, state.getBalance());
        assertEquals(2, state.getUnlockedContentIds().size());
        assertTrue(state.getUnlockedContentIds().contains("SHIP_SKIN_BLUE"));
        assertEquals(2, state.getItemQuantities().get("BOMB"));
        assertEquals(4, state.getItemQuantities().get("GRENADE"));
    }

    @Test
    @DisplayName("Constructor should handle null collections by initializing empty states")
    void testNullCollectionsHandling() {
        PlayerAssetsState state = new PlayerAssetsState(500, null, null);

        assertEquals(500, state.getBalance());
        assertNotNull(state.getUnlockedContentIds());
        assertTrue(state.getUnlockedContentIds().isEmpty());
        assertNotNull(state.getItemQuantities());
        assertTrue(state.getItemQuantities().isEmpty());
    }

    @Test
    @DisplayName("Modifying input collections after passing to constructor must not affect state (Defensive Copying)")
    void testConstructorDefensiveCopying() {
        Set<String> unlocks = new HashSet<>();
        unlocks.add("STAGE_07");

        Map<String, Integer> items = new HashMap<>();
        items.put("SHIELD", 1);

        PlayerAssetsState state = new PlayerAssetsState(100, unlocks, items);

        // Mutate original objects
        unlocks.add("EXTRA_SKIN");
        items.put("SHIELD", 99);

        assertFalse(state.getUnlockedContentIds().contains("EXTRA_SKIN"));
        assertEquals(1, state.getItemQuantities().get("SHIELD"));
    }

    @Test
    @DisplayName("Returned collections must be unmodifiable")
    void testGettersImmutability() {
        PlayerAssetsState state = new PlayerAssetsState(100, Set.of("STAGE_01"), Map.of("POTION", 1));

        assertThrows(UnsupportedOperationException.class, () -> state.getUnlockedContentIds().add("STAGE_02"));
        assertThrows(UnsupportedOperationException.class, () -> state.getItemQuantities().put("BOMB", 5));
    }

    @Test
    @DisplayName("Negative balance should throw IllegalArgumentException")
    void testNegativeBalanceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerAssetsState(-1, null, null));
    }

    @Test
    @DisplayName("Invalid content ID in unlocks should throw IllegalArgumentException")
    void testInvalidUnlockIdThrowsException() {
        Set<String> invalidSet = new HashSet<>();
        invalidSet.add("");

        assertThrows(IllegalArgumentException.class, () -> new PlayerAssetsState(100, invalidSet, null));
    }

    @Test
    @DisplayName("Invalid item ID or non-positive quantity should throw IllegalArgumentException")
    void testInvalidItemQuantitiesThrowException() {
        Map<String, Integer> invalidQtyMap = Map.of("BOMB", 0);
        assertThrows(IllegalArgumentException.class, () -> new PlayerAssetsState(100, null, invalidQtyMap));

        Map<String, Integer> blankIdMap = Map.of("   ", 3);
        assertThrows(IllegalArgumentException.class, () -> new PlayerAssetsState(100, null, blankIdMap));
    }
}
