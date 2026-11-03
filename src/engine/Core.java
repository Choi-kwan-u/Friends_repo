package engine;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JOptionPane;

import screen.ShopScreen;
import screen.HangarScreen;
import screen.HangarMenuScreen;
import screen.GameModeScreen;

import currency.PlayerAssets;
import currency.PlayerAssetsState;
import hud.AssetHudSnapshot;
import java.util.UUID;
import records.RunSession;
import records.RunRecord;
import records.RunRecordFormatter;
import records.RecordsService;
import level.LevelDefinition;
import audio.AudioControls;
import screen.AudioControlScreen;
import screen.GameScreen;
import screen.HighScoreScreen;
import screen.InfiniteGameScreen;
import screen.ScoreScreen;
import screen.Screen;
import screen.TitleScreen;
import entity.ShipType;
import screen.ShipSelectionScreen;
import screen.PlayerRecordsScreen;
import campaign.CampaignStageData;
import campaign.CampaignManager;
import screen.SettingsScreen;
import screen.ControlsSettingsScreen;
import screen.GameplaySettingsScreen;
import shop.ShopController;
import shop.ShopProduct;
import shop.ShopProducts;
import java.util.List;

/**
 * Implements core game logic.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class Core {
	/** Width of current screen. */
	private static final int WIDTH = 448;
	/** Height of current screen. */
	private static final int HEIGHT = 520;
	/** Max fps of current screen. */
	private static final int FPS = 60;

	/** Max lives. */
	private static final int MAX_LIVES = 3;
	/** Levels between extra life. */
	private static final int EXTRA_LIFE_FRECUENCY = 3;

	/** Frame to draw screens on. */
	private static Frame frame;
	/** Screen currently shown. */
	private static Screen currentScreen;
	/** Player assets shared across levels and new games in this session. */
	private static PlayerAssets playerAssets;
	/** Shop controller shared for this application session. */
	private static ShopController shopController;
	/** Rewards shared by Campaign stages and the HUD for this session. */
	private static CampaignRewards campaignRewards;
	private static EndlessRewards endlessRewards;
	private static RecordsService recordsService;
	/** Application logger. */
	private static final Logger LOGGER = Logger.getLogger(Core.class
			.getSimpleName());
	/** Logger handler for printing to disk. */
	private static Handler fileHandler;
	/** Logger handler for printing to console. */
	private static ConsoleHandler consoleHandler;

	/**
	 * Test implementation.
	 *
	 * @param args
	 *             Program args, ignored.
	 */
	public static void main(final String[] args) {
		run(ShopProducts.availableProducts());
	}

	/** Starts the game with the supplied catalog; previews can supply isolated sample products. */
	public static void run(final List<ShopProduct> products) {
		Objects.requireNonNull(products, "products");
		try {
			LOGGER.setUseParentHandlers(false);

			fileHandler = new FileHandler("log");
			fileHandler.setFormatter(new MinimalFormatter());

			consoleHandler = new ConsoleHandler();
			consoleHandler.setFormatter(new MinimalFormatter());

			LOGGER.addHandler(fileHandler);
			LOGGER.addHandler(consoleHandler);
			LOGGER.setLevel(Level.ALL);

		} catch (Exception e) {
			// TODO handle exception
			e.printStackTrace();
		}
		AudioControls.applySettings();

		frame = new Frame(WIDTH, HEIGHT);
		DrawManager.getInstance().setFrame(frame);
		int width = frame.getWidth();
		int height = frame.getHeight();

		playerAssets = new PlayerAssets();

		if (!initializeShopWithRetry(products)) {
			frame.dispose();
			if (fileHandler != null) {
				fileHandler.close();
			}
			return;
		}

		campaignRewards = new CampaignRewards(playerAssets, getFileManager());
		endlessRewards = new EndlessRewards(playerAssets, getFileManager());

		recordsService = new RecordsService(new java.io.File(System.getProperty("user.home"),
				".space-invaders/records-achievements.properties").toPath());
		registerRecordsShutdownHook(recordsService);
		if (recordsService.getSaveError() != null)
			LOGGER.warning(recordsService.getSaveError());
		if (recordsService.getLoadWarning() != null)
			LOGGER.warning(recordsService.getLoadWarning());

		GameState gameState;
		ShipType selectedShip = ShipType.STANDARD;

		int returnCode = 1;
		do {
			gameState = new GameState(1, 0, MAX_LIVES, 0, 0);
			// When starting a new game, reset the difficulty adjustment conditions (player has 1 life or less && player has not cleared 3 levels while on low difficulty)
			DifficultyAdjustWhilePlaying.getInstance().reset();
			switch (returnCode) {
			case 1:
				// Main menu.
				currentScreen = new TitleScreen(width, height, FPS);
				LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
						+ " title screen at " + FPS + " fps.");
				returnCode = frame.setScreen(currentScreen);
				LOGGER.info("Closing title screen.");

				if (returnCode == 2)
					returnCode = 14;
				break;
			case 14:
				currentScreen = new GameModeScreen(width, height, FPS);
				LOGGER.info("Starting game mode selection screen.");
				returnCode = frame.setScreen(currentScreen);
				LOGGER.info("Closing game mode selection screen.");
				break;
			case 2:
				// The selected ship decides how many lives the run starts with.
				LOGGER.info("Selected ship: " + selectedShip.getName());

					CampaignUpgrades campaignUpgrades = CampaignUpgrades.from(playerAssets.snapshot());
					gameState = new GameState(1, 0, campaignUpgrades.getMaxLives(selectedShip),
							0, 0);

					// Game & score.
					RunSession runSession = new RunSession(UUID.randomUUID().toString());
					campaignRewards.startRun(runSession.getRunId());
					recordsService.startRun(runSession.getRunId());
					do {
						// One extra live every few levels.
						boolean bonusLife = gameState.getLevel()
								% EXTRA_LIFE_FRECUENCY == 0
								&& gameState.getLivesRemaining() < campaignUpgrades.getMaxLives(selectedShip);

						currentScreen = new GameScreen(gameState,
								applyDifficulty(CampaignStageData
										.getStageSettings(gameState.getLevel())),
								bonusLife, width, height, FPS, selectedShip,
								runSession, campaignUpgrades);
						LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
								+ " game screen at " + FPS + " fps.");
						runCampaignStage((GameScreen) currentScreen, campaignRewards,
								screen -> frame.setScreen(screen));
						LOGGER.info("Closing game screen.");
						resolveRewards(false);

						boolean stageCleared = ((GameScreen) currentScreen).isStageCleared();
						gameState = ((GameScreen) currentScreen).getGameState();

						if (!stageCleared) {
							LOGGER.info("Stage " + gameState.getLevel()
									+ " failed. Not advancing.");
							break;
						}

						CampaignManager.updateMaxCampaignStage(
								Math.min(gameState.getLevel() + 1,
										CampaignStageData.getTotalStages()));
						LOGGER.info("Highest Stage is: " + CampaignManager.getMaxCampaignStage());

						gameState = new GameState(gameState.getLevel() + 1,
								gameState.getScore(),
								gameState.getLivesRemaining(),
								gameState.getBulletsShot(),
								gameState.getShipsDestroyed());

					} while (gameState.getLivesRemaining() > 0
							&& gameState.getLevel() <= CampaignStageData.getTotalStages());

					RunRecord completedRun = runSession.finish(gameState.getScore(),
							gameState.getShipsDestroyed(), System.currentTimeMillis());
					if (recordsService.completeRun(completedRun))
						LOGGER.info("Completed run recorded:\n" + RunRecordFormatter.format(completedRun));

					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " score screen at " + FPS + " fps, with a score of "
							+ gameState.getScore() + ", "
							+ gameState.getLivesRemaining() + " lives remaining, "
							+ gameState.getBulletsShot() + " bullets shot and "
							+ gameState.getShipsDestroyed() + " ships destroyed.");
					currentScreen = new ScoreScreen(width, height, FPS, gameState,
							recordsService.getCurrentRunUnlocks());
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing score screen.");
					break;
				case 3:
					// High scores.
					currentScreen = new HighScoreScreen(width, height, FPS);
					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " high score screen at " + FPS + " fps.");
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing high score screen.");
					break;
				case 9:
					currentScreen = new PlayerRecordsScreen(width, height, FPS, recordsService);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 10:
					// Shop.
					currentScreen = new ShopScreen(width, height, FPS, shopController);
					returnCode = frame.setScreen(currentScreen);
					resolveRewards(false);
					break;
				case 11: {
					currentScreen = new HangarMenuScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				}

				case 12: {
					ShipSelectionScreen shipSelection = new ShipSelectionScreen(width, height, FPS);
					currentScreen = shipSelection;
					int selectionResult = frame.setScreen(currentScreen);

					if (selectionResult == ShipSelectionScreen.SHIP_SELECTED) {
						selectedShip = shipSelection.getSelectedShip();
					}

					returnCode = 11;
					break;
				}

				case 13: {
					currentScreen = new HangarScreen(width, height, FPS);
					frame.setScreen(currentScreen);
					returnCode = 11;
					break;
				}
				case 4:
					currentScreen = new SettingsScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 5:
					currentScreen = new AudioControlScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 6:
					currentScreen = new ControlsSettingsScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 7:
					currentScreen = new GameplaySettingsScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 8:
					// Infinite mode & score.
					RunSession infiniteSession = new RunSession(UUID.randomUUID().toString());
					recordsService.startRun(infiniteSession.getRunId());
					currentScreen = new InfiniteGameScreen(gameState, width, height,
							FPS, infiniteSession);
					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " infinite game screen at " + FPS + " fps.");
					runEndless((InfiniteGameScreen) currentScreen, endlessRewards,
							screen -> frame.setScreen(screen));
					LOGGER.info("Closing infinite game screen.");
					resolveRewards(false);

					gameState = ((InfiniteGameScreen) currentScreen).getGameState();
					if (!((InfiniteGameScreen) currentScreen).isAbandoned()) {
						RunRecord infiniteRecord = infiniteSession.finish(gameState.getScore(),
								gameState.getShipsDestroyed(), System.currentTimeMillis());
						recordsService.completeRun(infiniteRecord);
					}

					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " score screen at " + FPS + " fps, with a score of "
							+ gameState.getScore() + ", "
							+ gameState.getBulletsShot() + " bullets shot and "
							+ gameState.getShipsDestroyed() + " ships destroyed.");
					// Infinite mode score screen, Space plays infinite mode again.
					currentScreen = new ScoreScreen(width, height, FPS, gameState,
							true, recordsService.getCurrentRunUnlocks());
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing score screen.");
					break;
			}

			if (returnCode == 0 && !resolveRewards(true)) {
				returnCode = 1;
			}
		} while (returnCode != 0);

		recordsService.close();
		if (recordsService.getSaveError() != null)
			LOGGER.warning(recordsService.getSaveError());
		fileHandler.flush();
		fileHandler.close();
		System.exit(0);
	}

	/** Registers rewards only for the official Campaign and releases the stage listener on exit. */
	static void runCampaignStage(final GameScreen screen, final CampaignRewards rewards,
			final Consumer<GameScreen> runner) {
		Objects.requireNonNull(runner, "runner");
		try (CampaignRewards.StageSubscription subscription = rewards.subscribe(
				screen.getGameplayEvents(), screen.getGameState().getLevel())) {
			runner.accept(screen);
		}
	}

	/** Keeps earned Endless rewards even when the existing records path abandons a run. */
	static void runEndless(final InfiniteGameScreen screen, final EndlessRewards rewards,
			final Consumer<InfiniteGameScreen> runner) {
		Objects.requireNonNull(runner, "runner");
		try (EndlessRewards.RunSubscription subscription = rewards.subscribe(
				screen.getGameplayEvents(), screen.getActiveRunId())) {
			runner.accept(screen);
		}
	}

	/** Attempts both modes before reporting a failure, using the same saved assets. */
	static SettlementResult retryRewards(final CampaignRewards campaign, final EndlessRewards endless) {
		SettlementResult campaignResult = campaign.retryPendingRewards();
		SettlementResult endlessResult = endless.retryPendingRewards();
		if (campaignResult == SettlementResult.SETTLEMENT_FAILED
				|| endlessResult == SettlementResult.SETTLEMENT_FAILED)
			return SettlementResult.SETTLEMENT_FAILED;
		if (campaignResult == SettlementResult.SAVE_FAILED || endlessResult == SettlementResult.SAVE_FAILED)
			return SettlementResult.SAVE_FAILED;
		return SettlementResult.SUCCESS;
	}

	private static boolean resolveRewards(final boolean exiting) {
		SettlementResult result = retryRewards(campaignRewards, endlessRewards);
		while (result != SettlementResult.SUCCESS) {
			String message = result == SettlementResult.SETTLEMENT_FAILED
					? "Your balance is full. Earned Credits remain pending in this session.\n"
							+ "Spend Credits in the Shop to make room."
					: "Your Credits are available in this session, but progress could not be saved.";
			if (exiting) message += "\nExiting now will lose unsaved progress or pending Credits.";
			String[] options = exiting
					? new String[] { "Retry", "Return to menu", "Exit without saving" }
					: new String[] { "Retry", "Continue" };
			int choice = JOptionPane.showOptionDialog(frame, message,
					"Unable to save rewards", JOptionPane.DEFAULT_OPTION,
					JOptionPane.WARNING_MESSAGE, null, options, "Retry");
			if (choice != 0) return !exiting || choice == 2;
			result = retryRewards(campaignRewards, endlessRewards);
		}
		return true;
	}

	/** Loads shared assets before creating the session's shop controller. */
	static ShopController initializeShop(final PlayerAssets assets,
			final FileManager files) throws IOException {
		return initializeShop(assets, files, files.loadPlayerAssets());
	}

	static ShopController initializeShop(final PlayerAssets assets,
			final FileManager files, final PlayerAssetsState savedAssets) throws IOException {
		return initializeShop(assets, files, savedAssets, ShopProducts.availableProducts());
	}

	static ShopController initializeShop(final PlayerAssets assets, final FileManager files,
			final PlayerAssetsState savedAssets, final List<ShopProduct> products) throws IOException {
		if (!assets.restore(savedAssets)) {
			throw new IOException("Loaded assets could not be restored.");
		}
		return new ShopController(assets, files, products);
	}

	private static boolean initializeShopWithRetry(final List<ShopProduct> products) {
		while (true) {
			try {
				shopController = initializeShop(playerAssets, getFileManager(),
						getFileManager().loadPlayerAssets(), products);
				return true;
			} catch (IOException exception) {
				LOGGER.warning("Player assets could not be loaded: " + exception);
			} catch (SecurityException exception) {
				LOGGER.warning("Player assets access denied: " + exception);
			}
			int choice = JOptionPane.showOptionDialog(frame,
					"Your saved progress could not be loaded.\n"
							+ "Your save has not been changed. Retry or quit?",
					"Unable to load progress", JOptionPane.DEFAULT_OPTION,
					JOptionPane.WARNING_MESSAGE, null,
					new String[] { "Retry", "Quit" }, "Retry");
			if (choice != 0) {
				return false;
			}
		}
	}

	/**
	 * Runs one validated custom stage in the existing window and returns its result.
	 * Call on the game's screen-running thread after file selection/import completes.
	 * This path never enters the official campaign loop, unlocks stages, or submits
	 * a high score. The caller decides which menu/result screen to display next.
	 */
	public static GameState playCustomLevel(final LevelDefinition definition) {
		if (frame == null)
			throw new IllegalStateException("The game window must be initialized first");
		return runCustomLevel(definition, GameplayConfig.getMultiplier(),
				frame.getWidth(), frame.getHeight(), FPS, screen -> {
					currentScreen = screen;
					frame.setScreen(screen);
				});
	}

	/** Creates the menu/controller's persistent custom-level selection for this window. */
	public static CustomLevelSession createCustomLevelSession() {
		if (frame == null)
			throw new IllegalStateException("The game window must be initialized first");
		return new CustomLevelSession(frame.getWidth(), frame.getHeight());
	}

	// Runner is separated so handoff and unlock isolation can be checked headlessly.
	static GameState runCustomLevel(final LevelDefinition definition, final double multiplier,
			final int width, final int height, final int fps, final Consumer<GameScreen> runner) {
		Objects.requireNonNull(runner, "runner");
		GameScreen custom = GameScreen.forCustomLevel(
				new GameState(1, 0, MAX_LIVES, 0, 0), definition, multiplier, width, height, fps);
		runner.accept(custom);
		return custom.getGameState();
	}

	private static GameSettings applyDifficulty(
			final GameSettings stageSettings) {
		return applyDifficulty(stageSettings, GameplayConfig.getMultiplier());
	}

	// The calculation is accessible to tests in the engine package.
	static GameSettings applyDifficulty(
			final GameSettings stageSettings, final double multiplier) {
		int movementInterval = Math.max(1,
				(int) Math.round(stageSettings.getBaseSpeed() * multiplier));
		int shootingInterval = Math.max(250,
				(int) Math.round(
						stageSettings.getShootingFrecuency() * multiplier));

		return new GameSettings(stageSettings.getFormationWidth(),
				stageSettings.getFormationHeight(), movementInterval,
				shootingInterval, stageSettings.getInitialX(),
				stageSettings.getInitialY());
	}

	/**
	 * Constructor, not called.
	 */
	private Core() {

	}

	/**
	 * Controls access to the logger.
	 *
	 * @return Application logger.
	 */
	public static Logger getLogger() {
		return LOGGER;
	}

	/**
	 * Controls access to the drawing manager.
	 *
	 * @return Application draw manager.
	 */
	public static DrawManager getDrawManager() {
		return DrawManager.getInstance();
	}

	/**
	 * Controls access to the input manager.
	 *
	 * @return Application input manager.
	 */
	public static InputManager getInputManager() {
		return InputManager.getInstance();
	}

	/**
	 * Controls access to the file manager.
	 *
	 * @return Application file manager.
	 */
	public static FileManager getFileManager() {
		return FileManager.getInstance();
	}

	/**
	 * Returns the initialized session controller without creating or reloading assets.
	 *
	 * @return The existing shop controller for this application session.
	 * @throws IllegalStateException if startup has not initialized the shop controller
	 */
	public static ShopController getShopController() {
		if (shopController == null) {
			throw new IllegalStateException("Player assets have not been initialized.");
		}
		return shopController;
	}

	/**
	 * Returns balance plus pending rewards on the gameplay thread after startup.
	 * Reads the existing session without loading, saving or settling rewards.
	 *
	 * @throws IllegalStateException if startup has not initialized assets and rewards
	 */
	public static AssetHudSnapshot getAssetHudSnapshot() {
		ShopController shop = getShopController();
		if (campaignRewards == null) {
			throw new IllegalStateException("Campaign rewards have not been initialized.");
		}
		long pending = campaignRewards.getPendingCurrency();
		if (endlessRewards != null) pending += endlessRewards.getPendingCurrency();
		return new AssetHudSnapshot((long) shop.getBalance() + pending);
	}

	/**
	 * Controls creation of new cooldowns.
	 *
	 * @param milliseconds
	 *                     Duration of the cooldown.
	 * @return A new cooldown.
	 */
	public static Cooldown getCooldown(final int milliseconds) {
		return new Cooldown(milliseconds);
	}

	/**
	 * Controls creation of new cooldowns with variance.
	 *
	 * @param milliseconds
	 *                     Duration of the cooldown.
	 * @param variance
	 *                     Variation in the cooldown duration.
	 * @return A new cooldown with variance.
	 */
	public static Cooldown getVariableCooldown(final int milliseconds,
			final int variance) {
		return new Cooldown(milliseconds, variance);
	}

	public static RecordsService getRecordsService() {
		return recordsService;
	}

	private static void registerRecordsShutdownHook(final RecordsService service) {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				service.close();
				if (service.getSaveError() != null)
					LOGGER.warning(service.getSaveError());
			}
		}, "records-profile-shutdown"));
	}
}
