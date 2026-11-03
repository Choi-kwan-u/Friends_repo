package event;

/**
 * Receives one specific type of gameplay event.
 *
 * @param <T> Event type handled by this listener
 */
public interface GameplayEventListener<T extends GameplayEvent> {

    /**
     * Handles a published gameplay event.
     *
     * @param event
     *            Event received by this listener.
     */
    void onEvent(T event);
}
