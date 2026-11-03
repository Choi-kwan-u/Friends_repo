package entity.item;

import engine.DrawManager;
import entity.Ship;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import audio.AudioManager;

public class ItemManager {

    private final List<Item> activeItems;
    private final BuffManager buffManager;
    private final int screenHeight;

    public ItemManager(final int screenHeight) {
        this.activeItems = new ArrayList<>();
        this.buffManager = new BuffManager();
        this.screenHeight = screenHeight;
    }

    public void spawnItem(final Item item) {
        this.activeItems.add(item);
    }

    public void update(final Ship ship) {
        Iterator<Item> iterator = this.activeItems.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            item.update();

            if (item.getPositionY() > this.screenHeight) {
                iterator.remove();
            }
        }

        this.buffManager.update(ship);
    }

    public void checkCollisions(final Ship ship) {
        Iterator<Item> iterator = this.activeItems.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (checkCollision(item, ship)) {
                item.setCollected(true);
                AudioManager.playSFX("audio/sfx/item_pickup.wav");
                this.buffManager.addBuff(item, ship);
                iterator.remove();
            }
        }
    }

    private boolean checkCollision(final Item item, final Ship ship) {
        return item.getPositionX() < ship.getPositionX() + ship.getWidth()
                && item.getPositionX() + item.getWidth() > ship.getPositionX()
                && item.getPositionY() < ship.getPositionY() + ship.getHeight()
                && item.getPositionY() + item.getHeight() > ship.getPositionY();
    }

    public void draw(final DrawManager drawManager) {
        for (Item item : this.activeItems) {
            if (item instanceof ClusterGrenadeItem) {
                drawManager.drawGrenadeItem(item.getPositionX(), item.getPositionY());
            } else {
                drawManager.drawEntity(item, item.getPositionX(), item.getPositionY());
            }
        }
    }

    public BuffManager getBuffManager() {
        return this.buffManager;
    }
}
