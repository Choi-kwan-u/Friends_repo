
package entity.item;

import entity.Ship;
import engine.DrawManager.SpriteType;
import audio.AudioManager;
/**
 * Hyperfire item.
 * Reduces the player's shooting cooldown by 60%
 * for 8 seconds (by default).
 */
public class Hyperfire extends Item {

    /** Default duration (Campaign), in milliseconds. */
    private static final long DEFAULT_DURATION_MS = 8_000;

    /** Duration of the buff, in milliseconds. */
    private final long duration;

    /**
     * Creates a Hyperfire item at the given position.
     */
    public Hyperfire(final int positionX, final int positionY) {
        this(positionX, positionY, DEFAULT_DURATION_MS);
    }

    /**
     * Creates a Hyperfire item with its own duration.
     *
     * @param duration
     *            Duration of the buff, in milliseconds.
     */
    public Hyperfire(final int positionX, final int positionY,
                     final long duration) {
        super(positionX, positionY, 16, 24, SpriteType.Hyperfire);
        this.duration = duration;
    }

    /**
     * Activates the Hyperfire effect on the player ship.
     */
    @Override
    public void applyEffect(final Ship ship) {
        ship.setHyperfireEnabled(true);
        AudioManager.playSFX("audio/sfx/item_hyperfire_activate.wav");
    }

    /**
     * Removes the Hyperfire effect from the player ship.
     */
    @Override
    public void removeEffect(final Ship ship) {
        ship.setHyperfireEnabled(false);
    }

    /**
     * Returns the duration of the buff in milliseconds.
     */
    @Override
    public long getDuration() {
        return this.duration;
    }

    /**
     * Returns the name of the item.
     */
    @Override
    public String getName() {
        return "Hyperfire";
    }
}
