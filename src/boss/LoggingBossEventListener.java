package boss;

import engine.Core;

/**
 * Writes every boss event to the game log. Used to check the events until
 * the HUD and Visual Effect teams connect their own listeners.
 *
 * Boss Developer 2.
 */
public final class LoggingBossEventListener implements BossEventListener {

    @Override
    public void onBossEvent(final BossEvent event) {
        Core.getLogger().info(event.toString());
    }
}
