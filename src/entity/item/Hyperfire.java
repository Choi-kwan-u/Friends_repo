
package entity.item;

import entity.Ship;
import engine.DrawManager.SpriteType;
import audio.AudioManager;
/**
 * Hyperfire item.
 * Reduces the player's shooting cooldown by 60%
 * for 8 seconds.
 */
public class Hyperfire extends Item {

    private static final long DURATION_MS = 8_000;

    /**
     * Creates a Hyperfire item at the given position.
     */
    public Hyperfire(final int positionX, final int positionY) {
        super(positionX, positionY, 16, 24, SpriteType.Hyperfire);

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
        return DURATION_MS;
    }

    /**
     * Returns the name of the item.
     */
    @Override
    public String getName() {
        return "Hyperfire";
    }
}
