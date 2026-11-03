package entity;

/**
 * Defines the available player ship types and their gameplay attributes.
 */
public enum ShipType {

    STANDARD("Standard Ship", 2, 750, 4, false),
    SPEEDSTER("Speedster", 4, 400, 3, false),
    TANK("Heavy Tank", 1, 1000, 5, true);

    /** Name displayed to the player. */
    private final String name;

    /** Horizontal movement speed. */
    private final int speed;

    /** Minimum time between shots in milliseconds. */
    private final int shootingInterval;

    /** Maximum number of lives. */
    private final int maxLives;

    /** Whether this ship supports a shield. */
    private final boolean shieldCapable;

    ShipType(final String name,
            final int speed,
            final int shootingInterval,
            final int maxLives,
            final boolean shieldCapable) {

        this.name = name;
        this.speed = speed;
        this.shootingInterval = shootingInterval;
        this.maxLives = maxLives;
        this.shieldCapable = shieldCapable;
    }

    public String getName() {
        return this.name;
    }

    public int getSpeed() {
        return this.speed;
    }

    public int getShootCooldown() {
        return this.shootingInterval;
    }

    public int getShootingInterval() {
        return this.shootingInterval;
    }

    public int getMaxLives() {
        return this.maxLives;
    }

    public boolean hasShield() {
        return this.shieldCapable;
    }

    public boolean isShieldCapable() {
        return this.shieldCapable;
    }

    public BulletType getBulletType() {
        switch (this) {
            case SPEEDSTER:
                return BulletType.RAPID;

            case TANK:
                return BulletType.MULTI;

            case STANDARD:
            default:
                return BulletType.NORMAL;
        }
    }

}
