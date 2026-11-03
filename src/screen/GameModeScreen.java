package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

public class GameModeScreen extends Screen { // Implementation of the game mode selection screen.

    private static final int SELECTION_TIME = 200;
    private static final int NUM_CHOICES = 3; // Number of game mode choices (0: Infinite, 1: Campaign, 2: Back)

    private int selected;
    private Cooldown selectionCooldown;

    public GameModeScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
        this.selected = 0; // Se positionne par défaut sur le premier mode
        this.returnCode = 1; // Par défaut : retour au menu principal
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
    }

    public final int run() {
        super.run();
        return this.returnCode;
    }

    protected final void update() {
        super.update();

        draw();
        if (this.selectionCooldown.checkFinished()
                && this.inputDelay.checkFinished()) {

            if (inputManager.isKeyDown(KeyEvent.VK_UP)
                    || inputManager.isKeyDown(KeyEvent.VK_W)) {
                this.selected = this.selected == 0 ? NUM_CHOICES - 1
                        : this.selected - 1;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");   // 추가 (두 곳 모두)
            }
            if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                    || inputManager.isKeyDown(KeyEvent.VK_S)) {
                this.selected = (this.selected + 1) % NUM_CHOICES;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }

            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                if (this.selected == 2) {
                    this.returnCode = 1;
                } else if (this.selected == 0) {
                    this.returnCode = 8;
                } else if (this.selected == 1) {
                    this.returnCode = 2;
                }
                this.isRunning = false;
                if(this.returnCode==1)
                    AudioManager.playSFX("audio/sfx/menu_back.wav");
                else
                    AudioManager.playSFX("audio/sfx/menu_select.wav");
            }

            if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
                this.returnCode = 1;
                this.isRunning = false;
                AudioManager.playSFX("audio/sfx/menu_back.wav");
            }
        }
    }

    public final int getSelectedGameMode() {
        return this.selected;
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawGameModeMenu(this, this.selected);
        drawManager.completeDrawing(this);
    }
}
