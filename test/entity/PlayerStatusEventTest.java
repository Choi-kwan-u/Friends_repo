package entity;

import java.util.ArrayList;
import java.util.List;

/** Deterministic checks for the player status events. */
public final class PlayerStatusEventTest {

	/**
	 * Player status driven by a clock the test moves by hand, so the shield
	 * expiry event is reached without waiting for real time.
	 */
	private static final class FakeClockStatus extends PlayerStatus {

		/** Current value of the fake clock, in milliseconds. */
		private long now;

		private FakeClockStatus(final int initialLives, final int maxLives) {
			super(initialLives, maxLives, true);
			this.now = 1000000L;
		}

		@Override
		long currentTimeMillis() {
			return this.now;
		}

		/**
		 * Moves the fake clock forward.
		 *
		 * @param milliseconds
		 *            Amount of time to advance.
		 */
		private void advance(final long milliseconds) {
			this.now += milliseconds;
		}
	}

	/** Listener that keeps every event it receives, in order. */
	private static final class Recorder implements PlayerStatusListener {

		/** Events received so far. */
		private final List<PlayerStatusEvent> events =
				new ArrayList<PlayerStatusEvent>();

		@Override
		public void onPlayerStatusEvent(final PlayerStatusEvent event) {
			this.events.add(event);
		}

		/** @return Kinds received so far, as one comma-separated string. */
		private String kinds() {
			StringBuilder text = new StringBuilder();
			for (PlayerStatusEvent event : this.events) {
				if (text.length() > 0)
					text.append(',');
				text.append(event.getType().name());
			}
			return text.toString();
		}

		/** @return The only event received, failing if there is not exactly
		 *          one. */
		private PlayerStatusEvent single() {
			check(this.events.size() == 1,
					"Expected exactly one event but got [" + kinds() + "]");
			return this.events.get(0);
		}

		private void clear() {
			this.events.clear();
		}
	}

	private PlayerStatusEventTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	/**
	 * Fails unless the recorder received exactly the given kinds, in order.
	 *
	 * @param recorder
	 *            Recorder to inspect.
	 * @param expected
	 *            Comma-separated kinds, as named by the enum.
	 */
	private static void checkKinds(final Recorder recorder,
			final String expected) {
		check(expected.equals(recorder.kinds()),
				"Expected [" + expected + "] but got [" + recorder.kinds()
						+ "]");
	}

	public static void main(final String[] args) {
		checkDamageEvent();
		checkBlockedDamageEvent();
		checkLifeEvents();
		checkShieldStartAndRefresh();
		checkShieldExpiryOnUpdate();
		checkShieldExpiryBeforeDamage();
		checkShieldExpiryBeforeNewShield();
		checkClearShieldEvent();
		checkSlowStartRefreshAndExpiry();
		checkSlowGettersAreReadOnlyAtExpiry();
		checkSlowBlockAndShieldClear();
		checkSlowEndsOnDeath();
		checkExistingEventsSnapshotSlowState();
		checkIgnoredChangesAreSilent();
		checkEventPayload();
		checkListenerRegistration();

		System.out.println("Player status event checks passed.");
	}

	/** A hit that gets through is announced with the lives left. */
	private static void checkDamageEvent() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		check(status.takeDamage(1), "The hit applies");
		checkKinds(recorder, "DAMAGE_TAKEN");
		check(recorder.single().getLives() == 2,
				"The event carries the lives left after the hit");
		check(recorder.single().getDamageAmount() == 1,
				"The event carries the damage");
		check(recorder.single().isAlive(), "Two lives left is alive");
	}

	/** A hit the shield absorbs is announced as its own kind. */
	private static void checkBlockedDamageEvent() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		status.activateShield();

		Recorder recorder = new Recorder();
		status.addListener(recorder);

		check(!status.takeDamage(50), "The shield absorbs the hit");
		checkKinds(recorder, "DAMAGE_BLOCKED");
		check(recorder.single().getLives() == 3, "No life was lost");
		check(recorder.single().getDamageAmount() == 50,
				"The blocked event still carries the damage");
		check(recorder.single().getShieldRemainingMillis() > 0,
				"The shield is still up after absorbing");
	}

	/** A granted life is announced; one refused at the maximum is not. */
	private static void checkLifeEvents() {
		PlayerStatus status = new FakeClockStatus(2, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		check(status.gainLife(), "A life is granted below the maximum");
		checkKinds(recorder, "LIFE_GAINED");
		check(recorder.single().getLives() == 3,
				"The event carries the new total");

		recorder.clear();
		check(!status.gainLife(), "No life is granted at the maximum");
		checkKinds(recorder, "");
	}

	/** The first shield starts, a second one refreshes. */
	private static void checkShieldStartAndRefresh() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		status.activateShield();
		checkKinds(recorder, "SHIELD_STARTED");
		check(recorder.single().getShieldRemainingMillis()
				== PlayerStatus.getShieldDuration(),
				"A new shield is announced at its full duration");

		recorder.clear();
		status.advance(1000);
		status.activateShield();
		checkKinds(recorder, "SHIELD_REFRESHED");
		check(recorder.single().getShieldRemainingMillis()
				== PlayerStatus.getShieldDuration(),
				"A refreshed shield is announced at its full duration");
	}

	/** The frame tick announces an expired shield exactly once. */
	private static void checkShieldExpiryOnUpdate() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.activateShield();

		Recorder recorder = new Recorder();
		status.addListener(recorder);

		// Still up on the last millisecond, so nothing is announced yet.
		status.advance(PlayerStatus.getShieldDuration() - 1);
		status.update();
		checkKinds(recorder, "");

		status.advance(1);
		status.update();
		checkKinds(recorder, "SHIELD_ENDED");
		check(recorder.single().getShieldRemainingMillis() == 0,
				"An ended shield reports no remaining time");

		// Later frames do not repeat it.
		recorder.clear();
		status.update();
		status.advance(5000);
		status.update();
		checkKinds(recorder, "");
	}

	/** The end of a shield is never announced after the hit that follows it. */
	private static void checkShieldExpiryBeforeDamage() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.activateShield();

		Recorder recorder = new Recorder();
		status.addListener(recorder);

		// No tick in between: the hit itself has to report the expiry first.
		status.advance(PlayerStatus.getShieldDuration());
		check(status.takeDamage(1), "The hit applies after expiry");
		checkKinds(recorder, "SHIELD_ENDED,DAMAGE_TAKEN");
	}

	/** A shield taken after one expired reports the end before the start. */
	private static void checkShieldExpiryBeforeNewShield() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		status.activateShield();

		Recorder recorder = new Recorder();
		status.addListener(recorder);

		status.advance(PlayerStatus.getShieldDuration());
		status.activateShield();
		checkKinds(recorder, "SHIELD_ENDED,SHIELD_STARTED");
	}

	/** clearShield announces the end, but only when a shield was up. */
	private static void checkClearShieldEvent() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		status.clearShield();
		checkKinds(recorder, "");

		status.activateShield();
		recorder.clear();
		status.clearShield();
		checkKinds(recorder, "SHIELD_ENDED");

		// A second call has nothing left to end.
		recorder.clear();
		status.clearShield();
		checkKinds(recorder, "");
	}

	/** Slow lifecycle events carry one immutable state snapshot each. */
	private static void checkSlowStartRefreshAndExpiry() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);
		Ship ship = new Ship(100, 100);

		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.STARTED,
				"The initial slow starts");
		checkKinds(recorder, "SLOW_STARTED");
		PlayerStatusEvent started = recorder.single();
		check(started.isSlowActive()
				&& started.getSlowRemainingMillis()
						== PlayerStatus.getSlowDuration(),
				"SLOW_STARTED carries the active state and full duration");
		check(started.getLives() == 3 && started.getMaxLives() == 3
				&& started.getShieldRemainingMillis() == 0,
				"Slow events retain the existing life and shield snapshot");

		recorder.clear();
		status.advance(1000);
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.REFRESHED,
				"A second slow refreshes the first");
		checkKinds(recorder, "SLOW_REFRESHED");
		check(recorder.single().isSlowActive()
				&& recorder.single().getSlowRemainingMillis()
						== PlayerStatus.getSlowDuration(),
				"SLOW_REFRESHED carries the restarted duration");

		recorder.clear();
		status.advance(PlayerStatus.getSlowDuration() - 1);
		status.update();
		checkKinds(recorder, "");
		status.advance(1);
		status.update();
		checkKinds(recorder, "SLOW_ENDED");
		PlayerStatusEvent ended = recorder.single();
		check(!ended.isSlowActive()
				&& ended.getSlowRemainingMillis() == 0,
				"SLOW_ENDED records an inactive state and zero time");

		recorder.clear();
		status.update();
		checkKinds(recorder, "");
	}

	/** Reading expired slow state must not perform its transition or publish. */
	private static void checkSlowGettersAreReadOnlyAtExpiry() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		Ship ship = new Ship(100, 100);
		status.addListener(recorder);
		status.applySlow(ship);
		recorder.clear();

		status.advance(PlayerStatus.getSlowDuration());
		check(!status.isSlowActive(),
				"The slow getter reports inactive at the expiry boundary");
		check(status.getSlowRemainingMillis() == 0,
				"The remaining-time getter reports zero at expiry");
		checkKinds(recorder, "");
		ship.moveRight();
		check(ship.getPositionX() == 101,
				"A getter does not clear the ship's slow modifier");

		status.update();
		checkKinds(recorder, "SLOW_ENDED");
		ship.moveRight();
		check(ship.getPositionX() == 103,
				"The update transition restores normal movement");
		status.update();
		checkKinds(recorder, "SLOW_ENDED");
	}

	/** A shield blocks new slow events and clears an active slow once. */
	private static void checkSlowBlockAndShieldClear() {
		FakeClockStatus status = new FakeClockStatus(3, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);
		Ship ship = new Ship(100, 100);

		status.activateShield();
		recorder.clear();
		check(status.applySlow(ship)
				== PlayerStatus.SlowApplicationResult.BLOCKED_BY_SHIELD,
				"The active shield blocks a new slow");
		checkKinds(recorder, "");

		status.clearShield();
		status.applySlow(ship);
		recorder.clear();
		status.activateShield();
		checkKinds(recorder, "SHIELD_STARTED");
		recorder.clear();
		status.update();
		checkKinds(recorder, "SLOW_ENDED");
		check(!recorder.single().isSlowActive()
				&& recorder.single().getSlowRemainingMillis() == 0,
				"Shield-cleared slow reports its ended state");

		recorder.clear();
		status.update();
		checkKinds(recorder, "");
	}

	/** Losing the last life immediately ends slow after the damage event. */
	private static void checkSlowEndsOnDeath() {
		FakeClockStatus status = new FakeClockStatus(1, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);
		Ship ship = new Ship(100, 100);
		status.applySlow(ship);
		recorder.clear();

		check(status.takeDamage(1), "The last life is lost");
		checkKinds(recorder, "DAMAGE_TAKEN,SLOW_ENDED");
		check(!recorder.events.get(1).isSlowActive()
				&& recorder.events.get(1).getSlowRemainingMillis() == 0,
				"Death ends slow with a zero-duration snapshot");
		recorder.clear();
		status.update();
		checkKinds(recorder, "");
	}

	/** Existing event kinds also carry the slow state at their creation time. */
	private static void checkExistingEventsSnapshotSlowState() {
		FakeClockStatus status = new FakeClockStatus(2, 4);
		Recorder recorder = new Recorder();
		Ship ship = new Ship(100, 100);
		status.addListener(recorder);
		status.applySlow(ship);
		recorder.clear();

		status.advance(500);
		check(status.gainLife(), "A life is gained while slow is active");
		checkKinds(recorder, "LIFE_GAINED");
		checkSlowSnapshot(recorder.single(), 1500,
				"LIFE_GAINED retains the active slow snapshot");

		recorder.clear();
		check(status.takeDamage(1), "Damage is accepted while slow is active");
		checkKinds(recorder, "DAMAGE_TAKEN");
		checkSlowSnapshot(recorder.single(), 1500,
				"DAMAGE_TAKEN retains the active slow snapshot");

		recorder.clear();
		status.activateShield();
		checkKinds(recorder, "SHIELD_STARTED");
		checkSlowSnapshot(recorder.single(), 0,
				"SHIELD_STARTED snapshots slow as inactive while shielded");

		recorder.clear();
		status.update();
		checkKinds(recorder, "SLOW_ENDED");
		checkSlowSnapshot(recorder.single(), 0,
				"SLOW_ENDED always snapshots inactive with zero time");
	}

	private static void checkSlowSnapshot(final PlayerStatusEvent event,
			final int expectedRemaining, final String message) {
		check(event.isSlowActive() == (expectedRemaining > 0)
				&& event.getSlowRemainingMillis() == expectedRemaining, message);
	}

	/** Calls that change nothing send nothing. */
	private static void checkIgnoredChangesAreSilent() {
		PlayerStatus status = new FakeClockStatus(1, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		check(!status.takeDamage(0), "Zero damage is ignored");
		check(!status.takeDamage(-1), "Negative damage is ignored");
		checkKinds(recorder, "");

		check(status.takeDamage(1), "The last life is spent");
		checkKinds(recorder, "DAMAGE_TAKEN");
		check(!recorder.single().isAlive(),
				"The event that spends the last life reports a dead player");

		recorder.clear();
		check(!status.takeDamage(1), "A dead player takes no more damage");
		check(!status.gainLife(), "A dead player cannot be revived");
		checkKinds(recorder, "");
	}

	/** Every event carries the whole player part of the HUD. */
	private static void checkEventPayload() {
		FakeClockStatus status = new FakeClockStatus(2, 3);
		Recorder recorder = new Recorder();
		status.addListener(recorder);

		status.activateShield();
		PlayerStatusEvent event = recorder.single();
		check(event.getType() == PlayerStatusEvent.Type.SHIELD_STARTED,
				"The kind is readable");
		check(event.getLives() == 2, "Lives are carried");
		check(event.getMaxLives() == 3, "Maximum lives are carried");
		check(event.getDamageAmount() == 0,
				"A shield event carries no damage");
		check(event.toString().contains("PlayerShieldStarted"),
				"The log line names the event");
		check(event.toString().contains("2/3"),
				"The log line carries the lives");
	}

	/** Registration rules: no duplicates, no nulls, removal works. */
	private static void checkListenerRegistration() {
		PlayerStatus status = new FakeClockStatus(3, 3);
		final Recorder recorder = new Recorder();

		status.addListener(null);
		status.addListener(recorder);
		status.addListener(recorder);

		status.activateShield();
		checkKinds(recorder, "SHIELD_STARTED");

		recorder.clear();
		status.removeListener(recorder);
		status.clearShield();
		checkKinds(recorder, "");

		// A listener that removes itself while being called must not break
		// the delivery that is under way.
		final PlayerStatus selfRemoving = new FakeClockStatus(3, 3);
		selfRemoving.addListener(new PlayerStatusListener() {
			@Override
			public void onPlayerStatusEvent(final PlayerStatusEvent event) {
				selfRemoving.removeListener(this);
				recorder.onPlayerStatusEvent(event);
			}
		});
		selfRemoving.activateShield();
		selfRemoving.clearShield();
		checkKinds(recorder, "SHIELD_STARTED");
	}
}
