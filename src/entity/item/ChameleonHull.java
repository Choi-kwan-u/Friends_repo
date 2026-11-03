package entity.item;

import java.awt.Color;
import engine.DrawManager.SpriteType;
import entity.Ship;

/** Collectible Chameleon Hull power-up. @author Brieuc Viougeat */
public final class ChameleonHull extends Item {

    public static final int REQUIRED_LEVEL = 1;
    public static final double DROP_CHANCE = 0.03;
    public static final long DURATION = 5000;
    public static final long COLOR_INTERVAL_NANOS = 150000000L;

    private static final Color[] COLORS = {
            Color.CYAN,
            Color.MAGENTA,
            Color.YELLOW,
            Color.ORANGE,
            Color.BLUE
    };

    private static final String[] STAR_PATTERN = {
            "0000001000000",
            "0000011100000",
            "0000011100000",
            "0000111110000",
            "1111111111111",
            "0111111111110",
            "0011111111100",
            "0001111111000",
            "0001111111000",
            "0011110111100",
            "0011100011100",
            "0111000001110",
            "0100000000010"
    };

    private static final boolean[][] STAR_SPRITE = createStarSprite();

    private final long appearanceTime = System.nanoTime();

    public ChameleonHull(final int positionX, final int positionY) {
        super(positionX, positionY, 26, 26, SpriteType.Ship);
        setColor(COLORS[0]);
    }

    @Override
    public void update() {
        super.update();

        long elapsed = System.nanoTime() - this.appearanceTime;

        int index = (int) (
                (elapsed / COLOR_INTERVAL_NANOS) % COLORS.length
        );

        setColor(COLORS[index]);
    }

    @Override
    public void applyEffect(final Ship ship) {
        ship.activateChameleonHull();
    }

    @Override
    public void removeEffect(final Ship ship) {
        ship.deactivateChameleonHull();
    }

    @Override
    public long getDuration() {
        return DURATION;
    }

    @Override
    public String getName() {
        return "Chameleon Hull";
    }

    public static boolean[][] getStarSprite() {
        return STAR_SPRITE;
    }

    private static boolean[][] createStarSprite() {
        boolean[][] sprite = new boolean[13][13];

        for (int y = 0; y < STAR_PATTERN.length; y++) {
            for (int x = 0; x < STAR_PATTERN[y].length(); x++) {
                sprite[x][y] = STAR_PATTERN[y].charAt(x) == '1';
            }
        }

        return sprite;
    }

    public static final double SPEED_MULTIPLIER = 2.0;
    public static final int BALL_SIZE = 32;

    private static final String[] BALL_PATTERN = {
            "0000000110000000",
            "0001001111001000",
            "0011111111111100",
            "0111111111111110",
            "0011111111111100",
            "0011111111111100",
            "0111111111111110",
            "1111111111111111",
            "1111111111111111",
            "0111111111111110",
            "0011111111111100",
            "0011111111111100",
            "0111111111111110",
            "0011111111111100",
            "0001001111001000",
            "0000000110000000"
    };

    private static final boolean[][] BALL_SPRITE = createBallSprite();

    public static boolean[][] getBallSprite() {
        return BALL_SPRITE;
    }

    private static boolean[][] createBallSprite() {
        boolean[][] sprite = new boolean[16][16];

        for (int y = 0; y < BALL_PATTERN.length; y++) {
            for (int x = 0; x < BALL_PATTERN[y].length(); x++) {
                sprite[x][y] = BALL_PATTERN[y].charAt(x) == '1';
            }
        }

        return sprite;
    }
}
