package manual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import audio.AudioManager;
import campaign.CampaignManager;
import engine.Core;
import engine.CustomLevelSession;
import engine.Frame;
import engine.GameState;
import level.JsonLevelReader;
import level.JsonLevelWriter;
import level.LevelDefinition;
import screen.GameScreen;

/** Real-file custom session demo. No menu changes, dialogs, or implicit saves. */
public final class CustomLevelFileDemo {
    public static void main(final String[] args) throws IOException {
        if (args.length > 2) {
            System.err.println("Usage: CustomLevelFileDemo [source.json] [new-destination.json]");
            return;
        }
        Path source = Paths.get(args.length == 0
                ? "examples/levels/campaign-stage-01.json" : args[0]);
        Path destination = args.length == 2 ? Paths.get(args[1]) : null;
        // This standalone demo cannot ask for overwrite confirmation, so refuse it.
        if (destination != null && Files.exists(destination)) {
            System.err.println("Choose a new destination: this demo does not overwrite existing files.");
            return;
        }
        int unlocked = CampaignManager.getMaxCampaignStage();
        Frame frame = new Frame(448, 520);
        try {
            Core.getDrawManager().setFrame(frame);
            CustomLevelSession session = new CustomLevelSession(frame.getWidth(), frame.getHeight(),
                    new JsonLevelReader(), new JsonLevelWriter(), () -> 1.0, definition -> {
                        GameScreen screen = GameScreen.forCustomLevel(new GameState(1, 0, 3, 0, 0),
                                definition, 1.0, frame.getWidth(), frame.getHeight(), 60);
                        frame.setScreen(screen);
                        System.out.println("Custom stage cleared: " + screen.isStageCleared());
                        return screen.getGameState();
                    });
            LevelDefinition loaded = session.load(source);
            System.out.println("Loaded: " + loaded.getName() + " (" + loaded.getWaves().size() + " waves)");
            session.play();
            System.out.println("Official unlock unchanged: "
                    + (unlocked == CampaignManager.getMaxCampaignStage()));
            if (destination != null) {
                session.save(destination);
                session.load(destination);
                System.out.println("Exported and re-imported: " + destination.toAbsolutePath());
            }
        } finally {
            AudioManager.stopBGM();
            frame.dispose();
        }
    }

    private CustomLevelFileDemo() { }
}
