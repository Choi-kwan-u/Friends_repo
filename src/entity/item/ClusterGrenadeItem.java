package entity.item;

import engine.DrawManager.SpriteType;
import entity.Ship;

/**
 * Cluster Grenade item.
 * Falls from a destroyed enemy ship (15% chance). When collected, it gives
 * the ship a grenade (1 at most) that can be launched with the G key.
 * A grenade explodes on impact (or at a fixed altitude) and splits into
 * 12 fragments that damage the enemies around the explosion.
 */
public class ClusterGrenadeItem extends Item {

    /** Number of grenades given by one item. */
    private static final int GRENADES_PER_PICKUP = 1;
    /** Size of the item on screen: its 11x14 sprite, drawn with 2x2 pixels. */
    private static final int ITEM_WIDTH = 11 * 2;
    private static final int ITEM_HEIGHT = 14 * 2;

    /** Pixel-art sprite of the grenade (1 = pixel), like the other items. */
    private static final String[] SPRITE_PATTERN = {
            "01011111110",
            "10111110010",
            "01001110010",
            "00000100010",
            "00011111000",
            "00101010100",
            "01111111110",
            "01101010110",
            "01111111110",
            "01101010110",
            "01111111110",
            "00101010100",
            "00111111100",
            "00001110000"
    };

    private static final boolean[][] SPRITE = createSprite();

    /**
     * Creates a Cluster Grenade item at the given position.
     */
    public ClusterGrenadeItem(final int positionX, final int positionY) {
        // The sprite type is not used: ItemManager draws this item with
        // DrawManager.drawGrenadeItem (its pixel-art sprite).
        super(positionX, positionY, ITEM_WIDTH, ITEM_HEIGHT,
                SpriteType.Explosion);
    }

    /**
     * Gives grenades to the player ship.
     */
    @Override
    public void applyEffect(final Ship ship) {
        if (ship != null) {
            ship.addGrenades(GRENADES_PER_PICKUP);
        }
    }

    /**
     * Nothing to remove: the item has no duration, grenades stay in the
     * ship's stock until they are launched.
     */
    @Override
    public void removeEffect(final Ship ship) {
        // Instant item, no effect to remove.
    }

    /**
     * Instant item: a duration of 0 means the BuffManager does not track it.
     */
    @Override
    public long getDuration() {
        return 0;
    }

    /**
     * Returns the pixel-art sprite of the item.
     *
     * @return Sprite indexed as [x][y].
     */
    public static boolean[][] getSprite() {
        return SPRITE;
    }

    private static boolean[][] createSprite() {
        boolean[][] sprite = new boolean[11][14];

        for (int y = 0; y < SPRITE_PATTERN.length; y++) {
            for (int x = 0; x < SPRITE_PATTERN[y].length(); x++) {
                sprite[x][y] = SPRITE_PATTERN[y].charAt(x) == '1';
            }
        }

        return sprite;
    }

    /**
     * Returns the name of the item.
     */
    @Override
    public String getName() {
        return "Cluster Grenade";
    }
}
