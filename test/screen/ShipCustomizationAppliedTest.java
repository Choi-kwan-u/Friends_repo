package screen;

import java.awt.Color;
import java.lang.reflect.Field;

import engine.GameSettings;
import engine.GameState;
import entity.Ship;

/**
 * Standalone checks that the colour chosen in the hangar reaches the ship a
 * campaign stage plays with, and survives a frame of updates.
 *
 * <p>The call that applied it was lost in a merge once already, and a plain
 * setColor would not be enough if it came back that way: the ship resets its
 * colour to its base colour on every update once the damage flash is over,
 * so only setBaseColor survives the first frame. Both halves are checked
 * here, since either one failing shows the ship in the default green.</p>
 */
public final class ShipCustomizationAppliedTest {

	/** Colour the ship falls back to, from Ship.DEFAULT_COLOR. */
	private static final Color DEFAULT_COLOR = Color.GREEN;

	private ShipCustomizationAppliedTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkTheChosenColourReachesTheShip();
		checkTheChosenColourSurvivesAnUpdate();

		System.out.println("Ship customization checks passed.");
	}

	private static void checkTheChosenColourReachesTheShip()
			throws Exception {
		Color chosen = selectColourOtherThanTheDefault();
		Ship ship = shipOfAStage();

		check(ship.getColor().equals(chosen), "The ship plays in the colour"
				+ " chosen in the hangar (chose " + chosen + ", got "
				+ ship.getColor() + ")");
	}

	private static void checkTheChosenColourSurvivesAnUpdate()
			throws Exception {
		Color chosen = selectColourOtherThanTheDefault();
		Ship ship = shipOfAStage();
		ship.update();

		check(ship.getColor().equals(chosen), "The colour survives an update"
				+ " rather than being reset to the base colour (chose "
				+ chosen + ", got " + ship.getColor() + ")");
	}

	/**
	 * Picks a colour that is not the one a ship would show anyway, so that
	 * the checks cannot pass on the default.
	 *
	 * @return The colour now selected in the hangar.
	 */
	private static Color selectColourOtherThanTheDefault() {
		for (int step = 0; step < 16; step++) {
			if (!ShipCustomization.getColor().equals(DEFAULT_COLOR))
				return ShipCustomization.getColor();
			ShipCustomization.changeColor(1);
		}
		throw new AssertionError("The hangar offers no colour other than "
				+ DEFAULT_COLOR);
	}

	/** Builds a campaign stage and hands back the ship it created. */
	private static Ship shipOfAStage() throws Exception {
		GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
				new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
		game.initialize();

		Field field = GameScreen.class.getDeclaredField("ship");
		field.setAccessible(true);
		return (Ship) field.get(game);
	}
}
