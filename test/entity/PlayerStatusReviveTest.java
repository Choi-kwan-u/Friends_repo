package entity;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatusReviveTest {
    @ParameterizedTest
    @CsvSource({"3,2", "4,2", "5,3", "6,3", "7,4", "8,4"})
    void restoresHalfTheConfiguredMaximumRoundedUp(int maximum, int expected) {
        PlayerStatus status = new PlayerStatus(0, maximum);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        assertTrue(status.reviveAtHalfLives());
        assertEquals(expected, status.getLives());
        assertEquals(maximum, status.getMaxLives());
        assertTrue(status.isAlive());
        assertEquals(1, events.size());
        PlayerStatusEvent event = events.get(0);
        assertEquals(PlayerStatusEvent.Type.REVIVED, event.getType());
        assertEquals(expected, event.getLives());
        assertEquals(maximum, event.getMaxLives());
        assertEquals(0, event.getDamageAmount());
        assertTrue(event.isAlive());
        assertFalse(event.isSlowActive());
        assertEquals(0, event.getSlowRemainingMillis());
        assertEquals(0, event.getShieldRemainingMillis());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,1", "2147483647,1073741824"})
    void positiveMaximumBoundariesCannotOverflow(int maximum, int expected) {
        PlayerStatus status = new PlayerStatus(0, maximum);
        assertTrue(status.reviveAtHalfLives());
        assertEquals(expected, status.getLives());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7})
    void aLivingPlayerCannotBeHealedOrPublishAnotherEvent(int initialLives) {
        PlayerStatus status = new PlayerStatus(initialLives, 8);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        assertFalse(status.reviveAtHalfLives());
        assertEquals(initialLives, status.getLives());
        assertTrue(events.isEmpty());
    }

    @Test
    void revivalPublishesOnceAndTheSnapshotStaysImmutable() {
        PlayerStatus status = new PlayerStatus(0, 7);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        assertTrue(status.reviveAtHalfLives());
        PlayerStatusEvent revived = events.get(0);
        assertFalse(status.reviveAtHalfLives());
        assertEquals(1, events.size());
        assertTrue(status.takeDamage(1));
        assertEquals(3, status.getLives());
        assertEquals(4, revived.getLives());
        assertEquals(7, revived.getMaxLives());
        assertEquals("PlayerRevived [lives 4/7]", revived.toString());
        assertEquals(PlayerStatusEvent.Type.DAMAGE_TAKEN, events.get(1).getType());
    }

    @Test
    void ordinaryExtraLivesStillCannotReviveADeadPlayer() {
        PlayerStatus status = new PlayerStatus(0, 5);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        assertFalse(status.gainLife());
        assertEquals(0, status.getLives());
        assertTrue(events.isEmpty());
        assertTrue(status.reviveAtHalfLives());
        assertTrue(status.gainLife());
        assertEquals(4, status.getLives());
        assertEquals(List.of(PlayerStatusEvent.Type.REVIVED, PlayerStatusEvent.Type.LIFE_GAINED),
                events.stream().map(PlayerStatusEvent::getType).toList());
    }

    @Test
    void anotherDeathCanBeRevivedOnTheSameStatusWithTheSameListeners() {
        PlayerStatus status = new PlayerStatus(0, 6);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        for (int death = 0; death < 2; death++) {
            assertTrue(status.reviveAtHalfLives());
            assertEquals(3, status.getLives());
            assertFalse(status.reviveAtHalfLives());
            assertTrue(status.takeDamage(3));
        }
        assertEquals(List.of(PlayerStatusEvent.Type.REVIVED, PlayerStatusEvent.Type.DAMAGE_TAKEN,
                PlayerStatusEvent.Type.REVIVED, PlayerStatusEvent.Type.DAMAGE_TAKEN),
                events.stream().map(PlayerStatusEvent::getType).toList());
    }

    @Test
    void deathRemovedSlowDoesNotReturnAndRevivalDoesNotGrantAShield() {
        Ship ship = new Ship(100, 100);
        PlayerStatus status = new PlayerStatus(1, 8, true);
        List<PlayerStatusEvent> events = new ArrayList<>();
        status.addListener(events::add);
        assertEquals(PlayerStatus.SlowApplicationResult.STARTED, status.applySlow(ship));
        assertTrue(status.takeDamage(1));
        assertEquals(List.of(PlayerStatusEvent.Type.SLOW_STARTED, PlayerStatusEvent.Type.DAMAGE_TAKEN,
                PlayerStatusEvent.Type.SLOW_ENDED), events.stream().map(PlayerStatusEvent::getType).toList());
        events.clear();
        assertTrue(status.reviveAtHalfLives());
        assertEquals(1, events.size());
        assertEquals(PlayerStatusEvent.Type.REVIVED, events.get(0).getType());
        assertFalse(status.isSlowActive());
        assertFalse(status.isShieldActive());
        assertFalse(ship.isInvulnerable());
        int start = ship.getPositionX();
        ship.moveRight();
        assertEquals(start + ship.getSpeed(), ship.getPositionX());
    }
}
