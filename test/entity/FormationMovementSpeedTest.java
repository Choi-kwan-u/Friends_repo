package entity;

import java.lang.reflect.Field;
import java.util.Iterator;

import engine.GameSettings;
import engine.GameState;
import screen.GameScreen;

/**
 * Standalone checks that the formation's pace follows the stage setting and
 * the number of enemies left, and that it still starts at the pace the game
 * has today. The diagonal pattern used a fixed delay and a fixed step, so it
 * answered to neither.
 *
 * <p>The checks are on measured pixels per frame rather than on the ordering
 * alone: an earlier version of this harness compared two cases and passed
 * even with the whole diagonal branch deleted, because any delay derived
 * from movementSpeed satisfied it.</p>
 */
public final class FormationMovementSpeedTest {

	/** Pace of a full formation on the opening stage, in pixels per frame. */
	private static final double REFERENCE_PACE = 2.122;
	/** How far a measurement may drift before it counts as a change. */
	private static final double TOLERANCE = 0.05;
	/** Frames to average a measurement over. */
	private static final int SAMPLE_FRAMES = 600;
	/** MAX_STEP / STEP_INTERVAL, the fastest the pattern goes. */
	private static final double MAXIMUM_PACE = 16.0 / 2.0;

	private FormationMovementSpeedTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkOpeningStageKeepsThePaceItHasToday();
		checkStageSettingChangesThePace();
		checkFormationSpeedsUpAsEnemiesAreDefeated();
		checkPaceIsCapped();

		System.out.println("Formation movement speed checks passed.");
	}

	/**
	 * The anchor. Making the pattern obey the settings must not change how
	 * the first stage already plays, so this pins the measured pace rather
	 * than only its direction.
	 */
	private static void checkOpeningStageKeepsThePaceItHasToday()
			throws Exception {
		double pace = pace(60, 0);
		check(Math.abs(pace - REFERENCE_PACE) < TOLERANCE,
				"A full formation on the opening stage keeps its pace of "
						+ REFERENCE_PACE + " px/frame (measured " + pace + ")");
	}

	/** A stage configured to move slowly must actually move slowly. */
	private static void checkStageSettingChangesThePace() throws Exception {
		double slowest = pace(140, 0);
		double slow = pace(90, 0);
		double reference = pace(60, 0);
		double fast = pace(20, 0);

		check(slowest < slow && slow < reference && reference < fast,
				"The pace rises as the base speed setting falls (" + slowest
						+ " < " + slow + " < " + reference + " < " + fast
						+ ")");
		check(slowest < reference / 2,
				"A much slower stage is markedly slower, not a rounding "
						+ "difference (" + slowest + " vs " + reference + ")");
	}

	/** The formation accelerates as it is thinned out. */
	private static void checkFormationSpeedsUpAsEnemiesAreDefeated()
			throws Exception {
		double full = pace(60, 0);
		double half = pace(60, 50);
		double nearlyCleared = pace(60, 90);

		check(full < half && half < nearlyCleared,
				"The pace rises as enemies are defeated (" + full + " < "
						+ half + " < " + nearlyCleared + ")");
		check(nearlyCleared > full * 3,
				"A nearly cleared formation is markedly faster (" + full
						+ " to " + nearlyCleared + ")");
	}

	/** A nearly cleared formation must not jump across the screen. */
	private static void checkPaceIsCapped() throws Exception {
		double fastest = pace(20, 90);
		check(fastest <= MAXIMUM_PACE,
				"The pace stays within its cap of " + MAXIMUM_PACE
						+ " px/frame (measured " + fastest + ")");
	}

	/**
	 * Measures how far the formation travels per frame, averaged over a
	 * sample long enough to cover several steps.
	 *
	 * @param baseSpeed
	 *            Base speed the stage is configured with.
	 * @param killedPercent
	 *            Share of the formation destroyed before measuring.
	 * @return Pixels travelled per frame.
	 */
	private static double pace(final int baseSpeed, final int killedPercent)
			throws Exception {
		EnemyShipFormation formation = formation(baseSpeed);
		destroyShare(formation, killedPercent);

		int lastX = readInt(formation, "positionX");
		int lastY = readInt(formation, "positionY");
		double travelled = 0;

		for (int frame = 0; frame < SAMPLE_FRAMES; frame++) {
			formation.update();
			int x = readInt(formation, "positionX");
			int y = readInt(formation, "positionY");
			travelled += Math.hypot(x - lastX, y - lastY);
			lastX = x;
			lastY = y;
		}
		return travelled / SAMPLE_FRAMES;
	}

	private static void destroyShare(final EnemyShipFormation formation,
			final int killedPercent) {
		if (killedPercent <= 0)
			return;

		int total = 0;
		for (Iterator<EnemyShip> it = formation.iterator(); it.hasNext();) {
			it.next();
			total++;
		}

		int toKill = total * killedPercent / 100;
		int killed = 0;
		for (Iterator<EnemyShip> it = formation.iterator();
				it.hasNext() && killed < toKill; killed++)
			formation.destroy(it.next());
	}


	/**
	 * Builds a formation attached to a real screen. GameScreen owns that
	 * wiring, so it is borrowed rather than rebuilt here.
	 */

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static EnemyShipFormation formation(final int baseSpeed)
            throws Exception {

        GameScreen game = new GameScreen(
                new GameState(1, 0, 3, 0, 0),
                new GameSettings(5, 4, baseSpeed, 2000),
                false, 448, 520, 60);

        game.initialize();

        Field field = GameScreen.class.getDeclaredField("enemyShipFormation");
        field.setAccessible(true);

        EnemyShipFormation formation =
                (EnemyShipFormation) field.get(game);

        // Explicitly select the diagonal pattern.
        Field patternField = EnemyShipFormation.class
                .getDeclaredField("movementPattern");

        patternField.setAccessible(true);
        patternField.set(formation,
                Enum.valueOf((Class<Enum>) patternField.getType(),
                        "DIAGONAL_BOUNCE"));

        return formation;
    }

    private static int readInt(final Object target, final String name)
            throws Exception {
        Field field = EnemyShipFormation.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
