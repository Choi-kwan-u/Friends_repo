package boss;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends boss events to every registered listener. The boss system never
 * calls other teams' code directly; it only sends events (PRD 7).
 *
 * Boss Developer 2.
 */
public final class BossEventManager {

    /** Registered listeners. */
    private final List<BossEventListener> listeners =
            new ArrayList<BossEventListener>();

    /**
     * Registers a listener.
     *
     * @param listener
     *            Listener to add.
     */
    public void addListener(final BossEventListener listener) {
        if (listener != null && !this.listeners.contains(listener))
            this.listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener
     *            Listener to remove.
     */
    public void removeListener(final BossEventListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Sends an event to all listeners.
     *
     * @param event
     *            Event to send.
     */
    public void publish(final BossEvent event) {
        // Copy so that a listener may remove itself safely.
        for (BossEventListener listener
                : new ArrayList<BossEventListener>(this.listeners))
            listener.onBossEvent(event);
    }
}
