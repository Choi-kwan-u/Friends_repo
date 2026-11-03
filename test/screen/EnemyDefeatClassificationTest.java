package screen;

import audio.AudioManager;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Boss;
import entity.BonusEnemyManager;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.GrenadeFragment;
import entity.Ship;
import entity.item.OrbitalAnnihilator;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.LevelCompletedEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import records.RunSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real defeat handlers of a campaign stage and checks the
 * classification each one publishes, so that the currency listener can tell
 * enemy kinds apart from the defeat event alone.
 */
class EnemyDefeatClassificationTest {

    /** Somewhere clear of the formation, the player and the beam corridor. */
    private static final int CLEAR_X = 300;
    private static final int CLEAR_Y = 400;

    private boolean wasMuted;

    @BeforeEach
    void silenceSoundEffects() {
        wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void restoreSoundEffects() {
        AudioManager.setMuted(wasMuted);
    }

    @Test
    void theBulletPathReportsEachNormalTypeWithItsOwnId() throws Exception {
        GameScreen game = campaign(1, "bullet-types");
        Defeats defeats = watch(game);

        for (DefeatedEnemyType expected : Arrays.asList(DefeatedEnemyType.A,
                DefeatedEnemyType.B, DefeatedEnemyType.C)) {
            shoot(game, isolate(game, expected));
            call(game, "manageCollisions");
        }

        assertEquals(Arrays.asList(DefeatedEnemyType.A, DefeatedEnemyType.B,
                DefeatedEnemyType.C), defeats.types(),
                "each normal enemy reports its own type");
        assertEquals(3, new HashSet<String>(defeats.ids()).size(),
                "separate enemies have separate defeat IDs");
        assertEquals(3, game.getGameState().getShipsDestroyed(),
                "the existing kill total is unchanged");
    }

    @Test
    void theClassificationSurvivesTheExplosionSprite() throws Exception {
        GameScreen game = campaign(1, "explosion-sprite");
        Defeats defeats = watch(game);
        EnemyShip target = isolate(game, DefeatedEnemyType.B);
        // Move to the second animation frame before the defeat.
        target.update();

        shoot(game, target);
        call(game, "manageCollisions");

        assertTrue(target.isDestroyed(), "the bullet path destroyed the enemy");
        assertSame(engine.DrawManager.SpriteType.Explosion,
                target.getSpriteType(),
                "the defeat is published after destruction swapped the sprite");
        assertEquals(Collections.singletonList(DefeatedEnemyType.B),
                defeats.types(), "the published type is still the original one");
    }

    @Test
    void anEnemyWithMoreHealthReportsOnlyItsFinalDefeat() throws Exception {
        GameScreen game = campaign(1, "tough-enemy");
        Defeats defeats = watch(game);
        EnemyShip target = isolate(game, DefeatedEnemyType.A);
        set(target, "health", 2);

        shoot(game, target);
        call(game, "manageCollisions");

        assertEquals(0, defeats.size(), "the first of two hits is not a defeat");
        assertFalse(target.isDestroyed(), "the enemy survived the first hit");

        shoot(game, target);
        call(game, "manageCollisions");

        assertEquals(Collections.singletonList(DefeatedEnemyType.A),
                defeats.types(), "the final hit is one classified defeat");
        assertTrue(target.isDestroyed());
    }

    @Test
    void repeatedCollisionChecksDoNotCreateNewDefeats() throws Exception {
        GameScreen game = campaign(1, "repeat-checks");
        Defeats defeats = watch(game);

        shoot(game, isolate(game, DefeatedEnemyType.A));
        call(game, "manageCollisions");
        assertEquals(1, defeats.size());
        String onlyId = defeats.ids().get(0);

        for (int frame = 0; frame < 5; frame++) {
            call(game, "manageCollisions");
            call(game, "manageGrenades");
        }

        assertEquals(1, Collections.frequency(defeats.ids(), onlyId),
                "one defeated enemy keeps one defeat ID");
        assertEquals(1, defeats.size(), "a defeated enemy is not defeated again");
    }

    @Test
    void anotherPathCannotDefeatAnAlreadyDefeatedEnemy() throws Exception {
        GameScreen game = campaign(1, "multi-path");
        set(game, "inputDelay", Core.getCooldown(0));
        Defeats defeats = watch(game);
        Ship player = (Ship) get(game, "ship");
        EnemyShip target = isolate(game, DefeatedEnemyType.A);

        shoot(game, target);
        call(game, "manageCollisions");
        assertEquals(1, defeats.size());

        // Offer the same enemy to the beam and to a contact kill.
        target.setPositionX(OrbitalAnnihilator.getBeamLeft(player));
        target.setPositionY(player.getPositionY() - 40);
        call(game, "manageAnnihilatorBeam");
        player.activateChameleonHull();
        target.setPositionX(player.getPositionX());
        target.setPositionY(player.getPositionY());
        call(game, "manageBallContact");

        assertEquals(1, defeats.size(),
                "one defeat is not reported again by another path");
    }

    @Test
    void theBeamPathClassifiesNormalAndBonusTargets() throws Exception {
        GameScreen game = campaign(1, "beam-path");
        Defeats defeats = watch(game);
        Ship player = (Ship) get(game, "ship");

        EnemyShip target = isolate(game, DefeatedEnemyType.C);
        target.setPositionX(OrbitalAnnihilator.getBeamLeft(player));
        target.setPositionY(player.getPositionY() - 40);
        EnemyShip bonus = placeBonusEnemy(game,
                OrbitalAnnihilator.getBeamLeft(player),
                player.getPositionY() - 80);

        call(game, "manageAnnihilatorBeam");
        call(game, "manageAnnihilatorBeam");

        assertEquals(Arrays.asList(DefeatedEnemyType.C, DefeatedEnemyType.BONUS),
                defeats.types(), "the beam classifies both of its targets");
        assertEquals(2, new HashSet<String>(defeats.ids()).size(),
                "the beam gives each target its own ID");
        assertTrue(bonus.isDestroyed());
    }

    @Test
    void theContactPathClassifiesNormalAndBonusTargets() throws Exception {
        GameScreen game = campaign(1, "contact-path");
        set(game, "inputDelay", Core.getCooldown(0));
        Defeats defeats = watch(game);
        Ship player = (Ship) get(game, "ship");
        player.activateChameleonHull();

        EnemyShip target = isolate(game, DefeatedEnemyType.C);
        target.setPositionX(player.getPositionX());
        target.setPositionY(player.getPositionY());
        placeBonusEnemy(game, player.getPositionX(), player.getPositionY());

        call(game, "manageBallContact");
        call(game, "manageBallContact");

        assertEquals(Arrays.asList(DefeatedEnemyType.C, DefeatedEnemyType.BONUS),
                defeats.types(), "contact kills classify both of their targets");
        assertEquals(2, new HashSet<String>(defeats.ids()).size());
    }

    @Test
    void theFragmentPathClassifiesItsTarget() throws Exception {
        GameScreen game = campaign(1, "fragment-path");
        Defeats defeats = watch(game);
        EnemyShip target = isolate(game, DefeatedEnemyType.B);

        @SuppressWarnings("unchecked")
        Set<GrenadeFragment> fragments =
                (Set<GrenadeFragment>) get(game, "fragments");
        fragments.addAll(GrenadeFragment.explode(
                target.getPositionX() + target.getWidth() / 2,
                target.getPositionY() + target.getHeight() / 2));

        call(game, "manageGrenades");

        assertEquals(Collections.singletonList(DefeatedEnemyType.B),
                defeats.types(),
                "a fragment kill is one classified defeat, not one per fragment");
    }

    @Test
    void aBonusEnemyIsClassifiedOnceAndAnEscapeIsNotADefeat() throws Exception {
        GameScreen game = campaign(1, "bonus-enemy");
        Defeats defeats = watch(game);

        EnemyShip bonus = placeBonusEnemy(game, CLEAR_X, CLEAR_Y);
        shoot(game, bonus);
        call(game, "manageCollisions");
        call(game, "manageCollisions");

        assertEquals(Collections.singletonList(DefeatedEnemyType.BONUS),
                defeats.types(), "a defeated bonus enemy is reported once");

        BonusEnemyManager bonusEnemies =
                (BonusEnemyManager) get(game, "bonusEnemyManager");
        EnemyShip escaping = placeBonusEnemy(game, game.getWidth() + 1, CLEAR_Y);
        bonusEnemies.update(game.getWidth());

        assertFalse(escaping.isDestroyed(), "the escaping enemy was not destroyed");
        assertEquals(1, defeats.size(), "an escape is not a defeat");
    }

    @ParameterizedTest
    @CsvSource({"3, BOSS_A", "5, BOSS_B"})
    void aDefeatedBossIsClassifiedOnceOnTheSameRoute(final int stage,
            final Boss.BossType expected) throws Exception {
        GameScreen game = campaign(stage, "boss-" + stage);
        Defeats defeats = watch(game);
        int destroyedBefore = game.getGameState().getShipsDestroyed();

        damageBoss(game, expected.getMaxHealth() - 1);
        assertEquals(0, defeats.size(), "a living boss is not a defeat");

        damageBoss(game, 1);
        assertEquals(1, defeats.size(), "the boss defeat arrives once");
        assertSame(DefeatedEnemyType.valueOf(expected.name()),
                defeats.types().get(0), "the boss type travels on the event");
        assertEquals(destroyedBefore + 1,
                game.getGameState().getShipsDestroyed(),
                "the existing boss kill total is unchanged");

        damageBoss(game, expected.getMaxHealth());
        assertEquals(1, defeats.size(),
                "hitting a defeated boss does not pay again");
    }

    @Test
    void laterWavesAndRunsStayDistinguishable() throws Exception {
        GameSettings single = new GameSettings(1, 1, 1, 100000);
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                Arrays.asList(single, single), false, 448, 520, 60,
                new RunSession("two-waves"));
        game.initialize();
        Defeats defeats = watch(game);

        EnemyShip first = onlyEnemy(game);
        shoot(game, first);
        call(game, "manageCollisions");
        game.advanceStageIfReady();

        EnemyShip second = onlyEnemy(game);
        assertNotEquals(first, second, "the next wave brought a new enemy");
        shoot(game, second);
        call(game, "manageCollisions");

        assertEquals(2, defeats.size(), "both waves reported their defeat");
        assertEquals(2, new HashSet<String>(defeats.ids()).size(),
                "a new wave's enemy is not confused with the previous one");

        Defeats otherRun = new Defeats();
        GameScreen next = campaign(1, "another-run");
        next.getGameplayEvents().subscribe(EnemyDefeatedEvent.class, otherRun);
        shoot(next, isolate(next, DefeatedEnemyType.A));
        call(next, "manageCollisions");

        assertEquals(1, otherRun.size());
        assertNotEquals(defeats.runIds().get(0), otherRun.runIds().get(0),
                "a new run is distinguishable by its run ID");
    }

    @Test
    void theLastDefeatArrivesBeforeTheCompletionEvent() throws Exception {
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(1, 1, 1, 100000), false, 448, 520, 60,
                new RunSession("completion-order"));
        game.initialize();
        final List<String> order = new ArrayList<String>();
        game.getGameplayEvents().subscribe(EnemyDefeatedEvent.class,
                event -> order.add("defeat:" + event.getEnemyType()));
        game.getGameplayEvents().subscribe(LevelCompletedEvent.class,
                event -> order.add("completed"));

        shoot(game, onlyEnemy(game));
        call(game, "manageCollisions");
        game.advanceStageIfReady();

        assertEquals(Arrays.asList("defeat:C", "completed"), order,
                "the final defeat is delivered before the stage completes");
    }

    @Test
    void everyDefeatIdIsGroupedUnderItsEnemyType() throws Exception {
        GameScreen game = campaign(1, "grouped-ids");
        Defeats defeats = watch(game);

        for (DefeatedEnemyType expected : Arrays.asList(DefeatedEnemyType.A,
                DefeatedEnemyType.B, DefeatedEnemyType.C)) {
            shoot(game, isolate(game, expected));
            call(game, "manageCollisions");
        }
        shoot(game, placeBonusEnemy(game, CLEAR_X, CLEAR_Y));
        call(game, "manageCollisions");

        assertEquals(4, defeats.size());
        for (int index = 0; index < defeats.size(); index++) {
            DefeatedEnemyType type = defeats.types().get(index);
            String id = defeats.ids().get(index);
            assertTrue(id.startsWith(type.name() + GameScreen.DEFEAT_ID_SEPARATOR),
                    "defeat ID " + id + " is grouped under " + type);
            assertEquals(type.name(),
                    id.substring(0, id.indexOf(GameScreen.DEFEAT_ID_SEPARATOR)),
                    "the grouping agrees with the published type");
        }
        assertEquals(4, new HashSet<String>(defeats.ids()).size(),
                "grouping the IDs keeps them distinct");
    }

    @Test
    void twoEnemiesOfOneTypeStayTwoSeparateDefeats() throws Exception {
        GameScreen game = campaign(1, "same-type-twice");
        Defeats defeats = watch(game);

        shoot(game, isolate(game, DefeatedEnemyType.A));
        call(game, "manageCollisions");
        shoot(game, isolate(game, DefeatedEnemyType.A));
        call(game, "manageCollisions");

        assertEquals(Arrays.asList(DefeatedEnemyType.A, DefeatedEnemyType.A),
                defeats.types(), "both defeats report the same type");
        assertEquals(2, new HashSet<String>(defeats.ids()).size(),
                "two enemies of one type are still two defeats");
    }

    @ParameterizedTest
    @CsvSource({"3, BOSS_A", "5, BOSS_B"})
    void aBossDefeatIdIsGroupedUnderItsBossType(final int stage,
            final Boss.BossType expected) throws Exception {
        GameScreen game = campaign(stage, "boss-id-" + stage);
        Defeats defeats = watch(game);

        damageBoss(game, expected.getMaxHealth());

        assertEquals(1, defeats.size());
        assertTrue(defeats.ids().get(0).startsWith(
                expected.name() + GameScreen.DEFEAT_ID_SEPARATOR),
                "the boss defeat ID is grouped under " + expected);
    }

    // Collects the published defeats in the order they arrive.
    private static final class Defeats
            implements GameplayEventListener<EnemyDefeatedEvent> {
        private final List<EnemyDefeatedEvent> seen =
                new ArrayList<EnemyDefeatedEvent>();

        @Override
        public void onEvent(final EnemyDefeatedEvent event) {
            seen.add(event);
        }

        int size() {
            return seen.size();
        }

        List<DefeatedEnemyType> types() {
            List<DefeatedEnemyType> types = new ArrayList<DefeatedEnemyType>();
            for (EnemyDefeatedEvent event : seen)
                types.add(event.getEnemyType());
            return types;
        }

        List<String> ids() {
            List<String> ids = new ArrayList<String>();
            for (EnemyDefeatedEvent event : seen)
                ids.add(event.getEventId());
            return ids;
        }

        List<String> runIds() {
            List<String> runIds = new ArrayList<String>();
            for (EnemyDefeatedEvent event : seen)
                runIds.add(event.getRunId());
            return runIds;
        }
    }

    /** Starts one recorded campaign stage with a three-by-five formation. */
    private static GameScreen campaign(final int stage, final String runId) {
        GameScreen game = new GameScreen(new GameState(stage, 0, 3, 0, 0),
                new GameSettings(3, 5, 1, 100000), false, 448, 520, 60,
                new RunSession(runId));
        game.initialize();
        return game;
    }

    private static Defeats watch(final GameScreen game) {
        Defeats defeats = new Defeats();
        game.getGameplayEvents().subscribe(EnemyDefeatedEvent.class, defeats);
        return defeats;
    }

    /** Moves one living enemy of the wanted type clear of everything else. */
    private static EnemyShip isolate(final GameScreen game,
            final DefeatedEnemyType wanted) throws Exception {
        EnemyShipFormation formation =
                (EnemyShipFormation) get(game, "enemyShipFormation");
        for (EnemyShip enemy : formation)
            if (!enemy.isDestroyed() && enemy.getDefeatedType() == wanted) {
                enemy.setPositionX(CLEAR_X);
                enemy.setPositionY(CLEAR_Y);
                return enemy;
            }
        throw new AssertionError("The formation has no living " + wanted + " enemy");
    }

    private static EnemyShip onlyEnemy(final GameScreen game) throws Exception {
        EnemyShipFormation formation =
                (EnemyShipFormation) get(game, "enemyShipFormation");
        for (EnemyShip enemy : formation)
            if (!enemy.isDestroyed()) {
                enemy.setPositionX(CLEAR_X);
                enemy.setPositionY(CLEAR_Y);
                return enemy;
            }
        throw new AssertionError("The formation is already empty");
    }

    private static EnemyShip placeBonusEnemy(final GameScreen game,
            final int positionX, final int positionY) throws Exception {
        EnemyShip bonus = new EnemyShip();
        bonus.setPositionX(positionX);
        bonus.setPositionY(positionY);
        set(get(game, "bonusEnemyManager"), "bonusShip", bonus);
        return bonus;
    }

    @SuppressWarnings("unchecked")
    private static void shoot(final GameScreen game, final EnemyShip target)
            throws Exception {
        Set<Bullet> bullets = (Set<Bullet>) get(game, "bullets");
        bullets.add(BulletPool.getBullet(target.getPositionX() + 4,
                target.getPositionY() + 4, -4));
    }

    private static void damageBoss(final GameScreen game, final int damage)
            throws Exception {
        Method method =
                GameScreen.class.getDeclaredMethod("damageBoss", int.class);
        method.setAccessible(true);
        method.invoke(game, damage);
    }

    private static void call(final Object target, final String name)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Object get(final Object target, final String name)
            throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void set(final Object target, final String name,
            final Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(final Class<?> owner, final String name)
            throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException ignored) {
                // Keep looking up the hierarchy.
            }
        throw new NoSuchFieldException(name);
    }
}
