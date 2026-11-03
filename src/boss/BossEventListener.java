package boss;

/**
 * Receives boss events. Gameplay HUD, Visual Effect System and Level Design
 * System implement this and register through
 * {@link BossEventManager#addListener(BossEventListener)}.
 *
 * Boss Developer 2.
 */
public interface BossEventListener {

    /**
     * Called when a boss event happens.
     *
     * @param event
     *            The event, with boss type, position, health, phase and
     *            state.
     */
    void onBossEvent(BossEvent event);
}
