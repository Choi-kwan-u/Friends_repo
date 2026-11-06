package entity.item;

import engine.DrawManager.SpriteType;
import entity.Ship;
import vfx.Icon;

/**
 * Extra life of the Infinite mode, drawn with the life HUD heart. Lives
 * belong to the screen, so the screen collects this item itself and gives
 * the life: the item has no effect on the ship.
 */
public class ExtraLifeItem extends Item {

    /** Scale the heart is drawn with, a bit bigger than the life HUD (2). */
    public static final int SCALE = 3;

    public ExtraLifeItem(final int positionX, final int positionY) {
        // The sprite type is not used: the screen draws Icon.LIFE.
        super(positionX, positionY, Icon.LIFE.getWidth() * SCALE,
                Icon.LIFE.getHeight() * SCALE, SpriteType.Explosion);
    }

    @Override
    public void applyEffect(final Ship ship) {
        // The screen gives the life.
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
        return "Extra Life";
    }
}
