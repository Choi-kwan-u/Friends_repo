package entity;

/**
 * Receives player status events. Gameplay HUD and Visual Effect System
 * implement this and register through
 * {@link PlayerStatus#addListener(PlayerStatusListener)}.
 *
 * <p>
 * All kinds arrive through one method on purpose. The project targets Java 7,
 * which has no default methods, so a method per kind of event would break
 * every implementation in the other teams' code each time a new kind is
 * added. With one method, a new {@link PlayerStatusEvent.Type} — the slow
 * debuff of B5, for one — reaches listeners that already compile.
 *
 * <p>
 * Registering looks like this:
 *
 * <pre>
 * gameScreen.getPlayerStatus().addListener(new PlayerStatusListener() {
 *     public void onPlayerStatusEvent(final PlayerStatusEvent event) {
 *         if (event.getType() == PlayerStatusEvent.Type.SHIELD_STARTED)
 *             startShieldGauge(event.getShieldRemainingMillis());
 *     }
 * });
 * </pre>
 *
 * Player Status &amp; Buff/Debuff 1.
 */
public interface PlayerStatusListener {

	/**
	 * Called when the player's status changes.
	 *
	 * @param event
	 *            The event, with its kind, the lives left, the maximum lives
	 *            and the shield time remaining.
	 */
	void onPlayerStatusEvent(PlayerStatusEvent event);
}
