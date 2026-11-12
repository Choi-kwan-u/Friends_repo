package screen;

import audio.AudioManager;
import campaign.CampaignStageData;
import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import engine.CampaignUpgrades;
import engine.GameState;
import entity.PlayerStatus;
import entity.PlayerStatusEvent;
import entity.Ship;
import entity.ShipType;
import event.EnemyDefeatedEvent;
import event.LevelCompletedEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class CampaignReviveLivesTest {
    private boolean muted;

    @BeforeEach
    void muteAudio() {
        muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void restoreAudio() {
        AudioManager.stopBGM();
        AudioManager.setMuted(muted);
    }

    static Stream<Arguments> shipHullCases() {
        return Stream.of(ShipType.values()).flatMap(type ->
                IntStream.rangeClosed(0, 3).mapToObj(hull -> Arguments.of(type, hull)));
    }

    @ParameterizedTest(name = "{0}, Hull Lv.{1}")
    @MethodSource("shipHullCases")
    void usesShipAndHullMaximumAndPublishesToTheExistingGameplayBus(ShipType type, int hull)
            throws Exception {
        Set<String> owned = new HashSet<>();
        for (int tier = 1; tier <= hull; tier++) owned.add(PermanentUpgrade.HULL.getProductId(tier));
        owned.add(PermanentUpgrade.ENGINE.getProductId(1));
        owned.add(PermanentUpgrade.RELOADER.getProductId(1));
        CampaignUpgrades upgrades = CampaignUpgrades.from(new PlayerAssetsState(0, owned, null));
        int maximum = type.getMaxLives() + hull;
        GameScreen screen = new GameScreen(new GameState(1, 321, maximum, 8, 12),
                CampaignStageData.getStageSettings(1), false, 448, 520, 60, type, null, upgrades);
        screen.initialize();
        PlayerStatus status = screen.getPlayerStatus();
        Ship ship = ship(screen);
        int baseSpeed = ship.getBaseSpeed();
        int firingInterval = ship.getShootingInterval();
        List<PlayerStatusEvent> events = new ArrayList<>();
        AtomicInteger defeats = new AtomicInteger();
        AtomicInteger clears = new AtomicInteger();
        screen.getGameplayEvents().subscribe(PlayerStatusEvent.class, events::add);
        screen.getGameplayEvents().subscribe(EnemyDefeatedEvent.class, event -> defeats.incrementAndGet());
        screen.getGameplayEvents().subscribe(LevelCompletedEvent.class, event -> clears.incrementAndGet());
        assertTrue(status.takeDamage(maximum));
        assertEquals(0, status.getLives());
        events.clear();
        assertTrue(status.reviveAtHalfLives());
        int expected = (maximum + 1) / 2;
        assertSame(status, screen.getPlayerStatus());
        assertSame(ship, ship(screen));
        assertEquals(maximum, status.getMaxLives());
        assertEquals(expected, status.getLives());
        assertEquals(expected, screen.getGameState().getLivesRemaining());
        assertEquals(type.isShieldCapable(), status.isShieldCapable());
        assertEquals(321, screen.getGameState().getScore());
        assertEquals(12, screen.getGameState().getShipsDestroyed());
        assertEquals(8, screen.getGameState().getBulletsShot());
        assertEquals(baseSpeed, ship.getBaseSpeed());
        assertEquals(firingInterval, ship.getShootingInterval());
        assertEquals(0, defeats.get());
        assertEquals(0, clears.get());
        assertEquals(1, events.size());
        assertEquals(PlayerStatusEvent.Type.REVIVED, events.get(0).getType());
        assertEquals(maximum, events.get(0).getMaxLives());
        assertEquals(expected, events.get(0).getLives());
        assertFalse(status.reviveAtHalfLives());
        assertEquals(1, events.size());
    }

    private Ship ship(GameScreen screen) throws Exception {
        Field field = GameScreen.class.getDeclaredField("ship");
        field.setAccessible(true);
        return (Ship) field.get(screen);
    }
}
