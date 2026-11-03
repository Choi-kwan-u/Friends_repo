package entity;

import java.awt.Color;

/**
 * Grenade launched by the player ship (Cluster Grenade item).
 * It moves slowly upwards. The game screen detonates it when it hits an
 * enemy or when it reaches its detonation altitude.
 */
public class Grenade extends Entity {

    /** Width and height of the grenade. */
    public static final int SIZE = 10;
    /** Vertical speed (negative = upwards), slower than a normal bullet. */
    private static final int SPEED = -3;

    /** Pixel-art sprite (5x5 pixels of 2x2, same size as the grenade). */
    private static final String[] SPRITE_PATTERN = {
            ".111.",
            "1.111",
            "11111",
            "11111",
            ".111."
    };
    private static final boolean[][] SPRITE = createSprite();

    /** Fixed altitude (Y) at which the grenade detonates. */
    private final int detonationY;

    /**
     * Creates a grenade.
     *
     * @param centerX
     *            X coordinate of the grenade's center.
     * @param positionY
     *            Y coordinate of the grenade's upper side.
     * @param detonationY
     *            Altitude at which the grenade explodes if it hits nothing.
     */
    public Grenade(final int centerX, final int positionY,
                   final int detonationY) {
        super(centerX - SIZE / 2, positionY, SIZE, SIZE, Color.ORANGE);
        this.detonationY = detonationY;
    }

    /**
     * Returns the pixel-art sprite of the grenade.
     *
     * @return Sprite indexed as [x][y].
     */
    public static boolean[][] getSprite() {
        return SPRITE;
    }

    private static boolean[][] createSprite() {
        boolean[][] sprite = new boolean[5][5];
        for (int y = 0; y < SPRITE_PATTERN.length; y++)
            for (int x = 0; x < SPRITE_PATTERN[y].length(); x++)
                sprite[x][y] = SPRITE_PATTERN[y].charAt(x) == '1';
        return sprite;
    }

    /**
     * Moves the grenade.
     */
    public final void update() {
        this.positionY += SPEED;
    }

    /**
     * Checks if the grenade reached its detonation altitude.
     *
     * @return True if it must explode.
     */
    public final boolean hasReachedAltitude() {
        return this.positionY <= this.detonationY;
    }

    /**
     * @return X coordinate of the center of the grenade.
     */
    public final int getCenterX() {
        return this.positionX + this.width / 2;
    }

    /**
     * @return Y coordinate of the center of the grenade.
     */
    public final int getCenterY() {
        return this.positionY + this.height / 2;
    }
}
