package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;

import engine.Cooldown;
import engine.Core;

/**
 * Implements the title screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class TitleScreen extends Screen {

    // Variable to track which menu option is selected (0 = first choice)
    private int selectedMenuOption = 0;

    /** Milliseconds between changes in user selection. */
    private static final int SELECTION_TIME = 200;

    /** Time between changes in user selection. */
    private Cooldown selectionCooldown;
    private boolean wasMousePressed = false;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param width
     *               Screen width.
     * @param height
     *               Screen height.
     * @param fps
     *               Frames per second, frame rate at which the game is run.
     */
    public TitleScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        // Defaults to play.
        this.returnCode = 2;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();

        AudioManager.playBGM("audio/bgm/music_menu.wav");
    }

    /**
     * Starts the action.
     *
     * @return Next screen code.
     */
    public final int run() {
        super.run();

        return this.returnCode;
    }

    /**
     * Updates the elements on screen and checks for events.
     */
    protected final void update() {
        super.update();
        // Get mouse position relative to the game drawing area.
        int mouseX = inputManager.getMouseX();
        int mouseY = inputManager.getMouseY();

        int hoveredOption = drawManager.getMenuOptionAt(this, mouseX, mouseY);

        // Update selection when hovering over a different option.
        if (hoveredOption != -1 && hoveredOption != this.returnCode) {
            this.returnCode = hoveredOption;
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }

        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !wasMousePressed;

        wasMousePressed = mousePressed;

        if (newMouseClick
                && hoveredOption != -1
                && this.inputDelay.checkFinished()) {

            this.returnCode = hoveredOption;
            AudioManager.playSFX("audio/sfx/menu_select.wav");
            this.isRunning = false;
        }

        draw();
        if (this.selectionCooldown.checkFinished()
                && this.inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_UP)
                    || inputManager.isKeyDown(KeyEvent.VK_W)) {
                previousMenuItem();
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
            if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                    || inputManager.isKeyDown(KeyEvent.VK_S)) {
                nextMenuItem();
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                AudioManager.playSFX("audio/sfx/menu_select.wav");
                this.isRunning = false;
            }
            // Shortcut to the infinite mode, until it gets a menu entry.
            if (inputManager.isKeyDown(KeyEvent.VK_I)) {
                AudioManager.playSFX("audio/sfx/menu_select.wav");
                this.returnCode = 8;
                this.isRunning = false;
            }
        }
    }

    /**
     * Shifts the focus to the next menu item.
     */


    /**
     * Shifts the focus to the previous menu item.
     */


    /**
     * Draws the elements associated with the screen.
     */
    private void draw() {
        drawManager.initDrawing(this);

        drawManager.drawTitle(this);
        drawManager.drawMenu(this, this.returnCode);

        drawManager.completeDrawing(this);
    }

    private void nextMenuItem() {
        if (this.returnCode == 2)
            this.returnCode = 10;
        else if (this.returnCode == 10)
            this.returnCode = 11;
        else if (this.returnCode == 11)
            this.returnCode = 3;
        else if (this.returnCode == 3)
            this.returnCode = 9;
        else if (this.returnCode == 9)
            this.returnCode = 4;
        else if (this.returnCode == 4)
            this.returnCode = 0;
        else
            this.returnCode = 2;
    }

    private void previousMenuItem() {
        if (this.returnCode == 2)
            this.returnCode = 0;
        else if (this.returnCode == 0)
            this.returnCode = 4;
        else if (this.returnCode == 4)
            this.returnCode = 9;
        else if (this.returnCode == 9)
            this.returnCode = 3;
        else if (this.returnCode == 3)
            this.returnCode = 11;
        else if (this.returnCode == 11)
            this.returnCode = 10;
        else
            this.returnCode = 2;
    }
}
