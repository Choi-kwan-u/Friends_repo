package currency;

import engine.GameplayConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates currency rewards for enemy defeats and level completion and
 * accumulates them until they are settled into the player's balance.
 *
 * <p>Responsibility split:
 * <ul>
 *   <li>CampaignRewards forwards enemy classifications during play and the
 *       completed stage's difficulty after its screen exits.</li>
 *   <li>Rewards are added to a pending amount. The player's balance changes
 *       only when {@link #settle()} succeeds.</li>
 *   <li>This class does not know whether a stage succeeded or failed. The caller
 *       decides whether to call {@link #rewardLevel(int)} (completion bonus)
 *       before settling. On success the caller includes the bonus; on failure or
 *       a normal early exit it settles only the rewards already earned.</li>
 *   <li>Deduplication of gameplay events is handled by the caller.</li>
 *   <li>This class does not write to disk. Saving is done by
 *       {@code FileManager}.</li>
 * </ul>
 *
 * @author Joshua Hernandez Ruiz
 */
public class RewardSystem {

    /** Level-completion reward used until setBaseLevelReward() changes it. */
    private static final int DEFAULT_LEVEL_REWARD = 100;

    /** Player assets that receive the pending rewards when settle() succeeds. */
    private final PlayerAssets playerAssets;
    /** Reward per enemy type. Keys are case-sensitive enemy type names. */
    private final Map<String, Integer> enemyRewards;

    /** Base reward for completing a level, before the difficulty adjustment. */
    private int baseLevelReward;
    /** Rewards earned but not yet added to the player's balance. */
    private int pendingCurrency;

    /**
     * Creates a reward system that settles into the given player assets.
     * Starts with no pending rewards and the default reward values.
     *
     * @param playerAssets assets that receive settled rewards (must not be null)
     * @throws IllegalArgumentException if playerAssets is null
     */
    public RewardSystem(PlayerAssets playerAssets) {
        if (playerAssets == null) {
            throw new IllegalArgumentException(
                "PlayerAssets cannot be null."
            );
        }

        this.playerAssets = playerAssets;
        this.enemyRewards = new HashMap<>();
        this.baseLevelReward = DEFAULT_LEVEL_REWARD;
        this.pendingCurrency = 0;

        initializeDefaultRewards();
    }

    /** Campaign classifications and legacy names; each defeat pays the full amount. */
    private void initializeDefaultRewards() {
        enemyRewards.put("A", 10);
        enemyRewards.put("B", 15);
        enemyRewards.put("C", 20);
        enemyRewards.put("BONUS", 20);
        enemyRewards.put("BOSS_A", 50);
        enemyRewards.put("BOSS_B", 50);
        enemyRewards.put("BASIC", 10);
        enemyRewards.put("FAST", 25);
        enemyRewards.put("TANK", 50);
    }

    /**
     * Sets the reward for an enemy type, replacing any existing value.
     * Type names are case-sensitive.
     *
     * @return false (nothing changed) if the type is null or blank, or amount is 0 or less
     */
    public boolean setEnemyReward(String enemyType, int amount) {
        if (enemyType == null || enemyType.trim().isEmpty() || amount <= 0) {
            return false;
        }

        enemyRewards.put(enemyType, amount);
        return true;
    }

    /**
     * Returns the reward configured for an enemy type.
     *
     * @return the reward, or 0 if the type is null or unknown
     */
    public int getEnemyReward(String enemyType) {
        if (enemyType == null) {
            return 0;
        }

        Integer reward = enemyRewards.get(enemyType);
        return reward != null ? reward : 0;
    }

    /**
     * Adds the reward for one defeated enemy to the pending amount.
     * The player's balance does not change until settle() succeeds.
     *
     * @return false (pending unchanged) if the enemy type is unknown or the
     *         pending amount would exceed Integer.MAX_VALUE
     */
    public boolean rewardEnemy(String enemyType) {

        int reward = getEnemyReward(enemyType);

        if (reward <= 0) {
            return false;
        }

        return accumulateReward(reward);
    }

    /**
     * Sets the base level-completion reward.
     *
     * @return false (nothing changed) if amount is 0 or less
     */
    public boolean setBaseLevelReward(int amount) {
        if (amount <= 0) {
            return false;
        }

        baseLevelReward = amount;
        return true;
    }

    /** Returns the base level-completion reward before the difficulty adjustment. */
    public int getBaseLevelReward() {
        return baseLevelReward;
    }

    /**
     * Adds the level-completion bonus to the pending amount, adjusted by difficulty:
     * EASY gives half of the base reward (at least 1), NORMAL gives the base
     * reward, and HARD gives double. The player's balance does not change
     * until settle() succeeds.
     *
     * <p>The caller decides whether to call this. It is expected to call this
     * only when the stage succeeded; on failure or a normal early exit the
     * caller skips it and settles only the rewards already earned.
     *
     * @param difficulty one of GameplayConfig.EASY, NORMAL or HARD
     * @return false (pending unchanged) for an unknown difficulty, or if the
     *         calculated reward or the new pending amount would exceed
     *         Integer.MAX_VALUE
     */
    public boolean rewardLevel(int difficulty) {
        return accumulateReward(getLevelReward(difficulty));
    }

    /** Returns the configured bonus without accruing it; invalid or overflowing values return 0. */
    public int getLevelReward(int difficulty) {
        final long calculatedReward;

        switch (difficulty) {
            case GameplayConfig.EASY:
                calculatedReward = Math.max(1L, baseLevelReward / 2L);
                break;
            case GameplayConfig.NORMAL:
                calculatedReward = baseLevelReward;
                break;
            case GameplayConfig.HARD:
                calculatedReward = (long) baseLevelReward * 2;
                break;
            default:
                return 0;
        }

        return calculatedReward > Integer.MAX_VALUE ? 0 : (int) calculatedReward;
    }


    /**
     * Accumulates a reward without changing the player's balance.
     *
     * @return false (pending unchanged) if amount is 0 or less, or if the
     *         total would exceed Integer.MAX_VALUE
     */
    private boolean accumulateReward(int amount) {
        if (amount <= 0) {
            return false;
        }

        long newPending = (long) pendingCurrency + amount;

        if (newPending > Integer.MAX_VALUE) {
            return false;
        }

        pendingCurrency = (int) newPending;

        return true;
    }

    /**
     * Returns accumulated rewards not yet transferred.
     */
    public int getPendingCurrency() {
        return pendingCurrency;
    }

    /**
     * Transfers pending rewards to PlayerAssets.
     *
     * <p>If nothing is pending, returns true without changing anything. If
     * PlayerAssets rejects the amount (for example, balance overflow), returns
     * false and keeps the pending rewards so settlement can be retried. After
     * a successful transfer the pending amount is reset to 0, so calling
     * settle() again does not pay the same rewards twice.
     *
     * <p>This does not save to disk. Saving the settled balance is a separate
     * step done through FileManager.
     *
     * @return true if nothing was pending or the transfer succeeded;
     *         false if the transfer was rejected
     */
    public boolean settle() {
        if (pendingCurrency == 0) {
            return true;
        }

        if (!playerAssets.addCurrency(pendingCurrency)) {
            return false;
        }

        pendingCurrency = 0;
        return true;
    }

}
