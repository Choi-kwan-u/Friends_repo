package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import entity.Ship;
import entity.ShipType;

/** A fixed upgrade snapshot shared by every stage of one Campaign run. */
public final class CampaignUpgrades {
    private static final CampaignUpgrades NONE = new CampaignUpgrades(0, 0, 0);
    private final int extraLives;
    private final int extraSpeed;
    private final int intervalReduction;

    private CampaignUpgrades(int extraLives, int extraSpeed, int intervalReduction) {
        this.extraLives = extraLives;
        this.extraSpeed = extraSpeed;
        this.intervalReduction = intervalReduction;
    }

    public static CampaignUpgrades none() {
        return NONE;
    }

    public static CampaignUpgrades from(PlayerAssetsState assets) {
        return new CampaignUpgrades(
                PermanentUpgrade.HULL.getBonus(PermanentUpgrade.HULL.getLevel(assets)),
                PermanentUpgrade.ENGINE.getBonus(PermanentUpgrade.ENGINE.getLevel(assets)),
                PermanentUpgrade.RELOADER.getBonus(PermanentUpgrade.RELOADER.getLevel(assets)));
    }

    public int getMaxLives(ShipType shipType) {
        if (shipType == null) throw new IllegalArgumentException("Ship type cannot be null.");
        return shipType.getMaxLives() + extraLives;
    }

    /** Apply once to a newly created ship, before stage items and debuffs. */
    public void initializeShip(Ship ship) {
        if (ship == null) throw new IllegalArgumentException("Ship cannot be null.");
        ShipType type = ship.getShipType();
        ship.setBaseSpeed(type.getSpeed() + extraSpeed);
        ship.setBaseShootingInterval(Math.max(1,
                type.getShootingInterval() * (100 - intervalReduction) / 100));
        ship.restoreBaseStats();
    }
}
