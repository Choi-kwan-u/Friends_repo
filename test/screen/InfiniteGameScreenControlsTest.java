package screen;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import engine.ControlsConfig;
import engine.Core;
import engine.GameState;
import entity.Ship;

/** Run in a fresh JVM. Uses memory-only preferences, never real user bindings. */
public final class InfiniteGameScreenControlsTest {
    private static final Canvas KEY_SOURCE = new Canvas();

    public static void main(final String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory",
                MemoryPreferencesFactory.class.getName());
        check(Preferences.userRoot() instanceof MemoryPreferences,
                "Run in a fresh JVM to isolate preferences");

        InfiniteGameScreen defaults = screen();
        Ship defaultShip = ship(defaults);
        int x = defaultShip.getPositionX();
        press(defaults, KeyEvent.VK_RIGHT);
        check(defaultShip.getPositionX() == x + defaultShip.getSpeed(),
                "Default right key did not move");
        press(defaults, KeyEvent.VK_LEFT);
        check(defaultShip.getPositionX() == x, "Default left key did not move");
        press(defaults, KeyEvent.VK_SPACE);
        check(defaults.getGameState().getBulletsShot() == 1,
                "Default fire key did not shoot");

        ControlsConfig.setKey(ControlsConfig.LEFT, KeyEvent.VK_J);
        ControlsConfig.setKey(ControlsConfig.RIGHT, KeyEvent.VK_L);
        ControlsConfig.setKey(ControlsConfig.FIRE, KeyEvent.VK_F);
        InfiniteGameScreen custom = screen();
        Ship customShip = ship(custom);
        x = customShip.getPositionX();
        for (int key : new int[] { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_SPACE }) {
            press(custom, key);
            check(customShip.getPositionX() == x,
                    "Old movement binding still active: " + key);
            check(custom.getGameState().getBulletsShot() == 0,
                    "Old fire binding still active");
        }
        press(custom, KeyEvent.VK_J);
        check(customShip.getPositionX() == x - customShip.getSpeed(),
                "Custom left key did not move");
        press(custom, KeyEvent.VK_L);
        check(customShip.getPositionX() == x, "Custom right key did not move");
        press(custom, KeyEvent.VK_F);
        check(custom.getGameState().getBulletsShot() == 1,
                "Custom fire key did not shoot");

        customShip.setPositionX(1);
        press(custom, KeyEvent.VK_J);
        check(customShip.getPositionX() == 1, "Crossed left border");
        int rightEdge = custom.getWidth() - 1 - customShip.getWidth();
        customShip.setPositionX(rightEdge);
        press(custom, KeyEvent.VK_L);
        check(customShip.getPositionX() == rightEdge, "Crossed right border");

        InfiniteGameScreen destroyed = screen();
        Ship destroyedShip = ship(destroyed);
        destroyedShip.destroy();
        x = destroyedShip.getPositionX();
        press(destroyed, KeyEvent.VK_L);
        press(destroyed, KeyEvent.VK_F);
        check(destroyedShip.getPositionX() == x
                && destroyed.getGameState().getBulletsShot() == 0,
                "Destroyed ship accepted controls");

        InfiniteGameScreen quitting = screen();
        quitting.inputDelay = Core.getCooldown(0);
        quitting.isRunning = true;
        key(quitting, KeyEvent.VK_ESCAPE, true);
        try {
            quitting.update();
        } finally {
            key(quitting, KeyEvent.VK_ESCAPE, false);
        }
        check(!quitting.isRunning, "Escape no longer exits the run");
        System.out.println("Infinite Mode controls checks passed.");
    }

    private static InfiniteGameScreen screen() {
        InfiniteGameScreen screen = new InfiniteGameScreen(
                new GameState(1, 0, 3, 0, 0), 448, 520, 60);
        screen.initialize();
        return screen;
    }

    private static Ship ship(final InfiniteGameScreen screen) throws Exception {
        Field field = InfiniteGameScreen.class.getDeclaredField("ship");
        field.setAccessible(true);
        return (Ship) field.get(screen);
    }

    private static void press(final InfiniteGameScreen screen, final int code) {
        key(screen, code, true);
        try {
            screen.updatePlayerControls();
        } finally {
            key(screen, code, false);
        }
    }

    private static void key(final InfiniteGameScreen screen, final int code,
            final boolean pressed) {
        KeyEvent event = new KeyEvent(KEY_SOURCE,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) {
            screen.inputManager.keyPressed(event);
        } else {
            screen.inputManager.keyReleased(event);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Public factory so Preferences can instantiate it in the test JVM. */
    public static final class MemoryPreferencesFactory implements PreferencesFactory {
        private final Preferences user = new MemoryPreferences(null, "");
        private final Preferences system = new MemoryPreferences(null, "");

        public Preferences userRoot() {
            return user;
        }

        public Preferences systemRoot() {
            return system;
        }
    }

    private static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<String, String>();
        private final Map<String, MemoryPreferences> children =
                new HashMap<String, MemoryPreferences>();

        MemoryPreferences(final AbstractPreferences parent, final String name) {
            super(parent, name);
        }

        protected void putSpi(final String key, final String value) { values.put(key, value); }
        protected String getSpi(final String key) { return values.get(key); }
        protected void removeSpi(final String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
        protected AbstractPreferences childSpi(final String name) {
            if (!children.containsKey(name)) {
                children.put(name, new MemoryPreferences(this, name));
            }
            return children.get(name);
        }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }

    private InfiniteGameScreenControlsTest() {
    }
}
