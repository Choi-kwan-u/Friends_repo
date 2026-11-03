package screen;

import entity.Boss;

/**
 * Standalone checks that both boss types are reachable in play. Boss B was
 * implemented but never spawned, because every boss stage asked for Boss A.
 */
public final class BossTypeByLevelTest {

	private BossTypeByLevelTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkTheFirstBossStageOpensWithTheSlowBoss();
		checkLaterBossStagesUseTheFastBoss();
		checkBothTypesDifferInMoreThanHealth();

		System.out.println("Boss type by level checks passed.");
	}

	/**
	 * Read from GameScreen.BOSS_LEVELS rather than hardcoded, so the test
	 * follows the boss stages if the team changes them.
	 */
	private static void checkTheFirstBossStageOpensWithTheSlowBoss() {
		int first = GameScreen.firstBossLevel();
		check(GameScreen.bossTypeForLevel(first) == Boss.BossType.BOSS_A,
				"The first boss stage (" + first + ") uses the slow boss");
	}

	private static void checkLaterBossStagesUseTheFastBoss() {
		int first = GameScreen.firstBossLevel();
		for (int level : GameScreen.BOSS_LEVELS)
			if (level != first)
				check(GameScreen.bossTypeForLevel(level)
						== Boss.BossType.BOSS_B,
						"Boss stage " + level + " uses the fast boss");
	}

	/**
	 * R4 asks for bosses with difficulty levels, so the two must differ in
	 * how they play and not only in the size of their health bar.
	 */
	private static void checkBothTypesDifferInMoreThanHealth() {
		Boss.BossType slow = Boss.BossType.BOSS_A;
		Boss.BossType fast = Boss.BossType.BOSS_B;

		check(slow.getMaxHealth() != fast.getMaxHealth(),
				"The two bosses differ in health");
		check(slow.getSpeed() != fast.getSpeed(),
				"The two bosses differ in speed");
	}
}
