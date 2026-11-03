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

import entity.EnemyShip;

/**
 * Shows the points earned at the destroyed ship's position.
 * The position never changes; only the opacity changes until expiration.
 */
public final class FloatingScoreHud {
    private static final long DURATION_MS = 950;
    private static final Font FONT = new Font("Monospaced", Font.BOLD, 16);
    private static final Color NORMAL = new Color(255, 225, 92);
    private static final Color BONUS = new Color(255, 160, 110);

    private final List<ScorePopup> popups = new ArrayList<ScorePopup>();

    /** Called at the exact moment the existing collision logic awards points. */
    public void show(final EnemyShip ship) {
        final int points = ship.getPointValue();
        if (points <= 0)
            return;

        // Store coordinates once. Moving ships will not move this text.
        popups.add(new ScorePopup(
                ship.getPositionX() + ship.getWidth() / 2,
                ship.getPositionY() - 7,
                points,
                System.currentTimeMillis()));
    }

    /** Draws onto the normal back buffer; no second window or launcher. */
    public void draw(final Graphics2D source, final int width, final int height) {
        final Graphics2D g = (Graphics2D) source.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(FONT);
            final FontMetrics metrics = g.getFontMetrics();
            final long now = System.currentTimeMillis();
            final Iterator<ScorePopup> iterator = popups.iterator();
            while (iterator.hasNext()) {
                final ScorePopup popup = iterator.next();
                final long elapsed = now - popup.createdAt;
                if (elapsed >= DURATION_MS) {
                    iterator.remove();
                    continue;
                }
                final String label = "+" + popup.points;
                final int textWidth = metrics.stringWidth(label);
                final int x = Math.max(2, Math.min(width - textWidth - 2,
                        popup.centerX - textWidth / 2));
                final int y = Math.max(54, Math.min(height - 4, popup.baselineY));
                // Keep the initial position fixed; fade only in the final 70%.
                final double progress = elapsed / (double) DURATION_MS;
                final float alpha = (float) (progress < 0.30
                        ? 1.0 : Math.max(0.0, (1.0 - progress) / 0.70));
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha));
                g.setColor(Color.BLACK);
                g.drawString(label, x + 1, y + 1);
                g.setColor(popup.points >= 100 ? BONUS : NORMAL);
                g.drawString(label, x, y);
            }
        } finally {
            g.dispose();
        }
    }

    private static final class ScorePopup {
        final int centerX;
        final int baselineY;
        final int points;
        final long createdAt;

        ScorePopup(final int centerX, final int baselineY,
                   final int points, final long createdAt) {
            this.centerX = centerX;
            this.baselineY = baselineY;
            this.points = points;
            this.createdAt = createdAt;
        }
    }
}
