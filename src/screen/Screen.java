package screen;

import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import audio.AudioControls;
import engine.ControlsConfig;
import engine.Cooldown;
import engine.Core;
import engine.DrawManager;
import engine.InputManager;

import java.awt.Graphics;
import engine.Starfield;

/**
 * Implements a generic screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Screen {

	/** Milliseconds until the screen accepts user input. */
	private static final int INPUT_DELAY = 1000;

	/** Draw Manager instance. */
	protected DrawManager drawManager;
	/** Input Manager instance. */
	protected InputManager inputManager;
	/** Application logger. */
	protected Logger logger;

	/** Screen width. */
	protected int width;
	/** Screen height. */
	protected int height;

	private final Starfield starfield;

	/** Frames per second shown on the screen. */
	protected int fps;
	/** Screen insets. */
	protected Insets insets;
	/** Time until the screen accepts user input. */
	protected Cooldown inputDelay;

	/** If the screen is running. */
	protected boolean isRunning;
	/** What kind of screen goes next. */
	protected int returnCode;

	/** Checks if the mute key was held down in the previous frame. */
	private static boolean muteKeyWasDown;

	/**
	 * Constructor, establishes the properties of the screen.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public Screen(final int width, final int height, final int fps) {
		this.width = width;
		this.height = height;
		this.starfield = new Starfield(width, height);
		this.fps = fps;

		this.drawManager = Core.getDrawManager();
		this.inputManager = Core.getInputManager();
		this.logger = Core.getLogger();
		this.inputDelay = Core.getCooldown(INPUT_DELAY);
		this.inputDelay.reset();
		this.returnCode = 0;
	}

	/**
	 * Initializes basic screen properties.
	 */
	public void initialize() {

	}

	/**
	 * Activates the screen.
	 *
	 * @return Next screen code.
	 */
	public int run() {
		this.isRunning = true;
		long previousTime = System.nanoTime();

		while (this.isRunning) {
			long time = System.currentTimeMillis();

			long currentTime = System.nanoTime();
			double deltaSeconds =
					(currentTime - previousTime) / 1_000_000_000.0;
			previousTime = currentTime;

			this.starfield.update(deltaSeconds);
			update();

			time = (1000 / this.fps) - (System.currentTimeMillis() - time);
			if (time > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(time);
				} catch (InterruptedException e) {
					return 0;
				}
			}
		}

		return 0;
	}

	/**
	 * Updates the elements on screen and checks for events.
	 */
	protected void update() {
		handleMuteKey();
	}

	/**
	 * Toggles the mute state when the M key is pressed.
	 * Called from update, so it works on every screen whose update calls
	 * super.update(). Holding the key toggles only once, and the key state
	 * is shared by all screens so a new screen does not toggle it again.
	 * The key state is still updated on screens where usesGlobalMuteKey
	 * returns false.
	 */
	private void handleMuteKey() {
		boolean muteKeyDown = inputManager.isKeyDown(KeyEvent.VK_M);
		if (muteKeyDown && !muteKeyWasDown && usesGlobalMuteKey()
				&& !isMuteKeyBoundElsewhere())
			AudioControls.toggleMute();
		muteKeyWasDown = muteKeyDown;
	}

	/**
	 * Checks whether the M key is also bound to movement or firing.
	 * The global mute key is turned off in that case, so the same key
	 * does not move or fire and toggle the mute state at once.
	 *
	 * @return True if M is used by another control.
	 */
	private static boolean isMuteKeyBoundElsewhere() {
		return ControlsConfig.getKey(ControlsConfig.LEFT) == KeyEvent.VK_M
				|| ControlsConfig.getKey(ControlsConfig.RIGHT) == KeyEvent.VK_M
				|| ControlsConfig.getKey(ControlsConfig.FIRE) == KeyEvent.VK_M;
	}

	/**
	 * Checks if the M key toggles the global mute on this screen.
	 * Screens that handle the M key themselves can return false.
	 *
	 * @return True if the M key toggles the global mute.
	 */
	protected boolean usesGlobalMuteKey() {
		return true;
	}

	/**
	 * Getter for screen width.
	 *
	 * @return Screen width.
	 */
	public final int getWidth() {
		return this.width;
	}

	/**
	 * Getter for screen height.
	 *
	 * @return Screen height.
	 */
	public final int getHeight() {
		return this.height;
	}

	public final void drawBackground(final Graphics graphics) {
		this.starfield.draw(graphics);
	}
}
