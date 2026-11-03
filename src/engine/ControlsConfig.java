package engine;

import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

public final class ControlsConfig {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int FIRE = 2;
    public static final int BUFF = 3;

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ControlsConfig.class);

    private ControlsConfig() {
    }

    public static int getKey(final int action) {
        switch (action) {
        case LEFT:
            return PREFS.getInt("left", KeyEvent.VK_LEFT);
        case RIGHT:
            return PREFS.getInt("right", KeyEvent.VK_RIGHT);
        case FIRE:
            return PREFS.getInt("fire", KeyEvent.VK_SPACE);
        default:
            return KeyEvent.VK_UNDEFINED;
        }
    }

    public static void setKey(final int action, final int keyCode) {
        switch (action) {
        case LEFT:
            PREFS.putInt("left", keyCode);
            break;
        case RIGHT:
            PREFS.putInt("right", keyCode);
            break;
        case FIRE:
            PREFS.putInt("fire", keyCode);
            break;
        default:
            break;
        }
    }
}
