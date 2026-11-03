package entity.item;

import entity.Entity;
import entity.Ship;
import engine.DrawManager;

public abstract class Item extends Entity {

    /** Vitesse de descente de l'item a l'ecran. */
    protected int speed;
    /** Indique si l'item a ete ramasse. */
    protected boolean isCollected;

    public Item(final int positionX, final int positionY, final int width, final int height,
                final DrawManager.SpriteType spriteType) {
        super(positionX, positionY, width, height, java.awt.Color.WHITE);
        this.spriteType = spriteType;
        this.speed = 2;
        this.isCollected = false;
    }

    public void update() {
        this.positionY += this.speed;
    }

    public abstract void applyEffect(final Ship ship);

    public abstract void removeEffect(final Ship ship);

    public abstract long getDuration();

    public abstract String getName();

    public boolean isCollected() {
        return this.isCollected;
    }

    public void setCollected(final boolean collected) {
        this.isCollected = collected;
    }
}
