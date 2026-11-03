package screen;

import audio.AudioManager;
import campaign.CampaignStageData;
import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import engine.CampaignUpgrades;
import engine.GameState;
import entity.Ship;
import entity.ShipType;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class PermanentUpgradeCampaignTest {
    private boolean muted;

    @BeforeEach
    void mute() {
        muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void restoreAudio() {
        AudioManager.stopBGM();
        AudioManager.setMuted(muted);
    }

    private CampaignUpgrades upgrades() {
        Set<String> owned = new HashSet<String>();
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            for (int level = 1; level <= 3; level++) owned.add(upgrade.getProductId(level));
        }
        return CampaignUpgrades.from(new PlayerAssetsState(0, owned, null));
    }

    private GameScreen stage(ShipType type, CampaignUpgrades upgrades,
            int level, int lives, boolean bonusLife) {
        GameScreen screen = new GameScreen(new GameState(level, 0, lives, 0, 0),
                CampaignStageData.getStageSettings(level), bonusLife,
                448, 520, 60, type, null, upgrades);
        screen.initialize();
        return screen;
    }

    private Ship ship(GameScreen screen) throws Exception {
        Field field = GameScreen.class.getDeclaredField("ship");
        field.setAccessible(true);
        return (Ship) field.get(screen);
    }

    @ParameterizedTest
    @EnumSource(ShipType.class)
    void stagesRetainBonusesAndDamageWithoutStackingOrHealing(ShipType type) throws Exception {
        CampaignUpgrades upgrades = upgrades();
        int maximum = type.getMaxLives() + 3;
        GameScreen first = stage(type, upgrades, 1, maximum, false);
        assertEquals(maximum, first.getPlayerStatus().getMaxLives());
        assertEquals(maximum, first.getGameState().getLivesRemaining());
        assertTrue(first.getPlayerStatus().takeDamage(2));
        GameScreen second = stage(type, upgrades, 2,
                first.getGameState().getLivesRemaining(), false);
        assertEquals(maximum - 2, second.getGameState().getLivesRemaining());
        assertEquals(maximum, second.getPlayerStatus().getMaxLives());
        assertEquals(type.getSpeed() + 3, ship(second).getSpeed());
        assertEquals(ship(first).getSpeed(), ship(second).getSpeed());
        assertEquals(type.getShootingInterval() * 70 / 100, ship(second).getShootingInterval());
        GameScreen bonus = stage(type, upgrades, 3, maximum - 1, true);
        assertEquals(maximum, bonus.getGameState().getLivesRemaining());
        assertFalse(bonus.getPlayerStatus().gainLife());
    }

    @ParameterizedTest
    @EnumSource(ShipType.class)
    void existingConstructorsKeepUnmodifiedStats(ShipType type) throws Exception {
        GameScreen screen = new GameScreen(new GameState(1, 0, 20, 0, 0),
                CampaignStageData.getStageSettings(1), false, 448, 520, 60, type, null);
        screen.initialize();
        assertEquals(type.getMaxLives(), screen.getPlayerStatus().getMaxLives());
        assertEquals(type.getMaxLives(), screen.getGameState().getLivesRemaining());
        assertEquals(type.getSpeed(), ship(screen).getSpeed());
        assertEquals(type.getShootingInterval(), ship(screen).getShootingInterval());
    }
}
