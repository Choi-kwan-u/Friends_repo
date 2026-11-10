package hud;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shows a rising "+1UP" where an extra life was caught (Infinite mode).
 * The text floats up and fades out.
 */
public final class OneUpHud {
    /** How long one popup stays on screen. */
    static final long DURATION_MS = 1000;
    /** Distance the text rises over its whole duration. */
    private static final int RISE = 36;
    private static final String LABEL = "+1UP";
    /** Text size, a bit bigger than the 14 pt HUD text. */
    public static final float FONT_SIZE = 18f;
    /** Same green as the player ship. */
    private static final Color COLOR = Color.GREEN;

    private final List<Popup> popups = new ArrayList<Popup>();

    /** Starts a popup centered on (centerX, baselineY). */
    public void show(final int centerX, final int baselineY) {
        popups.add(new Popup(centerX, baselineY, System.currentTimeMillis()));
    }

    /** Number of popups still on screen. */
    public int size() {
        return popups.size();
    }

    /**
     * Draws onto the normal back buffer.
     *
     * @param font
     *            Game font, already at FONT_SIZE.
     */
    public void draw(final Graphics2D source, final int width, final int height,
            final Font font) {
        final Graphics2D g = (Graphics2D) source.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            final FontMetrics metrics = g.getFontMetrics();
            final int textWidth = metrics.stringWidth(LABEL);
            final long now = System.currentTimeMillis();
            final Iterator<Popup> iterator = popups.iterator();
            while (iterator.hasNext()) {
                final Popup popup = iterator.next();
                final long elapsed = now - popup.createdAt;
                if (elapsed >= DURATION_MS) {
                    iterator.remove();
                    continue;
                }
                final double progress = elapsed / (double) DURATION_MS;
                final int x = Math.max(2, Math.min(width - textWidth - 2,
                        popup.centerX - textWidth / 2));
                final int y = Math.max(54, Math.min(height - 4,
                        popup.baselineY - (int) (RISE * progress)));
                // Fully visible first, then fades over the last 60%.
                final float alpha = (float) (progress < 0.40
                        ? 1.0 : Math.max(0.0, (1.0 - progress) / 0.60));
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha));
                g.setColor(Color.BLACK);
                g.drawString(LABEL, x + 1, y + 1);
                g.setColor(COLOR);
                g.drawString(LABEL, x, y);
            }
        } finally {
            g.dispose();
        }
    }

    private static final class Popup {
        final int centerX;
        final int baselineY;
        final long createdAt;

        Popup(final int centerX, final int baselineY, final long createdAt) {
            this.centerX = centerX;
            this.baselineY = baselineY;
            this.createdAt = createdAt;
        }
    }
}
