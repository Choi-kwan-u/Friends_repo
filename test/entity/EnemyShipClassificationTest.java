package entity;

import engine.DrawManager.SpriteType;
import event.DefeatedEnemyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that an enemy keeps the classification it was created with. The
 * sprite cannot stand in for it: the animation replaces the sprite every half
 * second, and destruction replaces it with an explosion before the bullet path
 * publishes the defeat.
 */
class EnemyShipClassificationTest {

    @ParameterizedTest
    @CsvSource({"A, A", "B, B", "C, C"})
    void aSpawnedEnemyReportsItsOwnType(final EnemyType spawned,
            final DefeatedEnemyType expected) {
        EnemyShip enemy = EnemyShip.createByType(spawned, 100, 100);

        assertSame(expected, enemy.getDefeatedType());
    }

    @ParameterizedTest
    @CsvSource({
        "EnemyShipA1, A", "EnemyShipA2, A",
        "EnemyShipB1, B", "EnemyShipB2, B",
        "EnemyShipC1, C", "EnemyShipC2, C",
        "EnemyShipSpecial, BONUS"
    })
    void bothAnimationFramesMeanTheSameType(final SpriteType frame,
            final DefeatedEnemyType expected) {
        assertSame(expected, new EnemyShip(100, 100, frame).getDefeatedType());
    }

    @Test
    void theAnimationDoesNotChangeTheType() {
        EnemyShip enemy = EnemyShip.createByType(EnemyType.B, 100, 100);
        SpriteType firstFrame = enemy.getSpriteType();

        // The cooldown has never been reset, so the first update flips the frame.
        enemy.update();

        assertSame(SpriteType.EnemyShipB2, enemy.getSpriteType(),
                "the animation moved to the second frame");
        assertSame(DefeatedEnemyType.B, enemy.getDefeatedType());
        assertSame(SpriteType.EnemyShipB1, firstFrame);
    }

    @Test
    void destructionDoesNotChangeTheType() {
        EnemyShip enemy = EnemyShip.createByType(EnemyType.C, 100, 100);

        enemy.update();
        enemy.destroy();

        assertSame(SpriteType.Explosion, enemy.getSpriteType(),
                "destruction replaced the sprite");
        assertSame(DefeatedEnemyType.C, enemy.getDefeatedType(),
                "the classification outlives the sprite");
    }

    @Test
    void theBonusEnemyIsClassifiedAsBonus() {
        EnemyShip bonus = new EnemyShip();

        assertSame(DefeatedEnemyType.BONUS, bonus.getDefeatedType());

        bonus.destroy();

        assertSame(DefeatedEnemyType.BONUS, bonus.getDefeatedType());
    }

    @Test
    void onlyTheFinalHitDestroysAToughEnemyAndTheTypeHoldsThroughout() {
        EnemyShip enemy = EnemyShip.createByType(EnemyType.A, 100, 100, 3);

        for (int remaining = 3; remaining > 1; remaining--) {
            assertFalse(enemy.hit(), "a hit leaving " + (remaining - 1)
                    + " health does not destroy the enemy");
            assertFalse(enemy.isDestroyed());
            assertSame(DefeatedEnemyType.A, enemy.getDefeatedType());
        }

        assertTrue(enemy.hit(), "the last hit destroys the enemy");
        assertTrue(enemy.isDestroyed());
        assertSame(DefeatedEnemyType.A, enemy.getDefeatedType());

        assertFalse(enemy.hit(), "a destroyed enemy cannot be destroyed again");
    }

    @Test
    void anInfiniteModeEnemyInheritsTheClassification() {
        ZigzagEnemyShip enemy =
                new ZigzagEnemyShip(100, 100, SpriteType.EnemyShipA1, 1);

        assertSame(DefeatedEnemyType.A, enemy.getDefeatedType());
    }
}
