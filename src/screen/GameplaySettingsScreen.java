package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Cooldown;
import engine.Core;
import engine.GameplayConfig;

public class GameplaySettingsScreen extends Screen {
    private static final int OPTION_COUNT = 3;
    private static final int SELECTION_TIME = 200;

    private final Cooldown selectionCooldown;
    private int selectedOption;
    /** Option under the mouse on the last frame, -1 if none. */
    private int lastHoveredOption = -1;
    /** Starts true so a click held from the previous screen is ignored. */
    private boolean wasMousePressed = true;

    public GameplaySettingsScreen(final int width, final int height,
            final int fps) {
        super(width, height, fps);
        this.returnCode = 4;
        this.selectedOption = GameplayConfig.getDifficulty();
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
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

        int hoveredOption = drawManager.getGameplaySettingsOptionAt(this,
                inputManager.getMouseX(), inputManager.getMouseY());

        // Select an option when the mouse moves onto it.
        if (hoveredOption != lastHoveredOption) {
            lastHoveredOption = hoveredOption;
            if (hoveredOption != -1 && hoveredOption != selectedOption) {
                selectedOption = hoveredOption;
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
        }

        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !wasMousePressed;
        wasMousePressed = mousePressed;

        if (newMouseClick && hoveredOption != -1
                && inputDelay.checkFinished()) {
            selectedOption = hoveredOption;
            confirmDifficulty();
            return;
        }

        if (!selectionCooldown.checkFinished()
                || !inputDelay.checkFinished()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP)
                || inputManager.isKeyDown(KeyEvent.VK_W)) {
            selectedOption = (selectedOption - 1 + OPTION_COUNT)
                    % OPTION_COUNT;
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || inputManager.isKeyDown(KeyEvent.VK_S)) {
            selectedOption = (selectedOption + 1) % OPTION_COUNT;
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            confirmDifficulty();
        } else if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            returnCode = 4;
            isRunning = false;
        }
    }

    /**
     * Saves the selected difficulty and returns to the settings menu.
     */
    private void confirmDifficulty() {
        GameplayConfig.setDifficulty(selectedOption);
        AudioManager.playSFX("audio/sfx/menu_back.wav");
        returnCode = 4;
        isRunning = false;
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawGameplaySettings(this, selectedOption);
        drawManager.completeDrawing(this);
    }
}
