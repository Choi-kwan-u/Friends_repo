package engine;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import audio.AudioManager;
import campaign.CampaignManager;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import level.JsonLevelReader;
import level.JsonLevelWriter;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import screen.GameScreen;
import static org.junit.jupiter.api.Assertions.*;

class CustomLevelSessionIntegrationTest {
    @TempDir Path directory;

    @Test
    void realFileLoadPlayCompleteExportAndReimportKeepOriginalDataAndOfficialUnlocks()
            throws Exception {
        Path source = directory.resolve("sample.json");
        try (InputStream sample = getClass().getResourceAsStream(
                "/examples/levels/campaign-stage-01.json")) {
            assertNotNull(sample, "Include the repository root on the test resource classpath");
            Files.copy(sample, source);
        }
        byte[] originalFile = Files.readAllBytes(source);
        int officialUnlock = CampaignManager.getMaxCampaignStage();
        AtomicInteger playedWaves = new AtomicInteger();
        AtomicReference<LevelDefinition> playedDefinition = new AtomicReference<>();
        final double multiplier = 0.8;
        CustomLevelSession session = new CustomLevelSession(448, 520,
                new JsonLevelReader(), new JsonLevelWriter(), () -> multiplier,
                definition -> {
                    playedDefinition.set(definition);
                    return Core.runCustomLevel(definition, multiplier, 448, 520, 60, screen -> {
                        boolean wasMuted = AudioManager.isMuted();
                        try {
                            AudioManager.setMuted(true);
                            screen.initialize();
                            Field formationField = GameScreen.class.getDeclaredField("enemyShipFormation");
                            formationField.setAccessible(true);
                            Method completion = GameScreen.class.getDeclaredMethod("advanceStageIfReady");
                            completion.setAccessible(true);
                            for (WaveDefinition wave : definition.getWaves()) {
                                assertFalse(screen.isStageCleared());
                                EnemyShipFormation formation = (EnemyShipFormation) formationField.get(screen);
                                int occupied = 0;
                                for (EnemyShip enemy : formation) {
                                    int row = (enemy.getPositionY() - wave.getInitialY()) / 40;
                                    int column = (enemy.getPositionX() - wave.getInitialX()) / 40;
                                    assertNotNull(wave.getCell(row, column));
                                    assertEquals(wave.getCell(row, column).getHealth(), enemy.getHealth());
                                    assertEquals(EnemyShipFormation.spriteFor(
                                            wave.getCell(row, column).getEnemyType()), enemy.getSpriteType());
                                    occupied++;
                                    formation.destroy(enemy);
                                }
                                assertEquals(wave.getEnemyCount(), occupied);
                                playedWaves.incrementAndGet();
                                completion.invoke(screen);
                            }
                            assertTrue(screen.isStageCleared());
                        } catch (ReflectiveOperationException failure) {
                            throw new AssertionError(failure);
                        } finally {
                            AudioManager.stopBGM();
                            AudioManager.setMuted(wasMuted);
                        }
                    });
                });

        LevelDefinition original = session.load(source);
        assertEquals(2, original.getWaves().size());
        assertEquals(20, original.getWaves().get(0).getEnemyCount());
        assertEquals(8, original.getWaves().get(1).getEnemyCount());
        Path beforePlay = directory.resolve("before-play.json");
        session.save(beforePlay);
        GameState result = session.play();
        assertEquals(2, playedWaves.get());
        assertEquals(3, result.getLivesRemaining());
        assertSame(original, playedDefinition.get());
        assertSame(original, session.getLoadedLevel().get());
        assertEquals(officialUnlock, CampaignManager.getMaxCampaignStage());
        Path afterPlay = directory.resolve("after-play.json");
        session.save(afterPlay);
        assertArrayEquals(Files.readAllBytes(beforePlay), Files.readAllBytes(afterPlay));
        assertArrayEquals(originalFile, Files.readAllBytes(source));
        // Use the actual reader again, then export the selected model once more.
        session.load(afterPlay);
        Path repeated = directory.resolve("repeated.json");
        session.save(repeated);
        assertArrayEquals(Files.readAllBytes(afterPlay), Files.readAllBytes(repeated));
    }
}
