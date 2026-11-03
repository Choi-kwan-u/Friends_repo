package entity.item;

import entity.Entity;
import entity.Ship;
import audio.AudioManager;
/**
 * Orbital Annihilator (Doomsday Laser). Once
 * caught (arm()), it can be triggered one time: 1.5 s of charge, then a 2 s
 * vertical piercing beam going up from the ship.
 */
public class OrbitalAnnihilator {

    public static final int CHARGE_DURATION = 1500;
    public static final int BEAM_DURATION = 2000;
    public static final int BEAM_WIDTH = 24;
    /** Level in which the item can drop. */
    public static final int REQUIRED_LEVEL = 7;

    private enum State { LOCKED, IDLE, CHARGING, FIRING }

    private State state;
    private long phaseStart;

    public OrbitalAnnihilator() {
        this.state = State.LOCKED;
        this.phaseStart = 0;
    }

    /** Unlocks the item, called when the player catches it. */
    public final void arm() {
        if (this.state == State.LOCKED)
            this.state = State.IDLE;
    }

    /** Triggers the item, if it is owned and ready. */
    public final boolean trigger() {
        if (this.state != State.IDLE)
            return false;
        this.state = State.CHARGING;
        this.phaseStart = System.currentTimeMillis();
        AudioManager.playSFX("audio/sfx/item_laser_charge.wav");
        return true;
    }

    /** Charging becomes firing; when the beam ends the item is consumed. */
    public final void update() {
        long elapsed = System.currentTimeMillis() - this.phaseStart;
        if (this.state == State.CHARGING && elapsed >= CHARGE_DURATION) {
            this.state = State.FIRING;
            this.phaseStart = System.currentTimeMillis();
            AudioManager.playSFX("audio/sfx/item_laser_beam.wav");
        } else if (this.state == State.FIRING && elapsed >= BEAM_DURATION) {
            this.state = State.LOCKED;
        }
    }

    /** Cancels a charge in progress (ship hit), the item is not lost. */
    public final void cancelCharge() {
        if (this.state == State.CHARGING) {
            AudioManager.playSFX("audio/sfx/item_laser_cancel.wav");
            this.state = State.IDLE;
        }
    }

    public final boolean isCharging() {
        return this.state == State.CHARGING;
    }

    public final boolean isFiring() {
        return this.state == State.FIRING;
    }

    /** True if the item is owned and can be triggered. */
    public final boolean isReady() {
        return this.state == State.IDLE;
    }

    /** Progress of the charging phase, between 0 and 1. */
    public final float getChargeProgress() {
        if (this.state != State.CHARGING)
            return this.state == State.FIRING ? 1f : 0f;
        float progress = (System.currentTimeMillis() - this.phaseStart)
                / (float) CHARGE_DURATION;
        return Math.min(1f, progress);
    }

    public static int getBeamLeft(final Ship ship) {
        return ship.getPositionX() + ship.getWidth() / 2 - BEAM_WIDTH / 2;
    }

    public static int getBeamRight(final Ship ship) {
        return getBeamLeft(ship) + BEAM_WIDTH;
    }

    /** True if the entity is inside the beam corridor (above the ship). */
    public static boolean hits(final Ship ship, final Entity target) {
        return target.getPositionX() < getBeamRight(ship)
                && target.getPositionX() + target.getWidth() > getBeamLeft(ship)
                && target.getPositionY() < ship.getPositionY();
    }
}
