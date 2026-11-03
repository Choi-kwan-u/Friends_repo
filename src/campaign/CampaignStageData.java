package campaign;

import java.util.ArrayList;
import java.util.List;
import engine.GameSettings;

/**
 * Stores difficulty and balance data for each campaign stage.
 *
 * The stage settings originally defined in engine.Core
 * have been moved to this class so that level design data is managed
 * separately from the core game logic.
 */
public class CampaignStageData {

    private static List<GameSettings> campaignSettings;

    static {
        campaignSettings = new ArrayList<>();
        campaignSettings.add(new GameSettings(5, 4, 60, 2000, 20, 100)); // Stage 1
        campaignSettings.add(new GameSettings(5, 5, 50, 2500, 60, 100)); // Stage 2
        campaignSettings.add(new GameSettings(6, 5, 40, 1500, 40, 120)); // Stage 3
        campaignSettings.add(new GameSettings(6, 6, 30, 1500, 80, 100)); // Stage 4
        campaignSettings.add(new GameSettings(7, 6, 20, 1000, 40, 120)); // Stage 5
        campaignSettings.add(new GameSettings(7, 7, 10, 1000, 40, 100)); // Stage 6
        campaignSettings.add(new GameSettings(8, 7, 2, 500, 20, 100));   // Stage 7
    }

    /**
     * I like 1-based indexing.
     */
    public static GameSettings getStageSettings(int stageLevel) {
        if (stageLevel < 1 || stageLevel > campaignSettings.size()) {
            return campaignSettings.get(0); // 기본값 1스테이지
        }
        return campaignSettings.get(stageLevel - 1);
    }

    public static List<GameSettings> getCampaignSettings() {
        return campaignSettings;
    }

    public static int getTotalStages() {
        return campaignSettings.size();
    }
}
