package currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopCatalogTest {
    private ShopCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ShopCatalog();
    }

    @Test
    void invalidIdsAndPricesAreRejected() {
        assertFalse(catalog.registerUpgrade(null, 100));
        assertFalse(catalog.registerUnlockable(" ", 100));
        assertFalse(catalog.registerUpgrade("free_upgrade", 0));
        assertFalse(catalog.registerUnlockable("free_stage", 0));
        assertFalse(catalog.registerUpgrade("invalid", -50));
        assertEquals(-1, catalog.getPrice("free_upgrade"));
    }

    @Test
    void duplicateRegistrationPreservesOriginal() {
        assertTrue(catalog.registerUpgrade("upgrade", 100));
        assertFalse(catalog.registerUnlockable("upgrade", 200));
        assertFalse(catalog.registerUpgrade("upgrade", 50));
        assertEquals(100, catalog.getPrice("upgrade"));
        assertTrue(catalog.isUpgrade("upgrade"));
        assertTrue(catalog.isUnlockable("upgrade"));
    }

    @Test
    void bothCategoriesGrantPermanentOwnership() {
        assertTrue(catalog.registerUpgrade("upgrade", 100));
        assertTrue(catalog.registerUnlockable("ship", 500));
        assertTrue(catalog.isUnlockable("upgrade"));
        assertTrue(catalog.isUnlockable("ship"));
        assertFalse(catalog.isUpgrade("ship"));
        assertFalse(catalog.isUnlockable("unknown"));
        assertFalse(catalog.isUpgrade(null));
    }
}
