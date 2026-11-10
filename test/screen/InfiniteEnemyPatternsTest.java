package screen;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import engine.DrawManager.SpriteType;
import engine.GameState;
import entity.Bullet;
import entity.Ship;
import entity.ZigzagEnemyShip;
import entity.ZigzagEnemyShip.Pattern;

/** Standalone checks for the Infinite mode enemy patterns. */
public final class InfiniteEnemyPatternsTest {

    private static final int WIDTH = 448;
    private static final int HEIGHT = 520;

    public static void main(final String[] args) throws Exception {
        checkStraightFallsFasterWithoutSwinging();
        checkWaveSwingsWide();
        checkChargerStopsWarnsAndDashes();
        checkSpawnTable();
        checkShotsByType();
        System.out.println("Infinite Mode enemy pattern checks passed.");
    }

    private static void checkStraightFallsFasterWithoutSwinging() {
        ZigzagEnemyShip zigzag = enemy(Pattern.ZIGZAG);
        ZigzagEnemyShip straight = enemy(Pattern.STRAIGHT);
        for (int i = 0; i < 60; i++) {
            zigzag.advance(0, 0);
            straight.advance(0, 0);
        }
        check(straight.getPositionX() == 200, "Straight enemy moved sideways");
        // 1 * 0.8 * 1.5 = 1.2 px per frame, against 0.8 for the zigzag.
        check(straight.getPositionY() - 100 >= 71
                && straight.getPositionY() - 100 <= 72,
                "Straight enemy should fall about 72 px in 60 frames, fell "
                        + (straight.getPositionY() - 100));
        check(straight.getPositionY() > zigzag.getPositionY(),
                "Straight enemy should fall faster than the zigzag");
    }

    private static void checkWaveSwingsWide() {
        ZigzagEnemyShip wave = enemy(Pattern.WAVE);
        int minX = wave.getPositionX();
        int maxX = wave.getPositionX();
        for (int i = 0; i < 150; i++) {
            wave.advance(0, 0);
            minX = Math.min(minX, wave.getPositionX());
            maxX = Math.max(maxX, wave.getPositionX());
        }
        check(maxX == 290 && minX == 110,
                "Wave should swing 90 px each way, went " + minX + ".." + maxX);
        check(wave.getPositionX() == 200, "Wave should be back after a period");
    }

    private static void checkChargerStopsWarnsAndDashes() {
        ZigzagEnemyShip charger = ZigzagEnemyShip.charger(200, 40, 1, 100);
        check(charger.getPointValue() == ZigzagEnemyShip.CHARGER_POINTS,
                "Charger should give 50 points");
        check(new Color(170, 70, 255).equals(charger.getColor()),
                "Charger should be purple");

        int frames = 0;
        while (!charger.isChargeWarning() && frames++ < 1000)
            charger.advance(0, 0);
        check(charger.isChargeWarning(), "Charger never stopped");
        int stopX = charger.getPositionX();
        int stopY = charger.getPositionY();
        check(stopY >= 100 && stopY <= 101, "Charger stopped at " + stopY);

        for (int i = 0; i < 35; i++)
            charger.advance(0, 0);
        check(charger.isChargeWarning() && charger.getPositionY() == stopY,
                "Charger moved during its warning");

        // Target straight below: the dash goes down at 6 px per frame.
        int targetX = stopX + charger.getWidth() / 2;
        int targetY = stopY + charger.getHeight() / 2 + 300;
        charger.advance(targetX, targetY);
        check(charger.isDashing(), "Charger did not dash after its warning");
        charger.advance(0, 0);
        check(charger.getPositionX() == stopX
                && charger.getPositionY() == stopY + 6,
                "Dash should head at the locked target, 6 px per frame");
    }

    private static void checkSpawnTable() throws Exception {
        InfiniteGameScreen screen = screen();
        List<ZigzagEnemyShip> enemies = enemies(screen);

        check(!screen.spawnFromRoll(0.05) && enemies.size() == 1
                && enemies.get(0).getPattern() == Pattern.CHARGER,
                "Roll 0.05 should spawn a charger");

        enemies.clear();
        check(screen.spawnFromRoll(0.15), "Roll 0.15 should be a formation");
        check(enemies.size() == InfiniteGameScreen.FORMATION_SIZE,
                "Formation should have 5 enemies");
        for (int i = 0; i < enemies.size(); i++) {
            ZigzagEnemyShip enemy = enemies.get(i);
            check(enemy.getPattern() == Pattern.ZIGZAG
                    && enemy.getPointValue() == 10,
                    "Formation enemies should be zigzagging A types");
            check(enemy.getPositionX() - enemies.get(0).getPositionX() == 40 * i,
                    "Formation enemies should be 40 px apart");
        }
        // Same first direction: the shape holds while they move.
        List<Integer> gaps = new ArrayList<Integer>();
        for (int frame = 0; frame < 120; frame++)
            for (ZigzagEnemyShip enemy : enemies)
                enemy.advance(0, 0);
        for (int i = 1; i < enemies.size(); i++)
            gaps.add(enemies.get(i).getPositionX()
                    - enemies.get(i - 1).getPositionX());
        for (int gap : gaps)
            check(gap == 40, "Formation lost its shape");

        checkSingle(screen, 0.25, Pattern.WAVE, 30);
        checkSingle(screen, 0.50, Pattern.STRAIGHT, 20);
        checkSingle(screen, 0.90, Pattern.ZIGZAG, 10);
    }

    private static void checkSingle(final InfiniteGameScreen screen,
            final double roll, final Pattern pattern, final int points)
            throws Exception {
        List<ZigzagEnemyShip> enemies = enemies(screen);
        enemies.clear();
        check(!screen.spawnFromRoll(roll) && enemies.size() == 1,
                "Roll " + roll + " should spawn one enemy");
        check(enemies.get(0).getPattern() == pattern
                && enemies.get(0).getPointValue() == points,
                "Roll " + roll + " should spawn a " + pattern + " enemy");
    }

    @SuppressWarnings("unchecked")
    private static void checkShotsByType() throws Exception {
        InfiniteGameScreen screen = screen();
        Set<Bullet> bullets = (Set<Bullet>) field("enemyBullets").get(screen);
        Ship ship = (Ship) field("ship").get(screen);

        screen.fireEnemy(enemy(Pattern.ZIGZAG));
        check(sideways(bullets).equals(List.of(0)), "A should shoot straight");

        bullets.clear();
        screen.fireEnemy(enemy(Pattern.STRAIGHT));
        List<Integer> spread = sideways(bullets);
        spread.sort(null);
        check(spread.equals(List.of(-2, 0, 2)), "B should fire a 3-way spread");

        bullets.clear();
        ship.setPositionX(400);
        screen.fireEnemy(enemy(Pattern.WAVE));
        List<Integer> aimed = sideways(bullets);
        check(aimed.size() == 1 && aimed.get(0) > 0,
                "C should aim right at a ship on its right");

        bullets.clear();
        screen.fireEnemy(ZigzagEnemyShip.charger(200, 100, 1, 200));
        check(bullets.isEmpty(), "Charger should not shoot");
        check(InfiniteGameScreen.fireChance(Pattern.CHARGER) == 0,
                "Charger should have no fire chance");
    }

    /** Sideways movement of each bullet over one update. */
    private static List<Integer> sideways(final Set<Bullet> bullets) {
        List<Integer> result = new ArrayList<Integer>();
        for (Bullet bullet : bullets) {
            int x = bullet.getPositionX();
            bullet.update();
            result.add(bullet.getPositionX() - x);
        }
        return result;
    }

    private static ZigzagEnemyShip enemy(final Pattern pattern) {
        return new ZigzagEnemyShip(200, 100, SpriteType.EnemyShipA1, 1,
                pattern, 1);
    }

    private static InfiniteGameScreen screen() {
        InfiniteGameScreen screen = new InfiniteGameScreen(
                new GameState(1, 0, 3, 0, 0), WIDTH, HEIGHT, 60);
        screen.initialize();
        return screen;
    }

    @SuppressWarnings("unchecked")
    private static List<ZigzagEnemyShip> enemies(
            final InfiniteGameScreen screen) throws Exception {
        return (List<ZigzagEnemyShip>) field("enemies").get(screen);
    }

    private static Field field(final String name) throws Exception {
        Field field = InfiniteGameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private InfiniteEnemyPatternsTest() {
    }
}
