package screen;

import audio.AudioManager;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import engine.Core;
import engine.FileManager;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class ShopScreenTest {
    @TempDir
    Path temp;
    private final Canvas keySource = new Canvas();
    private boolean originalMuted;
    private FileManager files;
    private ShopTestFixtures.SaveFile saveFile;
    private PlayerAssets assets;
    private ShopController controller;
    private ShopScreen screen;

    @BeforeEach
    void setUp() {
        originalMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseAll();
        files = Core.getFileManager();
        saveFile = new ShopTestFixtures.SaveFile(files, temp.resolve("assets.properties"));
        assets = ShopTestFixtures.fundedAssets();
        controller = ShopTestFixtures.controller(assets, saveFile);
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
    }

    @AfterEach
    void cleanUp() {
        releaseAll();
        AudioManager.setMuted(originalMuted);
    }

    private void key(int code, boolean down) {
        KeyEvent event = new KeyEvent(keySource,
                down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private void releaseAll() {
        for (int code = 0; code < 256; code++) key(code, false);
    }

    private void tap(int code) {
        key(code, true);
        screen.processInput(true);
        key(code, false);
        screen.processInput(true);
    }

    private Object state(String fieldName) throws Exception {
        Field field = ShopScreen.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(screen);
    }

    @Test
    void heldConfirmOnEntryAndDuringDelayNeverBecomesAPurchase() {
        key(KeyEvent.VK_SPACE, true);
        screen.initialize();
        screen.processInput(false);
        for (int i = 0; i < 180; i++) screen.processInput(true);
        assertEquals(1000, assets.getBalance());
        key(KeyEvent.VK_SPACE, false);
        screen.processInput(true);
        key(KeyEvent.VK_SPACE, true);
        screen.processInput(false);
        screen.processInput(true);
        assertEquals(1000, assets.getBalance());
        key(KeyEvent.VK_SPACE, false);
        screen.processInput(true);
        tap(KeyEvent.VK_SPACE);
        assertEquals(900, assets.getBalance());
    }

    @Test
    void heldConfirmAndRepeatedKeyPressedEventsBuyExactlyOnce() {
        key(KeyEvent.VK_SPACE, true);
        for (int i = 0; i < 180; i++) {
            key(KeyEvent.VK_SPACE, true);
            screen.processInput(true);
        }
        assertEquals(900, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        key(KeyEvent.VK_ENTER, true);
        screen.processInput(true);
        key(KeyEvent.VK_SPACE, false);
        screen.processInput(true);
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        key(KeyEvent.VK_ENTER, false);
        screen.processInput(true);
        tap(KeyEvent.VK_ENTER);
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        assertEquals(900, assets.getBalance());
    }

    @Test
    void permanentOwnershipDisablesAnotherPurchaseAndSnapshotRefreshes() throws Exception {
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_SPACE);
        assertTrue(assets.isUnlocked(ShopTestFixtures.UNLOCK));
        assertEquals(500, assets.getBalance());
        byte[] saved = Files.readAllBytes(saveFile.getPath());
        tap(KeyEvent.VK_SPACE);
        assertEquals(PurchaseResult.ALREADY_UNLOCKED, state("lastResult"));
        assertEquals(500, ((PlayerAssetsState) state("assets")).getBalance());
        assertArrayEquals(saved, Files.readAllBytes(saveFile.getPath()));
    }

    @Test
    void rejectedPurchaseRefreshesDisplayedStateAndStaysInShop() throws Exception {
        assets.restore(new PlayerAssetsState(0, null, null));
        screen.isRunning = true;
        tap(KeyEvent.VK_SPACE);
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, state("lastResult"));
        assertEquals(0, ((PlayerAssetsState) state("assets")).getBalance());
        assertTrue(screen.isRunning);
        assertFalse(Files.exists(saveFile.getPath()));
    }

    @Test
    void retryButtonAndShortcutSaveOnlyAndSurviveReentry() throws Exception {
        Path save = saveFile.getPath();
        Path blocker = temp.resolve("blocker");
        Files.write(blocker, new byte[] { 1 });
        saveFile.setPath(blocker.resolve("save"));
        screen.isRunning = true;
        tap(KeyEvent.VK_SPACE);
        assertEquals(PurchaseResult.SUCCESS, state("lastResult"));
        assertTrue(screen.isRunning);
        assertEquals(ShopController.SaveStatus.FAILED, controller.getSaveStatus());
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        assertEquals(900, ((PlayerAssetsState) state("assets")).getBalance());
        tap(KeyEvent.VK_R);
        assertEquals(900, assets.getBalance());
        saveFile.setPath(save);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_DOWN); // Footer after the two shelves.
        tap(KeyEvent.VK_RIGHT); // Retry save, next to Back.
        tap(KeyEvent.VK_SPACE);
        assertEquals(ShopController.SaveStatus.SAVED, controller.getSaveStatus());
        assertEquals(900, saveFile.load().getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UPGRADE));
        assertEquals(2, state("selectedSection"));
        assertEquals(0, state("selectedAction")); // Back after retry; never a product.
    }

    @Test
    void emptyCatalogRemainsNavigableWithoutPurchaseOrSave() {
        ShopController empty = ShopTestFixtures.controller(assets, saveFile,
                java.util.Collections.<shop.ShopProduct>emptyList());
        screen = new ShopScreen(448, 520, 60, empty);
        screen.initialize();
        screen.isRunning = true;
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_SPACE);
        assertFalse(screen.isRunning);
        assertEquals(ShopScreen.RETURN_TO_MAIN_MENU, screen.returnCode);
        assertEquals(1000, assets.getBalance());
        assertFalse(Files.exists(saveFile.getPath()));
    }

    @Test
    void backOptionAndEscapeReturnMenuWithoutResettingAssets() {
        tap(KeyEvent.VK_SPACE);
        screen = new ShopScreen(448, 520, 60, controller) {
            @Override protected void draw() { }
        };
        screen.initialize();
        screen.inputDelay = Core.getCooldown(0);
        key(KeyEvent.VK_ESCAPE, true);
        assertEquals(1, screen.run());
        assertEquals(900, assets.getBalance());
        releaseAll();
        screen.initialize();
        screen.isRunning = true;
        tap(KeyEvent.VK_UP); // Wrap from first product to Back.
        tap(KeyEvent.VK_SPACE);
        assertFalse(screen.isRunning);
        assertEquals(1, screen.returnCode);
    }

    @Test
    void simultaneousNavigationAndConfirmationDoesNotAccidentallyBuy() {
        key(KeyEvent.VK_DOWN, true);
        key(KeyEvent.VK_SPACE, true);
        screen.processInput(true);
        screen.processInput(true);
        assertEquals(1000, assets.getBalance());
        releaseAll();
        screen.processInput(true);
        tap(KeyEvent.VK_SPACE);
        assertEquals(500, assets.getBalance());
        assertTrue(assets.isUnlocked(ShopTestFixtures.UNLOCK));
    }

    @Test
    void idleFramesDoNotKeepReadingAssetSnapshots() {
        final int[] calls = { 0 };
        PlayerAssets counted = new PlayerAssets() {
            @Override public PlayerAssetsState snapshot() { calls[0]++; return super.snapshot(); }
        };
        ShopScreen idle = new ShopScreen(448, 520, 60, ShopTestFixtures.controller(counted, saveFile));
        idle.initialize();
        int initialCalls = calls[0];
        for (int i = 0; i < 600; i++) idle.processInput(true);
        assertEquals(initialCalls, calls[0]);
    }

    @Test
    void horizontalBrowsingBuysTheSelectedCardAndEachShelfRemembersItsPosition() throws Exception {
        controller = ShopTestFixtures.controller(assets, saveFile, ShopTestFixtures.previewProducts());
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_D);
        tap(KeyEvent.VK_SPACE);
        assertEquals(1, PermanentUpgrade.RELOADER.getLevel(assets.snapshot()));
        assertEquals(900, assets.getBalance());
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_SPACE);
        assertTrue(assets.isUnlocked("TEST_SHIP_B"));
        assertEquals(250, assets.getBalance());
        tap(KeyEvent.VK_UP);
        assertEquals(0, state("selectedSection"));
        assertEquals(2, state("upgradeIndex"));
        tap(KeyEvent.VK_SPACE);
        assertEquals(2, PermanentUpgrade.RELOADER.getLevel(assets.snapshot()));
        assertEquals(0, assets.getBalance());
        tap(KeyEvent.VK_SPACE);
        assertEquals(PurchaseResult.INSUFFICIENT_FUNDS, state("lastResult"));
        tap(KeyEvent.VK_DOWN);
        assertEquals(1, state("unlockIndex"));
    }

    @Test
    void previewKeepsUpgradesAboveExistingUnlockCardsAfterReload() throws Exception {
        controller = ShopTestFixtures.controller(assets, saveFile, ShopTestFixtures.previewProducts());
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        assertEquals(0, state("selectedSection"));
        assertEquals(3, ((java.util.List<?>) state("upgrades")).size());
        java.util.List<?> lower = (java.util.List<?>) state("unlocks");
        assertEquals(3, lower.size());
        assertEquals(ShopTestFixtures.UNLOCK, ((shop.ShopProduct) lower.get(0)).getId());
        assertEquals("TEST_SHIP_B", ((shop.ShopProduct) lower.get(1)).getId());
        assertEquals("TEST_SHIP_C", ((shop.ShopProduct) lower.get(2)).getId());

        tap(KeyEvent.VK_SPACE);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_SPACE);
        PlayerAssets restored = new PlayerAssets();
        assertTrue(restored.restore(saveFile.load()));
        controller = ShopTestFixtures.controller(restored, saveFile, ShopTestFixtures.previewProducts());
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();

        assertEquals(250, restored.getBalance());
        assertEquals(1, PermanentUpgrade.HULL.getLevel(restored.snapshot()));
        assertTrue(restored.isUnlocked("TEST_SHIP_B"));
        assertFalse(restored.isUnlocked("TEST_SHIP_C"));
        assertEquals(3, ((java.util.List<?>) state("upgrades")).size());
        assertEquals(3, ((java.util.List<?>) state("unlocks")).size());
        assertEquals("upgrade.hull.2", ((shop.ShopProduct)
                ((java.util.List<?>) state("upgrades")).get(0)).getId());
    }

    @Test
    void horizontalWrapDoesNotMoveToAnotherShelfAndHeldKeysDoNotRepeat() throws Exception {
        controller = ShopTestFixtures.controller(assets, saveFile, ShopTestFixtures.previewProducts());
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        tap(KeyEvent.VK_LEFT);
        assertEquals(2, state("upgradeIndex"));
        assertEquals(0, state("selectedSection"));
        key(KeyEvent.VK_RIGHT, true);
        for (int i = 0; i < 180; i++) screen.processInput(true);
        assertEquals(0, state("upgradeIndex"));
        assertEquals(1000, assets.getBalance());
    }

    @Test
    void missingShelfIsSkippedAndHorizontalNavigationNeverPurchasesAnEmptySlot() throws Exception {
        controller = ShopTestFixtures.controller(assets, saveFile,
                java.util.Collections.singletonList(ShopTestFixtures.products().get(1)));
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        assertEquals(1, state("selectedSection"));
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_SPACE);
        assertTrue(assets.isUnlocked(ShopTestFixtures.UNLOCK));
        tap(KeyEvent.VK_DOWN);
        assertEquals(2, state("selectedSection"));
        tap(KeyEvent.VK_DOWN);
        assertEquals(1, state("selectedSection"));
        tap(KeyEvent.VK_UP);
        assertEquals(2, state("selectedSection"));
    }

    @Test
    void contractRejectsInvalidArgumentsAndAllResultValuesHaveFeedback() {
        assertThrows(IllegalArgumentException.class, () -> new ShopScreen(0, 520, 60, controller));
        assertThrows(IllegalArgumentException.class, () -> new ShopScreen(448, -1, 60, controller));
        assertThrows(IllegalArgumentException.class, () -> new ShopScreen(448, 520, 0, controller));
        assertThrows(IllegalArgumentException.class, () -> new ShopScreen(448, 520, 60, null));
        for (PurchaseResult result : PurchaseResult.values()) {
            assertFalse(ShopScreen.resultMessage(result).isEmpty());
        }
    }

    @Test
    void lowerSectionAcceptsGenericPermanentContentWithoutShipSpecificRules() throws Exception {
        shop.ShopProduct bgm = new shop.ShopProduct("TEST_BGM", "BGM sample",
                currency.ShopCatalog.Category.UNLOCKABLE, 250, "Sample permanent unlock.");
        controller = ShopTestFixtures.controller(assets, saveFile, java.util.Arrays.asList(
                ShopTestFixtures.products().get(0), bgm));
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_SPACE);
        assertTrue(assets.isUnlocked("TEST_BGM"));
        assertEquals(750, assets.getBalance());
        assertEquals(1, state("selectedSection"));
    }
}
