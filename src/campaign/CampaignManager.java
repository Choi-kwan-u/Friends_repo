package campaign;

/**
 * Manages campaign progression and unlocked stages.
 */
public class CampaignManager {
    private static int maxCampaignStage = 1;

    public static int getMaxCampaignStage() {
        return maxCampaignStage;
    }

    public static void updateMaxCampaignStage(int stage) {
        maxCampaignStage = Math.max(maxCampaignStage, stage);
    }
}
