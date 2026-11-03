package entity.item;

import entity.Ship;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import audio.AudioManager;

public class BuffManager {

    private final Map<Item, Long> activeBuffs;

    public BuffManager() {
        this.activeBuffs = new HashMap<>();
    }

    public void addBuff(final Item item, final Ship ship) {
        // Refresh Chameleon without allowing the old timer to cancel it.
        if (item instanceof ChameleonHull) {
            Iterator<Item> previous = this.activeBuffs.keySet().iterator();

            while (previous.hasNext()) {
                if (previous.next() instanceof ChameleonHull) {
                    previous.remove();
                }
            }
        }

        item.applyEffect(ship);

        if (item.getDuration() > 0) {
            long expirationTime =
                    System.currentTimeMillis() + item.getDuration();

            this.activeBuffs.put(item, expirationTime);
        }
    }

    public void update(final Ship ship) {
        long currentTime = System.currentTimeMillis();

        Iterator<Map.Entry<Item, Long>> iterator =
                this.activeBuffs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Item, Long> entry = iterator.next();

            if (currentTime >= entry.getValue()) {
                if (entry.getKey() instanceof ShieldItem)
                    AudioManager.playSFX("audio/sfx/status_shield_off.wav");
                else
                    AudioManager.playSFX("audio/sfx/item_buff_expire.wav");
                entry.getKey().removeEffect(ship);
                iterator.remove();
            }
        }
    }

    public void resetAllBuffs(final Ship ship) {
        for (Item item : this.activeBuffs.keySet()) {
            item.removeEffect(ship);
        }

        this.activeBuffs.clear();
    }

    public Map<String, Long> getActiveBuffsRemainingTime() {
        Map<String, Long> remainingTimes = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<Item, Long> entry : this.activeBuffs.entrySet()) {
            long remaining = Math.max(
                    0,
                    (entry.getValue() - currentTime) / 1000
            );

            remainingTimes.put(entry.getKey().getName(), remaining);
        }

        return remainingTimes;
    }
}
