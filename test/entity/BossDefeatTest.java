package entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import boss.BossAttackSystem;
import boss.BossManager;
import boss.BossSpawnRequest;
import boss.BossStatus;

/**
 * Standalone checks for the third completion condition of R4: a defeated
 * boss stops moving and attacking and does not respawn.
 *
 * All three are driven by the boss entering its DEFEATED state, so each one
 * is checked on its own rather than through that state alone. A boss that
 * kept moving or shooting after its health reached zero would otherwise go
 * unnoticed, since its sprite already changes to an explosion.
 */
public final class BossDefeatTest {

	/** Stage the test bosses are spawned on. */
	private static final int LEVEL = 3;
	/** Frames run when checking that nothing happens any more. */
	private static final int FRAMES = 20;

	private BossDefeatTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		for (Boss.BossType type : Boss.BossType.values()) {
			checkALivingBossMoves(type);
			checkZeroHealthDefeatsTheBoss(type);
			checkADefeatedBossStopsMoving(type);
			checkADefeatedBossCannotBeRevived(type);
			checkADefeatedBossStopsAttacking(type);
			checkADefeatedBossDoesNotRespawn(type);
		}

		System.out.println("Boss defeat checks passed.");
	}

	/**
	 * The control case. Without it every check below would also pass on a
	 * boss that never moved in the first place.
	 *
	 * @param type
	 *            Kind of boss to move.
	 */
	private static void checkALivingBossMoves(final Boss.BossType type) {
		Boss boss = newBoss(type);
		int start = boss.getPositionX();

		boss.update(1, 447);
		check(boss.getPositionX() != start, type + " moves while it is alive");
	}

	/** Reaching zero health is what defeats a boss. */
	private static void checkZeroHealthDefeatsTheBoss(
			final Boss.BossType type) {
		Boss boss = newBoss(type);

		boss.takeDamage(type.getMaxHealth() - 1);
		check(!boss.isDefeated(),
				type + " is not defeated while it has health left");

		boss.takeDamage(1);
		check(boss.getCurrentHealth() == 0, type + " has no health left");
		check(boss.isDefeated(), type + " is defeated at zero health");
		check(boss.getState() == Boss.State.DEFEATED,
				type + " is in the defeated state at zero health");
	}

	/**
	 * A defeated boss holds its position, however many frames run.
	 *
	 * @param type
	 *            Kind of boss to defeat.
	 */
	private static void checkADefeatedBossStopsMoving(
			final Boss.BossType type) {
		Boss boss = defeated(type);
		int resting = boss.getPositionX();

		for (int frame = 0; frame < FRAMES; frame++)
			boss.update(1, 447);
		check(boss.getPositionX() == resting,
				type + " does not move once defeated");
		check(boss.getState() == Boss.State.DEFEATED,
				type + " stays defeated across its update");
	}

	/**
	 * Nothing may take a defeated boss back out of its state: not a state
	 * change asked for by the attack system, and not a further hit.
	 *
	 * @param type
	 *            Kind of boss to defeat.
	 */
	private static void checkADefeatedBossCannotBeRevived(
			final Boss.BossType type) {
		Boss boss = defeated(type);

		boss.setState(Boss.State.ATTACKING);
		check(boss.getState() == Boss.State.DEFEATED,
				type + " cannot be put back into an attacking state");
		boss.setState(Boss.State.MOVING);
		check(boss.getState() == Boss.State.DEFEATED,
				type + " cannot be put back into a moving state");

		boss.takeDamage(type.getMaxHealth());
		check(boss.getCurrentHealth() == 0,
				type + " takes no further damage once defeated");
		check(boss.isDefeated(), type + " is still defeated after a late hit");
	}

	/**
	 * Defeating a boss shuts its attack system down, so no new boss bullet
	 * can reach the player afterwards.
	 *
	 * @param type
	 *            Kind of boss to defeat.
	 */
	private static void checkADefeatedBossStopsAttacking(
			final Boss.BossType type) {
		BossManager manager = new BossManager(new Random(3));
		Boss boss = manager.spawn(request(type));
		check(boss != null, type + " was spawned");
		BossAttackSystem attacks = manager.getAttackSystem();
		check(attacks.isActive(), type + " attacks while it is alive");

		boss.takeDamage(type.getMaxHealth());
		manager.sync();

		check(!attacks.isActive(), type + " stops attacking once defeated");
		check(manager.getStatus().getBattleState()
				== BossStatus.BattleState.ENDED,
				type + " ends its battle once defeated");
		check(manager.getStatus().isDefeated(),
				type + " reports itself as defeated to the other systems");
		check(!manager.getStatus().isPresent(),
				type + " is no longer a boss the stage has to wait for");

		List<BossBullet> fired = new ArrayList<BossBullet>();
		for (int frame = 0; frame < FRAMES; frame++)
			manager.update(200, 500, fired);
		check(fired.isEmpty(), type + " fires nothing once defeated");
	}

	/**
	 * A defeated boss does not come back on its own stage, while a later
	 * stage's boss can still appear.
	 *
	 * @param type
	 *            Kind of boss to defeat.
	 */
	private static void checkADefeatedBossDoesNotRespawn(
			final Boss.BossType type) {
		BossManager manager = new BossManager(new Random(5));
		Boss boss = manager.spawn(request(type));
		check(boss != null, type + " was spawned");

		boss.takeDamage(type.getMaxHealth());
		manager.sync();

		check(manager.spawn(request(type)) == null,
				type + " does not respawn on the stage it was defeated on");
		check(manager.getBoss() == boss,
				type + " is still the one defeated boss after the refusal");

		// A different stage is a different boss, so it is allowed to appear.
		Boss later = manager.spawn(new BossSpawnRequest(type, 100, 78,
				LEVEL + 2, 0));
		check(later != null && later != boss,
				type + " may still appear on a later stage");
		check(!later.isDefeated(), type + " appears alive on a later stage");
	}

	/**
	 * @param type
	 *            Kind of boss to create.
	 * @return A boss already brought down to zero health.
	 */
	private static Boss defeated(final Boss.BossType type) {
		Boss boss = newBoss(type);
		boss.takeDamage(type.getMaxHealth());
		check(boss.isDefeated(), type + " was defeated");
		return boss;
	}

	private static Boss newBoss(final Boss.BossType type) {
		return new Boss(100, 78, type);
	}

	private static BossSpawnRequest request(final Boss.BossType type) {
		return new BossSpawnRequest(type, 100, 78, LEVEL, 0);
	}
}
