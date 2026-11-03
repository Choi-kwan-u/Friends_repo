package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import records.RecordsService;

/** Local browsing page for completed runs and achievements. */
public final class PlayerRecordsScreen extends Screen {
    private final RecordsService service;
    private int tab;
    private long nextInputAt;

    public PlayerRecordsScreen(final int width, final int height,
            final int fps, final RecordsService service) {
        super(width, height, fps);
        this.service = service;
        this.returnCode = 1;
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    protected void update() {
        super.update();
        this.drawManager.initDrawing(this);
        this.drawManager.drawRecordsPage(this, this.service, this.tab);
        this.drawManager.completeDrawing(this);
        if (!this.inputDelay.checkFinished()
                || System.currentTimeMillis() < this.nextInputAt)
            return;
        if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)
                || this.inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 1;
            this.isRunning = false;
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)
                || this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
            this.tab = 1 - this.tab;
            this.nextInputAt = System.currentTimeMillis() + 250;
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
    }
}
