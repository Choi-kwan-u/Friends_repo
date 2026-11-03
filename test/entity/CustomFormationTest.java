package entity;

import engine.GameSettings;
import engine.DrawManager.SpriteType;
import level.EnemyCell;
import level.WaveDefinition;
import screen.Screen;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomFormationTest {
    private WaveDefinition wave() {
        return new WaveDefinition("wave-01", new EnemyCell[][] {
            {null, new EnemyCell("C", 2), null, null},
            {null, null, null, null},
            {null, new EnemyCell("A", 1), null, new EnemyCell("B", 3)}
        }, 20, 100, 60, 2000);
    }

    private EnemyShipFormation formation() {
        WaveDefinition wave = wave();
        EnemyShipFormation formation = new EnemyShipFormation(
                new GameSettings(4, 3, 60, 2000, 20, 100), wave);
        formation.attach(new Screen(448, 520, 60));
        return formation;
    }

    @Test
    void sparseGridPreservesPhysicalCoordinatesTypesAndHealth() {
        EnemyShipFormation formation = formation();
        List<EnemyShip> enemies = new ArrayList<>();
        formation.forEach(enemies::add);
        assertEquals(3, enemies.size());
        assertEnemy(enemies, 60, 100, SpriteType.EnemyShipC1, 2);
        assertEnemy(enemies, 60, 180, SpriteType.EnemyShipA1, 1);
        assertEnemy(enemies, 140, 180, SpriteType.EnemyShipB1, 3);
        // Multiple empty columns must not corrupt cleanup or compact the grid.
        formation.update();
        assertEnemy(enemies, 60, 100, SpriteType.EnemyShipC1, 2);
    }

    @Test
    void shootersPromoteOnlyAfterDeathAndEmptyFormationCannotShoot() throws Exception {
        EnemyShipFormation formation = formation();
        EnemyShip bottom = null;
        EnemyShip top = null;
        for (EnemyShip enemy : formation) {
            if (enemy.getPositionX() == 60 && enemy.getPositionY() == 180) bottom = enemy;
            if (enemy.getPositionX() == 60 && enemy.getPositionY() == 100) top = enemy;
        }
        List<?> shooters = (List<?>) field(formation, "shooters");
        assertEquals(2, shooters.size());
        assertTrue(shooters.contains(bottom));
        assertFalse(shooters.contains(top));
        assertTrue(formation.hit(bottom));
        assertTrue(shooters.contains(top));
        assertFalse(formation.hit(top));
        assertEquals(1, top.getHealth());
        assertFalse(top.isDestroyed());
        assertTrue(formation.hit(top));
        for (EnemyShip enemy : formation) formation.destroy(enemy);
        assertTrue(formation.isEmpty());
        assertEquals(0, shooters.size());
        formation.shoot(new HashSet<Bullet>());
        formation.update();
        // Repeated destruction and hits must not decrement the remaining count twice.
        formation.destroy(top);
        assertFalse(formation.hit(top));
        assertEquals(0, field(formation, "shipCount"));
    }

    @Test
    void speedUsesOccupiedCellsRatherThanGridCapacity() throws Exception {
        EnemyShipFormation formation = formation();
        formation.update();
        assertEquals(70, field(formation, "movementSpeed"));
        EnemyShip killed = formation.iterator().next();
        formation.destroy(killed);
        formation.update();
        assertEquals(36, field(formation, "movementSpeed")); // floor((2/3)^2 * 60) + 10
    }

    @Test
    void legacyCampaignKeepsItsFullGridAndOneHitKills() {
        EnemyShipFormation formation = new EnemyShipFormation(new GameSettings(5, 4, 60, 2000));
        int count = 0;
        for (EnemyShip enemy : formation) {
            int row = (enemy.getPositionY() - 100) / 40;
            assertEquals(row == 0 ? SpriteType.EnemyShipC1
                    : row < 3 ? SpriteType.EnemyShipB1 : SpriteType.EnemyShipA1,
                    enemy.getSpriteType());
            assertEquals(1, enemy.getHealth());
            count++;
        }
        assertEquals(20, count);
        EnemyShip enemy = formation.iterator().next();
        assertTrue(formation.hit(enemy));
        assertTrue(enemy.isDestroyed());
    }

    @Test
    void directHealthAndForceDestructionRemainConsistent() {
        EnemyShip enemy = new EnemyShip(20, 100, SpriteType.EnemyShipA1, 2);
        assertFalse(enemy.hit());
        assertTrue(enemy.hit());
        assertFalse(enemy.hit());
        assertEquals(0, enemy.getHealth());
        EnemyShip bonus = new EnemyShip();
        assertTrue(bonus.hit());
        assertEquals(0, bonus.getHealth());
    }

    @Test
    void defeatedFormationStillCleansExplosionsWhileTheStageContinues() {
        EnemyShipFormation formation = formation();
        for (EnemyShip enemy : formation) formation.destroy(enemy);
        assertTrue(formation.isEmpty());
        for (int frame = 0; frame < 100; frame++) formation.update();
        assertFalse(formation.iterator().hasNext(),
                "Defeated ships must not remain drawn throughout a boss fight");
    }

    @Test
    void sparseHudCountsAndPromotedShooterRetainCurrentGameplayBehavior() throws Exception {
        WaveDefinition wave = new WaveDefinition("sparse", new EnemyCell[][] {
            {null, new EnemyCell("C", 1), null},
            {null, new EnemyCell("A", 2), null}
        }, 20, 100, 60, 2000);
        EnemyShipFormation formation = new EnemyShipFormation(
                new GameSettings(3, 2, 60, 2000, 20, 100), wave);
        formation.attach(new Screen(448, 520, 60));
        EnemyShip bottom = null;
        EnemyShip top = null;
        for (EnemyShip enemy : formation) {
            if (enemy.getPositionY() == 100) top = enemy;
            else bottom = enemy;
        }
        assertEquals(2, formation.getTotalCount());
        assertEquals(0, formation.getDestroyedCount());
        engine.Cooldown inherited = bottom.getShootingCooldown();
        assertNotNull(inherited);
        inherited.reset();
        assertFalse(formation.hit(bottom));
        assertEquals(2, formation.getRemainingCount());
        assertEquals(0, formation.getDestroyedCount());
        assertTrue(formation.hit(bottom));
        assertEquals(2, formation.getTotalCount());
        assertEquals(1, formation.getRemainingCount());
        assertEquals(1, formation.getDestroyedCount());
        assertSame(inherited, top.getShootingCooldown());

        Field cooldown = EnemyShipFormation.class.getDeclaredField("shootingCooldown");
        cooldown.setAccessible(true);
        cooldown.set(formation, engine.Core.getCooldown(0));
        top.setShootingCooldown(engine.Core.getCooldown(0));
        HashSet<Bullet> bullets = new HashSet<>();
        formation.shoot(bullets, new Ship(300, 490));
        assertEquals(1, bullets.size());
        assertTrue(bullets.iterator().next().isEnemyBullet());
    }

    private static void assertEnemy(List<EnemyShip> enemies, int x, int y,
            SpriteType type, int health) {
        EnemyShip enemy = enemies.stream().filter(e -> e.getPositionX() == x
                && e.getPositionY() == y).findFirst().orElseThrow(AssertionError::new);
        assertEquals(type, enemy.getSpriteType());
        assertEquals(health, enemy.getHealth());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
