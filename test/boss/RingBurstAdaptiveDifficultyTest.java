package boss;

import java.util.List;

import engine.DifficultyAdjustWhilePlaying;
import entity.Boss;
import entity.BossBullet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBurstAdaptiveDifficultyTest {
    private static final double EPSILON = 1e-9;
    private final DifficultyAdjustWhilePlaying adjustment =
            DifficultyAdjustWhilePlaying.getInstance();

    @BeforeEach
    @AfterEach
    void resetAdjustment() {
        adjustment.reset();
    }

    @Test
    void bossARingUsesTheSameReductionAsNormalAttacks() {
        SpecialAttack ring = BossAttackConfig.forType(Boss.BossType.BOSS_A)
                .getSpecialAttack();
        assertTrue(ring instanceof SpecialAttack.RingBurst);
        List<BossBullet> normal = fire(ring);
        double baseSpeed = speed(normal.get(0));
        assertTrue(baseSpeed > 0);

        adjustment.update(1);

        List<BossBullet> assisted = fire(ring);
        assertEquals(normal.size(), assisted.size());
        assertSpeed(assisted, baseSpeed / 2);
        assertSpeed(new AttackPattern.SingleShot(baseSpeed)
                .fire(180, 80, 180, 400, 2), baseSpeed / 2);
    }

    @Test
    void preservesRingDirectionsRotationAndBulletProperties() {
        SpecialAttack ring = new SpecialAttack.RingBurst(8000, 1000, 8, 3.5);
        assertRing(fire(ring), 3.5, 0);

        adjustment.update(1);

        assertRing(fire(ring), 1.75, 22.5);
    }

    @Test
    void recoveringLivesRestoresSpeedWithoutChangingTheBaseSpeed() {
        SpecialAttack ring = new SpecialAttack.RingBurst(8000, 1000, 8, 3.5);
        adjustment.update(1);
        assertSpeed(fire(ring), 1.75);
        assertSpeed(fire(ring), 1.75);

        adjustment.update(2);
        assertSpeed(fire(ring), 3.5);

        adjustment.update(1);
        assertSpeed(fire(ring), 1.75);
    }

    @Test
    void threeClearedStagesRestoreSpeedWhileOneLifeRemains() {
        SpecialAttack ring = new SpecialAttack.RingBurst(8000, 1000, 8, 3.5);
        adjustment.update(1);
        for (int cleared = 0; cleared < 3; cleared++) {
            assertSpeed(fire(ring), 1.75);
            adjustment.onLevelCleared();
        }

        adjustment.update(1);
        assertSpeed(fire(ring), 3.5);
    }

    @Test
    void resettingForANewRunRestoresSpeed() {
        SpecialAttack ring = new SpecialAttack.RingBurst(8000, 1000, 8, 3.5);
        adjustment.update(1);
        assertSpeed(fire(ring), 1.75);

        adjustment.reset();

        assertSpeed(fire(ring), 3.5);
    }

    @Test
    void changingAssistanceDoesNotChangeAlreadyFiredBullets() {
        SpecialAttack ring = new SpecialAttack.RingBurst(8000, 1000, 8, 3.5);
        List<BossBullet> normal = fire(ring);
        adjustment.update(1);
        List<BossBullet> assisted = fire(ring);
        adjustment.update(2);

        for (BossBullet bullet : normal) bullet.update();
        for (BossBullet bullet : assisted) bullet.update();
        assertSpeed(normal, 3.5);
        assertSpeed(assisted, 1.75);
        assertSpeed(fire(ring), 3.5);
    }

    private static List<BossBullet> fire(SpecialAttack ring) {
        return ring.execute(180, 80, 180, 400, 2);
    }

    private static double speed(BossBullet bullet) {
        return Math.hypot(bullet.getSpeedX(), bullet.getSpeedY());
    }

    private static void assertSpeed(List<BossBullet> bullets, double expected) {
        assertTrue(!bullets.isEmpty());
        for (BossBullet bullet : bullets)
            assertEquals(expected, speed(bullet), EPSILON);
    }

    private static void assertRing(List<BossBullet> bullets, double speed,
            double offset) {
        assertEquals(8, bullets.size());
        for (int i = 0; i < bullets.size(); i++) {
            BossBullet bullet = bullets.get(i);
            double angle = Math.toRadians(offset + i * 45);
            assertEquals(Math.cos(angle) * speed, bullet.getSpeedX(), EPSILON);
            assertEquals(Math.sin(angle) * speed, bullet.getSpeedY(), EPSILON);
            assertEquals(180 - bullet.getWidth() / 2, bullet.getPositionX());
            assertEquals(80, bullet.getPositionY());
            assertEquals(2, bullet.getDamage());
            assertTrue(bullet.isSpecial());
        }
    }
}
