package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.ControlsConfig;
import engine.Cooldown;
import engine.Core;

public class ControlsSettingsScreen extends Screen {
    private static final int SELECTION_TIME = 200;

    private static final int LEFT_OPTION = 0;
    private static final int RIGHT_OPTION = 1;
    private static final int FIRE_OPTION = 2;
    private static final int BACK_OPTION = 3;
    private static final int OPTION_COUNT = 4;

    private final Cooldown selectionCooldown;
    private int selectedOption;
    private boolean waitingForKey;
    private boolean waitingForKeyRelease;
    private int capturedKeyCode;
    /** Option under the mouse on the last frame, -1 if none. */
    private int lastHoveredOption = -1;
    /** Starts true so a click held from the previous screen is ignored. */
    private boolean wasMousePressed = true;

    public ControlsSettingsScreen(final int width, final int height,
            final int fps) {
        super(width, height, fps);
        this.returnCode = 4;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedOption = LEFT_OPTION;
        this.waitingForKey = false;
        this.waitingForKeyRelease = false;
        this.capturedKeyCode = KeyEvent.VK_UNDEFINED;
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

        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !this.wasMousePressed;
        this.wasMousePressed = mousePressed;

        // The mouse is ignored while a new key is being chosen.
        if (!this.waitingForKey && handleMouse(newMouseClick)) {
            return;
        }

        if (this.waitingForKey) {
            captureKey();
            return;
        }

        // Do not let the newly assigned key also move the menu selection.
        if (this.waitingForKeyRelease) {
            if (inputManager.isKeyDown(this.capturedKeyCode)) {
                return;
            }
            this.waitingForKeyRelease = false;
        }

        if (!this.selectionCooldown.checkFinished()
                || !this.inputDelay.checkFinished()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP)
                || inputManager.isKeyDown(KeyEvent.VK_W)) {
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
            this.returnCode = 4;
            this.isRunning = false;
        }
    }

    /**
     * Turns off the global mute key while a new key is being captured,
     * so pressing M to assign it does not also toggle mute.
     *
     * @return False while waiting for a new key, otherwise true.
     */
    @Override
    protected boolean usesGlobalMuteKey() {
        return !this.waitingForKey;
    }

    /**
     * Selects the option under the mouse and confirms it on a click.
     *
     * @param newMouseClick
     *            If the mouse button was pressed on this frame.
     * @return True if the click confirmed an option.
     */
    private boolean handleMouse(final boolean newMouseClick) {
        int hoveredOption = drawManager.getControlsSettingsOptionAt(this,
                this.selectedOption, this.waitingForKey,
                inputManager.getMouseX(), inputManager.getMouseY());

        // Select an option when the mouse moves onto it.
        if (hoveredOption != this.lastHoveredOption) {
            this.lastHoveredOption = hoveredOption;
            if (hoveredOption != -1 && hoveredOption != this.selectedOption) {
                this.selectedOption = hoveredOption;
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
        }

        if (newMouseClick && hoveredOption != -1
                && this.inputDelay.checkFinished()) {
            this.selectedOption = hoveredOption;
            confirmSelectedOption();
            return true;
        }
        return false;
    }

    /**
     * Goes back on the Back option, or waits for a new key on the others.
     */
    private void confirmSelectedOption() {
        if (this.selectedOption == BACK_OPTION) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 4;
            this.isRunning = false;
        } else {
            inputManager.clearLastPressedKey();
            this.waitingForKey = true;
        }
        this.selectionCooldown.reset();
    }

    private void captureKey() {
        int keyCode = inputManager.consumeLastPressedKey();

        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        this.waitingForKey = false;
        this.capturedKeyCode = keyCode;
        this.waitingForKeyRelease = inputManager.isKeyDown(keyCode);
        this.selectionCooldown.reset();

        if (keyCode == KeyEvent.VK_ESCAPE) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            return;
        }
        if (isKeyAssignedToAnotherAction(keyCode)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            return;
        }

        ControlsConfig.setKey(this.selectedOption, keyCode);
        AudioManager.playSFX("audio/sfx/menu_select.wav");
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawControlsSettings(this, this.selectedOption,
                this.waitingForKey);
        drawManager.completeDrawing(this);
    }

    private boolean isKeyAssignedToAnotherAction(final int keyCode) {
    return (this.selectedOption != LEFT_OPTION
            && ControlsConfig.getKey(ControlsConfig.LEFT) == keyCode)
            || (this.selectedOption != RIGHT_OPTION
                    && ControlsConfig.getKey(ControlsConfig.RIGHT) == keyCode)
            || (this.selectedOption != FIRE_OPTION
                    && ControlsConfig.getKey(ControlsConfig.FIRE) == keyCode);
}
}
