package entity.item;

import engine.Core;
import engine.DrawManager.SpriteType;
import entity.Ship;
import audio.AudioManager;

/**
 * Item qui octroie un bouclier d'energie temporaire (15 secondes par defaut).
 * Tant qu'il est actif, le vaisseau est protege contre les tirs ennemis.
 */
public class ShieldItem extends Item {

    /** Duree par defaut (Campaign) : 15 secondes en millisecondes. */
    private static final long DEFAULT_DURATION = 15000;
    private static final int ITEM_WIDTH = 13;
    private static final int ITEM_HEIGHT = 8;

    /** Duree du bouclier, en millisecondes. */
    private final long duration;
    /** True if any ship type can hold this shield (Infinite mode). */
    private final boolean ignoreShipType;

    public ShieldItem(final int positionX, final int positionY) {
        this(positionX, positionY, DEFAULT_DURATION, false);
    }

    /**
     * @param duration
     *            Shield duration, in milliseconds.
     * @param ignoreShipType
     *            True to raise the shield even on a ship that is not
     *            shield-capable.
     */
    public ShieldItem(final int positionX, final int positionY,
                      final long duration, final boolean ignoreShipType) {
        // On utilise temporairement un sprite existant pour l'affichage
        super(positionX, positionY, ITEM_WIDTH, ITEM_HEIGHT, SpriteType.EnemyShipSpecial);
        this.duration = duration;
        this.ignoreShipType = ignoreShipType;
    }

    @Override
    public void applyEffect(final Ship ship) {
        if (ship == null)
            return;
        if (this.ignoreShipType) {
            ship.activateShieldIgnoringShipType();
        } else if (ship.getShipType().isShieldCapable()) {
            // Ship.setShieldActive also refuses, but checking here keeps the
            // log line below from claiming a shield that never came up.
            ship.setShieldActive(true);
        } else {
            return;
        }
        AudioManager.playSFX("audio/sfx/status_shield_on.wav");
        Core.getLogger().info("Energy Shield activated for "
                + this.duration / 1000 + " seconds.");
    }

    @Override
    public void removeEffect(final Ship ship) {
        if (ship != null) {
            ship.setShieldActive(false);
            Core.getLogger().info("Energy Shield expired.");
        }
    }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public String getName() {
        return "Energy Shield";
    }
}
