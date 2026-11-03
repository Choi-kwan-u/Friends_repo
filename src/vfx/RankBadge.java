package vfx;

import java.awt.Color;

import engine.GameColors;

/**
 * Numbered badges for the high score table: gold, silver and bronze medals
 * for the top three, and a plain badge for the rest.
 */
public final class RankBadge {

	/** Badge width in pixels, before scaling. */
	public static final int WIDTH = 9;
	/** Badge height in pixels, before scaling. */
	public static final int HEIGHT = 12;

	/** Medal disc colors for ranks 1, 2 and 3. */
	private static final Color[] MEDALS = { GameColors.ICON_COIN,
			new Color(200, 205, 220), new Color(205, 125, 60) };

	/** Ribbon shape above the disc; 'L' and 'R' are its two halves. */
	private static final String[] RIBBON = {
			".LL...RR.",
			"..LL.RR..",
			"...LRR..." };

	/** Disc shape, drawn below the ribbon. */
	private static final String[] DISC = {
			"..#####..",
			".#######.",
			"#########",
			"#########",
			"#########",
			"#########",
			"#########",
			".#######.",
			"..#####.." };

	/** 3x5 digit glyphs, 0 to 9. */
	private static final String[][] DIGITS = {
			{ "###", "#.#", "#.#", "#.#", "###" },
			{ ".#.", "##.", ".#.", ".#.", "###" },
			{ "###", "..#", "###", "#..", "###" },
			{ "###", "..#", "###", "..#", "###" },
			{ "#.#", "#.#", "###", "..#", "..#" },
			{ "###", "#..", "###", "..#", "###" },
			{ "###", "#..", "###", "#.#", "###" },
			{ "###", "..#", "..#", "..#", "..#" },
			{ "###", "#.#", "###", "#.#", "###" },
			{ "###", "#.#", "###", "..#", "###" } };

	private RankBadge() {
	}

	/**
	 * Builds the badge for a position in the high score table.
	 *
	 * @param rank
	 *            Position, starting at 1. Ranks above 9 show their last digit.
	 * @return Pixel colors indexed [x][y]; null where empty.
	 */
	public static Color[][] forRank(final int rank) {
		boolean medal = rank >= 1 && rank <= MEDALS.length;
		Color disc = medal ? MEDALS[rank - 1] : GameColors.TEXT_DIM;
		Color[][] pixels = new Color[WIDTH][HEIGHT];

		if (medal)
			for (int y = 0; y < RIBBON.length; y++)
				for (int x = 0; x < WIDTH; x++) {
					char c = RIBBON[y].charAt(x);
					if (c == 'L')
						pixels[x][y] = GameColors.ICON_LIFE;
					else if (c == 'R')
						pixels[x][y] = GameColors.POWERUP_SHIELD;
				}

		int discTop = HEIGHT - DISC.length;
		for (int y = 0; y < DISC.length; y++)
			for (int x = 0; x < WIDTH; x++)
				if (DISC[y].charAt(x) == '#')
					pixels[x][discTop + y] = disc;

		String[] digit = DIGITS[Math.abs(rank) % 10];
		for (int y = 0; y < digit.length; y++)
			for (int x = 0; x < digit[y].length(); x++)
				if (digit[y].charAt(x) == '#')
					pixels[3 + x][discTop + 2 + y] = GameColors.BACKGROUND;

		return pixels;
	}
}
