package entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.util.logging.LogManager;

import engine.GameSettings;
import engine.GameState;
import screen.GameScreen;

/**
 * Standalone checks that every movement pattern moves in small, frequent
 * steps rather than in rare jumps, and that the stage setting and the number
 * of enemies left still change its pace.
 *
 * <p>A pattern that waits movementSpeed frames (about seventy on the opening
 * stage) between steps of several pixels looks like it stutters, so the
 * checks look at the longest pause and the longest jump, not only at the
 * average pace.</p>
 */
public final class FormationSmoothnessTest {

	/** Frames to observe each configuration for. */
	private static final int SAMPLE_FRAMES = 3000;
	/**
	 * Longest pause allowed between two steps, in frames. The diagonal
	 * pattern, which already moves smoothly, pauses for up to three.
	 */
	private static final int MAX_IDLE_FRAMES = 3;
	/**
	 * Longest distance covered in one frame, in pixels: the largest step on
	 * each axis at once. Anything beyond it reads as a teleport.
	 */
	private static final double MAX_JUMP = 16.0 * Math.sqrt(2);
	/** Width of the screen the formation is built on. */
	private static final int SCREEN_WIDTH = 448;
	/** Margin the formation keeps on each side. */
	private static final int SIDE_MARGIN = 20;
	/** Distance the classic pattern goes down each pass. */
	private static final int DESCENT_DISTANCE = 20;

	private static final String[] PATTERNS = { "CLASSIC", "WAVE_BOUNCE",
			"DIAGONAL_BOUNCE" };

	private FormationSmoothnessTest() {
	}

	/** Result of observing one configuration. */
	private static final class Measure {
		int maxIdle;
		double maxJump;
		double pace;
		int minX = Integer.MAX_VALUE;
		int maxRight = Integer.MIN_VALUE;
		boolean offGrid;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		LogManager.getLogManager().reset();
		boolean report = args.length > 0 && args[0].equals("report");

		for (String pattern : PATTERNS) {
			double previousPace = -1;
			for (int[] setting : new int[][] { { 140, 0 }, { 60, 0 },
					{ 60, 50 }, { 60, 90 } }) {
				Measure m = measure(pattern, setting[0], setting[1]);
				if (report)
					System.out.printf("%-16s base=%3d killed=%2d%%  maxIdle=%3d"
							+ "  maxJump=%5.1f  pace=%5.2f%n", pattern,
							setting[0], setting[1], m.maxIdle, m.maxJump,
							m.pace);
				else
					assertSmooth(pattern, setting, m, previousPace);
				previousPace = m.pace;
			}
		}
		if (!report)
			System.out.println("Formation smoothness checks passed.");
	}

	private static void assertSmooth(final String pattern, final int[] setting,
			final Measure m, final double previousPace) {
		String where = pattern + " (base " + setting[0] + ", "
				+ setting[1] + "% killed)";
		check(m.maxIdle <= MAX_IDLE_FRAMES, where + " never pauses for more"
				+ " than " + MAX_IDLE_FRAMES + " frame (paused " + m.maxIdle
				+ ")");
		check(m.maxJump <= MAX_JUMP, where + " never jumps more than "
				+ MAX_JUMP + " px in a frame (jumped " + m.maxJump + ")");
		check(m.pace > previousPace, where + " is faster than the previous"
				+ " setting, which is slower or has more enemies (" + m.pace
				+ " vs " + previousPace + ")");
		check(m.minX >= SIDE_MARGIN - 1 && m.maxRight <= SCREEN_WIDTH
				- SIDE_MARGIN + 1, where + " stays inside the side margins"
				+ " (left " + m.minX + ", right " + m.maxRight + ")");
		check(!m.offGrid, where + " only moves sideways on a descent"
				+ " altitude, so it never gets stuck going down");
	}


    private static Measure measure(final String pattern, final int baseSpeed,
                                   final int killedPercent) throws Exception {

        EnemyShipFormation formation = formation(baseSpeed, pattern);
        destroyShare(formation, killedPercent);

        // Remove destroyed ships before selecting a ship to track.
        formation.update();

        // Measure an actual ship, not the formation's anchor.
        EnemyShip trackedShip = null;

        for (EnemyShip ship : formation) {
            if (!ship.isDestroyed()) {
                trackedShip = ship;
                break;
            }
        }

        if (trackedShip == null) {
            throw new AssertionError("No surviving enemy ship to track");
        }

        Measure m = new Measure();
        int lastX = trackedShip.getPositionX();
        int lastY = trackedShip.getPositionY();

        int idle = 0;
        double travelled = 0;

        for (int frame = 0; frame < SAMPLE_FRAMES; frame++) {
            formation.update();

            int x = trackedShip.getPositionX();
            int y = trackedShip.getPositionY();

            double step = Math.hypot(x - lastX, y - lastY);

            if (step == 0) {
                idle++;
            } else {
                idle = 0;
            }

            m.maxIdle = Math.max(m.maxIdle, idle);
            m.maxJump = Math.max(m.maxJump, step);
            travelled += step;

            // Check the positions of the actual remaining ships.
            for (EnemyShip ship : formation) {
                m.minX = Math.min(m.minX, ship.getPositionX());
                m.maxRight = Math.max(m.maxRight,
                        ship.getPositionX() + ship.getWidth());
            }

            if (pattern.equals("CLASSIC")
                    && x != lastX
                    && y % DESCENT_DISTANCE != 0) {
                m.offGrid = true;
            }

            lastX = x;
            lastY = y;
        }

        m.pace = travelled / SAMPLE_FRAMES;
        return m;
    }


    private static void destroyShare(final EnemyShipFormation formation,
			final int killedPercent) {
		if (killedPercent <= 0)
			return;

		// iterator() hands back a HashSet iterator, so its order follows
		// allocation and changes whenever EnemyShip's constructor does. That
		// made which ships die vary between runs, and with it how far the
		// formation's bounding box shrank, so the same check could pass or
		// fail on identical code. Sorting by position fixes the selection to
		// the ships nearest the top-left, which is also the order a column
		// actually dies in.
		List<EnemyShip> ships = new ArrayList<EnemyShip>();
		for (Iterator<EnemyShip> it = formation.iterator(); it.hasNext();)
			ships.add(it.next());
		Collections.sort(ships, new Comparator<EnemyShip>() {
			public int compare(final EnemyShip left, final EnemyShip right) {
				if (left.getPositionX() != right.getPositionX())
					return left.getPositionX() - right.getPositionX();
				return left.getPositionY() - right.getPositionY();
			}
		});

		int toKill = ships.size() * killedPercent / 100;
		for (int i = 0; i < toKill; i++)
			formation.destroy(ships.get(i));
	}

	/** Builds a formation on a real screen and forces its movement pattern. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static EnemyShipFormation formation(final int baseSpeed,
			final String pattern) throws Exception {
		GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
				new GameSettings(5, 4, baseSpeed, 2000), false, SCREEN_WIDTH,
				520, 60);
		game.initialize();

		Field screenField = GameScreen.class
				.getDeclaredField("enemyShipFormation");
		screenField.setAccessible(true);
		EnemyShipFormation formation = (EnemyShipFormation) screenField
				.get(game);

		Field patternField = EnemyShipFormation.class
				.getDeclaredField("movementPattern");
		patternField.setAccessible(true);
		patternField.set(formation,
				Enum.valueOf((Class<Enum>) patternField.getType(), pattern));
		return formation;
	}

	private static int read(final Object target, final String name)
			throws Exception {
		Field field = EnemyShipFormation.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(target);
	}
}
