package vfx;

import entity.ShipType;

/** Checks the UI icons and custom ship sprites are well-formed. */
public final class IconTest {

	private IconTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		check(Icon.values().length == 23,
				"HUD, menu, settings, title, permanent upgrade and lock icons are registered");

		for (Icon icon : Icon.values()) {
			check(icon.getWidth() > 0 && icon.getHeight() > 0,
					icon + " has a size");

			int lit = 0;
			for (boolean[] column : icon.getPixels()) {
				check(column.length == icon.getHeight(),
						icon + " columns all have the same height");
				for (boolean pixel : column)
					if (pixel)
						lit++;
			}
			check(lit > 0, icon + " has at least one lit pixel");
		}

		check(Icon.LIFE.getPixels()[0][1], "Heart left lobe is lit");
		check(!Icon.LIFE.getPixels()[0][5], "Heart tip is narrow");
		check(!Icon.PAUSE.getPixels()[3][3], "Pause icon has a gap");
		check(!Icon.LIFE_LOST.getPixels()[3][1], "Broken heart has a crack");
		check(Icon.PLAY.getColor(4, 2) != Icon.PLAY.getColor(4, 0),
				"Rocket window differs from its body");

		for (ShipType type : ShipType.values()) {
			boolean[][] sprite = ShipSprites.forType(type);
			check(sprite.length == 13 && sprite[0].length == 8,
					type + " sprite matches the 13x8 ship hitbox");
		}
		java.awt.Color[][] bullet = ShipSprites.playerBullet();
		check(bullet.length == 3 && bullet[0].length == 5,
				"Player bullet matches the 3x5 bullet hitbox");
		java.awt.Color[][] enemyBullet = ShipSprites.enemyBullet();
		check(enemyBullet.length == 3 && enemyBullet[0].length == 5,
				"Enemy bullet matches the 3x5 bullet hitbox");

		for (int rank = 1; rank <= 7; rank++) {
			java.awt.Color[][] badge = RankBadge.forRank(rank);
			check(badge.length == RankBadge.WIDTH
					&& badge[0].length == RankBadge.HEIGHT,
					"Rank " + rank + " badge has the badge size");
		}
		check(RankBadge.forRank(1)[1][0] != null,
				"Top three medals have a ribbon");
		check(RankBadge.forRank(4)[1][0] == null,
				"Other ranks have no ribbon");
		check(!RankBadge.forRank(1)[5][6].equals(RankBadge.forRank(2)[5][6]),
				"Gold and silver medals differ");

		System.out.println("Icon checks passed.");
	}
}
