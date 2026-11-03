package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Core;
import engine.Cooldown;
import entity.Ship;

/** Lets the player customize the ship's color. */
public class HangarScreen extends Screen {

    private static final int SELECTION_TIME = 200;

    private final Cooldown selectionCooldown;
    private final Ship previewShip;

    public HangarScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        this.returnCode = 1;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.previewShip = new Ship(width / 2 - 13, height / 2 - 8);
    }

    @Override
    public final int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();

        if (this.inputDelay.checkFinished()
                && this.selectionCooldown.checkFinished()) {
            if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)
                    || this.inputManager.isKeyDown(KeyEvent.VK_A)) {
                ShipCustomization.changeColor(-1);
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            } else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)
                    || this.inputManager.isKeyDown(KeyEvent.VK_D)) {
                ShipCustomization.changeColor(1);
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            } else if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
                this.returnCode = 1;
                this.isRunning = false;
                AudioManager.playSFX("audio/sfx/menu_back.wav");
            }
        }

        draw();
    }

    private void draw() {
        this.drawManager.initDrawing(this);

        this.drawManager.drawCenteredBigString(this, "Hangar",
                this.getHeight() / 5);

        this.previewShip.setColor(ShipCustomization.getColor());
        this.drawManager.drawEntity(this.previewShip,
                this.previewShip.getPositionX(),
                this.previewShip.getPositionY());

        this.drawManager.drawCenteredRegularString(this,
                "Color: " + ShipCustomization.getColorName(),
                this.getHeight() / 2 + 45);
        this.drawManager.drawCenteredRegularString(this,
                "Left/Right: change color",
                this.getHeight() - 60);
        this.drawManager.drawCenteredRegularString(this,
                "Esc: return to menu",
                this.getHeight() - 35);

        this.drawManager.completeDrawing(this);
    }
}
