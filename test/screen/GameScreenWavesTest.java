package screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import engine.GameSettings;
import engine.GameState;

/** Standalone checks for wave list validation; no test framework is required. */
public final class GameScreenWavesTest {
	public static void main(final String[] args) {
		final GameState state = new GameState(1, 0, 3, 0, 0);
		final GameSettings first = new GameSettings(1, 1, 60, 2000);
		final GameSettings second = new GameSettings(2, 1, 60, 2000);

		List<GameSettings> input = new ArrayList<GameSettings>(
				Arrays.asList(first, second));
		new GameScreen(state, input, false, 448, 520, 60);
		new GameScreen(state, first, false, 448, 520, 60);

		List<GameSettings> copy = GameScreen.copyWaves(input);
		input.clear();
		check(copy.size() == 2 && copy.get(0) == first && copy.get(1) == second,
				"Wave order or input isolation was not preserved");

		expectInvalid(new Runnable() {
			public void run() {
				new GameScreen(state, (List<GameSettings>) null, false,
						448, 520, 60);
			}
		});
		expectInvalid(new Runnable() {
			public void run() {
				new GameScreen(state, new ArrayList<GameSettings>(), false,
						448, 520, 60);
			}
		});
		expectInvalid(new Runnable() {
			public void run() {
				new GameScreen(state, Arrays.asList(first, null), false,
						448, 520, 60);
			}
		});
		expectInvalid(new Runnable() {
			public void run() {
				new GameScreen(state, (GameSettings) null, false,
						448, 520, 60);
			}
		});
		System.out.println("GameScreen wave list checks passed.");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void expectInvalid(final Runnable operation) {
		try {
			operation.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("Invalid wave list was accepted");
	}

	private GameScreenWavesTest() {
	}
}
