package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.Set;

import engine.DrawManager.SpriteType;
import engine.GameState;
import entity.Bullet;
import entity.BulletPool;
import entity.Ship;
import entity.ZigzagEnemyShip;
import entity.item.ClusterGrenadeItem;
import entity.item.Hyperfire;
import entity.item.Item;
import entity.item.ItemManager;
import entity.item.OrbitalAnnihilator;
import entity.item.ShieldItem;

/** Standalone checks for the items added to Infinite mode. */
public final class InfiniteGameScreenItemsTest {

    private static final int WIDTH = 448;
    private static final int HEIGHT = 520;

    public static void main(final String[] args) throws Exception {
        checkDropTable();
        checkShieldWorksOnTheStandardShip();
        checkDropsAtAnyHeight();
        checkGaugeArmsAfterEnoughKills();
        checkKillsDoNotCountDuringCooldown();
        checkShieldBlocksEnemyBullet();
        checkNewRecordOnlyAboveTheBestScore();
        System.out.println("Infinite Mode item checks passed.");
    }

    private static void checkDropTable() {
        Item shield = InfiniteGameScreen.createDrop(0, 0, 0.0);
        check(shield instanceof ShieldItem, "Roll 0.00 should drop a shield");
        check(shield.getDuration() == 8000, "Shield should last 8 s");
        check(InfiniteGameScreen.createDrop(0, 0, 0.099) instanceof ShieldItem,
                "Roll 0.099 should drop a shield");

        Item hyperfire = InfiniteGameScreen.createDrop(0, 0, 0.10);
        check(hyperfire instanceof Hyperfire, "Roll 0.10 should drop Hyperfire");
        check(hyperfire.getDuration() == 5000, "Hyperfire should last 5 s");
        check(InfiniteGameScreen.createDrop(0, 0, 0.249) instanceof Hyperfire,
                "Roll 0.249 should drop Hyperfire");

        check(InfiniteGameScreen.createDrop(0, 0, 0.25)
                instanceof ClusterGrenadeItem, "Roll 0.25 should drop a grenade");
        check(InfiniteGameScreen.createDrop(0, 0, 0.299)
                instanceof ClusterGrenadeItem, "Roll 0.299 should drop a grenade");
        check(InfiniteGameScreen.createDrop(0, 0, 0.30) == null,
                "Roll 0.30 should drop nothing");

        check(new ShieldItem(0, 0).getDuration() == 15000,
                "Campaign shield duration changed");
        check(new Hyperfire(0, 0).getDuration() == 8000,
                "Campaign Hyperfire duration changed");
    }

    private static void checkShieldWorksOnTheStandardShip() {
        Ship ship = new Ship(100, 100);
        InfiniteGameScreen.createDrop(0, 0, 0.0).applyEffect(ship);
        check(ship.isShieldActive(),
                "Infinite shield should work on the Standard Ship");

        Ship campaignShip = new Ship(100, 100);
        new ShieldItem(0, 0).applyEffect(campaignShip);
        check(!campaignShip.isShieldActive(),
                "Campaign shield must still refuse the Standard Ship");
    }

    private static void checkDropsAtAnyHeight() throws Exception {
        InfiniteGameScreen screen = screen();
        screen.itemDropRandom = alwaysRoll(0.0);

        screen.destroyEnemy(enemy(50));
        screen.destroyEnemy(enemy(HEIGHT - 50));
        check(fallingItems(screen).size() == 2,
                "Enemies should drop items at any height");
    }

    private static void checkGaugeArmsAfterEnoughKills() throws Exception {
        InfiniteGameScreen screen = screen();
        screen.itemDropRandom = alwaysRoll(0.99);
        OrbitalAnnihilator annihilator = annihilator(screen);

        for (int i = 0; i < InfiniteGameScreen.ANNIHILATOR_KILLS - 1; i++)
            screen.destroyEnemy(enemy(50));
        int kills = InfiniteGameScreen.ANNIHILATOR_KILLS;
        check(kills == 30, "The gauge should need 30 kills");
        check(!annihilator.isReady(), "Armed before 30 kills");
        check(near(screen.getAnnihilatorGaugeProgress(),
                (kills - 1) / (float) kills), "Gauge should be at 29/30");

        screen.destroyEnemy(enemy(50));
        check(annihilator.isReady(), "Not armed after 30 kills");
        long blink = InfiniteGameScreen.GAUGE_BLINK_PERIOD;
        check(screen.getAnnihilatorGaugeProgress(0) == 1f
                && screen.getAnnihilatorGaugeProgress(blink) == 0f
                && screen.getAnnihilatorGaugeProgress(2 * blink) == 1f,
                "Full gauge does not blink");

        check(annihilator.trigger(), "Armed Annihilator did not trigger");
        check(screen.getAnnihilatorGaugeProgress(blink) == 1f,
                "Gauge should stay full while charging");
        screen.destroyEnemy(enemy(50));
        check(intField(screen, "annihilatorKills")
                == InfiniteGameScreen.ANNIHILATOR_KILLS,
                "Kills during the charge changed the gauge");
    }

    private static void checkKillsDoNotCountDuringCooldown() throws Exception {
        InfiniteGameScreen screen = screen();
        screen.itemDropRandom = alwaysRoll(0.99);
        long now = System.currentTimeMillis();
        int cooldown = InfiniteGameScreen.ANNIHILATOR_COOLDOWN;
        field("annihilatorCooldownUntil").setLong(screen, now + cooldown);

        check(near(screen.getAnnihilatorGaugeProgress(now), 1f),
                "Gauge should be full when the cooldown starts");
        check(near(screen.getAnnihilatorGaugeProgress(now + cooldown / 2), 0.5f),
                "Gauge should be half drained mid-cooldown");
        check(screen.getAnnihilatorGaugeProgress(now + cooldown) == 0f,
                "Gauge should be empty when the cooldown ends");

        screen.destroyEnemy(enemy(50));
        check(intField(screen, "annihilatorKills") == 0,
                "A kill during the cooldown filled the gauge");

        field("annihilatorCooldownUntil").setLong(screen, 0);
        screen.destroyEnemy(enemy(50));
        check(intField(screen, "annihilatorKills") == 1,
                "A kill after the cooldown did not fill the gauge");
    }

    @SuppressWarnings("unchecked")
    private static void checkShieldBlocksEnemyBullet() throws Exception {
        InfiniteGameScreen screen = screen();
        Ship ship = (Ship) field("ship").get(screen);
        ship.activateShieldIgnoringShipType();
        Set<Bullet> enemyBullets = (Set<Bullet>) field("enemyBullets").get(screen);
        enemyBullets.add(BulletPool.getBullet(
                ship.getPositionX() + ship.getWidth() / 2,
                ship.getPositionY() + ship.getHeight() / 2, 4));

        Method manageCollisions =
                InfiniteGameScreen.class.getDeclaredMethod("manageCollisions");
        manageCollisions.setAccessible(true);
        manageCollisions.invoke(screen);

        check(enemyBullets.isEmpty(), "Blocked bullet was not removed");
        check(screen.getGameState().getLivesRemaining() == 3,
                "Shielded ship lost a life");
        check(!ship.isDestroyed(), "Shielded ship was destroyed");
    }

    private static void checkNewRecordOnlyAboveTheBestScore()
            throws Exception {
        InfiniteGameScreen screen = screen();
        field("bestInfiniteScore").setInt(screen, 100);

        field("score").setInt(screen, 0);
        check(!screen.isBeatingHighScore(), "No points is not a record");
        field("score").setInt(screen, 100);
        check(!screen.isBeatingHighScore(), "Tying the best is not a record");
        field("score").setInt(screen, 101);
        check(screen.isBeatingHighScore(), "Beating the best is a record");

        field("bestInfiniteScore").setInt(screen, 0);
        field("score").setInt(screen, 0);
        check(!screen.isBeatingHighScore(),
                "Without records, a score of 0 is not a record");
        field("score").setInt(screen, 10);
        check(screen.isBeatingHighScore(),
                "Without records, the first points are a record");
    }

    private static InfiniteGameScreen screen() {
        InfiniteGameScreen screen = new InfiniteGameScreen(
                new GameState(1, 0, 3, 0, 0), WIDTH, HEIGHT, 60);
        screen.initialize();
        return screen;
    }

    private static ZigzagEnemyShip enemy(final int positionY) {
        return new ZigzagEnemyShip(100, positionY, SpriteType.EnemyShipA1, 1);
    }

    private static Random alwaysRoll(final double value) {
        return new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Item> fallingItems(final InfiniteGameScreen screen)
            throws Exception {
        ItemManager manager = (ItemManager) field("itemManager").get(screen);
        Field items = ItemManager.class.getDeclaredField("activeItems");
        items.setAccessible(true);
        return (List<Item>) items.get(manager);
    }

    private static OrbitalAnnihilator annihilator(
            final InfiniteGameScreen screen) throws Exception {
        return (OrbitalAnnihilator) field("annihilator").get(screen);
    }

    private static int intField(final InfiniteGameScreen screen,
            final String name) throws Exception {
        return field(name).getInt(screen);
    }

    private static Field field(final String name) throws Exception {
        Field field = InfiniteGameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static boolean near(final float actual, final float expected) {
        return Math.abs(actual - expected) < 0.001f;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private InfiniteGameScreenItemsTest() {
    }
}
