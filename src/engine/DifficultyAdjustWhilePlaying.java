package engine;

/**
 * A temporary difficulty adjustment when the lives of the player is 1.
 *
 * Recovery conditions (either one):
 *  1) The player clears 3 levels while the difficulty is lowered
 *  2) Lives recovered to 2 or more
 *
 *
 */
public class DifficultyAdjustWhilePlaying {



    private static final DifficultyAdjustWhilePlaying INSTANCE =
            new DifficultyAdjustWhilePlaying();

    private boolean tooLowLives;               // the boolean value is true when the player has 1 lives remaining.
    private boolean ClearedEnoughLevels;       // the boolean value is true when the player has cleared 3 levels while the difficulty is lowered.
    private int clearedLevelsWhileActive;      // the number of levels cleared while the difficulty is lowered.

    private DifficultyAdjustWhilePlaying() {
    }

    public static DifficultyAdjustWhilePlaying getInstance() {
        return INSTANCE;
    }

    /** resets the difficulty adjustment state whenever a new game is started */

    public void reset() {
        tooLowLives = false;
        ClearedEnoughLevels = false;
        clearedLevelsWhileActive = 0;
    }


    /*gets the current number of lives remaining and updates the difficulty adjustment state */
    public void update(int livesRemaining) {
        if (livesRemaining >= 2) {
            // the difficulty is recovered when the player has 2 or more lives remaining.
            tooLowLives = false;
            ClearedEnoughLevels = false;
            clearedLevelsWhileActive = 0;
        } else if (livesRemaining <= 1 && livesRemaining > 0
                && !tooLowLives && !ClearedEnoughLevels) {
            tooLowLives = true;
            clearedLevelsWhileActive = 0;
        }
    }

    /** Called when a level is cleared (when the player has lives remaining). */
    public void onLevelCleared() {
        if (!tooLowLives) {
            return; // the difficulty adjustment only works when the player has 1 life remaining.
        }
        clearedLevelsWhileActive++;
        if (clearedLevelsWhileActive >= 3) {

            ClearedEnoughLevels = true; // the difficulty adjustment is disabled when the player has cleared 3 levels while the difficulty is lowered

            clearedLevelsWhileActive = 0;
        }
    }

    /** Returns the adjusted enemy bullet speed depending on whether the difficulty is adjusted. */
    public double adjustEnemyBulletSpeed(double baseSpeed) {
        if (tooLowLives&&!ClearedEnoughLevels) {
            return   (baseSpeed * 0.5);
        }
            return baseSpeed;
    }


}
