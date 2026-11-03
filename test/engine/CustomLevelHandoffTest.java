package engine;

import campaign.CampaignManager;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import level.*;
import screen.GameScreen;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CustomLevelHandoffTest {
    private WaveDefinition wave(String id, String type) {
        return new WaveDefinition(id, new EnemyCell[][] {{new EnemyCell(type, 1)}}, 20, 100, 60, 2000);
    }

    @Test
    void officialLookingIdStillCannotUnlockOfficialCampaign() throws Exception {
        int unlocked = CampaignManager.getMaxCampaignStage();
        LevelDefinition definition = new LevelDefinition(1, "campaign-stage-07", "Official-looking",
                Collections.singletonList(wave("last", "A")));
        // Importer remains separately owned: a stub reader supplies the agreed model.
        LevelReader reader = path -> definition;
        LevelDefinition loaded = reader.read(java.nio.file.Paths.get("not-read-by-stub.json"));
        GameState result = Core.runCustomLevel(loaded, 1, 448, 520, 60, screen -> {
            try {
                audio.AudioManager.setMuted(true);
                screen.initialize();
                Field field = GameScreen.class.getDeclaredField("enemyShipFormation");
                field.setAccessible(true);
                EnemyShipFormation formation = (EnemyShipFormation) field.get(screen);
                for (EnemyShip enemy : formation) formation.destroy(enemy);
                Method completion = GameScreen.class.getDeclaredMethod("advanceStageIfReady");
                completion.setAccessible(true);
                completion.invoke(screen);
                assertTrue(screen.isStageCleared());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            } finally {
                audio.AudioManager.stopBGM();
            }
        });
        assertEquals(1, result.getLevel());
        assertEquals(3, result.getLivesRemaining());
        assertEquals(unlocked, CampaignManager.getMaxCampaignStage());
    }

    @Test
    void invalidLaterWaveDoesNotInvokeRunnerOrChangeActiveScreen() throws Exception {
        LevelDefinition definition = new LevelDefinition(1, "bad-stage", "Bad",
                Arrays.asList(wave("valid", "A"), wave("invalid", "boss")));
        AtomicBoolean invoked = new AtomicBoolean();
        Field active = Core.class.getDeclaredField("currentScreen");
        active.setAccessible(true);
        Object previous = active.get(null);
        int unlocked = CampaignManager.getMaxCampaignStage();
        assertThrows(IllegalArgumentException.class,
                () -> Core.runCustomLevel(definition, 1, 448, 520, 60, screen -> invoked.set(true)));
        assertFalse(invoked.get());
        assertSame(previous, active.get(null));
        assertEquals(unlocked, CampaignManager.getMaxCampaignStage());
    }
}
