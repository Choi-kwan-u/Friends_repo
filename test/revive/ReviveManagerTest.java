package revive;

public class ReviveManagerTest {

    public static void main(String[] args) {

        ReviveManager campaign =
                new ReviveManager(ReviveManager.Mode.CAMPAIGN);

        check(campaign.getNextReviveCost() == 50);
        check(!campaign.canRevive(49));
        check(campaign.canRevive(50));

        campaign.recordSuccessfulRevive();
        check(campaign.getNextReviveCost() == 150);

        campaign.recordSuccessfulRevive();
        check(!campaign.hasRevivesLeft());
        check(campaign.getNextReviveCost() == -1);

        ReviveManager endless =
                new ReviveManager(ReviveManager.Mode.ENDLESS);

        check(endless.getNextReviveCost() == 20);

        endless.recordSuccessfulRevive();
        check(endless.getNextReviveCost() == 50);

        check(endless.calculateRevivedLives(3) == 2);
        check(endless.calculateRevivedLives(4) == 2);
        check(endless.calculateRevivedLives(5) == 3);
        check(endless.calculateRevivedLives(8) == 4);

        System.out.println("ALL TESTS PASSED!");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Test failed!");
        }
    }
}
