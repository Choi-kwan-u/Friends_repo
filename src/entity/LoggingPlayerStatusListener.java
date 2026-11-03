package entity;

import engine.Core;

/**
 * Writes every player status event to the game log. Used to check the events
 * until the HUD and Visual Effect teams connect their own listeners.
 *
 * Player Status &amp; Buff/Debuff 1.
 */
public final class LoggingPlayerStatusListener
		implements PlayerStatusListener {

	@Override
	public void onPlayerStatusEvent(final PlayerStatusEvent event) {
		Core.getLogger().info(event.toString());
	}
}
