
package revive;

public final class ReviveManager {

    public enum Mode {
        CAMPAIGN,
        ENDLESS
    }

    private static final int MAX_REVIVES = 2;

    private final Mode mode;
    private int revivesUsed;

    public ReviveManager(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Mode cannot be null.");
        }

        this.mode = mode;
        this.revivesUsed = 0;
    }

    public int getRevivesUsed() {
        return revivesUsed;
    }

    public int getRemainingRevives() {
        return MAX_REVIVES - revivesUsed;
    }

    public boolean hasRevivesLeft() {
        return revivesUsed < MAX_REVIVES;
    }

    public int getNextReviveCost() {
        if (!hasRevivesLeft()) {
            return -1;
        }

        if (mode == Mode.CAMPAIGN) {
            return revivesUsed == 0 ? 50 : 150;
        }

        return revivesUsed == 0 ? 20 : 50;
    }

    public boolean canRevive(int availableCredits) {
        return hasRevivesLeft()
                && availableCredits >= getNextReviveCost();
    }

    public int calculateRevivedLives(int maxLives) {
        if (maxLives <= 0) {
            throw new IllegalArgumentException(
                    "Max lives must be positive.");
        }

        return maxLives / 2 + maxLives % 2;
    }

    public void recordSuccessfulRevive() {
        if (!hasRevivesLeft()) {
            throw new IllegalStateException(
                    "No revives remaining.");
        }

        revivesUsed++;
    }
}

