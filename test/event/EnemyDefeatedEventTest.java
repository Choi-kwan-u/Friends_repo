package event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks the defeat event contract that the currency listener relies on: the
 * classification it carries, the compatibility value for callers that supply
 * none, and the identifier validation that was already in place.
 */
class EnemyDefeatedEventTest {

    @Test
    void carriesTheClassificationItWasGiven() {
        for (DefeatedEnemyType type : DefeatedEnemyType.values()) {
            EnemyDefeatedEvent event = new EnemyDefeatedEvent("run", "defeat", type);
            assertSame(type, event.getEnemyType(), "classification of " + type);
            assertEquals("run", event.getRunId());
            assertEquals("defeat", event.getEventId());
        }
    }

    @Test
    void anUnspecifiedClassificationIsUnknown() {
        EnemyDefeatedEvent event = new EnemyDefeatedEvent("run", "defeat");

        assertSame(DefeatedEnemyType.UNKNOWN, event.getEnemyType());
        assertEquals("run", event.getRunId());
        assertEquals("defeat", event.getEventId());
    }

    @Test
    void rejectsAMissingClassification() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent("run", "defeat", null));
    }

    @Test
    void keepsTheExistingIdentifierValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent(null, "defeat"));
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent("run", null));
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent(" ", "defeat"));
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent("run", " "));
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent(" ", "defeat", DefeatedEnemyType.A));
        assertThrows(IllegalArgumentException.class,
                () -> new EnemyDefeatedEvent("run", " ", DefeatedEnemyType.A));
    }

    @Test
    void readsBackTheSameValuesEveryTime() {
        EnemyDefeatedEvent event =
                new EnemyDefeatedEvent("run", "defeat", DefeatedEnemyType.BOSS_B);

        assertEquals(event.getRunId(), event.getRunId());
        assertEquals(event.getEventId(), event.getEventId());
        assertSame(event.getEnemyType(), event.getEnemyType());
    }

    @Test
    void coversEveryClassificationTheRewardListenerExpects() {
        assertSame(DefeatedEnemyType.A, DefeatedEnemyType.valueOf("A"));
        assertSame(DefeatedEnemyType.B, DefeatedEnemyType.valueOf("B"));
        assertSame(DefeatedEnemyType.C, DefeatedEnemyType.valueOf("C"));
        assertSame(DefeatedEnemyType.BONUS, DefeatedEnemyType.valueOf("BONUS"));
        assertSame(DefeatedEnemyType.BOSS_A, DefeatedEnemyType.valueOf("BOSS_A"));
        assertSame(DefeatedEnemyType.BOSS_B, DefeatedEnemyType.valueOf("BOSS_B"));
        assertSame(DefeatedEnemyType.UNKNOWN, DefeatedEnemyType.valueOf("UNKNOWN"));
        assertEquals(7, DefeatedEnemyType.values().length,
                "a new classification needs an agreed reward mapping");
    }
}
