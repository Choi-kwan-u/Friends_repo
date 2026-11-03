package screen;

import audio.AudioManager;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import engine.Core;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeShopScreenTest {
    @TempDir Path temp;
    private final Canvas keySource = new Canvas();
    private PlayerAssets assets;
    private ShopController controller;
    private ShopScreen screen;
    private boolean muted;

    @BeforeEach
    void setUp() {
        muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseKeys();
        assets = ShopTestFixtures.fundedAssets();
        controller = ShopTestFixtures.upgradeController(assets,
                new ShopTestFixtures.SaveFile(Core.getFileManager(), temp.resolve("assets.properties")));
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
    }

    @AfterEach
    void cleanUp() {
        releaseKeys();
        AudioManager.setMuted(muted);
    }

    private void key(int code, boolean pressed) {
        KeyEvent event = new KeyEvent(keySource,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private void releaseKeys() {
        for (int code = 0; code < 256; code++) key(code, false);
    }

    private void tap(int code) {
        key(code, true);
        screen.processInput(true);
        key(code, false);
        screen.processInput(true);
    }

    @Test
    void cardAdvancesBetweenPressesButHeldConfirmNeverBuysMoreTiers() {
        key(KeyEvent.VK_ENTER, true);
        for (int i = 0; i < 120; i++) screen.processInput(true);
        assertEquals(900, assets.getBalance());
        assertEquals(1, PermanentUpgrade.HULL.getLevel(assets.snapshot()));
        key(KeyEvent.VK_ENTER, false);
        screen.processInput(true);
        tap(KeyEvent.VK_ENTER);
        assertEquals(650, assets.getBalance());
        assertEquals(2, PermanentUpgrade.HULL.getLevel(assets.snapshot()));
        tap(KeyEvent.VK_ENTER);
        assertEquals(150, assets.getBalance());
        assertEquals(3, PermanentUpgrade.HULL.getLevel(assets.snapshot()));
        assertEquals("Hull MAX", controller.getProducts().get(0).getName());
        tap(KeyEvent.VK_ENTER);
        assertEquals(150, assets.getBalance());
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_ENTER);
        assertEquals(1, PermanentUpgrade.ENGINE.getLevel(assets.snapshot()));
        assertEquals(50, assets.getBalance());
        tap(KeyEvent.VK_ENTER);
        assertEquals(50, assets.getBalance());
        assertEquals(6, controller.getProducts().size());
    }

    @Test
    void reenteringShopUsesTheNextTierWithoutReplayingHeldInput() {
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_SPACE);
        assertEquals(1, PermanentUpgrade.RELOADER.getLevel(assets.snapshot()));
        key(KeyEvent.VK_SPACE, true);
        screen = new ShopScreen(448, 520, 60, controller);
        screen.initialize();
        screen.processInput(true);
        assertEquals(900, assets.getBalance());
        assertEquals(PermanentUpgrade.RELOADER.getProductId(2), controller.getProducts().get(2).getId());
    }

    @Test
    void lowerRowBuysOnlySpeedsterAndUpperUpgradesStillWork() throws Exception {
        Path save = temp.resolve("assets.properties");
        tap(KeyEvent.VK_DOWN);
        for (int ship = 0; ship < entity.ShipType.values().length; ship++) {
            tap(KeyEvent.VK_ENTER);
            tap(KeyEvent.VK_RIGHT);
        }
        assertEquals(800, assets.getBalance());
        assertEquals(java.util.Collections.singleton("ship.speedster"),
                assets.snapshot().getUnlockedContentIds());
        assertTrue(Files.exists(save));
        assertEquals(ShopController.SaveStatus.SAVED, controller.getSaveStatus());

        tap(KeyEvent.VK_UP);
        tap(KeyEvent.VK_ENTER);
        assertEquals(700, assets.getBalance());
        assertEquals(java.util.Set.of("ship.speedster", PermanentUpgrade.HULL.getProductId(1)),
                assets.snapshot().getUnlockedContentIds());
        byte[] written = Files.readAllBytes(save);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_ENTER);
        assertArrayEquals(written, Files.readAllBytes(save));
        assertEquals(700, assets.getBalance());
    }
}
