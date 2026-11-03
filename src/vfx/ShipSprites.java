package vfx;

import java.awt.Color;

import engine.GameColors;
import entity.ShipType;

/**
 * Custom sprites for the selectable player ships and the bullets. They keep
 * the original 13x8 ship and 3x5 bullet sizes so hitboxes are unchanged.
 */
public final class ShipSprites {

	/** Classic fighter with side cannons for the Standard Ship. */
	private static final boolean[][] STANDARD = parse(
			"......#......",
			"......#......",
			".....###.....",
			"..#..###..#..",
			".###########.",
			"#############",
			"###.#####.###",
			"##.........##");

	/** Delta-wing jet with twin exhausts for the Speedster. */
	private static final boolean[][] SPEEDSTER = parse(
			"......#......",
			".....###.....",
			".....#.#.....",
			"....#####....",
			"..#########..",
			".###########.",
			"##..##.##..##",
			"#....#.#....#");

	/** Twin-cannon turret on armored treads for the Heavy Tank. */
	private static final boolean[][] TANK = parse(
			"....#...#....",
			"....#...#....",
			"...#######...",
			"..#########..",
			"#############",
			"#.#.#.#.#.#.#",
			"#############",
			".###########.");

	/** Plasma bolt: white-hot core, blue glow and a spark trail. */
	private static final Color[][] PLAYER_BULLET = parseColors("WB",
			new Color[] { Color.WHITE, GameColors.PLAYER_BULLET },
			".W.",
			"BWB",
			"BWB",
			".B.",
			"B.B");

	/** Falling fireball: bright red body, white-hot core, orange sparks. */
	private static final Color[][] ENEMY_BULLET = parseColors("RWO",
			new Color[] { GameColors.ENEMY_BULLET_GLOW, Color.WHITE,
					GameColors.ENEMY_BULLET },
			"O.O",
			"RRR",
			"RWR",
			"RWR",
			".R.");

	private ShipSprites() {
	}

	/**
	 * Returns the sprite for a ship type.
	 *
	 * @param shipType
	 *            Ship type to look up.
	 * @return Sprite indexed [x][y].
	 */
	public static boolean[][] forType(final ShipType shipType) {
		switch (shipType) {
		case SPEEDSTER:
			return SPEEDSTER;
		case TANK:
			return TANK;
		default:
			return STANDARD;
		}
	}

	/**
	 * @return Player bullet pixel colors indexed [x][y]; null where empty.
	 */
	public static Color[][] playerBullet() {
		return PLAYER_BULLET;
	}

	/**
	 * @return Enemy bullet pixel colors indexed [x][y]; null where empty.
	 */
	public static Color[][] enemyBullet() {
		return ENEMY_BULLET;
	}

	private static boolean[][] parse(final String... rows) {
		boolean[][] sprite = new boolean[rows[0].length()][rows.length];
		for (int y = 0; y < rows.length; y++)
			for (int x = 0; x < rows[y].length(); x++)
				sprite[x][y] = rows[y].charAt(x) == '#';
		return sprite;
	}

	private static Color[][] parseColors(final String legend,
			final Color[] colors, final String... rows) {
		Color[][] sprite = new Color[rows[0].length()][rows.length];
		for (int y = 0; y < rows.length; y++)
			for (int x = 0; x < rows[y].length(); x++) {
				int key = legend.indexOf(rows[y].charAt(x));
				if (key >= 0)
					sprite[x][y] = colors[key];
			}
		return sprite;
	}
}
