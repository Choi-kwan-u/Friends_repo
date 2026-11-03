package entity;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Fragment created when a grenade explodes. Each explosion creates 12
 * fragments flying away from the center, up to 100 pixels around the
 * explosion. A fragment destroys the first enemy it touches, then
 * disappears (area of effect damage).
 */
public class GrenadeFragment extends Entity {

    /** Number of fragments created by one explosion. */
    public static final int FRAGMENT_COUNT = 12;
    /** Width and height of a fragment. */
    public static final int SIZE = 6;
    /** Distance travelled by a fragment each update. */
    private static final double SPEED = 3.0;
    /** Maximum distance (in pixels) travelled around the explosion. */
    private static final double RANGE = 100.0;

    /** Pixel-art sprite (3x3 pixels of 2x2, same size as the fragment). */
    private static final String[] SPRITE_PATTERN = {
            ".1.",
            "111",
            ".1."
    };
    private static final boolean[][] SPRITE = createSprite();

    /** Exact position, positionX/positionY only keep the rounded values. */
    private double exactX;
    private double exactY;
    /** Movement of the fragment for each update. */
    private final double moveX;
    private final double moveY;
    /** Distance already travelled. */
    private double travelled;

    /**
     * Creates a fragment.
     *
     * @param centerX
     *            X coordinate of the explosion.
     * @param centerY
     *            Y coordinate of the explosion.
     * @param angle
     *            Direction of the fragment, in radians.
     */
    public GrenadeFragment(final int centerX, final int centerY,
                           final double angle) {
        super(centerX - SIZE / 2, centerY - SIZE / 2, SIZE, SIZE,
                Color.YELLOW);
        this.exactX = centerX - SIZE / 2.0;
        this.exactY = centerY - SIZE / 2.0;
        this.moveX = Math.cos(angle) * SPEED;
        this.moveY = Math.sin(angle) * SPEED;
        this.travelled = 0;
    }

    /**
     * Creates the 12 fragments of an explosion, evenly spread around it.
     *
     * @param centerX
     *            X coordinate of the explosion.
     * @param centerY
     *            Y coordinate of the explosion.
     * @return The new fragments.
     */
    public static Set<GrenadeFragment> explode(final int centerX,
                                               final int centerY) {
        Set<GrenadeFragment> fragments = new HashSet<GrenadeFragment>();
        for (int i = 0; i < FRAGMENT_COUNT; i++) {
            double angle = 2 * Math.PI * i / FRAGMENT_COUNT;
            fragments.add(new GrenadeFragment(centerX, centerY, angle));
        }
        return fragments;
    }

    /**
     * Returns the pixel-art sprite of a fragment.
     *
     * @return Sprite indexed as [x][y].
     */
    public static boolean[][] getSprite() {
        return SPRITE;
    }

    private static boolean[][] createSprite() {
        boolean[][] sprite = new boolean[3][3];
        for (int y = 0; y < SPRITE_PATTERN.length; y++)
            for (int x = 0; x < SPRITE_PATTERN[y].length(); x++)
                sprite[x][y] = SPRITE_PATTERN[y].charAt(x) == '1';
        return sprite;
    }

    /**
     * Moves the fragment.
     */
    public final void update() {
        this.exactX += this.moveX;
        this.exactY += this.moveY;
        this.travelled += SPEED;
        this.positionX = (int) Math.round(this.exactX);
        this.positionY = (int) Math.round(this.exactY);
    }

    /**
     * Progress of the fragment, used to fade out its halo.
     *
     * @return 0 when the fragment is created, 1 when it disappears.
     */
    public final double getProgress() {
        return Math.min(1.0, this.travelled / RANGE);
    }

    /**
     * Checks if the fragment went far enough to disappear.
     *
     * @return True if the fragment is finished.
     */
    public final boolean isExpired() {
        return this.travelled >= RANGE;
    }
}
