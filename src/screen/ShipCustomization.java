package screen;

import java.awt.Color;

/** Stores the player's visual ship customization for the current session. */
public final class ShipCustomization {

    private static final Color[] COLORS = {
        Color.GREEN,
        Color.CYAN,
        Color.MAGENTA,
        Color.YELLOW,
        Color.ORANGE,
        Color.BLUE
    };

    private static final String[] COLOR_NAMES = {
        "Green",
        "Cyan",
        "Magenta",
        "Yellow",
        "Orange",
        "Blue"
    };

    private static int selectedColorIndex;

    private ShipCustomization() {
    }

    public static Color getColor() {
        return COLORS[selectedColorIndex];
    }

    public static String getColorName() {
        return COLOR_NAMES[selectedColorIndex];
    }

    public static void changeColor(final int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 or 1.");
        }

        selectedColorIndex = (selectedColorIndex + direction + COLORS.length)
                % COLORS.length;
    }
}
