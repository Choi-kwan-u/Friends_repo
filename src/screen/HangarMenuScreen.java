package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Core;
import engine.Cooldown;

/** Menu for choosing a Hangar action. */
public class HangarMenuScreen extends Screen {

    public static final int RETURN_TO_MAIN_MENU = 1;
    public static final int OPEN_SHIP_SELECTION = 12;
    public static final int OPEN_COLOR_SELECTION = 13;

    private static final int SELECTION_TIME = 200;

    private final Cooldown selectionCooldown;
    private int selectedOption;

    public HangarMenuScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        this.returnCode = RETURN_TO_MAIN_MENU;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedOption = 0;
    }

    @Override
    public final int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        draw();

        if (!this.inputDelay.checkFinished()
                || !this.selectionCooldown.checkFinished()) {
            return;
        }

        if (this.inputManager.isKeyDown(KeyEvent.VK_UP)
                || this.inputManager.isKeyDown(KeyEvent.VK_W)) {
            this.selectedOption = (this.selectedOption + 2) % 3;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || this.inputManager.isKeyDown(KeyEvent.VK_S)) {
            this.selectedOption = (this.selectedOption + 1) % 3;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_SPACE)
                || this.inputManager.isKeyDown(KeyEvent.VK_ENTER)) {
            selectOption();
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            this.returnCode = RETURN_TO_MAIN_MENU;
            this.isRunning = false;
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        }
    }

    private void selectOption() {
        if (this.selectedOption == 0) {
            this.returnCode = OPEN_SHIP_SELECTION;
        } else if (this.selectedOption == 1) {
            this.returnCode = OPEN_COLOR_SELECTION;
        } else {
            this.returnCode = RETURN_TO_MAIN_MENU;
        }

        this.isRunning = false;
        if (this.returnCode == RETURN_TO_MAIN_MENU) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        } else {
            AudioManager.playSFX("audio/sfx/menu_select.wav");
        }
    }

    private void draw() {
        this.drawManager.initDrawing(this);
        this.drawManager.drawHangarMenu(this, this.selectedOption);
        this.drawManager.completeDrawing(this);
    }
}
