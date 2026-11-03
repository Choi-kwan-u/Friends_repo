package manual;

import java.util.Arrays;
import audio.AudioManager;
import campaign.CampaignManager;
import engine.Core;
import engine.Frame;
import engine.GameState;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import screen.GameScreen;

/** Optional visual demo using in-memory definitions; no JSON importer or save files. */
public final class CustomLevelDemo {
    public static void main(String[] args) {
        WaveDefinition first = new WaveDefinition("mixed-wave", new EnemyCell[][] {
            {new EnemyCell("C", 2), null, new EnemyCell("A", 1)},
            {null, null, null},
            {new EnemyCell("B", 1), null, new EnemyCell("C", 2)}
        }, 60, 100, 60, 2000);
        WaveDefinition second = new WaveDefinition("last-wave", new EnemyCell[][] {
            {null, new EnemyCell("A", 1), null},
            {new EnemyCell("B", 2), null, new EnemyCell("C", 1)}
        }, 80, 120, 50, 1800);
        LevelDefinition definition = new LevelDefinition(1, "custom-demo", "Custom demo",
                Arrays.asList(first, second));
        int unlocked = CampaignManager.getMaxCampaignStage();
        Frame frame = new Frame(448, 520);
        try {
            Core.getDrawManager().setFrame(frame);
            GameScreen screen = GameScreen.forCustomLevel(new GameState(1, 0, 3, 0, 0),
                    definition, 1.0, frame.getWidth(), frame.getHeight(), 60);
            frame.setScreen(screen);
            System.out.println("Custom stage cleared: " + screen.isStageCleared());
            System.out.println("Official unlock unchanged: "
                    + (unlocked == CampaignManager.getMaxCampaignStage()));
        } finally {
            AudioManager.stopBGM();
            frame.dispose();
        }
    }

    private CustomLevelDemo() { }
}
