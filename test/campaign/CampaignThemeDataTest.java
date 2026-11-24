package campaign;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampaignThemeDataTest {
    @Test
    void allSevenStagesHaveDecodableImagesAndActualPcmWavFiles() throws Exception {
        for (int level = 1; level <= 7; level++) {
            CampaignThemeData theme = CampaignThemeData.forLevel(level);
            String stage = String.format(java.util.Locale.ROOT, "stage_%02d", level);
            assertEquals("backgrounds/campaign/" + stage + ".png", theme.getBackgroundPath());
            assertEquals("audio/bgm/campaign/" + stage + ".wav", theme.getBgmPath());
            var loader = getClass().getClassLoader();
            var imageUrl = loader.getResource(theme.getBackgroundPath());
            assertNotNull(imageUrl);
            var image = ImageIO.read(imageUrl);
            assertNotNull(image);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
            var audioUrl = loader.getResource(theme.getBgmPath());
            assertNotNull(audioUrl);
            try (var stream = AudioSystem.getAudioInputStream(audioUrl)) {
                AudioFormat format = stream.getFormat();
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
                assertEquals(44100f, format.getSampleRate());
                assertEquals(16, format.getSampleSizeInBits());
                assertEquals(2, format.getChannels());
                assertFalse(format.isBigEndian());
                assertTrue(stream.getFrameLength() > 0);
            }
        }
    }

    @Test
    void unsupportedLevelsKeepTheDefaultTheme() {
        for (int level : new int[] {Integer.MIN_VALUE, -1, 0, 8, Integer.MAX_VALUE})
            assertNull(CampaignThemeData.forLevel(level));
    }
}
