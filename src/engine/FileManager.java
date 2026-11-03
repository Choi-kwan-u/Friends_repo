package engine;

import currency.PlayerAssetsState;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

import engine.DrawManager.SpriteType;

/**
 * Manages files used in the application.
 */
public final class FileManager {

	/** Singleton instance of the class. */
	private static FileManager instance;
	/** Application logger. */
	private static Logger logger;
	/** Max number of high scores. */
	private static final int MAX_SCORES = 7;

	/**
	 * private constructor.
	 */
	private FileManager() {
		logger = Core.getLogger();
	}

	/**
	 * Returns shared instance of FileManager.
	 *
	 * @return Shared instance of FileManager.
	 */
	protected static FileManager getInstance() {
		if (instance == null)
			instance = new FileManager();
		return instance;
	}

	/**
	 * Resolves a file in invaders.storageDir when supplied, otherwise beside the
	 * application's code/JAR. Gradle run supplies a directory outside build/.
	 *
	 * @param filename file name to resolve
	 * @return Path pointing to the file in the application storage directory
	 */
	static Path resolveStoragePath(final String filename) {
		String storageDirectory = System.getProperty("invaders.storageDir");
		if (storageDirectory != null && !storageDirectory.trim().isEmpty()) {
			return Paths.get(storageDirectory).toAbsolutePath().normalize().resolve(filename);
		}
		try {
			Path codePath = Paths.get(FileManager.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI());

			Path parent = codePath.getParent();
			if (parent != null) {
				return parent.resolve(filename);
			}
		} catch (Exception e) {
			if (logger != null) {
				logger.warning("Failed to resolve application directory: " + e.getMessage());
			}
		}
		return Paths.get(filename);
	}

	/**
	 * Loads sprites from disk.
	 *
	 * @param spriteMap
	 *            Mapping of sprite type and empty boolean matrix that will
	 *            contain the image.
	 * @throws IOException
	 *             In case of loading problems.
	 */
	public void loadSprite(final Map<SpriteType, boolean[][]> spriteMap)
			throws IOException {
		InputStream inputStream = null;

		try {
			inputStream = DrawManager.class.getClassLoader()
					.getResourceAsStream("graphics");
			char c;

			// Sprite loading.
			for (Map.Entry<SpriteType, boolean[][]> sprite : spriteMap
					.entrySet()) {
				for (int i = 0; i < sprite.getValue().length; i++)
					for (int j = 0; j < sprite.getValue()[i].length; j++) {
						do
							c = (char) inputStream.read();
						while (c != '0' && c != '1');

						sprite.getValue()[i][j] = c == '1';
					}
				logger.fine("Sprite " + sprite.getKey() + " loaded.");
			}
			if (inputStream != null)
				inputStream.close();
		} finally {
			if (inputStream != null)
				inputStream.close();
		}
	}

	/**
	 * Loads a font of a given size.
	 *
	 * @param size
	 *            Point size of the font.
	 * @return New font.
	 * @throws IOException
	 *             In case of loading problems.
	 * @throws FontFormatException
	 *             In case of incorrect font format.
	 */
	public Font loadFont(final float size) throws IOException,
			FontFormatException {
		InputStream inputStream = null;
		Font font;

		try {
			// Font loading.
			inputStream = FileManager.class.getClassLoader()
					.getResourceAsStream("font.ttf");
			font = Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont(
					size);
		} finally {
			if (inputStream != null)
				inputStream.close();
		}

		return font;
	}

	/**
	 * Returns the application default scores if there is no user high scores
	 * file.
	 *
	 * @return Default high scores.
	 * @throws IOException
	 *             In case of loading problems.
	 */
	private List<Score> loadDefaultHighScores() throws IOException {
		List<Score> highScores = new ArrayList<Score>();
		InputStream inputStream = null;
		BufferedReader reader = null;

		try {
			inputStream = FileManager.class.getClassLoader()
					.getResourceAsStream("scores");
			reader = new BufferedReader(new InputStreamReader(inputStream));

			Score highScore = null;
			String name = reader.readLine();
			String score = reader.readLine();

			while ((name != null) && (score != null)) {
				highScore = new Score(name, Integer.parseInt(score));
				highScores.add(highScore);
				name = reader.readLine();
				score = reader.readLine();
			}
		} finally {
			if (inputStream != null)
				inputStream.close();
		}

		return highScores;
	}

	/**
	 * Loads high scores from file, and returns a sorted list of pairs score -
	 * value.
	 *
	 * @return Sorted list of scores - players.
	 * @throws IOException
	 *             In case of loading problems.
	 */
	public List<Score> loadHighScores() throws IOException {

		List<Score> highScores = new ArrayList<Score>();
		InputStream inputStream = null;
		BufferedReader bufferedReader = null;

		try {
			File scoresFile = resolveStoragePath("scores").toFile();
			inputStream = new FileInputStream(scoresFile);
			bufferedReader = new BufferedReader(new InputStreamReader(
					inputStream, Charset.forName("UTF-8")));

			logger.info("Loading user high scores.");

			Score highScore = null;
			String name = bufferedReader.readLine();
			String score = bufferedReader.readLine();

			while ((name != null) && (score != null)) {
				highScore = new Score(name, Integer.parseInt(score));
				highScores.add(highScore);
				name = bufferedReader.readLine();
				score = bufferedReader.readLine();
			}

		} catch (FileNotFoundException e) {
			// loads default if there's no user scores.
			logger.info("Loading default high scores.");
			highScores = loadDefaultHighScores();
		} finally {
			if (bufferedReader != null)
				bufferedReader.close();
		}

		Collections.sort(highScores);
		return highScores;
	}

	/**
	 * Saves user high scores to disk.
	 *
	 * @param highScores
	 *            High scores to save.
	 * @throws IOException
	 *             In case of loading problems.
	 */
	public void saveHighScores(final List<Score> highScores)
			throws IOException {
		OutputStream outputStream = null;
		BufferedWriter bufferedWriter = null;

		try {
			File scoresFile = resolveStoragePath("scores").toFile();

			if (!scoresFile.exists())
				scoresFile.createNewFile();

			outputStream = new FileOutputStream(scoresFile);
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(
					outputStream, Charset.forName("UTF-8")));

			logger.info("Saving user high scores.");

			// Saves 7 or fewer scores.
			int savedCount = 0;
			for (Score score : highScores) {
				if (savedCount >= MAX_SCORES)
					break;
				bufferedWriter.write(score.getName());
				bufferedWriter.newLine();
				bufferedWriter.write(Integer.toString(score.getScore()));
				bufferedWriter.newLine();
				savedCount++;
			}

		} finally {
			if (bufferedWriter != null)
				bufferedWriter.close();
		}
	}

	public static final String ASSETS_FILENAME = "player_assets.properties";
	public static final String DEFAULT_ASSETS_SAVE_PATH = ASSETS_FILENAME;
	private static final String FORMAT_VERSION = "1";


	/**
	 * Saves the provided player asset snapshot to the default save path.
	 *
	 * <p>This method does not grant rewards or modify in-memory assets.
	 * It delegates saving to {@link #savePlayerAssets(PlayerAssetsState, Path)}.
	 *
	 * @param state the player asset snapshot to save (must not be null)
	 * @throws IllegalArgumentException if state is null
	 * @throws IOException if saving fails
	 */

	public void savePlayerAssets(final PlayerAssetsState state) throws IOException {
		savePlayerAssets(state, resolveStoragePath(ASSETS_FILENAME));
	}


	/**
	 * Saves the provided player asset snapshot using atomic replacement.
	 *
	 * <p>Saved data includes the player's balance, permanently unlocked
	 * content IDs, and owned item quantities. Active buffs and remaining
	 * effect durations are not included.
	 *
	 * <p>This method only writes the provided snapshot. It does not
	 * grant rewards or modify in-memory player assets.
	 * If saving fails after settlement, already settled assets remain
	 * unchanged in memory. Retrying a save does not grant rewards again.
	 * To save the latest asset state, the caller must provide a new snapshot.
	 *
	 * <p>Data is first written to a temporary file and then replaces
	 * the target atomically. If atomic replacement is unsupported,
	 * saving fails rather than overwriting the existing file non-atomically.
	 *
	 * @param state the player asset snapshot to save (must not be null)
	 * @param targetPath destination path, or null to use the default save path
	 * @throws IllegalArgumentException if state is null
	 * @throws IOException if saving fails
	 */

	public void savePlayerAssets(final PlayerAssetsState state, final Path targetPath) throws IOException {
		if (state == null) {
			throw new IllegalArgumentException("PlayerAssetsState cannot be null.");
		}
		Path target = (targetPath != null) ? targetPath : resolveStoragePath(ASSETS_FILENAME);

		Properties properties = new Properties();
		properties.setProperty("version", FORMAT_VERSION);
		properties.setProperty("balance", String.valueOf(state.getBalance()));

		// Lossless URL encoding to preserve commas, colons, semicolons, and whitespace
		StringBuilder encodedUnlocks = new StringBuilder();
		for (String id : state.getUnlockedContentIds()) {
			if (encodedUnlocks.length() > 0) {
				encodedUnlocks.append(',');
			}
			encodedUnlocks.append(URLEncoder.encode(id, StandardCharsets.UTF_8.name()));
		}
		properties.setProperty("unlockedContentIds", encodedUnlocks.toString());

		StringBuilder encodedItems = new StringBuilder();
		for (Map.Entry<String, Integer> entry : state.getItemQuantities().entrySet()) {
			if (encodedItems.length() > 0) {
				encodedItems.append(',');
			}
			String encodedId = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
			encodedItems.append(encodedId).append(':').append(entry.getValue());
		}
		properties.setProperty("itemQuantities", encodedItems.toString());

		Path parent = target.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		// Write to temporary file first so failed saves do not truncate or corrupt the valid save
		Path tempFile = Files.createTempFile(parent != null ? parent : Paths.get("."), "player_assets_", ".tmp");
		try {
			try (OutputStream os = Files.newOutputStream(tempFile)) {
				properties.store(os, "Player Assets Save Data");
			}

			try {
				Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				// Abort without falling back to non-atomic replacement to guarantee the destination file remains intact
				throw new IOException("Atomic replacement is not supported on this filesystem; aborting save to preserve existing file.", e);
			}
			logger.info("Successfully saved player assets state to " + target);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}


	/**
	 * Loads the player asset state from the default save path.
	 *
	 * <p>If the save file does not exist, an empty default state is returned.
	 * If the file exists but is corrupted or malformed, an IOException is thrown.
	 *
	 * @return the loaded PlayerAssetsState, or an empty default state if no save exists
	 * @throws IOException if the save file is invalid or cannot be read
	 */

	public PlayerAssetsState loadPlayerAssets() throws IOException {
		return loadPlayerAssets(resolveStoragePath(ASSETS_FILENAME));
	}

	/** Decodes form-encoded IDs, rejecting malformed UTF-8 instead of replacing it. */
	private static String decodeAssetId(final String encodedId) throws CharacterCodingException {
		StringBuilder decoded = new StringBuilder();
		byte[] bytes = null;
		int index = 0;
		while (index < encodedId.length()) {
			char current = encodedId.charAt(index);
			if (current == '%') {
				if (bytes == null) {
					bytes = new byte[encodedId.length() / 3];
				}
				int count = 0;
				while (index < encodedId.length() && encodedId.charAt(index) == '%') {
					if (index + 2 >= encodedId.length()) {
						throw new IllegalArgumentException("Incomplete percent escape in asset ID.");
					}
					int high = Character.digit(encodedId.charAt(index + 1), 16);
					int low = Character.digit(encodedId.charAt(index + 2), 16);
					if (high < 0 || low < 0) {
						throw new IllegalArgumentException("Invalid percent escape in asset ID.");
					}
					int value = (high << 4) | low;
					// Java 7's UTF-8 decoder also accepts encoded surrogate code points.
					if (count > 0 && bytes[count - 1] == (byte) 0xED
							&& value >= 0xA0 && value <= 0xBF) {
						throw new MalformedInputException(2);
					}
					bytes[count++] = (byte) value;
					index += 3;
				}
				decoded.append(StandardCharsets.UTF_8.newDecoder()
						.onMalformedInput(CodingErrorAction.REPORT)
						.onUnmappableCharacter(CodingErrorAction.REPORT)
						.decode(ByteBuffer.wrap(bytes, 0, count)));
			} else {
				decoded.append(current == '+' ? ' ' : current);
				index++;
			}
		}
		return decoded.toString();
	}


	/**
	 * Loads the player asset state from a specific path with strict validation.
	 *
	 * <p>If sourcePath is null, the default save path is used.
	 * When Files.exists() returns false, this method returns the default
	 * state with zero balance, no unlocked content, and no owned items.
	 * This may occur when the file is missing or its existence cannot be
	 * determined, including certain file access errors.
	 *
	 * <p>Existing save files are validated for format version, required
	 * properties, balance, asset identifiers, and item quantities.
	 * When an existing save file is detected, empty, corrupted, or malformed
	 * contents are rejected with an IOException.
	 * However, failures to determine file existence may result in the
	 * default state rather than an IOException.
	 *
	 * <p>This method only reads the saved data and does not modify the file.
	 *
	 * @param sourcePath file path to load from, or null to use the default save path
	 * @return the loaded PlayerAssetsState, or an empty default state if Files.exists() returns false
	 * @throws IOException if the save file is invalid or cannot be read
	 */

	public PlayerAssetsState loadPlayerAssets(final Path sourcePath) throws IOException {
		Path target = (sourcePath != null) ? sourcePath : resolveStoragePath(ASSETS_FILENAME);

		// Only a genuinely missing file produces the default state
		if (!Files.exists(target)) {
			logger.info("Save file not found at " + target + ". Returning default initial state.");
			return new PlayerAssetsState(0, Collections.<String>emptySet(),
					Collections.<String, Integer>emptyMap());
		}

		Properties properties = new Properties();
		try (InputStream is = Files.newInputStream(target)) {
			properties.load(is);
		} catch (IllegalArgumentException e) {
			throw new IOException("Malformed properties formatting in save file: " + e.getMessage(), e);
		}

		// 1. Validate format version
		String version = properties.getProperty("version");
		if (version == null) {
			throw new IOException("Missing required property: 'version'");
		}
		if (!FORMAT_VERSION.equals(version.trim())) {
			throw new IOException("Unsupported format version: '" + version + "'");
		}

		// 2. Validate balance
		String balanceStr = properties.getProperty("balance");
		if (balanceStr == null) {
			throw new IOException("Missing required property: 'balance'");
		}
		int balance;
		try {
			balance = Integer.parseInt(balanceStr.trim());
		} catch (NumberFormatException e) {
			throw new IOException("Malformed numeric balance: '" + balanceStr + "'", e);
		}
		if (balance < 0) {
			throw new IOException("Balance cannot be negative: " + balance);
		}

		// 3. Validate unlockedContentIds
		String unlocksRaw = properties.getProperty("unlockedContentIds");
		if (unlocksRaw == null) {
			throw new IOException("Missing required property: 'unlockedContentIds'");
		}
		Set<String> unlockedIds = new HashSet<>();
		if (!unlocksRaw.isEmpty()) {
			String[] tokens = unlocksRaw.split(",", -1);
			for (String token : tokens) {
				if (token.isEmpty()) {
					throw new IOException("Malformed unlockedContentIds: contains empty identifier token");
				}
				String decodedId;
				try {
					decodedId = decodeAssetId(token);
				} catch (IllegalArgumentException | CharacterCodingException e) {
					throw new IOException("Malformed URL-encoded unlocked content ID: '" + token + "'", e);
				}
				if (decodedId.trim().isEmpty()) {
					throw new IOException("Malformed unlocked content ID: cannot be blank");
				}
				if (!unlockedIds.add(decodedId)) {
					throw new IOException("Duplicate unlocked content ID found: " + decodedId);
				}
			}
		}

		// 4. Validate itemQuantities
		String itemsRaw = properties.getProperty("itemQuantities");
		if (itemsRaw == null) {
			throw new IOException("Missing required property: 'itemQuantities'");
		}
		Map<String, Integer> itemQuantities = new HashMap<>();
		if (!itemsRaw.isEmpty()) {
			String[] entries = itemsRaw.split(",", -1);
			for (String entry : entries) {
				if (entry.isEmpty()) {
					throw new IOException("Malformed itemQuantities: contains empty item entry");
				}
				int colonIdx = entry.indexOf(':');
				if (colonIdx <= 0 || colonIdx == entry.length() - 1) {
					throw new IOException("Malformed item entry (expected encodedId:quantity): '" + entry + "'");
				}
				String rawId = entry.substring(0, colonIdx);
				String rawQty = entry.substring(colonIdx + 1);

				String decodedId;
				try {
					decodedId = decodeAssetId(rawId);
				} catch (IllegalArgumentException | CharacterCodingException e) {
					throw new IOException("Malformed URL-encoded item ID: '" + rawId + "' in entry '" + entry + "'", e);
				}
				if (decodedId.trim().isEmpty()) {
					throw new IOException("Malformed item ID: cannot be blank in entry '" + entry + "'");
				}
				int quantity;
				try {
					quantity = Integer.parseInt(rawQty.trim());
				} catch (NumberFormatException e) {
					throw new IOException("Malformed numeric item quantity in entry: '" + entry + "'", e);
				}
				if (quantity <= 0) {
					throw new IOException("Item quantity must be greater than 0: " + quantity);
				}
				if (itemQuantities.put(decodedId, quantity) != null) {
					throw new IOException("Duplicate item ID found in itemQuantities: " + decodedId);
				}
			}
		}

		try {
			return new PlayerAssetsState(balance, unlockedIds, itemQuantities);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid asset state invariant: " + e.getMessage(), e);
		}
	}
}
