package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

public class SettingsScreen extends Screen {

    private static final int SELECTION_TIME = 200;
    private static final int AUDIO_OPTION = 0;
    private static final int CONTROLS_OPTION = 1;
    private static final int GAMEPLAY_OPTION = 2;
    private static final int BACK_OPTION = 3;
    private static final int OPTION_COUNT = 4;

    private Cooldown selectionCooldown;
    private int selectedOption;
    /** Option under the mouse on the last frame, -1 if none. */
    private int lastHoveredOption = -1;
    /** Starts true so a click held from the previous screen is ignored. */
    private boolean wasMousePressed = true;

    public SettingsScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
        this.returnCode = 1; // Retour au menu principal
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedOption = AUDIO_OPTION;
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        draw();

        int hoveredOption = drawManager.getSettingsMenuOptionAt(this,
                inputManager.getMouseX(), inputManager.getMouseY());

        // Select an option when the mouse moves onto it.
        if (hoveredOption != this.lastHoveredOption) {
            this.lastHoveredOption = hoveredOption;
            if (hoveredOption != -1 && hoveredOption != this.selectedOption) {
                this.selectedOption = hoveredOption;
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
        }

        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !this.wasMousePressed;
        this.wasMousePressed = mousePressed;

        if (newMouseClick && hoveredOption != -1
                && this.inputDelay.checkFinished()) {
            this.selectedOption = hoveredOption;
            confirmSelectedOption();
            return;
        }

        if (!this.selectionCooldown.checkFinished()
                || !this.inputDelay.checkFinished()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W)) {
            this.selectedOption =
                    (this.selectedOption - 1 + OPTION_COUNT) % OPTION_COUNT;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || inputManager.isKeyDown(KeyEvent.VK_S)) {
            this.selectedOption =
                    (this.selectedOption + 1) % OPTION_COUNT;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            confirmSelectedOption();
        } else if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 1;
            this.isRunning = false;
        }
    }

    /**
     * Opens the screen of the selected option.
     */
    private void confirmSelectedOption() {
        switch (this.selectedOption) {
        case AUDIO_OPTION:
            this.returnCode = 5;
            break;
        case CONTROLS_OPTION:
            this.returnCode = 6;
            break;
        case GAMEPLAY_OPTION:
            this.returnCode = 7;
            break;
        case BACK_OPTION:
        default:
            this.returnCode = 1;
            break;
        }
        if(this.selectedOption == BACK_OPTION){
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        }
        else {
            AudioManager.playSFX("audio/sfx/menu_select.wav");
        }
        this.isRunning = false;
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawSettingsMenu(this, this.selectedOption);
        drawManager.completeDrawing(this);
    }
}
