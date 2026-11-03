package engine;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Random;

/**
 * Background of small stars that scroll slowly downwards and loop back to the top of the screen.
 */
public final class Starfield {

    private static final int STAR_COUNT = 60;
    /** Downwards speed of the stars, in pixels per second. */
    private static final double SPEED = 30.0;

    private final int width;
    private final int height;
    private final double[] x = new double[STAR_COUNT];
    private final double[] y = new double[STAR_COUNT];
    private final Random random = new Random();

    /**
     * Places every star at a random position inside the given area.
     *
     * @param width
     *            Width of the area, in pixels.
     * @param height
     *            Height of the area, in pixels.
     */
    public Starfield(final int width, final int height) {
        this.width = width;
        this.height = height;

        for (int i = 0; i < STAR_COUNT; i++) {
            x[i] = random.nextInt(width);
            y[i] = random.nextInt(height);
        }
    }

    /**
     * Moves the stars down. A star leaving the bottom reappears at the top with a new random horizontal position.
     *
     * @param deltaSeconds
     *            Time elapsed since the previous update, in seconds.
     */
    public void update(final double deltaSeconds) {
        for (int i = 0; i < STAR_COUNT; i++) {
            y[i] += SPEED * deltaSeconds;

            if (y[i] >= height) {
                y[i] %= height;
                x[i] = random.nextInt(width);
            }
        }
    }

    /**
     * Draws the stars, then restores the previous color of the context.
     *
     * @param graphics
     *            Graphics context to draw on.
     */
    public void draw(final Graphics graphics) {
        Color previousColor = graphics.getColor();
        graphics.setColor(new Color(130, 140, 160));

        for (int i = 0; i < STAR_COUNT; i++) {
            graphics.fillRect((int) x[i], (int) y[i], 2, 2);
        }

        graphics.setColor(previousColor);
    }
}
