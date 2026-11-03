package currency;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the unlocked content IDs in the player's asset system.
 * Provides methods to check whether content is unlocked and to unlock
 * new content while preventing invalid IDs and duplicate unlocks.
 * Also provides a copy of the unlocked content IDs for asset snapshots.
 */
public class UnlockManager {

    /**
     * Stores the IDs of content that has been successfully unlocked.
     */
    private final Set<String> unlockedContentIds;

    /**
     * Initializes an empty set of unlocked content IDs.
     */
    public UnlockManager() {
        this.unlockedContentIds = new HashSet<>();
    }

    /**
     * Checks whether a content ID is null or blank.
     *
     * @param id the content ID to validate
     * @return true if the ID is null or contains only whitespace;
     *         otherwise, false
     */
    private boolean isInvalidId(String id) {
        return id == null || id.trim().isEmpty();
    }

    /**
     * Checks whether a content ID is present in the set of unlocked content.
     *
     * @param contentId the content ID to check; null or blank IDs return false
     * @return true if the content ID is unlocked; otherwise, false
     */
    public boolean isUnlocked(String contentId) {
        if (isInvalidId(contentId)) {
            return false;
        }
        return this.unlockedContentIds.contains(contentId);
    }

    /**
     * Unlocks content if its ID is valid and has not been unlocked before.
     *
     * @param contentId the content ID to unlock; null or blank IDs are rejected
     * @return true if the content was newly unlocked; false if the ID is
     *         invalid or the content is already unlocked
     */
    public boolean unlock(String contentId) {
        if (isInvalidId(contentId)) {
            return false;
        }
        if (this.unlockedContentIds.contains(contentId)) {
            return false;
        }
        this.unlockedContentIds.add(contentId);
        return true;
    }

    /**
     * Returns a detached copy of the unlocked content IDs.
     *
     * @return a new set containing the currently unlocked content IDs
     */
    Set<String> copyUnlockedContentIds() {
        return new HashSet<>(this.unlockedContentIds);
    }
}
