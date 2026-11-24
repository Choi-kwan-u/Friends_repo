package screen;

import audio.AudioBus;
import audio.AudioCache;
import audio.AudioManager;
import audio.AudioResource;
import campaign.CampaignStageData;
import engine.Core;
import engine.DrawManager;
import engine.GameState;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampaignThemeScreenTest {
    private Object oldCache;
    private Object oldInitialized;
    private boolean oldMuted;
    private int oldVolume;
    private double oldRate;
    private AudioBus bus;
    private RecordingCache cache;

    @BeforeEach
    void isolateAudioWithoutRequiringAnOutputDevice() throws Exception {
        oldCache = get(AudioManager.class, "audioCache");
        oldInitialized = get(AudioManager.class, "initialized");
        bus = (AudioBus) get(AudioManager.class, "bgmBus");
        oldRate = bus.getPlaybackRate();
        oldVolume = AudioManager.getBGMVolume();
        oldMuted = AudioManager.isMuted();
        AudioManager.stopBGM();
        AudioManager.setMuted(true);
        cache = new RecordingCache();
        set(AudioManager.class, "audioCache", cache);
        set(AudioManager.class, "initialized", true);
    }

    @AfterEach
    void restoreAudio() throws Exception {
        AudioManager.stopBGM();
        bus.setPaused(false);
        bus.setPlaybackRate(oldRate);
        AudioManager.setBGMVolume(oldVolume);
        AudioManager.setMuted(oldMuted);
        set(AudioManager.class, "audioCache", oldCache);
        set(AudioManager.class, "initialized", oldInitialized);
    }

    @Test
    void stageEntryAndRecoveryKeepEachLevelTrackIncludingBossTracks() throws Exception {
        for (int level = 1; level <= 7; level++) {
            GameScreen game = stage(level, 3);
            String path = String.format(java.util.Locale.ROOT, "audio/bgm/campaign/stage_%02d.wav", level);
            assertEquals(path, cache.lastBgm());
            assertNotNull(get(game, "campaignBackground"));
            // Recovery must select the same stage music, including levels 3, 5 and 7.
            invoke(game, "resumeAfterRevive");
            assertEquals(path, cache.lastBgm());
            assertEquals(1.0, bus.getPlaybackRate());
        }
    }

    @Test
    void lastLifeRateStillAppliesOnlyToOrdinaryStagesAndRestoresAfterRecovery() throws Exception {
        for (int level = 1; level <= 7; level++) {
            GameScreen game = stage(level, 1);
            boolean boss = level == 3 || level == 5 || level == 7;
            assertEquals(boss ? 1.0 : 1.08, bus.getPlaybackRate());
            game.getPlayerStatus().gainLife();
            invoke(game, "updateBgmPlaybackRate");
            assertEquals(1.0, bus.getPlaybackRate());
        }
    }

    @Test
    void missingOrFailedStageMusicFallsBackToTheCorrectDefault() throws Exception {
        for (boolean corrupt : new boolean[] {false, true}) {
            cache.failCampaign = true;
            cache.throwOnFailure = corrupt;
            stage(1, 3);
            assertEquals("audio/bgm/music_game.wav", cache.lastBgm());
            stage(3, 3);
            assertEquals("audio/bgm/music_boss.wav", cache.lastBgm());
        }
        cache.requested.clear();
        AudioManager.playBGM("missing.wav", "missing.wav");
        assertEquals(List.of("missing.wav"), cache.requested);
    }

    @Test
    void switchingTracksAndFallbackPreserveVolumeMuteAndPauseResumeControls() throws Exception {
        AudioManager.setBGMVolume(23);
        stage(2, 3);
        AudioManager.pauseBGM();
        assertEquals(true, get(bus, "paused"));
        AudioManager.resumeBGM();
        assertEquals(false, get(bus, "paused"));
        cache.failCampaign = true;
        stage(5, 3);
        assertEquals(23, AudioManager.getBGMVolume());
        assertTrue(AudioManager.isMuted());
        assertEquals(true, get(bus, "looping"));
        assertEquals(false, get(bus, "paused"));
    }

    @Test
    void customLevelKeepsDefaultBackgroundAndMusicEvenWhenNumberedLikeABoss() throws Exception {
        var wave = new WaveDefinition("custom", new EnemyCell[][] {{new EnemyCell("A", 1)}},
                20, 100, 60, 2000);
        var definition = new LevelDefinition(1, "custom", "Custom", List.of(wave));
        GameScreen custom = GameScreen.forCustomLevel(new GameState(3, 0, 3, 0, 0),
                definition, 1.0, 448, 520, 60);
        custom.initialize();
        assertNull(get(custom, "campaignTheme"));
        assertNull(get(custom, "campaignBackground"));
        assertEquals("audio/bgm/music_boss.wav", cache.lastBgm());
        GameScreen invalid = stage(8, 3);
        assertNull(get(invalid, "campaignTheme"));
        assertEquals("audio/bgm/music_game.wav", cache.lastBgm());
    }

    @Test
    void framesReuseLoadedImageAndPaintObjectsAndHudAboveItWithoutRestartingBgm() throws Exception {
        GameScreen game = stage(1, 3);
        BufferedImage background = new BufferedImage(448, 520, BufferedImage.TYPE_INT_RGB);
        var graphics = background.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, 448, 520);
        graphics.dispose();
        set(game, "campaignBackground", background);
        int requests = cache.requested.size();
        for (int frame = 0; frame < 2; frame++) {
            invoke(game, "draw");
            assertSame(background, get(game, "campaignBackground"));
            BufferedImage rendered = (BufferedImage) get(DrawManager.class, "backBuffer");
            assertEquals(Color.MAGENTA.getRGB(), rendered.getRGB(5, 400));
            assertTrue(changedPixels(rendered, 0, 0, 448, 60) > 0, "HUD above background");
            assertTrue(changedPixels(rendered, 0, 60, 448, 300) > 0, "Entities above background");
        }
        assertEquals(requests, cache.requested.size());
    }

    private static int changedPixels(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int row = y; row < y + height; row++)
            for (int col = x; col < x + width; col++)
                if (image.getRGB(col, row) != Color.MAGENTA.getRGB()) count++;
        return count;
    }

    private GameScreen stage(int level, int lives) {
        GameScreen game = new GameScreen(new GameState(level, 0, lives, 0, 0),
                CampaignStageData.getStageSettings(level), false, 448, 520, 60);
        game.initialize();
        return game;
    }

    private static class RecordingCache extends AudioCache {
        private final List<String> requested = new ArrayList<>();
        private final AudioResource sample = new AudioCache().getAudioData("audio/sfx/menu_move.wav");
        private boolean failCampaign;
        private boolean throwOnFailure;

        @Override
        public synchronized AudioResource getAudioData(String path) {
            requested.add(path);
            if (path.equals("missing.wav") || (failCampaign && path.startsWith("audio/bgm/campaign/"))) {
                if (throwOnFailure) throw new IllegalStateException("Simulated unreadable track");
                return null;
            }
            return sample;
        }

        String lastBgm() {
            return requested.stream().filter(path -> path.startsWith("audio/bgm/"))
                    .reduce((first, second) -> second).orElseThrow();
        }
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target, name).get(target instanceof Class ? null : target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target instanceof Class ? null : target, value);
    }

    private static void invoke(GameScreen target, String name) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
