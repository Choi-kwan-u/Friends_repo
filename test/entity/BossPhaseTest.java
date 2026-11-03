package entity;

import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import boss.AttackPattern;
import boss.BossAttackConfig;
import boss.BossAttackSystem;
import boss.BossManager;
import boss.BossSpawnRequest;

/**
 * Standalone checks for the second completion condition of R4: the attack
 * pattern changes as the boss loses HP.
 *
 * The phase is what ties the two together — the boss raises it from its
 * remaining health, and the attack system is told about it — so the checks
 * follow that chain from a hit through to the attack setup in use. Without
 * them a boss could keep its opening attack for the whole battle.
 */
public final class BossPhaseTest {

	/** Number of draws used to collect the patterns a phase can produce. */
	private static final int PATTERN_SAMPLES = 500;

	private BossPhaseTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		for (Boss.BossType type : Boss.BossType.values()) {
			checkABossOpensInItsFirstPhase(type);
			checkThePhaseFollowsTheRemainingHealth(type);
			checkThePhaseNeverGoesBack(type);
			checkEachPhaseHasItsOwnAttackSetup(type);
			checkLosingHealthChangesTheAttackInUse(type);
		}

		System.out.println("Boss phase checks passed.");
	}

	/** At full health a boss is in phase 1 and moves at its base speed. */
	private static void checkABossOpensInItsFirstPhase(
			final Boss.BossType type) {
		Boss boss = newBoss(type);

		check(boss.getPhase() == 1, type + " opens in phase 1");
		check(boss.getCurrentHealth() == type.getMaxHealth(),
				type + " opens at full health");
		check(boss.getSpeed() == type.getSpeed(),
				type + " opens at its base speed");
	}

	/**
	 * The phase is driven by the remaining health, at the thresholds this
	 * kind of boss declares. Each threshold is approached from both sides,
	 * so a phase that never changes and one that changes too early are both
	 * caught.
	 *
	 * @param type
	 *            Kind of boss to damage.
	 */
	private static void checkThePhaseFollowsTheRemainingHealth(
			final Boss.BossType type) {
		Boss boss = newBoss(type);

		// Just above the phase 2 threshold: still the opening phase.
		damageDownTo(boss, type.getPhase2Threshold() + 0.02);
		check(boss.getPhase() == 1,
				type + " is still in phase 1 above its phase 2 threshold");
		check(boss.getSpeed() == type.getSpeed(),
				type + " still moves at its base speed in phase 1");

		// Below the phase 2 threshold but above the phase 3 one.
		damageDownTo(boss, type.getPhase2Threshold() - 0.02);
		check(boss.getPhase() == 2,
				type + " enters phase 2 below its phase 2 threshold");
		check(boss.getSpeed() > type.getSpeed(),
				type + " moves faster in phase 2 than at its base speed");

		int phase2Speed = boss.getSpeed();
		damageDownTo(boss, type.getPhase3Threshold() + 0.02);
		check(boss.getPhase() == 2,
				type + " is still in phase 2 above its phase 3 threshold");

		// Below the phase 3 threshold: the final phase.
		damageDownTo(boss, type.getPhase3Threshold() - 0.02);
		check(boss.getPhase() == 3,
				type + " enters phase 3 below its phase 3 threshold");
		check(boss.getSpeed() > phase2Speed,
				type + " moves faster in phase 3 than in phase 2");
		check(boss.getCurrentHealth() > 0,
				type + " is still alive in phase 3");
	}

	/**
	 * A phase is reached once and kept. Healing a boss must not hand it back
	 * its gentler opening attacks.
	 *
	 * @param type
	 *            Kind of boss to damage and then heal.
	 */
	private static void checkThePhaseNeverGoesBack(final Boss.BossType type) {
		Boss boss = newBoss(type);
		damageDownTo(boss, type.getPhase3Threshold() - 0.02);
		check(boss.getPhase() == 3, type + " reached phase 3");
		int finalSpeed = boss.getSpeed();

		// Negative damage is healing; the health is clamped at the maximum.
		boss.takeDamage(-type.getMaxHealth());
		check(boss.getCurrentHealth() == type.getMaxHealth(),
				type + " is back at full health");
		check(boss.getPhase() == 3, type + " stays in phase 3 once healed");
		check(boss.getSpeed() == finalSpeed,
				type + " keeps its phase 3 speed once healed");
	}

	/**
	 * The three phases of a boss must really ask for different attacks, or
	 * raising the phase would change nothing the player can see.
	 *
	 * @param type
	 *            Kind of boss whose attack data is read.
	 */
	private static void checkEachPhaseHasItsOwnAttackSetup(
			final Boss.BossType type) {
		BossAttackConfig config = BossAttackConfig.forType(type);
		BossAttackConfig.PhaseConfig phase1 = config.getPhase(1);
		BossAttackConfig.PhaseConfig phase2 = config.getPhase(2);
		BossAttackConfig.PhaseConfig phase3 = config.getPhase(3);

		// R4 asks for three attack patterns and one special attack kept for
		// the final phase.
		check(config.getSpecialAttack() != null,
				type + " has a special attack");
		check(!phase1.isSpecialEnabled(),
				type + " keeps its special attack out of phase 1");
		check(!phase2.isSpecialEnabled(),
				type + " keeps its special attack out of phase 2");
		check(phase3.isSpecialEnabled(),
				type + " saves its special attack for the final phase");

		// A later phase attacks at least as often as the one before it.
		check(phase2.getIntervalMultiplier() < phase1.getIntervalMultiplier(),
				type + " attacks more often in phase 2 than in phase 1");
		check(phase3.getIntervalMultiplier() < phase2.getIntervalMultiplier(),
				type + " attacks more often in phase 3 than in phase 2");

		Set<String> opening = patternsOf(phase1);
		Set<String> closing = patternsOf(phase3);
		check(!opening.isEmpty() && !closing.isEmpty(),
				type + " has patterns in its first and last phase");
		check(!opening.equals(closing), type + " fires different patterns in "
				+ "phase 3 (" + closing + ") than in phase 1 (" + opening
				+ ")");
	}

	/**
	 * The whole chain R4 commits to: the boss is hit, its phase rises from
	 * the health it has left, and the attack system it fights with switches
	 * to that phase's setup.
	 *
	 * @param type
	 *            Kind of boss to spawn and damage.
	 */
	private static void checkLosingHealthChangesTheAttackInUse(
			final Boss.BossType type) {
		BossManager manager = new BossManager(new Random(1));
		Boss boss = manager.spawn(new BossSpawnRequest(type, 100, 78, 3, 0));
		check(boss != null, type + " was spawned");
		BossAttackSystem attacks = manager.getAttackSystem();

		check(attacks.getPhase() == 1,
				type + " starts the battle on its phase 1 attacks");
		int openingInterval = attacks.getCurrentIntervalMilliseconds();

		damageDownTo(boss, type.getPhase2Threshold() - 0.02);
		manager.sync();
		check(attacks.getPhase() == 2,
				type + " switches to its phase 2 attacks as its HP drops");
		int middleInterval = attacks.getCurrentIntervalMilliseconds();
		check(middleInterval < openingInterval,
				type + " shortens its attack interval in phase 2");

		damageDownTo(boss, type.getPhase3Threshold() - 0.02);
		manager.sync();
		check(attacks.getPhase() == 3,
				type + " switches to its phase 3 attacks as its HP drops");
		check(attacks.getCurrentIntervalMilliseconds() < middleInterval,
				type + " shortens its attack interval again in phase 3");
	}

	/**
	 * Collects the names of every pattern a phase can draw, so two phases
	 * can be compared by what they actually fire.
	 *
	 * @param phase
	 *            Phase setup to sample.
	 * @return Sorted names of its patterns.
	 */
	private static Set<String> patternsOf(
			final BossAttackConfig.PhaseConfig phase) {
		Set<String> names = new TreeSet<String>();
		Random random = new Random(7);
		for (int draw = 0; draw < PATTERN_SAMPLES; draw++) {
			AttackPattern pattern = phase.pick(random);
			names.add(pattern.getName());
		}
		return names;
	}

	/**
	 * Damages a boss until the given share of its health is left. The share
	 * is read from the boss type's own thresholds, so the checks follow the
	 * team's tuning instead of repeating its numbers.
	 *
	 * @param boss
	 *            Boss to damage.
	 * @param healthRatio
	 *            Share of maximum health to leave.
	 */
	private static void damageDownTo(final Boss boss,
			final double healthRatio) {
		int target = (int) Math.round(healthRatio * boss.getMaxHealth());
		boss.takeDamage(boss.getCurrentHealth() - target);
	}

	private static Boss newBoss(final Boss.BossType type) {
		return new Boss(100, 78, type);
	}
}
