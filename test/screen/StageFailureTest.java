package screen;

import java.lang.reflect.Field;

import engine.GameSettings;
import engine.GameState;
import entity.EnemyShipFormation;
import entity.PlayerStatus;

/**
 * Standalone checks for the campaign rule "if the player's health reaches
 * zero, the stage is failed and the game does not advance to the next
 * stage"; no test framework is required.
 */
public final class StageFailureTest {

    private StageFailureTest() {
    }

    private static void check(final boolean condition, final String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    public static void main(final String[] args) throws Exception {
        checkRunningStageIsNotCleared();
        checkZeroLivesFailsTheStage();
        checkZeroLivesWinsOverAnEmptyFormation();
        checkEmptyFormationClearsTheStage();

        System.out.println("Stage failure checks passed.");
    }

    /** While the player lives and enemies remain, nothing is decided yet. */
    private static void checkRunningStageIsNotCleared() throws Exception {
        GameScreen game = newGame();
        game.advanceStageIfReady();

        check(!game.isStageCleared(), "A running stage is not cleared");
        check(!levelFinished(game), "A running stage is not finished");
    }

    /** Lives at zero: the stage ends as failed, so Core must not advance. */
    private static void checkZeroLivesFailsTheStage() throws Exception {
        GameScreen game = newGame();
        killPlayer(game);
        game.advanceStageIfReady();

        check(levelFinished(game), "Zero lives finishes the stage");
        check(!game.isStageCleared(), "Zero lives must mark the stage failed");
    }

    /** Dying and killing the last enemy together still counts as a failure. */
    private static void checkZeroLivesWinsOverAnEmptyFormation()
            throws Exception {
        GameScreen game = newGame();
        emptyFormation(game);
        killPlayer(game);
        game.advanceStageIfReady();

        check(!game.isStageCleared(),
                "A dead player cannot clear the stage, even with no enemies");
    }

    /** Control case: a living player with no enemies left clears the stage. */
    private static void checkEmptyFormationClearsTheStage() throws Exception {
        GameScreen game = newGame();
        emptyFormation(game);
        game.advanceStageIfReady();

        check(game.isStageCleared(), "Living player and no enemies clears it");
    }

    private static GameScreen newGame() {
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
        game.initialize();
        return game;
    }

    private static void killPlayer(final GameScreen game) throws Exception {
        PlayerStatus status = (PlayerStatus) read(game, "playerStatus");
        status.takeDamage(status.getLives());
        check(!status.isAlive(), "Test setup: the player must be dead");
    }

    private static void emptyFormation(final GameScreen game)
            throws Exception {
        EnemyShipFormation formation =
                (EnemyShipFormation) read(game, "enemyShipFormation");
        Field count = EnemyShipFormation.class.getDeclaredField("shipCount");
        count.setAccessible(true);
        count.setInt(formation, 0);
        check(formation.isEmpty(), "Test setup: the formation must be empty");
    }

    private static boolean levelFinished(final GameScreen game)
            throws Exception {
        return ((Boolean) read(game, "levelFinished")).booleanValue();
    }

    private static Object read(final Object target, final String name)
            throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
