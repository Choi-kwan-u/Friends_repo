package vfx;

import java.awt.Color;

import engine.GameColors;

/**
 * UI icon set (requirement 4.1). Each icon is pixel art described row by row,
 * where '.' is empty and every other character is a lit pixel. Single-color
 * icons use '#'; multi-color icons map each character to a color through a
 * legend. Icons are kept separate from the gameplay sprites so the HUD never
 * reuses ship art.
 */
public enum Icon {

	/** Score display. */
	SCORE(GameColors.ICON_SCORE,
			"...#...",
			"...#...",
			"#######",
			".#####.",
			"..###..",
			".##.##.",
			".#...#."),

	/** Remaining life. */
	LIFE(GameColors.ICON_LIFE,
			".##.##.",
			"#######",
			"#######",
			".#####.",
			"..###..",
			"...#..."),

	/** Lost life: the heart split by a crack. */
	LIFE_LOST(GameColors.ICON_LIFE_LOST,
			".##..##.",
			"###.####",
			"###..###",
			".##.###.",
			"..#..#..",
			"...#...."),

	/** Currency / coins. */
	COIN(GameColors.ICON_COIN,
			"..####..",
			".##..##.",
			"##.##.##",
			"##.#####",
			"##.#####",
			"##.##.##",
			".##..##.",
			"..####.."),

	/** Locked content: a padlock with a keyhole. */
	LOCK(GameColors.ICON_UI,
			"...###...",
			"..#...#..",
			"..#...#..",
			"..#...#..",
			".#######.",
			"#########",
			"####.####",
			"###...###",
			"####.####",
			"#########",
			".#######."),

	/** Shop: a shopping bag. */
	SHOP("WG", new Color[] { GameColors.ICON_UI, GameColors.ICON_COIN },
			"...WWW...",
			"..W...W..",
			"..W...W..",
			"GGGGGGGGG",
			"GGGGGGGGG",
			"GGGG.GGGG",
			"GGG...GGG",
			"GGGG.GGGG",
			"GGGGGGGGG"),

	/** Hull upgrade: reinforced plating with a life emblem. */
	UPGRADE_HULL(Color.WHITE,
			"................",
			"..############..",
			".##..........##.",
			".#.#........#.#.",
			".#...##..##...#.",
			".#..########..#.",
			".#..########..#.",
			".#...######...#.",
			".#....####....#.",
			".#.....##.....#.",
			".#.#........#.#.",
			".##..........##.",
			"..############..",
			"................",
			"................",
			"................"),

	/** Engine upgrade: a thruster and its exhaust. */
	UPGRADE_ENGINE(Color.WHITE,
			"......####......",
			".....#....#.....",
			"....#..##..#....",
			"....#..##..#....",
			"...##......##...",
			"..#.#......#.#..",
			"..#.#......#.#..",
			"...##......##...",
			"....########....",
			".....#....#.....",
			".....######.....",
			"......#..#......",
			"......#..#......",
			".......##.......",
			".......##.......",
			"................"),

	/** Reloader upgrade: cartridges inside cycling arrows. */
	UPGRADE_RELOADER(Color.WHITE,
			".....######.....",
			"...##......##...",
			"..##........##..",
			".##..........##.",
			".##...#...#.####",
			".##..###.###.##.",
			".....#.#.#.#..#.",
			".....#.#.#.#....",
			"..#..#.#.#.#....",
			".##..#.#.#.#.##.",
			"####.###.###.##.",
			".##..........##.",
			"..##........##..",
			"...##......##...",
			".....######.....",
			"................"),

	/** Hangar: a hangar with a ship parked inside. */
	HANGAR("WB", new Color[] { GameColors.ICON_UI, GameColors.POWERUP_SHIELD },
			"...WWWWW...",
			"..WWWWWWW..",
			".WWWWWWWWW.",
			"WWWWWWWWWWW",
			"W.........W",
			"W....B....W",
			"W...BBB...W",
			"W..BBBBB..W",
			"W.........W"),

	/** Pause function. */
	PAUSE(GameColors.ICON_UI,
			"##...##",
			"##...##",
			"##...##",
			"##...##",
			"##...##",
			"##...##",
			"##...##"),

	/** Settings function. */
	SETTINGS(GameColors.ICON_UI,
			"...##...",
			".#.##.#.",
			".######.",
			"###..###",
			"###..###",
			".######.",
			".#.##.#.",
			"...##..."),

	/** Play: a rocket lifting off. */
	PLAY("WBRFO", new Color[] { GameColors.ICON_UI,
			GameColors.POWERUP_SHIELD, GameColors.ICON_LIFE,
			GameColors.EXPLOSION_ORANGE, GameColors.EXPLOSION_YELLOW },
			"....W....",
			"...WWW...",
			"...WBW...",
			"...WWW...",
			"..RWWWR..",
			".RRWWWRR.",
			".R.WWW.R.",
			"...FOF...",
			"....F...."),

	/** High scores: a trophy with a star inside. */
	HIGH_SCORES("GY", new Color[] { GameColors.ICON_COIN,
			GameColors.ICON_SCORE },
			"GGGGGGGGG",
			"G.GGYGG.G",
			"G.GYYYG.G",
			".GGGYGGG.",
			"..GGGGG..",
			"...GGG...",
			"....G....",
			"...GGG...",
			"..GGGGG.."),

	/** Records and achievements: a ringed planet. */
	RECORDS("PDR", new Color[] { GameColors.POWERUP_RAPID_FIRE,
			GameColors.ICON_PLANET_SHADE, GameColors.POWERUP_SHIELD },
			"....PPP....",
			"..PPPPPPP..",
			"..PPPPPPD.R",
			".RRRRRRRRR.",
			"R.PPPPPDD..",
			"..PPPPDDD..",
			"....DDD...."),

	/** Exit: a wormhole portal. */
	EXIT("PBW", new Color[] { GameColors.POWERUP_RAPID_FIRE,
			GameColors.POWERUP_SHIELD, GameColors.ICON_UI },
			"..PPPPP..",
			".PBBBBBP.",
			"PBB...BBP",
			"PB.....BP",
			"PB..W..BP",
			"PB.....BP",
			"PBB...BBP",
			".PBBBBBP.",
			"..PPPPP.."),

	/** Audio settings: a speaker with sound waves. */
	AUDIO("WB", new Color[] { GameColors.ICON_UI, GameColors.POWERUP_SHIELD },
			"...W......",
			"..WW...B..",
			"WWWW.B..B.",
			"WWWW.B..B.",
			"WWWW.B..B.",
			"WWWW.B..B.",
			"..WW...B..",
			"...W......"),

	/** Controls settings: a gamepad. */
	CONTROLS("WR", new Color[] { GameColors.ICON_UI, GameColors.ICON_LIFE },
			".WWWWWWWWW.",
			"WW.WWWWWRWW",
			"W...WWWR.RW",
			"WW.WWWWWRWW",
			"WWWWWWWWWWW",
			"WWW.....WWW",
			".W.......W."),

	/** Gameplay settings: a targeting reticle. */
	GAMEPLAY("RW", new Color[] { GameColors.ICON_LIFE, GameColors.ICON_UI },
			"..RRRRR..",
			".R..R..R.",
			"R...R...R",
			"R.......R",
			"RRR.W.RRR",
			"R.......R",
			"R...R...R",
			".R..R..R.",
			"..RRRRR.."),

	/** Back: an arrow pointing left. */
	BACK(GameColors.ICON_UI,
			"...#....",
			"..##....",
			".#######",
			"########",
			".#######",
			"..##....",
			"...#...."),

	/** Title screen hero ship. */
	TITLE_SHIP("WBGR", new Color[] { GameColors.ICON_UI,
			GameColors.POWERUP_SHIELD, GameColors.TEXT_DIM,
			GameColors.ICON_LIFE },
			"..........W..........",
			".........WWW.........",
			".........WBW.........",
			"........WWBWW........",
			"........WBBBW........",
			".......WWWWWWW.......",
			"...R...WWWWWWW...R...",
			"...R..WWGWWWGWW..R...",
			"..RRRWWWWWWWWWWWRRR..",
			".RRRRRWWWWWWWWWRRRRR.",
			"RRRR.WWWWWWWWWWW.RRRR",
			"RR...WWW.WWW.WWW...RR",
			".....GGG.GGG.GGG....."),

	/** Title ship engine flames, long frame. */
	TITLE_FLAME_LONG("YO", new Color[] { GameColors.EXPLOSION_YELLOW,
			GameColors.EXPLOSION_ORANGE },
			"......Y...Y...Y......",
			"......O...O...O......",
			"......O...O...O......"),

	/** Title ship engine flames, short frame. */
	TITLE_FLAME_SHORT("YO", new Color[] { GameColors.EXPLOSION_YELLOW,
			GameColors.EXPLOSION_ORANGE },
			"......Y...Y...Y......",
			"......O...O...O......",
			".....................");

	/** Pixel colors indexed [x][y]; null where the pixel is empty. */
	private final Color[][] pixels;

	Icon(final Color color, final String... rows) {
		this("#", new Color[] { color }, rows);
	}

	Icon(final String legend, final Color[] colors, final String... rows) {
		this.pixels = new Color[rows[0].length()][rows.length];
		for (int y = 0; y < rows.length; y++) {
			if (rows[y].length() != rows[0].length())
				throw new IllegalArgumentException(
						"Icon rows must all have the same width.");
			for (int x = 0; x < rows[y].length(); x++) {
				int key = legend.indexOf(rows[y].charAt(x));
				if (key >= 0)
					this.pixels[x][y] = colors[key];
			}
		}
	}

	/**
	 * @param x
	 *            Column of the pixel.
	 * @param y
	 *            Row of the pixel.
	 * @return Color of the pixel, or null if it is empty.
	 */
	public Color getColor(final int x, final int y) {
		return this.pixels[x][y];
	}

	/**
	 * @return Lit pixels of the icon, indexed [x][y].
	 */
	public boolean[][] getPixels() {
		boolean[][] lit = new boolean[getWidth()][getHeight()];
		for (int x = 0; x < getWidth(); x++)
			for (int y = 0; y < getHeight(); y++)
				lit[x][y] = this.pixels[x][y] != null;
		return lit;
	}

	/**
	 * @return Width of the icon in pixels, before scaling.
	 */
	public int getWidth() {
		return this.pixels.length;
	}

	/**
	 * @return Height of the icon in pixels, before scaling.
	 */
	public int getHeight() {
		return this.pixels[0].length;
	}
}
