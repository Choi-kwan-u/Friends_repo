package engine;

import entity.ShipType;
import entity.item.ChameleonHull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import screen.Screen;
import hud.FloatingScoreHud;
import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import currency.ShopCatalog.Category;
import shop.ShopController.SaveStatus;
import shop.ShopProduct;
import records.Achievement;
import records.RecordsService;
import records.RunRecord;
import java.text.DateFormat;
import java.util.Date;
import entity.Entity;
import entity.Grenade;
import entity.GrenadeFragment;
import entity.item.AnnihilatorItem;
import entity.item.ClusterGrenadeItem;
import entity.Ship;
import entity.Bullet;
import vfx.Icon;
import vfx.RankBadge;
import vfx.ShipSprites;
import screen.GameModeScreen;

/**
 * Manages screen drawing.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class DrawManager {
    private int getNormalGameOverTitleY(final Screen screen) {
        int regularHeight = fontRegularMetrics.getHeight();
        int bigHeight = fontBigMetrics.getHeight();

        int promptY = screen.getHeight() / 2 + regularHeight * 10;
        int titleToAccuracy = bigHeight + regularHeight * 8;

        return (promptY
                - fontRegularMetrics.getAscent()
                - titleToAccuracy
                - fontRegularMetrics.getDescent()
                + fontBigMetrics.getAscent()) / 2;
    }

    /**
     * Centers the complete achievement result block above the input prompt.
     *
     * @param screen
     *                    Screen to draw on.
     * @param isNewRecord
     *                    If the score is a new high score.
     * @param failedStage
     *                    Failed stage number, or zero when no stage failed.
     * @param achievementCount
     *                    Number of newly unlocked achievements.
     * @return Baseline for the game-over title.
     */
    private int getAchievementResultsTitleY(final Screen screen,
            final boolean isNewRecord, final int failedStage,
            final int achievementCount) {
        int spacing = fontRegularMetrics.getHeight();
        int lastLineOffset = 0;

        if (failedStage > 0)
            lastLineOffset += spacing;

        // Gap before the four result rows, including the increment after the
        // last row used by drawAchievementResults.
        lastLineOffset += spacing * 2;
        lastLineOffset += spacing * 4;

        if (isNewRecord) {
            // Gap before the three name-input rows and the increment after
            // that block.
            lastLineOffset += spacing;
            lastLineOffset += spacing * 3;
        }

        // Achievement heading followed by one row per unlocked achievement.
        lastLineOffset += spacing;
        lastLineOffset += spacing * achievementCount;

        int promptY = screen.getHeight() - spacing;
        int availableTop = spacing;
        int availableBottom = promptY - spacing * 2;
        int blockHeight = fontBigMetrics.getAscent() + lastLineOffset
                + fontRegularMetrics.getDescent();
        int availableHeight = availableBottom - availableTop;
        int blockTop = availableTop
                + Math.max(0, (availableHeight - blockHeight) / 2);

        return blockTop + fontBigMetrics.getAscent();
    }

    /** Singleton instance of the class. */
    private static DrawManager instance;
    /** Current frame. */
    private static Frame frame;
    /** FileManager instance. */
    private static FileManager fileManager;
    /** Application logger. */
    private static Logger logger;
    /** Graphics context. */
    private static Graphics graphics;
    /** Buffer Graphics. */
    private static Graphics backBufferGraphics;
    /** Buffer image. */
    private static BufferedImage backBuffer;
    /** Normal sized font. */
    private static Font fontRegular;
    /** Normal sized font properties. */
    private static FontMetrics fontRegularMetrics;
    /** Big sized font. */
    private static Font fontBig;
    /** Big sized font properties. */
    private static FontMetrics fontBigMetrics;

    /** Options of the settings menu, in order. */
    private static final String[] SETTINGS_MENU_OPTIONS = { "Audio", "Controls", "Gameplay", "Back" };
    /** Options of the gameplay settings menu, in order. */
    private static final String[] GAMEPLAY_SETTINGS_OPTIONS = { "Easy", "Normal", "Hard" };
    /** Width of the column holding the audio settings. */
    private static final int AUDIO_COLUMN_WIDTH = 200;
    /** Space between an audio option name and its volume bar. */
    private static final int AUDIO_BAR_GAP = 6;
    /** Height of the audio volume bars. */
    private static final int AUDIO_BAR_HEIGHT = 8;

    /** Sprite types mapped to their images. */
    private static Map<SpriteType, boolean[][]> spriteMap;

    /** Sprite types. */
    public static enum SpriteType {
        /** Player ship. */
        Ship,
        /** Destroyed player ship. */
        ShipDestroyed,
        /** Player bullet. */
        Bullet,
        /** Enemy bullet. */
        EnemyBullet,
        /** First enemy ship - first form. */
        EnemyShipA1,
        /** First enemy ship - second form. */
        EnemyShipA2,
        /** Second enemy ship - first form. */
        EnemyShipB1,
        /** Second enemy ship - second form. */
        EnemyShipB2,
        /** Third enemy ship - first form. */
        EnemyShipC1,
        /** Third enemy ship - second form. */
        EnemyShipC2,
        /** Bonus ship. */
        EnemyShipSpecial,
        /** Hyperfire falling power-up. */
        Hyperfire,
        /** Destroyed enemy ship. */
        Explosion,
        /** Falling hazard. */
        FallingObstacle
    };

    /**
     * Private constructor.
     */
    private DrawManager() {
        fileManager = Core.getFileManager();
        logger = Core.getLogger();
        logger.info("Started loading resources.");

        try {
            spriteMap = new LinkedHashMap<SpriteType, boolean[][]>();

            spriteMap.put(SpriteType.Ship, new boolean[13][8]);
            spriteMap.put(SpriteType.ShipDestroyed, new boolean[13][8]);
            spriteMap.put(SpriteType.Bullet, new boolean[3][5]);
            spriteMap.put(SpriteType.EnemyBullet, new boolean[3][5]);
            spriteMap.put(SpriteType.EnemyShipA1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipA2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipB1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipB2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipC1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipC2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipSpecial, new boolean[16][7]);
            spriteMap.put(SpriteType.Explosion, new boolean[13][7]);
            spriteMap.put(SpriteType.FallingObstacle, new boolean[13][7]);

            fileManager.loadSprite(spriteMap);
            logger.info("Finished loading the sprites.");

            // Hyperfire lightning bolt sprite (8 x 12 pixels), built in code
            // because it is not part of the graphics file.
            boolean[][] hyperfireSprite = new boolean[8][12];
            String[] lightning = {
                    "....##..",
                    "...###..",
                    "...##...",
                    "..###...",
                    "..##....",
                    ".######.",
                    "...##...",
                    "...##...",
                    "..##....",
                    "..##....",
                    ".##.....",
                    ".#......"
            };
            for (int y = 0; y < lightning.length; y++)
                for (int x = 0; x < lightning[y].length(); x++)
                    hyperfireSprite[x][y] = lightning[y].charAt(x) == '#';
            spriteMap.put(SpriteType.Hyperfire, hyperfireSprite);

            // Font loading.
            fontRegular = fileManager.loadFont(14f);
            fontBig = fileManager.loadFont(24f);
            logger.info("Finished loading the fonts.");

        } catch (IOException e) {
            logger.warning("Loading failed.");
        } catch (FontFormatException e) {
            logger.warning("Font formating failed.");
        }
    }

    /**
     * Returns shared instance of DrawManager.
     *
     * @return Shared instance of DrawManager.
     */
    protected static DrawManager getInstance() {
        if (instance == null)
            instance = new DrawManager();
        return instance;
    }

    /**
     * Sets the frame to draw the image on.
     *
     * @param currentFrame
     *                     Frame to draw on.
     */
    public void setFrame(final Frame currentFrame) {
        frame = currentFrame;
    }

    /**
     * Draws an entity, using the apropiate image.
     *
     * @param entity
     *                  Entity to be drawn.
     * @param positionX
     *                  Coordinates for the left side of the image.
     * @param positionY
     *                  Coordinates for the upper side of the image.
     */
    public void drawEntity(final Entity entity, final int positionX,
            final int positionY) {
        if (entity instanceof AnnihilatorItem) {
            drawFallingLaser(entity, positionX, positionY);
            return;
        }

        boolean[][] image = spriteMap.get(entity.getSpriteType());

        if (entity instanceof ChameleonHull) {
            image = ChameleonHull.getStarSprite();
        }

        if (entity instanceof Ship
                && entity.getSpriteType() == SpriteType.Ship) {
            image = ShipSprites.forType(((Ship) entity).getShipType());
        }

        if (entity instanceof Bullet
                && entity.getSpriteType() == SpriteType.Bullet) {
            drawPixels(ShipSprites.playerBullet(), positionX, positionY);
            return;
        }

        if (entity instanceof Bullet
                && entity.getSpriteType() == SpriteType.EnemyBullet) {
            drawPixels(ShipSprites.enemyBullet(), positionX, positionY);
            return;
        }

        if (entity instanceof Ship
                && ((Ship) entity).isChameleonHullActive()) {
            image = ChameleonHull.getBallSprite();
        }

        backBufferGraphics.setColor(entity.getColor());
        for (int i = 0; i < image.length; i++)
            for (int j = 0; j < image[i].length; j++)
                if (image[i][j])
                    backBufferGraphics.drawRect(positionX + i * 2, positionY
                            + j * 2, 1, 1);
    }

    /**
     * Draws multi-colored sprite pixels at the gameplay sprite scale.
     *
     * @param pixels
     *                  Pixel colors indexed [x][y]; null where empty.
     * @param positionX
     *                  Coordinates for the left side of the image.
     * @param positionY
     *                  Coordinates for the upper side of the image.
     */
    private void drawPixels(final Color[][] pixels, final int positionX,
            final int positionY) {
        for (int i = 0; i < pixels.length; i++)
            for (int j = 0; j < pixels[i].length; j++)
                if (pixels[i][j] != null) {
                    backBufferGraphics.setColor(pixels[i][j]);
                    backBufferGraphics.drawRect(positionX + i * 2,
                            positionY + j * 2, 1, 1);
                }
    }

    /**
     * For debugging purpouses, draws the canvas borders.
     *
     * @param screen
     *               Screen to draw in.
     */
    @SuppressWarnings("unused")
    private void drawBorders(final Screen screen) {
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawLine(0, 0, screen.getWidth() - 1, 0);
        backBufferGraphics.drawLine(0, 0, 0, screen.getHeight() - 1);
        backBufferGraphics.drawLine(screen.getWidth() - 1, 0,
                screen.getWidth() - 1, screen.getHeight() - 1);
        backBufferGraphics.drawLine(0, screen.getHeight() - 1,
                screen.getWidth() - 1, screen.getHeight() - 1);
    }

    /**
     * For debugging purpouses, draws a grid over the canvas.
     *
     * @param screen
     *               Screen to draw in.
     */
    @SuppressWarnings("unused")
    private void drawGrid(final Screen screen) {
        backBufferGraphics.setColor(Color.DARK_GRAY);
        for (int i = 0; i < screen.getHeight() - 1; i += 2)
            backBufferGraphics.drawLine(0, i, screen.getWidth() - 1, i);
        for (int j = 0; j < screen.getWidth() - 1; j += 2)
            backBufferGraphics.drawLine(j, 0, j, screen.getHeight() - 1);
    }

    /**
     * Draws current score on screen.
     *
     * @param screen
     *               Screen to draw on.
     * @param score
     *               Current score.
     */
    public void drawScore(final Screen screen, final int score) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);
        String scoreString = String.format("%04d", score);

        int scoreX = screen.getWidth() - 60;
        drawIcon(Icon.SCORE, scoreX - Icon.SCORE.getWidth() * 2 - 6, 12, 2);
        backBufferGraphics.drawString(scoreString, scoreX, 25);
    }

    /** K, M, B, T, Q suffixes for shortened credits. */
    private static final String[] CREDIT_SUFFIXES = { "", "K", "M", "B", "T", "Q" };

    /**
     * Draws credits with a coin icon right-aligned just left of the score.
     * Up to 4 digits fit between the level label and the score.
     */
    public void drawCredits(final Screen screen, final long credits) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);
        String text = shortenCredits(credits);
        // Ends 8px left of the score icon drawn by drawScore().
        int textX = screen.getWidth() - 60 - Icon.SCORE.getWidth() * 2 - 14
                - backBufferGraphics.getFontMetrics().stringWidth(text);
        drawIcon(Icon.COIN, textX - 4 - Icon.COIN.getWidth() * 2, 12, 2);
        backBufferGraphics.drawString(text, textX, 25);
    }

    /** 5+ digits become 12.3K or 123K, rounded down so it never shows more. */
    private static String shortenCredits(final long credits) {
        if (credits < 10000)
            return Long.toString(credits);
        long unit = 1;
        int suffix = 0;
        while (suffix < CREDIT_SUFFIXES.length - 1 && credits / unit >= 1000) {
            unit *= 1000;
            suffix++;
        }
        long whole = credits / unit;
        return whole + (whole < 100 ? "." + credits % unit / (unit / 10) : "")
                + CREDIT_SUFFIXES[suffix];
    }

    /**
     * Draws remaining lives as heart icons on screen.
     *
     * @param screen
     *               Screen to draw on.
     * @param lives
     *               Current lives.
     */
    public void drawLives(final Screen screen, final int lives) {
        drawLives(screen, lives, lives);
    }

    /**
     * Draws remaining lives as heart icons and lost lives as broken hearts.
     *
     * @param screen
     *                 Screen to draw on.
     * @param lives
     *                 Current lives.
     * @param maxLives
     *                 Lives the player started with.
     */
    public void drawLives(final Screen screen, final int lives,
            final int maxLives) {
        for (int i = 0; i < Math.max(lives, maxLives); i++)
            drawIcon(i < lives ? Icon.LIFE : Icon.LIFE_LOST,
                    20 + 22 * i, 12, 2);
    }

    /**
     * Draws a UI icon in its own colors.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     */
    public void drawIcon(final Icon icon, final int positionX,
            final int positionY, final int scale) {
        drawIconPixels(icon, positionX, positionY, scale, null, false);
    }

    /**
     * Draws a UI icon in its own colors, darkened to show it is inactive.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     */
    public void drawDimmedIcon(final Icon icon, final int positionX,
            final int positionY, final int scale) {
        drawIconPixels(icon, positionX, positionY, scale, null, true);
    }

    /**
     * Draws a UI icon in the given color.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     * @param color
     *                  Color of the icon.
     */
    public void drawIcon(final Icon icon, final int positionX,
            final int positionY, final int scale, final Color color) {
        drawIconPixels(icon, positionX, positionY, scale, color, false);
    }

    private void drawIconPixels(final Icon icon, final int positionX,
            final int positionY, final int scale, final Color override,
            final boolean dimmed) {
        Color previous = backBufferGraphics.getColor();
        for (int i = 0; i < icon.getWidth(); i++)
            for (int j = 0; j < icon.getHeight(); j++) {
                Color color = icon.getColor(i, j);
                if (color == null)
                    continue;
                if (override != null)
                    color = override;
                if (dimmed)
                    color = new Color(color.getRed() / 2,
                            color.getGreen() / 2, color.getBlue() / 2);
                backBufferGraphics.setColor(color);
                backBufferGraphics.fillRect(positionX + i * scale,
                        positionY + j * scale, scale, scale);
            }
        backBufferGraphics.setColor(previous);
    }

    /**
     * Draws the pause overlay: a pause icon with a "Paused" label.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawPaused(final Screen screen) {
        int scale = 4;
        drawIcon(Icon.PAUSE, screen.getWidth() / 2 - Icon.PAUSE.getWidth()
                * scale / 2, screen.getHeight() / 2 - 50, scale);
        backBufferGraphics.setColor(GameColors.TEXT);
        drawCenteredBigString(screen, "Paused", screen.getHeight() / 2 + 20);
    }

    /**
     * Draws an icon to the left of a centered menu label. The selected
     * option's icon is in full color and bobs; the others are dimmed.
     *
     * @param screen
     *                 Screen to draw on.
     * @param icon
     *                 Icon to draw.
     * @param label
     *                 Centered label the icon sits next to.
     * @param labelY
     *                 Baseline of the label.
     * @param selected
     *                 Whether the option is selected.
     */
    private void drawMenuIcon(final Screen screen, final Icon icon,
            final String label, final int labelY, final boolean selected) {
        int iconX = screen.getWidth() / 2
                - fontRegularMetrics.stringWidth(label) / 2
                - icon.getWidth() * 2 - 8;
        int iconY = labelY - 5 - icon.getHeight();
        if (selected)
            drawIcon(icon, iconX, iconY + (int) Math.round(
                    Math.sin(System.currentTimeMillis() / 150.0) * 2), 2);
        else
            drawDimmedIcon(icon, iconX, iconY, 2);
    }

    /**
     * Draws a grid of pixel colors.
     *
     * @param pixels
     *                  Pixel colors indexed [x][y]; null where empty.
     * @param positionX
     *                  Coordinates for the left side of the grid.
     * @param positionY
     *                  Coordinates for the upper side of the grid.
     * @param scale
     *                  Size in screen pixels of each grid pixel.
     */
    private void drawColorGrid(final Color[][] pixels, final int positionX,
            final int positionY, final int scale) {
        Color previous = backBufferGraphics.getColor();
        for (int i = 0; i < pixels.length; i++)
            for (int j = 0; j < pixels[i].length; j++)
                if (pixels[i][j] != null) {
                    backBufferGraphics.setColor(pixels[i][j]);
                    backBufferGraphics.fillRect(positionX + i * scale,
                            positionY + j * scale, scale, scale);
                }
        backBufferGraphics.setColor(previous);
    }

    /**
     * Draws current level progress on screen.
     *
     * @param screen
     *                    Screen to draw on.
     * @param level
     *                    Current level.
     * @param totalLevels
     *                    Total number of levels.
     */
    public void drawLevel(final Screen screen, final int level,
            final int totalLevels) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);

        String levelString = "LEVEL " + level + "/" + totalLevels;
        int textWidth = backBufferGraphics.getFontMetrics().stringWidth(levelString);
        int levelX = (screen.getWidth() - textWidth) / 2;

        backBufferGraphics.drawString(levelString, levelX, 25);
    }

    /**
     * Draws a thick line from side to side of the screen.
     *
     * @param screen
     *                  Screen to draw on.
     * @param positionY
     *                  Y coordinate of the line.
     */
    public void drawHorizontalLine(final Screen screen, final int positionY) {
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawLine(0, positionY, screen.getWidth(), positionY);
        backBufferGraphics.drawLine(0, positionY + 1, screen.getWidth(),
                positionY + 1);
    }

    /**
     * Draws the Orbital Annihilator: a growing energy charge on the ship's
     * nose while charging, then a full vertical beam going up from the ship.
     */
    public void drawOrbitalAnnihilator(final Ship ship, final int beamWidth,
            final int topY, final boolean firing, final float chargeProgress) {
        int centerX = ship.getPositionX() + ship.getWidth() / 2;
        int startY = ship.getPositionY();
        int beamX = centerX - beamWidth / 2;

        if (firing) {
            backBufferGraphics.setColor(new Color(255, 0, 0, 120));
            backBufferGraphics.fillRect(beamX - 4, topY, beamWidth + 8, startY - topY);

            backBufferGraphics.setColor(Color.RED);
            backBufferGraphics.fillRect(beamX, topY, beamWidth, startY - topY);

            backBufferGraphics.setColor(Color.WHITE);
            backBufferGraphics.fillRect(centerX - beamWidth / 4, topY, beamWidth / 2, startY - topY);
        } else {
            backBufferGraphics.setColor(new Color(255, 0, 0, 90));
            backBufferGraphics.fillRect(centerX - 1, topY, 2, startY - topY);

            int radius = 2 + (int) (chargeProgress * beamWidth / 2);
            boolean flicker = ((int) (chargeProgress * 20)) % 2 == 0;

            backBufferGraphics.setColor(flicker ? Color.WHITE : Color.RED);
            backBufferGraphics.fillOval(centerX - radius, startY - radius, radius * 2, radius * 2);
        }
    }

    /**
     * Draws the Orbital Annihilator recharge gauge under the score.
     *
     * @param screen
     *                 Screen to draw on.
     * @param progress
     *                 Recharge progress between 0 and 1.
     */
    public void drawAnnihilatorGauge(final Screen screen, final float progress) {
        int barWidth = 80;
        int barX = screen.getWidth() - barWidth - 20;
        int barY = 29;

        backBufferGraphics.setColor(Color.DARK_GRAY);
        backBufferGraphics.fillRect(barX, barY, barWidth, 6);

        backBufferGraphics.setColor(progress >= 1f ? Color.GREEN : Color.RED);
        backBufferGraphics.fillRect(barX, barY, (int) (barWidth * progress), 6);
    }

    /**
     * Draws game title.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawTitle(final Screen screen) {
        String titleString = "Invaders";
        String instructionsString = "select with w+s / arrows, confirm with space";

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, instructionsString,
                screen.getHeight() / 2);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, titleString, screen.getHeight() / 3);

        long time = System.currentTimeMillis();
        int scale = 4;
        int shipX = screen.getWidth() / 2 - Icon.TITLE_SHIP.getWidth()
                * scale / 2;
        int shipY = screen.getHeight() / 3 - 40
                - (Icon.TITLE_SHIP.getHeight() + 3) * scale
                + (int) Math.round(Math.sin(time / 400.0) * 3);
        drawIcon(Icon.TITLE_SHIP, shipX, shipY, scale);
        drawIcon((time / 90) % 2 == 0 ? Icon.TITLE_FLAME_LONG
                : Icon.TITLE_FLAME_SHORT, shipX,
                shipY + Icon.TITLE_SHIP.getHeight() * scale, scale);
    }

    /**
     * Draws game results.
     *
     * @param screen
     *                       Screen to draw on.
     * @param score
     *                       Score obtained.
     * @param livesRemaining
     *                       Lives remaining when finished.
     * @param shipsDestroyed
     *                       Total ships destroyed.
     * @param accuracy
     *                       Total accuracy.
     * @param isNewRecord
     *                       If the score is a new high score.
     */
    public void drawResults(final Screen screen, final int score,
            final int livesRemaining, final int shipsDestroyed,
            final float accuracy, final boolean isNewRecord) {
        String scoreString = String.format("score %04d", score);
        String livesRemainingString = "lives remaining " + livesRemaining;
        String shipsDestroyedString = "enemies destroyed " + shipsDestroyed;
        String accuracyString = String
                .format("accuracy %.2f%%", accuracy * 100);

        int resultsY;

        if (isNewRecord)
            resultsY = screen.getHeight() / 4;
        else
            resultsY = getNormalGameOverTitleY(screen)
                    + fontBigMetrics.getHeight()
                    + fontRegularMetrics.getHeight() * 2;

        backBufferGraphics.setColor(Color.WHITE);

        drawCenteredRegularString(screen, scoreString, resultsY);

        drawCenteredRegularString(
                screen,
                livesRemainingString,
                resultsY + fontRegularMetrics.getHeight() * 2);

        drawCenteredRegularString(
                screen,
                shipsDestroyedString,
                resultsY + fontRegularMetrics.getHeight() * 4);

        drawCenteredRegularString(
                screen,
                accuracyString,
                resultsY + fontRegularMetrics.getHeight() * 6);
    }

    /**
     * Draws basic content of game over screen.
     *
     * @param screen
     *                     Screen to draw on.
     * @param acceptsInput
     *                     If the screen accepts input.
     * @param isNewRecord
     *                     If the score is a new high score.
     * @param gameCleared
     *                     If the game was cleared successfully.
     */
    public void drawGameOver(final Screen screen, final boolean acceptsInput,
            final boolean isNewRecord, final boolean gameCleared) {
        String gameOverString = gameCleared ? "Game Clear" : "Game Over";
        String continueOrExitString = "Press Space to play again, Escape to exit";

        int gameOverY;

        if (isNewRecord)
            gameOverY = screen.getHeight() / 4
                    - fontBigMetrics.getHeight() * 2;
        else
            gameOverY = getNormalGameOverTitleY(screen);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, gameOverString, gameOverY);

        if (acceptsInput)
            backBufferGraphics.setColor(Color.GREEN);
        else
            backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, continueOrExitString,
                screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 10);
    }

    /**
     * Draws which stage was failed, right below the game over title.
     *
     * @param screen
     *                    Screen to draw on.
     * @param stage
     *                    Number of the stage that was failed.
     * @param isNewRecord
     *                    If the score is a new high score (changes the layout).
     */
    public void drawStageFailed(final Screen screen, final int stage,
            final boolean isNewRecord) {
        int stageFailedY;

        if (isNewRecord)
            stageFailedY = screen.getHeight() / 4
                    - fontBigMetrics.getHeight();
        else
            stageFailedY = getNormalGameOverTitleY(screen)
                    + fontBigMetrics.getHeight();

        backBufferGraphics.setColor(Color.RED);

        drawCenteredRegularString(
                screen,
                "Stage " + stage + " Failed",
                stageFailedY);
    }

    /** Draws the game mode as one more row of the results list. */
    public void drawModeLabel(final Screen screen, final String modeLabel,
            final boolean isNewRecord) {
        int height = isNewRecord ? 4 : 2;
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, modeLabel, screen.getHeight()
                / height + fontRegularMetrics.getHeight() * 8);
    }

    /**
     * Draws high score screen title and instructions.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawHighScoreMenu(final Screen screen) {
        String highScoreString = "High Scores";
        String instructionsString = "Press Space to return";

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, highScoreString, screen.getHeight() / 8);

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, instructionsString,
                screen.getHeight() / 5);
    }

    /**
     * Draws high scores.
     *
     * @param screen
     *                   Screen to draw on.
     * @param highScores
     *                   List of high scores.
     */
    public void drawHighScores(final Screen screen,
            final List<Score> highScores) {
        backBufferGraphics.setColor(Color.WHITE);
        int i = 0;
        String scoreString = "";

        for (Score score : highScores) {
            scoreString = String.format("%s        %04d", score.getName(),
                    score.getScore());
            int rowY = screen.getHeight() / 4
                    + fontRegularMetrics.getHeight() * (i + 1) * 2;
            drawCenteredRegularString(screen, scoreString, rowY);
            drawColorGrid(RankBadge.forRank(i + 1), screen.getWidth() / 2
                    - fontRegularMetrics.stringWidth(scoreString) / 2
                    - RankBadge.WIDTH * 2 - 12,
                    rowY - 5 - RankBadge.HEIGHT - 4, 2);
            i++;
        }
    }

    /**
     * Draws a centered string on regular font.
     *
     * @param screen
     *               Screen to draw on.
     * @param string
     *               String to draw.
     * @param height
     *               Height of the drawing.
     */
    public void drawCenteredRegularString(final Screen screen,
            final String string, final int height) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.drawString(string, screen.getWidth() / 2
                - fontRegularMetrics.stringWidth(string) / 2, height);
    }

    /**
     * Draws a centered string on big font.
     *
     * @param screen
     *               Screen to draw on.
     * @param string
     *               String to draw.
     * @param height
     *               Height of the drawing.
     */
    public void drawCenteredBigString(final Screen screen, final String string,
            final int height) {
        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.drawString(string, screen.getWidth() / 2
                - fontBigMetrics.stringWidth(string) / 2, height);
    }

    /**
     * Countdown to game start.
     *
     * @param screen
     *                  Screen to draw on.
     * @param level
     *                  Level number.
     * @param number
     *                  Countdown number.
     * @param bonusLife
     *                  Checks if a bonus life is received.
     */
    public void drawCountDown(final Screen screen, final int level,
            final int number, final boolean bonusLife) {
        String title = "Level " + level;
        if (bonusLife)
            title += " - Bonus life!";

        drawCountDown(screen, title, number);
    }

    /**
     * Draws the countdown with the given title.
     *
     * @param screen
     *               Screen to draw on.
     * @param title
     *               Title shown before the countdown numbers.
     * @param number
     *               Countdown number.
     */
    public void drawCountDown(final Screen screen, final String title,
            final int number) {
        int rectWidth = screen.getWidth();
        int rectHeight = screen.getHeight() / 6;

        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(0,
                screen.getHeight() / 2 - rectHeight / 2,
                rectWidth, rectHeight);
        backBufferGraphics.setColor(Color.GREEN);

        if (number >= 4)
            drawCenteredBigString(screen, title,
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
        else if (number != 0)
            drawCenteredBigString(screen, Integer.toString(number),
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
        else
            drawCenteredBigString(screen, "GO!",
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
    }

    /** Draws the shop cards and purchase/save feedback. */
    public void drawShop(final Screen screen, final List<ShopProduct> upgrades,
            final List<ShopProduct> unlocks, final PlayerAssetsState assets, final int section,
            final int upgradeIndex, final int unlockIndex, final int actionIndex,
            final String feedback, final boolean purchaseSucceeded, final SaveStatus saveStatus) {
        int width = screen.getWidth();
        int height = screen.getHeight();
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Shop", 34);
        backBufferGraphics.setColor(Color.WHITE);
        String credits = "Credits: " + assets.getBalance();
        drawCenteredRegularString(screen, credits, 61);
        drawIcon(Icon.COIN, width / 2 - fontRegularMetrics.stringWidth(credits) / 2
                - Icon.COIN.getWidth() * 2 - 6, 47, 2);

        int cardSize = Math.max(76, Math.min(120, Math.min((width - 80) / 3, (height - 248) / 2)));
        drawShopShelf(screen, upgrades, assets, "Upgrades", "No upgrades available",
                96, cardSize, upgradeIndex, section == 0);
        drawShopShelf(screen, unlocks, assets, "Unlocks", "No unlocks available",
                132 + cardSize, cardSize, unlockIndex, section == 1);

        int actionsY = height - 96;
        drawShopText("Back (Esc)", 31, actionsY, 12,
                section == 2 && actionIndex == 0 ? Color.GREEN : Color.WHITE, 120);
        if (saveStatus == SaveStatus.FAILED) {
            drawShopText("Retry save (R)", width / 2, actionsY, 12,
                    section == 2 && actionIndex == 1 ? Color.GREEN : Color.WHITE, width / 2 - 24);
        }
        backBufferGraphics.setColor(purchaseSucceeded ? Color.GREEN : Color.ORANGE);
        drawShopCentered(screen, feedback, height - 70);
        String saveMessage = saveStatus == SaveStatus.FAILED ? "Save failed. Progress kept this session."
                : saveStatus == SaveStatus.SAVED ? "Progress saved." : "";
        backBufferGraphics.setColor(saveStatus == SaveStatus.FAILED ? Color.ORANGE : Color.GRAY);
        drawShopCentered(screen, saveMessage, height - 52);
        backBufferGraphics.setColor(Color.GRAY);
        drawShopCentered(screen, "Up/Down: section   Left/Right: product", height - 37);
        drawShopCentered(screen, "WASD also works   Space/Enter: confirm", height - 21);
    }

    private void drawShopShelf(Screen screen, List<ShopProduct> products, PlayerAssetsState assets,
            String heading, String emptyText, int top, int size, int index, boolean active) {
        int width = screen.getWidth();
        drawShopText(heading, 31, top - 12, 12, active ? Color.GREEN : Color.WHITE, width - 110);
        String count = products.isEmpty() ? "0 / 0" : (index + 1) + " / " + products.size();
        drawShopText(count, width - 70, top - 12, 10, Color.GRAY, 52);
        if (products.isEmpty()) {
            backBufferGraphics.setColor(Color.DARK_GRAY);
            backBufferGraphics.drawRect(31, top, width - 63, size);
            backBufferGraphics.setColor(Color.GRAY);
            drawShopCentered(screen, emptyText, top + size / 2);
            return;
        }

        int first = index / 3 * 3;
        int end = Math.min(products.size(), first + 3);
        int rowWidth = (end - first) * size + (end - first - 1) * 10;
        int startX = (width - rowWidth) / 2;
        for (int productIndex = first; productIndex < end; productIndex++) {
            drawShopCard(products.get(productIndex), assets,
                    startX + (productIndex - first) * (size + 10), top, size,
                    active && productIndex == index);
        }
        if (products.size() > 1) {
            int cy = top + size / 2;
            backBufferGraphics.setColor(active ? Color.GREEN : Color.GRAY);
            backBufferGraphics.fillPolygon(new int[] { 12, 18, 18 }, new int[] { cy, cy - 5, cy + 5 }, 3);
            backBufferGraphics.fillPolygon(new int[] { width - 13, width - 19, width - 19 },
                    new int[] { cy, cy - 5, cy + 5 }, 3);
        }
    }

    private void drawShopCard(ShopProduct product, PlayerAssetsState assets,
            int x, int y, int size, boolean selected) {
        backBufferGraphics.setColor(selected ? Color.GREEN : Color.DARK_GRAY);
        backBufferGraphics.drawRect(x, y, size, size);
        boolean owned = assets.getUnlockedContentIds().contains(product.getId());
        boolean available = product.isAvailableByDefault();
        String badge = available ? "Available" : owned ? "Owned" : "";
        drawShopText(badge, x + 7, y + 12, 9, Color.GRAY, size - 14);
        backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
        drawShopProductIcon(product, x + size / 2, y + 18);
        drawShopText(product.getName(), x + 9, y + 60, 11,
                selected ? Color.GREEN : Color.WHITE, size - 18);
        drawShopDescription(product.getDescription(), x + 9, y + 75, size - 18);
        backBufferGraphics.setColor(Color.DARK_GRAY);
        backBufferGraphics.drawLine(x + 9, y + size - 25, x + size - 9, y + size - 25);
        String price = available || owned ? "Unlocked" : product.getPrice() + " C";
        drawShopText(price, x + 9, y + size - 9, 11, available || owned ? Color.GRAY : Color.WHITE, size - 18);
    }

    private void drawShopProductIcon(ShopProduct product, int centerX, int y) {
        if (product.getShipType() != null) {
            boolean[][] sprite = ShipSprites.forType(product.getShipType());
            backBufferGraphics.setColor(Color.WHITE);
            for (int x = 0; x < sprite.length; x++) {
                for (int row = 0; row < sprite[x].length; row++) {
                    if (sprite[x][row]) {
                        backBufferGraphics.fillRect(centerX - sprite.length + x * 2, y + 8 + row * 2, 2, 2);
                    }
                }
            }
            return;
        }
        PermanentUpgrade upgrade = PermanentUpgrade.forProduct(product.getId());
        if (upgrade == null) {
            drawShopCategoryIcon(product.getCategory() == Category.UNLOCKABLE, centerX - 10, y + 2);
            return;
        }
        Icon icon = switch (upgrade) {
        case HULL -> Icon.UPGRADE_HULL;
        case ENGINE -> Icon.UPGRADE_ENGINE;
        case RELOADER -> Icon.UPGRADE_RELOADER;
        };
        drawIcon(icon, centerX - icon.getWidth(), y, 2);
    }

    /** Neutral placeholders until content owners supply agreed product artwork. */
    private void drawShopCategoryIcon(boolean permanent, int x, int y) {
        if (permanent) {
            backBufferGraphics.drawRect(x + 5, y, 10, 9);
            backBufferGraphics.fillRect(x, y + 8, 21, 15);
            backBufferGraphics.setColor(Color.BLACK);
            backBufferGraphics.fillRect(x + 9, y + 12, 3, 7);
        } else {
            backBufferGraphics.drawRect(x, y + 3, 20, 19);
            backBufferGraphics.drawRect(x + 1, y + 4, 18, 17);
            backBufferGraphics.drawLine(x + 1, y + 9, x + 19, y + 9);
            backBufferGraphics.drawRect(x + 7, y + 1, 6, 4);
        }
    }

    private void drawShopDescription(String description, int x, int y, int width) {
        backBufferGraphics.setFont(fontRegular.deriveFont(10f));
        backBufferGraphics.setColor(Color.GRAY);
        FontMetrics metrics = backBufferGraphics.getFontMetrics();
        String remaining = description.trim();
        for (int line = 0; line < 2 && !remaining.isEmpty(); line++) {
            String text = remaining;
            if (line == 0 && metrics.stringWidth(text) > width) {
                int end = text.length();
                while (end > 1 && metrics.stringWidth(text.substring(0, end)) > width)
                    end--;
                int space = text.lastIndexOf(' ', end);
                if (space > 0)
                    end = space;
                text = remaining.substring(0, end);
                remaining = remaining.substring(end).trim();
            } else {
                remaining = "";
            }
            drawShopLabel(text, x, y + line * 12, width);
        }
    }

    private void drawShopText(String text, int x, int y, int size, Color color, int width) {
        backBufferGraphics.setFont(fontRegular.deriveFont((float) size));
        backBufferGraphics.setColor(color);
        drawShopLabel(text, x, y, width);
    }

    private void drawShopCentered(final Screen screen, final String text, final int y) {
        Font fitted = fontRegular.deriveFont(11f);
        while (fitted.getSize() > 9
                && backBufferGraphics.getFontMetrics(fitted).stringWidth(text) > screen.getWidth() - 32) {
            fitted = fitted.deriveFont((float) fitted.getSize() - 1);
        }
        backBufferGraphics.setFont(fitted);
        int x = Math.max(16, (screen.getWidth() - backBufferGraphics.getFontMetrics().stringWidth(text)) / 2);
        drawShopLabel(text, x, y, screen.getWidth() - x - 16);
    }

    private void drawShopLabel(final String text, final int x, final int y, final int maxWidth) {
        String label = text;
        FontMetrics metrics = backBufferGraphics.getFontMetrics();
        if (metrics.stringWidth(label) > maxWidth) {
            while (!label.isEmpty() && metrics.stringWidth(label + "...") > maxWidth) {
                label = label.substring(0, label.length() - 1);
            }
            label += "...";
        }
        backBufferGraphics.drawString(label, x, y);
    }

    public void drawSettingsMenu(final Screen screen, final int selectedOption) {
        String[] options = SETTINGS_MENU_OPTIONS;
        Icon[] icons = { Icon.AUDIO, Icon.CONTROLS, Icon.GAMEPLAY, Icon.BACK };

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Settings", screen.getHeight() / 5);
        drawIcon(Icon.SETTINGS, screen.getWidth() / 2
                - fontBigMetrics.stringWidth("Settings") / 2
                - Icon.SETTINGS.getWidth() * 3 - 10,
                screen.getHeight() / 5 - 22, 3, Color.GREEN);

        for (int i = 0; i < options.length; i++) {
            boolean selected = i == selectedOption;
            int labelY = screen.getHeight() / 2
                    + i * fontRegularMetrics.getHeight() * 2;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, options[i], labelY);
            drawMenuIcon(screen, icons[i], options[i], labelY, selected);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, "Up/Down: select  Space: confirm",
                screen.getHeight() / 8 * 7);
    }

    public void drawControlsSettings(final Screen screen,
            final int selectedOption, final boolean waitingForKey) {
        String[] labels = getControlsSettingsLabels(selectedOption,
                waitingForKey);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Controls", screen.getHeight() / 5);

        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(
                    i == selectedOption ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 2
                            + i * fontRegularMetrics.getHeight() * 2);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, "Up/Down: select  Space: change",
                screen.getHeight() / 8 * 7);
    }

    public void drawGameplaySettings(final Screen screen, final int selectedOption) {
        String[] options = GAMEPLAY_SETTINGS_OPTIONS;

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Gameplay", screen.getHeight() / 5);

        for (int i = 0; i < options.length; i++) {
            backBufferGraphics.setColor(
                    i == selectedOption ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, options[i],
                    screen.getHeight() / 2
                            + i * fontRegularMetrics.getHeight() * 2);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, "Up/Down: select  Space: confirm",
                screen.getHeight() / 8 * 7);
    }

    /**
     * Draws the audio settings screen.
     *
     * @param screen
     *                  Screen to draw on.
     * @param option
     *                  Option selected.
     * @param bgmVolume
     *                  Current BGM volume, from 0 to 100.
     * @param sfxVolume
     *                  Current SFX volume, from 0 to 100.
     * @param muted
     *                  If the audio is muted.
     */
    public void drawAudioSettings(final Screen screen, final int option,
            final int bgmVolume, final int sfxVolume, final boolean muted) {
        String titleString = "Audio Settings";
        String selectString = "Up/Down: select";
        String adjustString = "Left/Right: adjust  M: mute";
        String returnString = "Press Esc to return";
        // In the order of the options: BGM volume, SFX volume, mute.
        String[] optionStrings = { "BGM Volume", "SFX Volume", "Mute" };
        // Values shown on the right side of each option.
        String[] valueStrings = { Integer.toString(bgmVolume),
                Integer.toString(sfxVolume), muted ? "ON" : "OFF" };
        // Volumes shown as bars. Only the volume options have a bar.
        int[] barVolumes = { bgmVolume, sfxVolume };

        // Centered column that holds the option names, values, and bars.
        int columnWidth = AUDIO_COLUMN_WIDTH;
        int columnLeft = screen.getWidth() / 2 - columnWidth / 2;
        // Vertical position of the first option and the space between options.
        int firstOptionY = screen.getHeight() / 3;
        int optionSpacing = fontRegularMetrics.getHeight() * 4;
        // Mute is the last option.
        int muteIndex = optionStrings.length - 1;
        // Space taken by the widest value, so the brackets do not move.
        int valueRight = columnLeft + columnWidth;
        int valueLeft = valueRight - fontRegularMetrics.stringWidth("< 100 >");
        int valueCenter = (valueLeft + valueRight) / 2;
        // Volume bars are drawn right below the volume names.
        int barGap = AUDIO_BAR_GAP;
        int barHeight = AUDIO_BAR_HEIGHT;

        // Title.
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, titleString, screen.getHeight() / 8);

        // Control instructions.
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, selectString,
                screen.getHeight() / 5);
        drawCenteredRegularString(screen, adjustString,
                screen.getHeight() / 5 + fontRegularMetrics.getHeight());

        // Options, one row each.
        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < optionStrings.length; i++) {
            // The selected option is green and the others are white.
            if (i == option)
                backBufferGraphics.setColor(Color.GREEN);
            else
                backBufferGraphics.setColor(Color.WHITE);
            // Option name on the left edge of the column.
            backBufferGraphics.drawString(optionStrings[i], columnLeft,
                    firstOptionY + optionSpacing * i);
            // Value centered in the value space.
            backBufferGraphics.drawString(valueStrings[i], valueCenter
                    - fontRegularMetrics.stringWidth(valueStrings[i]) / 2,
                    firstOptionY + optionSpacing * i);
            // Brackets at fixed positions around the volume values, to show
            // that they can be changed with Left and Right.
            if (i != muteIndex) {
                backBufferGraphics.drawString("<", valueLeft,
                        firstOptionY + optionSpacing * i);
                backBufferGraphics.drawString(">", valueRight
                        - fontRegularMetrics.stringWidth(">"),
                        firstOptionY + optionSpacing * i);
            }
            // Strike through the volume numbers while muted.
            if (muted && i != muteIndex) {
                int numberWidth = fontRegularMetrics
                        .stringWidth(valueStrings[i]);
                backBufferGraphics.fillRect(valueCenter - numberWidth / 2 - 2,
                        firstOptionY + optionSpacing * i
                                - fontRegularMetrics.getAscent() / 2,
                        numberWidth + 4, 2);
            }
            // Volume bar below the volume names, gray while muted.
            if (i < barVolumes.length) {
                Color barColor;
                if (muted)
                    barColor = Color.GRAY;
                else if (i == option)
                    barColor = Color.GREEN;
                else
                    barColor = Color.WHITE;
                drawVolumeBar(columnLeft,
                        firstOptionY + optionSpacing * i + barGap,
                        columnWidth, barHeight, barVolumes[i], barColor);
            }
        }

        // Return instruction near the bottom.
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, returnString,
                screen.getHeight() / 8 * 7);
    }

    /**
     * Draws a horizontal volume bar.
     * The whole bar is drawn in dark gray, and the part up to the volume is
     * filled with the given color.
     *
     * @param positionX
     *                  Left edge of the bar.
     * @param positionY
     *                  Top edge of the bar.
     * @param width
     *                  Width of the bar at volume 100.
     * @param height
     *                  Height of the bar.
     * @param volume
     *                  Volume to show, from 0 to 100.
     * @param color
     *                  Color of the filled part.
     */
    private void drawVolumeBar(final int positionX, final int positionY,
            final int width, final int height, final int volume,
            final Color color) {
        backBufferGraphics.setColor(Color.DARK_GRAY);
        backBufferGraphics.fillRect(positionX, positionY, width, height);

        backBufferGraphics.setColor(color);
        backBufferGraphics.fillRect(positionX, positionY,
                width * volume / 100, height);
    }

    /**
     * Draws the Orbital Annihilator item as a short laser bolt that falls:
     * the tail fades out at the top, and a bright pointed head blinks at the
     * front (bottom).
     *
     * @param entity
     *                  Item to be drawn.
     * @param positionX
     *                  Coordinates for the left side of the item.
     * @param positionY
     *                  Coordinates for the upper side of the item.
     */
    private void drawFallingLaser(final Entity entity, final int positionX,
            final int positionY) {
        int w = entity.getWidth();
        int h = entity.getHeight();
        boolean blink = (System.currentTimeMillis() / 150) % 2 == 0;

        // Body: red beam with a white core, more opaque towards the head.
        int slices = h / 2;
        for (int i = 0; i < slices; i++) {
            int y = positionY + i * 2;
            int alpha = 60 + (int) (195.0 * i / slices);

            backBufferGraphics.setColor(new Color(255, 0, 0, alpha / 3));
            backBufferGraphics.fillRect(positionX, y, w, 2);

            backBufferGraphics.setColor(new Color(255, 0, 0, alpha));
            backBufferGraphics.fillRect(positionX + 3, y, w - 6, 2);

            backBufferGraphics.setColor(new Color(255, 255, 255, alpha));
            backBufferGraphics.fillRect(positionX + w / 2 - 1, y, 2, 2);
        }

        // Head: bright triangle pointing down, blinking.
        int headTop = positionY + h - 8;
        int[] xs = { positionX + 1, positionX + w - 1, positionX + w / 2 };
        int[] ys = { headTop, headTop, positionY + h };
        backBufferGraphics.setColor(blink ? Color.WHITE : Color.YELLOW);
        backBufferGraphics.fillPolygon(xs, ys, 3);
    }

    /**
     * Draws a pixel-art sprite (2x2 pixels per cell) in the current color,
     * the same way as the other entities of the game.
     */
    private void drawSprite(final boolean[][] image, final int x,
            final int y) {
        for (int i = 0; i < image.length; i++) {
            for (int j = 0; j < image[i].length; j++) {
                if (image[i][j]) {
                    backBufferGraphics.drawRect(x + i * 2, y + j * 2, 1, 1);
                }
            }
        }
    }

    /**
     * Draws the Cluster Grenade item with its pixel-art sprite, so that
     * players recognize it when it falls.
     *
     * @param x Coordinate for the left side of the item.
     * @param y Coordinate for the upper side of the item.
     */
    public void drawGrenadeItem(final int x, final int y) {
        backBufferGraphics.setColor(Color.WHITE);
        drawSprite(ClusterGrenadeItem.getSprite(), x, y);
    }

    /**
     * Draws a grenade launched by the player ship: a pixel-art grenade
     * with a halo.
     *
     * @param grenade Grenade to draw.
     */
    public void drawGrenade(final Grenade grenade) {
        Graphics g = backBufferGraphics;
        int x = grenade.getPositionX();
        int y = grenade.getPositionY();
        int w = grenade.getWidth();
        int h = grenade.getHeight();

        // Halo around the grenade.
        g.setColor(new Color(255, 140, 0, 50));
        g.fillOval(x - 10, y - 10, w + 20, h + 20);
        g.setColor(new Color(255, 170, 0, 100));
        g.fillOval(x - 5, y - 5, w + 10, h + 10);

        // Grenade.
        g.setColor(grenade.getColor());
        drawSprite(Grenade.getSprite(), x, y);
    }

    /**
     * Draws a fragment of an exploded grenade: a pixel-art spark with a halo
     * that fades out.
     *
     * @param fragment Fragment to draw.
     */
    public void drawGrenadeFragment(final GrenadeFragment fragment) {
        Graphics g = backBufferGraphics;
        int x = fragment.getPositionX();
        int y = fragment.getPositionY();
        int cx = x + fragment.getWidth() / 2;
        int cy = y + fragment.getHeight() / 2;

        // The halo fades out while the fragment flies away.
        double fade = 1.0 - 0.6 * fragment.getProgress();
        g.setColor(new Color(255, 140, 0, (int) (60 * fade)));
        g.fillOval(cx - 11, cy - 11, 22, 22);
        g.setColor(new Color(255, 190, 0, (int) (110 * fade)));
        g.fillOval(cx - 7, cy - 7, 14, 14);

        // Spark with a bright center.
        g.setColor(fragment.getColor());
        drawSprite(GrenadeFragment.getSprite(), x, y);
        g.setColor(Color.WHITE);
        g.fillRect(cx - 1, cy - 1, 2, 2);
    }

    /**
     * Draws the number of grenades left, when the ship has some.
     *
     * @param screen Screen to draw on.
     * @param grenades Number of grenades left.
     */
    public void drawGrenadeCount(final Screen screen, final int grenades) {
        if (grenades <= 0) {
            return;
        }
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.ORANGE);
        backBufferGraphics.drawString("GRENADE [G] x" + grenades, 10, 55);
    }

    /**
     * Draws the names of the active buffs, top right, under the separator.
     *
     * @param screen Screen to draw on.
     * @param lines Buff names to show.
     */
    public void drawBuffs(final Screen screen, final List<String> lines) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.CYAN);
        int y = 62;
        for (String line : lines) {
            int textWidth = backBufferGraphics.getFontMetrics().stringWidth(line);
            backBufferGraphics.drawString(line,
                    screen.getWidth() - textWidth - 10, y);
            y += 20;
        }
    }

    /**
     * Draws a blinking message in the top bar when the Orbital Annihilator
     * is ready to be used.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawAnnihilatorReady(final Screen screen) {
        boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
        backBufferGraphics.setColor(blink ? Color.RED : Color.WHITE);
        drawCenteredRegularString(screen, "HOLD SPACE = LASER", 78);
    }

    /**
     * Dessine un bouclier protecteur autour du vaisseau.
     *
     * @param screen Écran de jeu cible.
     * @param ship   Vaisseau à entourer.
     */
    public void drawShield(final Screen screen, final Entity ship) {
        backBufferGraphics.setColor(new Color(0, 200, 255, 120)); // Cyan semi-transparent

        int padding = 4;
        int x = ship.getPositionX() - padding;
        int y = ship.getPositionY() - padding;
        int width = ship.getWidth() + (padding * 2);
        int height = ship.getHeight() + (padding * 2);

        // Dessine l'intérieur lumineux du bouclier
        backBufferGraphics.fillOval(x, y, width, height);

        // Dessine le contour plus net du bouclier
        backBufferGraphics.setColor(Color.CYAN);
        backBufferGraphics.drawOval(x, y, width, height);
    }

    private String getKeyDisplayText(final int keyCode) {
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT:
                return "Left Arrow";
            case java.awt.event.KeyEvent.VK_RIGHT:
                return "Right Arrow";
            case java.awt.event.KeyEvent.VK_UP:
                return "Up Arrow";
            case java.awt.event.KeyEvent.VK_DOWN:
                return "Down Arrow";
            case java.awt.event.KeyEvent.VK_SPACE:
                return "Space";
            default:
                if (keyCode >= java.awt.event.KeyEvent.VK_A
                        && keyCode <= java.awt.event.KeyEvent.VK_Z) {
                    return String.valueOf((char) keyCode);
                }
                return java.awt.event.KeyEvent.getKeyText(keyCode);
        }
    }

    public void initDrawing(final Screen screen) {
        backBuffer = new BufferedImage(screen.getWidth(), screen.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        graphics = frame == null ? null : frame.getGraphics();
        backBufferGraphics = backBuffer.getGraphics();

        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics
                .fillRect(0, 0, screen.getWidth(), screen.getHeight());
        screen.drawBackground(backBufferGraphics);

        fontRegularMetrics = backBufferGraphics.getFontMetrics(fontRegular);
        fontBigMetrics = backBufferGraphics.getFontMetrics(fontBig);

        // drawBorders(screen);
        // drawGrid(screen);
    }

    public void completeDrawing(final Screen screen) {
        try {
            if (graphics != null)
                graphics.drawImage(backBuffer, frame.getInsets().left,
                        frame.getInsets().top, frame);
        } finally {
            backBufferGraphics.dispose();
            if (graphics != null)
                graphics.dispose();
        }
    }

    public void drawNameInput(final Screen screen, final char[] name,
            final int nameCharSelected) {
        drawNameInputAt(screen, name, nameCharSelected, screen.getHeight()
                / 4 + fontRegularMetrics.getHeight() * 10,
                fontRegularMetrics.getHeight() * 2);
    }

    private void drawNameInputAt(final Screen screen, final char[] name,
            final int nameCharSelected, final int y, final int spacing) {
        String newRecordString = "New Record!";
        String introduceNameString = "Introduce name:";

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, newRecordString, y);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, introduceNameString, y + spacing);

        // 3 letters name.
        int positionX = screen.getWidth()
                / 2
                - (fontRegularMetrics.getWidths()[name[0]]
                        + fontRegularMetrics.getWidths()[name[1]]
                        + fontRegularMetrics.getWidths()[name[2]]
                        + fontRegularMetrics.getWidths()[' ']) / 2;

        for (int i = 0; i < 3; i++) {
            if (i == nameCharSelected)
                backBufferGraphics.setColor(Color.GREEN);
            else
                backBufferGraphics.setColor(Color.WHITE);

            positionX += fontRegularMetrics.getWidths()[name[i]] / 2;
            positionX = i == 0 ? positionX
                    : positionX
                            + (fontRegularMetrics.getWidths()[name[i - 1]]
                                    + fontRegularMetrics.getWidths()[' ']) / 2;

            backBufferGraphics.drawString(Character.toString(name[i]),
                    positionX,
                    y + spacing * 2);
        }
    }

    public void drawAchievementNotice(final Screen screen,
            final Achievement achievement) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, "Achievement unlocked: "
                + achievement.getName(), screen.getHeight() / 7);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, achievement.getCondition(),
                screen.getHeight() / 7 + fontRegularMetrics.getHeight());
    }
    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<Achievement> achievements, final boolean acceptsInput, final int failedStage) {
        drawAchievementResults(screen, score, lives, enemies, accuracy, isNewRecord, name, selected,
                achievements, acceptsInput, failedStage, false);
    }

    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<Achievement> achievements, final boolean acceptsInput, final int failedStage,
            final boolean gameCleared) {
        drawAchievementResults(screen, score, lives, enemies, accuracy,
                isNewRecord, name, selected, achievements, acceptsInput,
                failedStage, gameCleared, null);
    }

    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<Achievement> achievements, final boolean acceptsInput,
            final int failedStage, final boolean gameCleared, final String modeLabel) {
        int spacing = fontRegularMetrics.getHeight();
        int y = getAchievementResultsTitleY(screen, isNewRecord, failedStage,
                achievements.size());
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, gameCleared ? "Game Cleared!" : "Game Over", y);
        if (failedStage > 0) {
            y += spacing;
            backBufferGraphics.setColor(Color.RED);
            drawCenteredRegularString(screen, "Stage " + failedStage + " Failed", y);
        }
        y += spacing * 2;
        backBufferGraphics.setColor(Color.WHITE);
        String[] results = { "Score " + score, "Lives remaining " + lives,
                "Enemies destroyed " + enemies,
                String.format("Accuracy %.2f%%", accuracy * 100) };
        if (modeLabel != null)
            results = new String[] { results[0], results[1], results[2], results[3], modeLabel };
        for (String result : results) {
            drawCenteredRegularString(screen, result, y);
            y += spacing;
        }
        if (isNewRecord) {
            y += spacing;
            drawNameInputAt(screen, name, selected, y, spacing);
            y += spacing * 3;
        }
        y += spacing;
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, "New achievements:", y);
        backBufferGraphics.setColor(Color.WHITE);
        for (Achievement achievement : achievements) {
            y += spacing;
            drawCenteredRegularString(screen, achievement.getName(), y);
        }
        backBufferGraphics.setColor(acceptsInput ? Color.GREEN : Color.GRAY);
        drawCenteredRegularString(screen, "Space: play again   Esc: menu",
                screen.getHeight() - spacing);
    }

    public void drawAchievementList(final Screen screen, final RecordsService service, int y) {
        for (Achievement achievement : Achievement.values()) {
            Long unlocked = service.getUnlockTime(achievement);
            backBufferGraphics.setColor(unlocked == null ? Color.WHITE : Color.GREEN);
            drawCenteredRegularString(screen, achievement.getName() + (unlocked == null ? " [Locked]" : " [Unlocked]"),
                    y);
            backBufferGraphics.setColor(Color.GRAY);
            drawCenteredRegularString(screen, achievement.getCondition(), y + fontRegularMetrics.getHeight());
            if (unlocked != null)
                drawCenteredRegularString(screen, "Unlocked: "
                        + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(unlocked)),
                        y + fontRegularMetrics.getHeight() * 2);
            y += fontRegularMetrics.getHeight() * 3 + 4;
        }
    }

    public void drawMenu(final Screen screen, final int option) {
        String[] labels = {
            "Play", "Shop", "Hangar", "High scores",
            "Records & Achievements", "Settings", "Exit"
        };
        int[] codes = { 2, 10, 11, 3, 9, 4, 0 };
        Icon[] icons = {
                Icon.PLAY, Icon.SHOP, Icon.HANGAR, Icon.HIGH_SCORES,
                Icon.RECORDS, Icon.SETTINGS, Icon.EXIT
        };
        int spacing = fontRegularMetrics.getHeight() * 3 / 2;
        int firstY = screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 2;
        for (int i = 0; i < labels.length; i++) {
            boolean selected = option == codes[i];
            int labelY = firstY + i * spacing;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i], labelY);
            if (icons[i] != null)
                drawMenuIcon(screen, icons[i], labels[i], labelY, selected);
        }
    }

    public int getMenuOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        String[] labels = {
                "Play", "Shop", "Hangar", "High scores",
                "Records & Achievements", "Settings", "Exit"
        };
        int[] codes = { 2, 10, 11, 3, 9, 4, 0 };
        int fontHeight = fontRegularMetrics.getHeight();
        int spacing = fontHeight * 3 / 2;
        int firstY = screen.getHeight() / 2 + fontHeight * 2;

        for (int i = 0; i < labels.length; i++) {
            int textWidth = fontRegularMetrics.stringWidth(labels[i]);
            int left = screen.getWidth() / 2 - textWidth / 2;
            int baseline = firstY + i * spacing;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + fontRegularMetrics.getDescent();

            if (mouseX >= left && mouseX <= left + textWidth
                    && mouseY >= top && mouseY <= bottom)
                return codes[i];
        }

        return -1;
    }

    /**
     * Returns the settings menu option under the mouse.
     *
     * @return Index of the hovered option, or -1 if none.
     */
    public int getSettingsMenuOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        return getCenteredOptionAt(screen, SETTINGS_MENU_OPTIONS,
                screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
                mouseX, mouseY);
    }

    /**
     * Returns the controls settings option under the mouse.
     *
     * @return Index of the hovered option, or -1 if none.
     */
    public int getControlsSettingsOptionAt(final Screen screen,
            final int selectedOption, final boolean waitingForKey,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        return getCenteredOptionAt(screen,
                getControlsSettingsLabels(selectedOption, waitingForKey),
                screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
                mouseX, mouseY);
    }

    /**
     * Returns the gameplay settings option under the mouse.
     *
     * @return Index of the hovered option, or -1 if none.
     */
    public int getGameplaySettingsOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        return getCenteredOptionAt(screen, GAMEPLAY_SETTINGS_OPTIONS,
                screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
                mouseX, mouseY);
    }

    /**
     * Returns the audio settings option under the mouse. A volume option
     * covers its whole row, from its name down to its volume bar.
     *
     * @param optionCount
     *                    Number of options on the audio settings screen.
     * @return Index of the hovered option, or -1 if none.
     */
    public int getAudioSettingsOptionAt(final Screen screen,
            final int optionCount, final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        int columnLeft = screen.getWidth() / 2 - AUDIO_COLUMN_WIDTH / 2;
        int firstOptionY = screen.getHeight() / 3;
        int optionSpacing = fontRegularMetrics.getHeight() * 4;
        int muteIndex = optionCount - 1;

        if (mouseX < columnLeft || mouseX > columnLeft + AUDIO_COLUMN_WIDTH)
            return -1;

        for (int i = 0; i < optionCount; i++) {
            int baseline = firstOptionY + optionSpacing * i;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = i == muteIndex
                    ? baseline + fontRegularMetrics.getDescent()
                    : baseline + AUDIO_BAR_GAP + AUDIO_BAR_HEIGHT;

            if (mouseY >= top && mouseY <= bottom)
                return i;
        }

        return -1;
    }

    /**
     * Returns the index of the horizontally centered option under the mouse.
     *
     * @param labels
     *                Option texts, drawn one under the other.
     * @param firstY
     *                Baseline of the first option.
     * @param spacing
     *                Vertical space between two option baselines.
     * @return Index of the hovered option, or -1 if none.
     */
    private int getCenteredOptionAt(final Screen screen, final String[] labels,
            final int firstY, final int spacing,
            final int mouseX, final int mouseY) {
        for (int i = 0; i < labels.length; i++) {
            int textWidth = fontRegularMetrics.stringWidth(labels[i]);
            int left = screen.getWidth() / 2 - textWidth / 2;
            int baseline = firstY + i * spacing;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + fontRegularMetrics.getDescent();

            if (mouseX >= left && mouseX <= left + textWidth
                    && mouseY >= top && mouseY <= bottom)
                return i;
        }

        return -1;
    }

    /**
     * Builds the texts of the controls settings options, with their keys.
     *
     * @return One text per option: Move Left, Move Right, Fire, Back.
     */
    private String[] getControlsSettingsLabels(final int selectedOption,
            final boolean waitingForKey) {
        String[] labels = { "Move Left", "Move Right", "Fire", "Back" };
        String[] keys = {
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.LEFT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.RIGHT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.FIRE))
        };

        for (int i = 0; i < keys.length; i++) {
            labels[i] += ": ";
            labels[i] += waitingForKey && i == selectedOption
                    ? "Press a key (Esc to cancel)"
                    : keys[i];
        }

        return labels;
    }

    public void drawRecordsPage(final Screen screen,
            final RecordsService service, final int tab) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen,
                tab == 0 ? "Run Records" : "Achievements", screen.getHeight() / 9);
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen,
                "Left/Right: switch   Esc/Space: return", screen.getHeight() / 6);
        int y = screen.getHeight() / 6 + fontRegularMetrics.getHeight() * 2;
        if (tab == 0) {
            backBufferGraphics.setColor(Color.WHITE);
            drawCenteredRegularString(screen, "Personal bests", y);
            if (service.getRecordCount() == 0) {
                drawCenteredRegularString(screen, "No completed runs yet.",
                        y + fontRegularMetrics.getHeight());
            } else {
                drawCenteredRegularString(screen, "Highest score: "
                        + service.getHighestScore(), y + fontRegularMetrics.getHeight());
                drawCenteredRegularString(screen, "Most enemies: "
                        + service.getMostEnemiesDefeated(),
                        y + fontRegularMetrics.getHeight() * 2);
                drawCenteredRegularString(screen, "Longest survival: "
                        + service.getLongestSurvivalMillis() / 1000.0 + "s",
                        y + fontRegularMetrics.getHeight() * 3);
            }
            y += fontRegularMetrics.getHeight() * 4;
            drawCenteredRegularString(screen, "Top completed runs", y);
            List<RunRecord> runs = service.getTopRuns();
            if (runs.isEmpty()) {
                drawCenteredRegularString(screen, "No completed runs yet.",
                        y + fontRegularMetrics.getHeight() * 2);
            } else {
                int index = 1;
                for (RunRecord run : runs) {
                    y += fontRegularMetrics.getHeight();
                    drawCenteredRegularString(screen, String.format(
                            "%2d. %d pts  %d enemies  %.1fs", index++,
                            run.getScore(), run.getEnemiesDefeated(),
                            run.getActiveDurationMillis() / 1000.0), y);
                }
            }
        } else {
            drawAchievementList(screen, service, y);
        }
        backBufferGraphics.setColor(Color.GRAY);
        String status = service.getSaveError();
        String message = status == null
                ? (service.getLoadWarning() == null ? "Profile ready" : "Profile restored from backup")
                : (status.startsWith("Saving") ? "Saving profile..."
                        : "Profile error - check game log");
        drawCenteredRegularString(screen, message,
                screen.getHeight() - fontRegularMetrics.getHeight());
    }

    /**
     * Draws every live explosion particle.
     */
    public void drawParticles(final vfx.ParticleSystem particleSystem) {
        for (vfx.Particle p : particleSystem.getParticles()) {
            backBufferGraphics.setColor(p.getColor());
            backBufferGraphics.fillRect(p.getX(), p.getY(), p.getSize(),
                    p.getSize());
        }
    }

    /** Draws earned points without changing the existing score HUD. */
    public void drawFloatingScores(final FloatingScoreHud hud, final Screen screen) {
        hud.draw((java.awt.Graphics2D) backBufferGraphics,
                screen.getWidth(), screen.getHeight());
    }

    /**
     * Draws the ship selection screen, listing every selectable ship with its
     * stats and marking the highlighted one.
     *
     * @param screen
     *                      Screen to draw on.
     * @param selectedIndex
     *                      Index of the currently highlighted ship.
     * @param shipTypes
     *                      Ships available for selection, in display order.
     */
    public void drawShipSelection(final Screen screen, final ShipType[] shipTypes,
            final int selectedIndex) {
        String titleString = "SELECT SHIP";
        String instructionsString = Core.getShopController().isShipAvailable(shipTypes[selectedIndex])
                ? "SPACE: SELECT   ESC: BACK" : "UNLOCK IN SHOP   ESC: BACK";

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, titleString, screen.getHeight() / 6);

        int startY = screen.getHeight() / 3;
        int spacing = 85;

        for (int i = 0; i < shipTypes.length; i++) {
            ShipType type = shipTypes[i];
            boolean isSelected = (i == selectedIndex);
            boolean available = Core.getShopController().isShipAvailable(type);

            if (!available) {
                int scale = 3;
                drawIcon(Icon.LOCK, screen.getWidth() / 2 - Icon.LOCK.getWidth() * scale / 2,
                        startY + i * spacing - 50, scale, Color.GRAY);
            } else if (isSelected) {
                entity.Ship previewShip = new entity.Ship(screen.getWidth() / 2 - 13,
                        startY + i * spacing - 40, type);
                drawEntity(previewShip, screen.getWidth() / 2 - 13, startY + i * spacing - 40);
            }

            String name = (isSelected ? "> " : "") + type.getName()
                    + (available ? "" : " [LOCKED]") + (isSelected ? " <" : "");
            String stats = String.format("SPEED: %d | HP: %d | RELOAD: %dMS",
                    type.getSpeed(), type.getMaxLives(), type.getShootingInterval());

            backBufferGraphics.setColor(isSelected ? Color.GREEN : Color.GRAY);
            drawCenteredRegularString(screen, name, startY + i * spacing);
            drawCenteredRegularString(screen, stats, startY + i * spacing + 20);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, instructionsString, screen.getHeight() - 60);
    }

    /**
     * Returns the index of the ship under the mouse cursor.
     * Returns -1 if the cursor is outside the ship options.
     */
    public int getShipSelectionOptionAt(final Screen screen,
            final ShipType[] shipTypes, final int mouseX, final int mouseY) {

        if (fontRegularMetrics == null) {
            return -1;
        }

        int startY = screen.getHeight() / 3;
        int spacing = 85;

        for (int i = 0; i < shipTypes.length; i++) {
            int baseline = startY + i * spacing;

            // Detect the mouse within the ship name and stats area.
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + 20 + fontRegularMetrics.getDescent();

            if (mouseX >= screen.getWidth() / 4
                    && mouseX <= screen.getWidth() * 3 / 4
                    && mouseY >= top && mouseY <= bottom) {
                return i;
            }
        }

        return -1;
    }

    public void drawHangarMenu(final Screen screen, final int selectedOption) {
        String[] labels = { "Choose ship", "Choose color", "Back" };

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Hangar", screen.getHeight() / 5);

        int firstY = screen.getHeight() / 2;
        int spacing = fontRegularMetrics.getHeight() * 3;
        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(
                    i == selectedOption ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i], firstY + i * spacing);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen,
                "Up/Down: select   Space/Enter: confirm",
                screen.getHeight() - 50);
        drawCenteredRegularString(screen, "Esc: main menu",
                screen.getHeight() - 28);
    }

    public void drawGameModeMenu(final Screen screen, final int option) {
        String[] labels = { "Endless Mode", "Campaign Mode", "Back" };

        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Game Mode", screen.getHeight() / 3);

        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(i == option ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 3 + 60 + i * 40);
        }
    }
}
