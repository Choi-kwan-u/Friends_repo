package screen;

import java.awt.event.KeyEvent;

import engine.Cooldown;
import engine.Core;
import entity.ShipType;
import audio.AudioManager;

/**
 * Screen where the player selects a ship type in the Hangar.
 */
public class ShipSelectionScreen extends Screen {

    public static final int RETURN_TO_HANGAR_MENU = 1;
    public static final int SHIP_SELECTED = 20;

    private static final int SELECTION_TIME = 200;

    private final Cooldown selectionCooldown;
    private final ShipType[] shipTypes = ShipType.values();
    /** Currently selected index. */
    private int selectedIndex = 0;
    /** Tracks the previous left mouse button state. */
    private boolean wasMousePressed = false;
    /** Option hovered in the previous frame, used only to time the hover sound. */
    private int lastHoveredIndex = -1;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param width  Screen width.
     * @param height Screen height.
     * @param fps    Frames per second.
     */
    public ShipSelectionScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        this.returnCode = RETURN_TO_HANGAR_MENU;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedIndex = 0;
    }

    @Override
    public final int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();

        // Get the ship currently hovered by the mouse.
        int mouseX = inputManager.getMouseX();
        int mouseY = inputManager.getMouseY();

        int hoveredIndex = drawManager.getShipSelectionOptionAt(
                this, this.shipTypes, mouseX, mouseY);

        // Update the selected ship when hovering over a valid option.
        if (hoveredIndex != -1 && hoveredIndex != this.selectedIndex) {
            this.selectedIndex = hoveredIndex;
        }

        // Play the move sound only when the mouse enters a different option.
        if (hoveredIndex != -1 && hoveredIndex != this.lastHoveredIndex) {
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
        this.lastHoveredIndex = hoveredIndex;

        // Detect a new left mouse click.
        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !wasMousePressed;

        wasMousePressed = mousePressed;

        // Confirm the ship when clicking on a valid option.
        if (newMouseClick
                && hoveredIndex != -1
                && this.inputDelay.checkFinished()) {
            this.selectedIndex = hoveredIndex;
            confirmSelection();
            if (!this.isRunning) return;
        }

        draw();

        if (!this.selectionCooldown.checkFinished()
                || !this.inputDelay.checkFinished()) {
            return;
        }

        if (this.inputManager.isKeyDown(KeyEvent.VK_UP)
                || this.inputManager.isKeyDown(KeyEvent.VK_W)) {
            this.selectedIndex =
                    (this.selectedIndex - 1 + this.shipTypes.length)
                            % this.shipTypes.length;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || this.inputManager.isKeyDown(KeyEvent.VK_S)) {
            this.selectedIndex =
                    (this.selectedIndex + 1) % this.shipTypes.length;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_SPACE)
                || this.inputManager.isKeyDown(KeyEvent.VK_ENTER)) {
            confirmSelection();
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            this.returnCode = RETURN_TO_HANGAR_MENU;
            this.isRunning = false;
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        }
    }

    private void confirmSelection() {
        if (!Core.getShopController().isShipAvailable(getSelectedShip())) return;
        this.returnCode = SHIP_SELECTED;
        this.isRunning = false;
        AudioManager.playSFX("audio/sfx/menu_select.wav");
    }

    private void draw() {
        this.drawManager.initDrawing(this);
        this.drawManager.drawShipSelection(
                this, this.shipTypes, this.selectedIndex);
        this.drawManager.completeDrawing(this);
    }

    public final ShipType getSelectedShip() {
        return this.shipTypes[this.selectedIndex];
    }
}
