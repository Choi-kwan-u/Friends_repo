package engine;

import java.util.prefs.Preferences;

public final class GameplayConfig {
    public static final int EASY = 0;
    public static final int NORMAL = 1;
    public static final int HARD = 2;

    private static final String DIFFICULTY_KEY = "difficulty";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(GameplayConfig.class);

    private GameplayConfig() {
    }

    public static int getDifficulty() {
        int value = PREFS.getInt(DIFFICULTY_KEY, NORMAL);
        if (value < EASY || value > HARD) {
            return NORMAL;
        }
        return value;
    }

    public static void setDifficulty(final int difficulty) {
        if (difficulty >= EASY && difficulty <= HARD) {
            PREFS.putInt(DIFFICULTY_KEY, difficulty);
        }
    }

    public static double getMultiplier() {
        switch (getDifficulty()) {
        case EASY:
            return 1.25;
        case HARD:
            return 0.8;
        default:
            return 1.0;
        }
    }
}
