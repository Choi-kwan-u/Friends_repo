package engine;

import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import entity.PlayerStatus;
import entity.Ship;
import entity.ShipType;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CampaignUpgradesTest {
    private PlayerAssetsState owned(int level) {
        Set<String> ids = new HashSet<String>();
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            for (int tier = 1; tier <= level; tier++) ids.add(upgrade.getProductId(tier));
        }
        return new PlayerAssetsState(0, ids, null);
    }

    @ParameterizedTest
    @EnumSource(ShipType.class)
    void upgradesEachShipWithoutChangingItsTypeOrTemporaryEffects(ShipType type) {
        for (int level = 0; level <= 3; level++) {
            CampaignUpgrades upgrades = CampaignUpgrades.from(owned(level));
            Ship ship = new Ship(100, 100, type);
            upgrades.initializeShip(ship);
            assertEquals(type.getMaxLives() + level, upgrades.getMaxLives(type));
            assertEquals(type.getSpeed() + level, ship.getBaseSpeed());
            assertEquals(type.getShootingInterval() * (100 - level * 10) / 100,
                    ship.getBaseShootingInterval());
            assertSame(type, ship.getShipType());
            assertEquals(type.getBulletType(), ship.getCurrentBulletType());
            int speed = ship.getSpeed();
            int interval = ship.getShootingInterval();
            upgrades.initializeShip(ship);
            assertEquals(speed, ship.getSpeed(), "Reinitialization must not add another bonus");
            ship.setSpeed(99);
            ship.setShootingInterval(10);
            ship.restoreBaseStats();
            assertEquals(speed, ship.getSpeed());
            assertEquals(interval, ship.getShootingInterval());
            PlayerStatus status = new PlayerStatus(1, upgrades.getMaxLives(type), type.isShieldCapable());
            status.applySlow(ship);
            status.clearSlowDebuff();
            assertEquals(speed, ship.getSpeed(), "Slow expiry preserves the upgraded baseline");
            assertEquals(interval, ship.getShootingInterval());
        }
    }

    @Test
    void anActiveRunKeepsItsSnapshotAndTheNextRunReadsNewPurchases() {
        PlayerAssets assets = new PlayerAssets();
        assets.restore(owned(1));
        CampaignUpgrades activeRun = CampaignUpgrades.from(assets.snapshot());
        assets.restore(owned(3));
        assertEquals(5, activeRun.getMaxLives(ShipType.STANDARD));
        assertEquals(7, CampaignUpgrades.from(assets.snapshot()).getMaxLives(ShipType.STANDARD));
        assertEquals(4, CampaignUpgrades.none().getMaxLives(ShipType.STANDARD));
        assertThrows(IllegalArgumentException.class, () -> CampaignUpgrades.from(null));
    }
}
