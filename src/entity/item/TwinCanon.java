package entity.item;

import engine.DrawManager.SpriteType;
import entity.Ship;

/**
 * Twin Cannon item (double barrel).
 * When collected by the ship, the single shot is replaced by a parallel
 * volley of two bullets for 15 seconds. The timer is handled by the
 * BuffManager, and the buff expires immediately if the player loses a life.
 *
 * @author Clarisse Majourau
 *
 */
public class TwinCanon extends Item {

    /** First level in which the Twin Cannon is available. */
    public static final int SPAWN_LEVEL = 3;
    /** Number of enemies destroyed in the level before the item appears. */
    public static final int SPAWN_KILLS = 10;

    /** Duration of the buff, in milliseconds. */
    private static final long DURATION = 15000;
    /** Width of the item (same size as the ship). */
    private static final int WIDTH = 13 * 2;
    /** Height of the item (same size as the ship). */
    private static final int HEIGHT = 8 * 2;

    /**
     * Constructor, the item falls from the given position.
     *
     * @param positionX
     *            Initial position of the item in the X axis.
     * @param positionY
     *            Initial position of the item in the Y axis.
     */
    public TwinCanon(final int positionX, final int positionY) {
        // TODO: replace by a dedicated item sprite when one exists.
        super(positionX, positionY, WIDTH, HEIGHT, SpriteType.Ship);
    }

    /**
     * Checks if the Twin Cannon is available in the given level.
     * It is unlocked from SPAWN_LEVEL and stays available in later levels.
     *
     * @param level
     *            Current level number.
     * @return True if the Twin Cannon is available in this level.
     */
    public static boolean isUnlocked(final int level) {
        return level >= SPAWN_LEVEL;
    }

    @Override
    public void applyEffect(final Ship ship) {
        ship.setTwinCannonActive(true);
    }

    @Override
    public void removeEffect(final Ship ship) {
        ship.setTwinCannonActive(false);
    }

    @Override
    public long getDuration() {
        return DURATION;
    }

    @Override
    public String getName() {
        return "Twin Cannon";
    }
}
