package engine;

import campaign.CampaignStageData;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import screen.Screen;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Integrates campaign data, difficulty conversion and real formation construction.
 * Menu preferences and GameScreen's campaign/custom routing are outside this test.
 */
class CampaignFormationIntegrationTest {
    private static final int SPACING = 40;
    private static final int SIDE_MARGIN = 20;
    private static final int BOTTOM_MARGIN = 80;

    // Fixed viewport fixtures, not a claim about every platform's window insets.
    private static final int[][] SCREEN_SIZES = {{448, 520}, {432, 497}};

    @TestFactory
    Stream<DynamicTest> campaignPlacementSurvivesDifficultyAdjustment() {
        // Stage, columns, rows, initial X, initial Y: independent design fixtures.
        int[][] stages = {
            {1, 5, 4, 20, 100},
            {2, 5, 5, 60, 100},
            {3, 6, 5, 40, 120},
            {4, 6, 6, 80, 100},
            {5, 7, 6, 40, 120},
            {6, 7, 7, 40, 100},
            {7, 8, 7, 20, 100}
        };
        assertEquals(stages.length, CampaignStageData.getTotalStages(),
                "Update the placement fixtures when the campaign stage count changes");
        // Keep these multipliers in sync with GameplayConfig without accessing preferences.
        return Stream.of(stages).flatMap(stage -> Stream.of(
                placementCase(stage[0], stage[1], stage[2], stage[3], stage[4], "Easy", 1.25),
                placementCase(stage[0], stage[1], stage[2], stage[3], stage[4], "Normal", 1.0),
                placementCase(stage[0], stage[1], stage[2], stage[3], stage[4], "Hard", 0.8)));
    }

    private DynamicTest placementCase(int stage, int columns, int rows,
            int initialX, int initialY, String difficulty, double multiplier) {
        return dynamicTest("Stage " + stage + " / " + difficulty, () -> {
            // These expectations come from the agreed stage layout, not its getters.
            Set<Point> expected = new HashSet<>();
            for (int column = 0; column < columns; column++) {
                for (int row = 0; row < rows; row++) {
                    expected.add(new Point(initialX + column * SPACING,
                            initialY + row * SPACING));
                }
            }

            for (int[] size : SCREEN_SIZES) {
                Screen screen = new Screen(size[0], size[1], 60);
                String context = "Stage " + stage + " / " + difficulty
                        + " / screen " + size[0] + "x" + size[1];
                GameSettings configured = CampaignStageData.getStageSettings(stage);
                Set<Point> before = positions(new EnemyShipFormation(configured),
                        screen, columns * rows, context + " before difficulty");
                GameSettings adjusted = Core.applyDifficulty(configured, multiplier);
                Set<Point> after = positions(new EnemyShipFormation(adjusted),
                        screen, columns * rows, context + " after difficulty");

                assertEquals(expected, before, context + " configured grid");
                assertEquals(expected, after, context + " adjusted grid");
            }
        });
    }

    private Set<Point> positions(EnemyShipFormation formation, Screen screen,
            int expectedCount, String context) {
        formation.attach(screen);
        Set<Point> positions = new HashSet<>();
        int count = 0;
        for (EnemyShip enemy : formation) {
            int x = enemy.getPositionX();
            int y = enemy.getPositionY();
            assertTrue(positions.add(new Point(x, y)),
                    context + " duplicate enemy position at " + x + "," + y);
            // Check actual enemy dimensions as well as attach()'s formation bounds.
            assertTrue(x >= SIDE_MARGIN
                            && (long) x + enemy.getWidth() <= screen.getWidth() - SIDE_MARGIN,
                    context + " horizontal bounds at " + x + "," + y);
            assertTrue(y >= 0
                            && (long) y + enemy.getHeight() <= screen.getHeight() - BOTTOM_MARGIN,
                    context + " vertical bounds at " + x + "," + y);
            count++;
        }
        assertEquals(expectedCount, count, context + " enemy count");
        return positions;
    }
}
