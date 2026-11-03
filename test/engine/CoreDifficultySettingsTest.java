package engine;

import campaign.CampaignStageData;

/** Regression checks for the per-stage enemy firing intervals. */
public final class CoreDifficultySettingsTest {

	private CoreDifficultySettingsTest() {
	}

	public static void main(final String[] args) throws Exception {
		int[] expectedIntervals = { 2000, 2500, 1500, 1500, 1000, 1000, 500 };
		if (CampaignStageData.getTotalStages() != expectedIntervals.length)
			throw new AssertionError("Unexpected number of campaign stages: "
					+ CampaignStageData.getTotalStages());
		for (int i = 0; i < expectedIntervals.length; i++) {
			GameSettings settings = CampaignStageData.getStageSettings(i + 1);
			if (settings.getShootingFrecuency() != expectedIntervals[i])
				throw new AssertionError("Unexpected firing interval at stage "
						+ (i + 1));
		}
		System.out.println("Core difficulty setting checks passed.");
	}
}
