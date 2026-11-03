package currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnlockManagerTest {
    private UnlockManager unlockManager;

    @BeforeEach
    void setUp() {
        unlockManager = new UnlockManager();
    }

    @Test
    void testInitialState() {
        assertFalse(unlockManager.isUnlocked("STAGE_01"));
    }

    @Test
    void testUnlockValidContent() {
        assertTrue(unlockManager.unlock("STAGE_01"));
        assertTrue(unlockManager.isUnlocked("STAGE_01"));
    }

    @Test
    void testUnlockAlreadyUnlockedContent() {
        assertTrue(unlockManager.unlock("STAGE_01"));
        // Second attempt should return false
        assertFalse(unlockManager.unlock("STAGE_01"));
        assertTrue(unlockManager.isUnlocked("STAGE_01"));
    }

    @Test
    void testInvalidContentIds() {
        assertFalse(unlockManager.unlock(null));
        assertFalse(unlockManager.unlock("   "));
        assertFalse(unlockManager.isUnlocked(null));
        assertFalse(unlockManager.isUnlocked("   "));
    }

    @Test
    void testIndependentContentIds() {
        assertTrue(unlockManager.unlock("STAGE_01"));
        assertTrue(unlockManager.unlock("BGM_03"));
        assertTrue(unlockManager.isUnlocked("STAGE_01"));
        assertTrue(unlockManager.isUnlocked("BGM_03"));
    }
}
