package engine;

import campaign.CampaignStageData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreDifficultyTest {

    @Test
    void preservesFormationPositionsAcrossDifficulties() {
        // Easy, Normal, Hard multipliers from GameplayConfig.
        double[] multipliers = {1.25, 1.0, 0.8};

        for (double multiplier : multipliers) {
            for (int stage = 1; stage <= 7; stage++) {
                GameSettings configured =
                        CampaignStageData.getStageSettings(stage);
                GameSettings adjusted =
                        Core.applyDifficulty(configured, multiplier);
                String context = "stage " + stage
                        + ", multiplier " + multiplier;

                assertEquals(configured.getInitialX(),
                        adjusted.getInitialX(), context);
                assertEquals(configured.getInitialY(),
                        adjusted.getInitialY(), context);
                assertEquals(configured.getFormationWidth(),
                        adjusted.getFormationWidth(), context);
                assertEquals(configured.getFormationHeight(),
                        adjusted.getFormationHeight(), context);

                int expectedMovement = Math.max(1,
                        (int) Math.round(
                                configured.getBaseSpeed() * multiplier));
                int expectedShooting = Math.max(250,
                        (int) Math.round(
                                configured.getShootingFrecuency()
                                        * multiplier));

                assertEquals(expectedMovement,
                        adjusted.getBaseSpeed(), context);
                assertEquals(expectedShooting,
                        adjusted.getShootingFrecuency(), context);
            }
        }
    }
}
