package engine;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import screen.Screen;
import static org.junit.jupiter.api.Assertions.*;

class DrawManagerCampaignBackgroundTest {
    @Test
    void wideAndTallImagesAreCenteredCroppedWithoutStretching() throws Exception {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(100, 100, 60);
        for (boolean wide : new boolean[] {true, false}) {
            BufferedImage image = new BufferedImage(wide ? 400 : 200, wide ? 200 : 400,
                    BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLUE);
            g.fillRect(wide ? 100 : 0, wide ? 0 : 100, 200, 200);
            g.setColor(Color.GREEN);
            g.fillRect(wide ? 180 : 80, wide ? 80 : 180, 40, 40);
            g.dispose();
            draw.initDrawing(screen, image);
            try {
                BufferedImage frame = buffer();
                assertEquals(Color.BLUE.getRGB(), frame.getRGB(0, 0));
                assertEquals(Color.BLUE.getRGB(), frame.getRGB(99, 99));
                assertEquals(Color.GREEN.getRGB(), frame.getRGB(40, 40));
                assertEquals(Color.GREEN.getRGB(), frame.getRGB(59, 59));
                assertEquals(Color.BLUE.getRGB(), frame.getRGB(39, 50));
                assertEquals(Color.BLUE.getRGB(), frame.getRGB(50, 60));
            } finally {
                draw.completeDrawing(screen);
            }
        }
    }

    @Test
    void missingAndUndecodableImagesUseTheExistingStarfield() throws Exception {
        DrawManager draw = Core.getDrawManager();
        assertNull(draw.loadCampaignBackground("backgrounds/campaign/missing.png"));
        assertNull(draw.loadCampaignBackground("audio/bgm/CREDITS.md"));
        Screen screen = new Screen(100, 100, 60);
        draw.initDrawing(screen, null);
        try {
            int black = 0;
            int stars = 0;
            BufferedImage frame = buffer();
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    if (frame.getRGB(x, y) == Color.BLACK.getRGB()) black++;
                    else stars++;
                }
            }
            assertTrue(black > 9000);
            assertTrue(stars > 0);
        } finally {
            draw.completeDrawing(screen);
        }
    }

    private static BufferedImage buffer() throws Exception {
        Field field = DrawManager.class.getDeclaredField("backBuffer");
        field.setAccessible(true);
        return (BufferedImage) field.get(null);
    }
}
