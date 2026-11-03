package screen;

import engine.GameState;
import entity.*;
import level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CustomLevelGameScreenTest {
    @AfterEach
    void stopAudio() { audio.AudioManager.stopBGM(); }

    private GameScreen screen() {
        WaveDefinition first = new WaveDefinition("first",
                new EnemyCell[][] {{null, new EnemyCell("B", 2), null}, {null, null, null}},
                20, 100, 60, 2000);
        WaveDefinition second = new WaveDefinition("second",
                new EnemyCell[][] {{new EnemyCell("C", 1)}}, 80, 120, 90, 3000);
        LevelDefinition level = new LevelDefinition(1, "campaign-stage-07", "Custom", Arrays.asList(first, second));
        GameScreen screen = GameScreen.forCustomLevel(new GameState(1, 0, 3, 0, 0), level,
                1.25, 448, 520, 60);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        return screen;
    }

    @Test
    void playsOriginalGridsInOrderAndClearsOnlyAfterFinalWave() throws Exception {
        GameScreen screen = screen();
        EnemyShipFormation first = formation(screen);
        EnemyShip enemy = first.iterator().next();
        assertEquals(60, enemy.getPositionX());
        assertEquals(100, enemy.getPositionY());
        assertEquals(2, enemy.getHealth());
        assertEquals(3, screen.getGameState().getLivesRemaining());
        assertFalse(screen.isStageCleared());
        first.destroy(enemy);
        screen.advanceStageIfReady();
        assertFalse(screen.isStageCleared());
        assertEquals(1, get(screen, "currentWave"));
        assertEquals(3, screen.getGameState().getLivesRemaining());
        EnemyShipFormation second = formation(screen);
        assertNotSame(first, second);
        EnemyShip last = second.iterator().next();
        assertEquals(80, last.getPositionX());
        assertEquals(120, last.getPositionY());
        assertEquals(engine.DrawManager.SpriteType.EnemyShipC1, last.getSpriteType());
        second.destroy(last);
        screen.advanceStageIfReady();
        assertTrue(screen.isStageCleared());
        screen.advanceStageIfReady();
        assertEquals(1, get(screen, "currentWave"));
    }

    @Test
    void zeroLivesWinsOverWaveClearAndDoesNotAdvance() throws Exception {
        GameScreen screen = screen();
        for (EnemyShip enemy : formation(screen)) formation(screen).destroy(enemy);
        ((PlayerStatus) get(screen, "playerStatus")).takeDamage(3);
        screen.advanceStageIfReady();
        assertFalse(screen.isStageCleared());
        assertEquals(0, get(screen, "currentWave"));
        assertEquals(true, get(screen, "levelFinished"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bulletsDamageWithoutAwardingScoreOrKillsUntilDeath() throws Exception {
        GameScreen screen = screen();
        EnemyShip enemy = formation(screen).iterator().next();
        Set<Bullet> bullets = (Set<Bullet>) get(screen, "bullets");
        Method collisions = GameScreen.class.getDeclaredMethod("manageCollisions");
        collisions.setAccessible(true);
        for (int hit = 1; hit <= 2; hit++) {
            bullets.add(new Bullet(enemy.getPositionX() + 8, enemy.getPositionY(), -6));
            collisions.invoke(screen);
            assertTrue(bullets.isEmpty());
            assertEquals(2 - hit, enemy.getHealth());
            assertEquals(hit == 2 ? 20 : 0, screen.getGameState().getScore());
            assertEquals(hit == 2 ? 1 : 0, screen.getGameState().getShipsDestroyed());
            vfx.ParticleSystem particles = (vfx.ParticleSystem) get(screen, "particleSystem");
            assertEquals(hit != 2, particles.isEmpty(),
                    "Explosion particles must appear only on the fatal hit");
        }
        assertTrue(enemy.isDestroyed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aConsumedBulletCannotAlsoHitTheBonusEnemy() throws Exception {
        GameScreen screen = screen();
        EnemyShip enemy = formation(screen).iterator().next();
        EnemyShip bonus = new EnemyShip();
        bonus.setPositionX(enemy.getPositionX());
        bonus.setPositionY(enemy.getPositionY());
        BonusEnemyManager manager = (BonusEnemyManager) get(screen, "bonusEnemyManager");
        Field bonusField = BonusEnemyManager.class.getDeclaredField("bonusShip");
        bonusField.setAccessible(true);
        bonusField.set(manager, bonus);
        Set<Bullet> bullets = (Set<Bullet>) get(screen, "bullets");
        bullets.add(new Bullet(enemy.getPositionX() + 8, enemy.getPositionY(), -6));
        Method collisions = GameScreen.class.getDeclaredMethod("manageCollisions");
        collisions.setAccessible(true);

        collisions.invoke(screen);

        assertEquals(1, enemy.getHealth());
        assertTrue(bullets.isEmpty());
        assertTrue(manager.isActive());
        assertFalse(bonus.isDestroyed());
        assertEquals(0, screen.getGameState().getScore());
        assertEquals(0, screen.getGameState().getShipsDestroyed());
    }

    @Test
    void officialStageStillWaitsForTheBossAndStopsItsRunTimer() throws Exception {
        records.RunSession session = new records.RunSession("restored-stage");
        GameScreen screen = new GameScreen(new GameState(3, 0, 3, 0, 0),
                new engine.GameSettings(1, 1, 60, 2000), false,
                448, 520, 60, ShipType.TANK, session);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        for (EnemyShip enemy : formation(screen)) formation(screen).destroy(enemy);
        session.startActiveSegment(System.nanoTime() - 1000000000L);

        screen.advanceStageIfReady();
        assertFalse(screen.isStageCleared());
        assertEquals(ShipType.TANK, ((Ship) get(screen, "ship")).getShipType());
        assertEquals(ShipType.TANK.getMaxLives(),
                ((PlayerStatus) get(screen, "playerStatus")).getMaxLives());

        boss.BossManager manager = (boss.BossManager) get(screen, "bossManager");
        manager.getBoss().takeDamage(Integer.MAX_VALUE);
        manager.sync();
        set(screen, "bossExplosionCooldown", engine.Core.getCooldown(0));
        screen.advanceStageIfReady();

        assertTrue(screen.isStageCleared());
        long duration = session.getActiveDurationMillis(System.nanoTime());
        assertEquals(duration,
                session.getActiveDurationMillis(System.nanoTime() + 5000000000L));
    }

    static EnemyShipFormation formation(GameScreen screen) throws Exception {
        return (EnemyShipFormation) get(screen, "enemyShipFormation");
    }
    static Object get(Object target, String name) throws Exception {
        Field field = GameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    static void set(Object target, String name, Object value) throws Exception {
        Field field = GameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
