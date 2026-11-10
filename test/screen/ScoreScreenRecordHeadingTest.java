package screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import engine.Core;
import engine.GameState;
import engine.Score;

/**
 * Standalone checks for the heading above the name input: "New Record!" only
 * for a new best score, "Rank In!" for a list entry, in both modes.
 */
public final class ScoreScreenRecordHeadingTest {

    public static void main(final String[] args) throws Exception {
        Path storage = Files.createTempDirectory("score-heading");
        System.setProperty("invaders.storageDir", storage.toString());

        Core.getFileManager().saveInfiniteHighScores(Arrays.asList(
                new Score("TOP", 500), new Score("LOW", 100)));
        check(heading(true, 300).equals(ScoreScreen.RANK_IN_HEADING),
                "Infinite score below the best should be a rank-in");
        check(heading(true, 500).equals(ScoreScreen.RANK_IN_HEADING),
                "Tying the best is not a new record");
        check(heading(true, 501).equals(ScoreScreen.NEW_RECORD_HEADING),
                "Beating the best should be a new record");

        // No Infinite records yet: points beat the empty best, 0 does not.
        Files.delete(storage.resolve("scores_infinite"));
        check(heading(true, 10).equals(ScoreScreen.NEW_RECORD_HEADING),
                "First Infinite points should be a new record");
        check(heading(true, 0).equals(ScoreScreen.RANK_IN_HEADING),
                "0 points should not be a new record");

        // Campaign follows the same rule (default scores, best ROB 6500).
        check(heading(false, 1).equals(ScoreScreen.RANK_IN_HEADING),
                "Campaign score below the best should be a rank-in");
        check(heading(false, 6501).equals(ScoreScreen.NEW_RECORD_HEADING),
                "Campaign score above the best should be a new record");

        System.out.println("Score screen record heading checks passed.");
    }

    private static String heading(final boolean infinite, final int score) {
        ScoreScreen screen = new ScoreScreen(448, 520, 60,
                new GameState(1, score, 0, 1, 1), infinite);
        return screen.getRecordHeading();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private ScoreScreenRecordHeadingTest() {
    }
}
