package currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RewardSystemTest {

    private PlayerAssets assets;
    private RewardSystem rewards;

    @BeforeEach
    void setUp() {
        assets = new PlayerAssets();
        rewards = new RewardSystem(assets);
    }

    @Test
    void testInitialBalance() {
        assertEquals(0, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
    }

    @ParameterizedTest
    @CsvSource({"A,10", "B,15", "C,20", "BONUS,20", "BOSS_A,50", "BOSS_B,50"})
    void campaignEnemyRewardsAccumulateWithoutChangingTheBalance(String type, int amount) {
        assertEquals(amount, rewards.getEnemyReward(type));
        assertTrue(rewards.rewardEnemy(type));
        assertTrue(rewards.rewardEnemy(type));
        assertEquals(amount * 2, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }

    @Test
    void testBasicEnemyReward() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertEquals(10, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }

    @Test
    void testFastEnemyReward() {
        assertTrue(rewards.rewardEnemy("FAST"));
        assertEquals(25, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }

    @Test
    void testTankEnemyReward() {
        assertTrue(rewards.rewardEnemy("TANK"));
        assertEquals(50, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }


    @Test
    void testLevelCompletionReward() {
        assertTrue(rewards.rewardLevel(2));
        assertEquals(200, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }


    @Test
    void testUnknownEnemy() {
        assertFalse(rewards.rewardEnemy("UNKNOWN"));
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testInvalidDifficulty() {
        assertFalse(rewards.rewardLevel(-1));
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testCustomEnemyReward() {
        assertTrue(rewards.setEnemyReward("BOSS", 500));
        assertTrue(rewards.rewardEnemy("BOSS"));
        assertEquals(500, rewards.getPendingCurrency());
    }

    @Test
    void testInvalidAssets() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RewardSystem(null)
        );
    }

    @Test
    void testInvalidRewardConfiguration() {
        assertFalse(rewards.setEnemyReward(null, 10));
        assertFalse(rewards.setEnemyReward("BASIC", -10));
        assertFalse(rewards.setBaseLevelReward(0));
        assertEquals(10, rewards.getEnemyReward("BASIC"));
        assertEquals(100, rewards.getBaseLevelReward());
    }

    @Test
    void testLevelRewardMultiplicationOverflow() {
        assertFalse(rewards.rewardLevel(Integer.MAX_VALUE
        ));

        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testLevelRewardQueriesDoNotAccrueOrSettleCurrency() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.setBaseLevelReward(5));
        assertEquals(2, rewards.getLevelReward(0));
        assertEquals(5, rewards.getLevelReward(1));
        assertEquals(10, rewards.getLevelReward(2));
        assertEquals(0, rewards.getLevelReward(-1));
        assertEquals(0, assets.getBalance());
        assertEquals(10, rewards.getPendingCurrency());
        assertTrue(rewards.rewardLevel(2));
        assertEquals(20, rewards.getPendingCurrency());

        assertTrue(rewards.setBaseLevelReward(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, rewards.getLevelReward(1));
        assertEquals(0, rewards.getLevelReward(2));
        assertFalse(rewards.rewardLevel(2));
        assertEquals(20, rewards.getPendingCurrency());
        assertTrue(rewards.setBaseLevelReward(1));
        assertEquals(1, rewards.getLevelReward(0));
    }

    @Test
    void testRewardAccumulation() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.rewardEnemy("FAST"));
        assertTrue(rewards.rewardLevel(2));

        assertEquals(235, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }

    @Test
    void testSettlement() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.rewardLevel(2));

        assertEquals(210, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());

        assertTrue(rewards.settle());

        assertEquals(210, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testEmptySettlement() {
        assertTrue(rewards.settle());
        assertEquals(0, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testRepeatedSettlementDoesNotDuplicateRewards() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.settle());
        assertTrue(rewards.settle());
        assertEquals(10, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testSettlementOverflowPreservesPendingRewards() {
        PlayerAssets maxAssets = new PlayerAssets();

        assertTrue(maxAssets.addCurrency(Integer.MAX_VALUE));

        RewardSystem maxRewards = new RewardSystem(maxAssets);

        assertTrue(maxRewards.rewardEnemy("BASIC"));

        assertFalse(maxRewards.settle());

        assertEquals(Integer.MAX_VALUE, maxAssets.getBalance());
        assertEquals(10, maxRewards.getPendingCurrency());

    }

    @Test
    void testPendingCurrencyOverflow() {
        assertTrue(rewards.setEnemyReward(
            "BOSS", Integer.MAX_VALUE
        ));

        assertTrue(rewards.rewardEnemy("BOSS"));

        assertFalse(rewards.rewardEnemy("BASIC"));

        assertEquals(
            Integer.MAX_VALUE,
            rewards.getPendingCurrency()
        );
    }

    @Test
    void testRewardsAfterSettlement() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.settle());

        assertTrue(rewards.rewardEnemy("FAST"));

        assertEquals(10, assets.getBalance());
        assertEquals(25, rewards.getPendingCurrency());

        assertTrue(rewards.settle());

        assertEquals(35, assets.getBalance());
        assertEquals(0, rewards.getPendingCurrency());
    }

    @Test
    void testRepeatedValidRewardsAccumulate() {
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertTrue(rewards.rewardEnemy("BASIC"));
        assertEquals(20, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());

        assertTrue(rewards.rewardLevel(1));
        assertTrue(rewards.rewardLevel(1));
        assertEquals(220, rewards.getPendingCurrency());
        assertEquals(0, assets.getBalance());
    }

}
