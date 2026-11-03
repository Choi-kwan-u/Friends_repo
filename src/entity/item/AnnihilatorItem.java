package entity.item;

import engine.DrawManager.SpriteType;
import entity.Ship;

/**
 * Item dropped by a destroyed enemy. When the ship catches it, the Orbital
 * Annihilator becomes available for one use.
 */
public class AnnihilatorItem extends Item {

    private static final int ITEM_WIDTH = 14;
    private static final int ITEM_HEIGHT = 28;

    private final OrbitalAnnihilator annihilator;

    public AnnihilatorItem(final int positionX, final int positionY,
                           final OrbitalAnnihilator annihilator) {
        super(positionX, positionY, ITEM_WIDTH, ITEM_HEIGHT,
                SpriteType.Explosion);
        this.annihilator = annihilator;
    }

    @Override
    public void applyEffect(final Ship ship) {
        this.annihilator.arm();
    }

    @Override
    public void removeEffect(final Ship ship) {
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public String getName() {
        return "Orbital Annihilator";
    }
}
