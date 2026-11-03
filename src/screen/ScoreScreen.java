package screen;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import campaign.CampaignStageData;
import engine.Cooldown;
import engine.Core;
import engine.GameState;
import engine.GameplayConfig;
import engine.Score;
import audio.AudioManager;
import records.Achievement;
import records.RecordsService;
import java.util.ArrayList;

/**
 * Implements the score screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class ScoreScreen extends Screen {

	/** Milliseconds between changes in user selection. */
	private static final int SELECTION_TIME = 200;
	/** Maximum number of high scores. */
	private static final int MAX_HIGH_SCORE_NUM = 7;
	/** Code of first mayus character. */
	private static final int FIRST_CHAR = 65;
	/** Code of last mayus character. */
	private static final int LAST_CHAR = 90;

	/** Level (stage) that was being played when the game ended. */
	private int level;
	/** Checks if the last stage was failed (no lives left). */
	private boolean stageFailed;
    private final List<Achievement> runUnlocks;
  /** Checks if every campaign stage was cleared. */
  private boolean gameCleared;
	/** Checks if the game that just ended was the infinite mode. */
	private boolean infiniteMode;
    /** Text describing the mode played: difficulty (campaign) or infinite. */
    private String modeLabel;
	/** Current score. */
	private int score;
	/** Player lives left. */
	private int livesRemaining;
	/** Total bullets shot by the player. */
	private int bulletsShot;
	/** Total ships destroyed by the player. */
	private int shipsDestroyed;
	/** List of past high scores. */
	private List<Score> highScores;
	/** Checks if current score is a new high score. */
	private boolean isNewRecord;
	/** Player name for record input. */
	private char[] name;
	/** Character of players name selected for change. */
	private int nameCharSelected;
	/** Time between changes in user selection. */
	private Cooldown selectionCooldown;

	/**
	 * Constructor, establishes the properties of the screen.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 * @param gameState
	 *            Current game state.
	 */
    public ScoreScreen(final int width, final int height, final int fps, final GameState state) {
        this(width,height,fps,state,false,Collections.<Achievement>emptyList());
    }
    public ScoreScreen(final int width, final int height, final int fps,
            final GameState state, final boolean infiniteMode) {
        this(width,height,fps,state,infiniteMode,Collections.<Achievement>emptyList());
    }
    public ScoreScreen(final int width, final int height, final int fps,
            final GameState state, final List<Achievement> unlocks) {
        this(width,height,fps,state,false,unlocks);
    }
    public ScoreScreen(final int width, final int height, final int fps,
            final GameState gameState, final boolean infiniteMode, final List<Achievement> unlocks) {
        super(width,height,fps);
        this.runUnlocks = new ArrayList<Achievement>(unlocks);

		this.infiniteMode = infiniteMode;
        this.modeLabel = infiniteMode ? "infinite mode"
                : "mode: " + difficultyName(GameplayConfig.getDifficulty());
		this.level = gameState.getLevel();
		this.stageFailed = gameState.getLivesRemaining() == 0;
        this.gameCleared = !this.infiniteMode && !this.stageFailed
                && gameState.getLevel() > CampaignStageData.getTotalStages();
		this.score = gameState.getScore();
		this.livesRemaining = gameState.getLivesRemaining();
		this.bulletsShot = gameState.getBulletsShot();
		this.shipsDestroyed = gameState.getShipsDestroyed();
		this.isNewRecord = false;
		this.name = "AAA".toCharArray();
		this.nameCharSelected = 0;
		this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
		this.selectionCooldown.reset();

		try {
			this.highScores = Core.getFileManager().loadHighScores();
			if (highScores.size() < MAX_HIGH_SCORE_NUM
					|| highScores.get(highScores.size() - 1).getScore()
					< this.score)
				this.isNewRecord = true;

		} catch (IOException e) {
			logger.warning("Couldn't load high scores!");
		}

		if (this.livesRemaining > 0){
			AudioManager.playBGM("audio/bgm/music_end_victory.wav");
		} else {
			AudioManager.playBGM("audio/bgm/music_end_defeat.wav");
		}

        if (this.isNewRecord) {
            AudioManager.playSFX("audio/sfx/new_record.wav");
        }

        RecordsService recordsService = Core.getRecordsService();
        if (recordsService != null) {
            boolean hasNewAchievement = false;
            Achievement achievement;
            while ((achievement = recordsService.pollNotification()) != null) {
                if (this.runUnlocks.contains(achievement)) {
                    hasNewAchievement = true;
                }
            }
            if (hasNewAchievement && !this.isNewRecord) {
                AudioManager.playSFX("audio/sfx/achievement_unlock.wav");
            }
        }


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

		draw();
		if (this.inputDelay.checkFinished()) {
			if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				// Return to main menu.
				AudioManager.playSFX("audio/sfx/menu_back.wav");
				this.returnCode = 1;
				this.isRunning = false;
				if (this.isNewRecord)
					saveScore();
			} else if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
				// Play again.
				AudioManager.playSFX("audio/sfx/menu_select.wav");
				// The infinite mode (8) restarts itself instead of the campaign.
				this.returnCode = this.infiniteMode ? 8 : 2;
				this.isRunning = false;
				if (this.isNewRecord)
					saveScore();
			}

			if (this.isNewRecord && this.selectionCooldown.checkFinished()) {
				if (inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
					this.nameCharSelected = this.nameCharSelected == 2 ? 0
							: this.nameCharSelected + 1;
					this.selectionCooldown.reset();
					AudioManager.playSFX("audio/sfx/name_cursor_move.wav");
				}
				if (inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
					this.nameCharSelected = this.nameCharSelected == 0 ? 2
							: this.nameCharSelected - 1;
					this.selectionCooldown.reset();
					AudioManager.playSFX("audio/sfx/name_cursor_move.wav");
				}
				if (inputManager.isKeyDown(KeyEvent.VK_UP)) {
					this.name[this.nameCharSelected] =
							(char) (this.name[this.nameCharSelected]
									== LAST_CHAR ? FIRST_CHAR
							: this.name[this.nameCharSelected] + 1);
					this.selectionCooldown.reset();
					AudioManager.playSFX("audio/sfx/name_char_change.wav");
				}
				if (inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
					this.name[this.nameCharSelected] =
							(char) (this.name[this.nameCharSelected]
									== FIRST_CHAR ? LAST_CHAR
							: this.name[this.nameCharSelected] - 1);
					this.selectionCooldown.reset();
					AudioManager.playSFX("audio/sfx/name_char_change.wav");
				}
			}
		}

	}

	/**
	 * Saves the score as a high score.
	 */
	private void saveScore() {
		highScores.add(new Score(new String(this.name), score));
		Collections.sort(highScores);
		if (highScores.size() > MAX_HIGH_SCORE_NUM)
			highScores.remove(highScores.size() - 1);

		try {
			Core.getFileManager().saveHighScores(highScores);
		} catch (IOException e) {
			logger.warning("Couldn't load high scores!");
		}
	}
    /** Converts a GameplayConfig difficulty value to its display name. */
    private static String difficultyName(final int difficulty) {
        switch (difficulty) {
            case GameplayConfig.EASY:  return "Easy";
            case GameplayConfig.HARD:  return "Hard";
            default:                   return "Normal";
        }
    }
	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		drawManager.initDrawing(this);
        float accuracy = this.bulletsShot == 0 ? 1f : (float)this.shipsDestroyed / this.bulletsShot;
        if (!this.runUnlocks.isEmpty()) {
            drawManager.drawAchievementResults(this,this.score,this.livesRemaining,
                    this.shipsDestroyed,accuracy,this.isNewRecord,this.name,this.nameCharSelected,
                    this.runUnlocks,this.inputDelay.checkFinished(),
                    this.stageFailed && !this.infiniteMode ? this.level : 0, this.gameCleared,
                    this.modeLabel);
            drawManager.completeDrawing(this); return;
        }

		drawManager.drawGameOver(this, this.inputDelay.checkFinished(),
				this.isNewRecord,this.gameCleared);
		if (this.stageFailed && !this.infiniteMode)
			drawManager.drawStageFailed(this, this.level, this.isNewRecord);
        drawManager.drawResults(this, this.score, this.livesRemaining,
                this.shipsDestroyed, accuracy, this.isNewRecord);
        drawManager.drawModeLabel(this, this.modeLabel, this.isNewRecord);

		if (this.isNewRecord)
			drawManager.drawNameInput(this, this.name, this.nameCharSelected);

		drawManager.completeDrawing(this);
	}

}
