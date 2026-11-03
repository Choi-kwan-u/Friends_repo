package screen;

import audio.AudioManager;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import engine.Core;
import entity.ShipType;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class ShipSelectionUnlockTest {
    @TempDir Path temp;
    private final Canvas inputSource = new Canvas();
    private ShopController shop;
    private ShipSelectionScreen screen;
    private Field shopField;
    private Object previousShop;
    private boolean previousMute;

    @BeforeEach
    void setUp() throws Exception {
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseInput();
        PlayerAssets assets = new PlayerAssets();
        assertTrue(assets.restore(new PlayerAssetsState(200, null, null)));
        shop = ShopTestFixtures.upgradeController(assets,
                new ShopTestFixtures.SaveFile(Core.getFileManager(), temp.resolve("assets.properties")));
        shopField = Core.class.getDeclaredField("shopController");
        shopField.setAccessible(true);
        previousShop = shopField.get(null);
        shopField.set(null, shop);
        screen = new ShipSelectionScreen(448, 520, 60);
        screen.inputDelay = Core.getCooldown(0);
        screen.isRunning = true;
        screen.update();
    }

    @AfterEach
    void cleanUp() throws Exception {
        releaseInput();
        shopField.set(null, previousShop);
        AudioManager.setMuted(previousMute);
    }

    @ParameterizedTest
    @ValueSource(ints = {KeyEvent.VK_ENTER, KeyEvent.VK_SPACE})
    void keyboardCannotSelectSpeedsterUntilPurchased(int confirm) throws Exception {
        tap(KeyEvent.VK_DOWN);
        assertEquals(ShipType.SPEEDSTER, screen.getSelectedShip());
        tap(confirm);
        assertTrue(screen.isRunning);
        assertEquals(ShipSelectionScreen.RETURN_TO_HANGAR_MENU, screen.returnCode);
        assertEquals(200, shop.getBalance());

        assertEquals(PurchaseResult.SUCCESS, shop.purchase("ship.speedster"));
        tap(confirm);
        assertFalse(screen.isRunning);
        assertEquals(ShipSelectionScreen.SHIP_SELECTED, screen.returnCode);
        assertEquals(ShipType.SPEEDSTER, screen.getSelectedShip());
        assertEquals(0, shop.getBalance());
    }

    @Test
    void mouseCannotBypassTheLockAndWorksAfterPurchase() {
        Core.getInputManager().mouseMoved(new MouseEvent(inputSource, MouseEvent.MOUSE_MOVED,
                0, 0, 224, 260, 0, false));
        click();
        assertEquals(ShipType.SPEEDSTER, screen.getSelectedShip());
        assertTrue(screen.isRunning);
        assertEquals(ShipSelectionScreen.RETURN_TO_HANGAR_MENU, screen.returnCode);
        assertEquals(PurchaseResult.SUCCESS, shop.purchase("ship.speedster"));
        click();
        assertFalse(screen.isRunning);
        assertEquals(ShipSelectionScreen.SHIP_SELECTED, screen.returnCode);
        assertEquals(ShipType.SPEEDSTER, screen.getSelectedShip());
    }

    @ParameterizedTest
    @EnumSource(value = ShipType.class, names = {"STANDARD", "TANK"})
    void freeShipsStaySelectableWithoutPurchase(ShipType type) throws Exception {
        for (int index = 0; index < type.ordinal(); index++) tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_ENTER);
        assertFalse(screen.isRunning);
        assertEquals(ShipSelectionScreen.SHIP_SELECTED, screen.returnCode);
        assertEquals(type, screen.getSelectedShip());
        assertEquals(200, shop.getBalance());
        assertTrue(shop.snapshot().getUnlockedContentIds().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("simultaneousInputs")
    void mouseConfirmationCannotChangeToALockedShipInTheSameFrame(ShipType clicked, int code)
            throws Exception {
        finishSelectionCooldown();
        int y = screen.getHeight() / 3 + clicked.ordinal() * 85;
        Core.getInputManager().mouseMoved(new MouseEvent(inputSource, MouseEvent.MOUSE_MOVED,
                0, 0, 224, y, 0, false));
        key(code, true);
        Core.getInputManager().mousePressed(new MouseEvent(inputSource, MouseEvent.MOUSE_PRESSED,
                0, 0, 224, y, 1, false, MouseEvent.BUTTON1));
        screen.update();

        assertFalse(screen.isRunning);
        assertEquals(ShipSelectionScreen.SHIP_SELECTED, screen.returnCode);
        assertEquals(clicked, screen.getSelectedShip());
        assertTrue(shop.isShipAvailable(screen.getSelectedShip()));
        assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
        assertEquals(200, shop.getBalance());
        assertTrue(shop.snapshot().getUnlockedContentIds().isEmpty());
    }

    private static Stream<Arguments> simultaneousInputs() {
        return Stream.of(
                Arguments.of(ShipType.STANDARD, KeyEvent.VK_DOWN),
                Arguments.of(ShipType.STANDARD, KeyEvent.VK_S),
                Arguments.of(ShipType.TANK, KeyEvent.VK_UP),
                Arguments.of(ShipType.TANK, KeyEvent.VK_W));
    }

    @Test
    void escapeStillLeavesTheLockedSelection() throws Exception {
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_ENTER);
        tap(KeyEvent.VK_ESCAPE);
        assertFalse(screen.isRunning);
        assertEquals(ShipSelectionScreen.RETURN_TO_HANGAR_MENU, screen.returnCode);
        assertFalse(shop.isShipAvailable(ShipType.SPEEDSTER));
    }

    private void tap(int code) throws Exception {
        finishSelectionCooldown();
        key(code, true);
        screen.update();
        key(code, false);
    }

    private void finishSelectionCooldown() throws Exception {
        Field field = ShipSelectionScreen.class.getDeclaredField("selectionCooldown");
        field.setAccessible(true);
        Object cooldown = field.get(screen);
        Field time = cooldown.getClass().getDeclaredField("time");
        time.setAccessible(true);
        time.setLong(cooldown, 0);
    }

    private void key(int code, boolean down) {
        KeyEvent event = new KeyEvent(inputSource,
                down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private void click() {
        Core.getInputManager().mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED));
        screen.update();
        Core.getInputManager().mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED));
        screen.update();
    }

    private MouseEvent mouseEvent(int id) {
        return new MouseEvent(inputSource, id, 0, 0, 224, 260, 1, false, MouseEvent.BUTTON1);
    }

    private void releaseInput() {
        for (int code = 0; code < 256; code++) key(code, false);
        Core.getInputManager().mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED));
        Core.getInputManager().mouseExited(mouseEvent(MouseEvent.MOUSE_EXITED));
        Core.getInputManager().clearLastPressedKey();
    }
}
