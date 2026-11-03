package engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;

import level.JsonLevelReader;
import level.JsonLevelWriter;
import level.LevelDefinition;
import level.LevelReader;
import level.LevelWriter;

/**
 * UI-independent custom level workflow. Own one instance in the menu/controller
 * and use it on a single controller thread. No dialogs or persistent settings
 * are created here. The stored definition always contains original design data.
 */
public final class CustomLevelSession {
    private final int width;
    private final int height;
    private final LevelReader reader;
    private final LevelWriter writer;
    private final DoubleSupplier difficulty;
    private final Player player;
    private LevelDefinition loaded;

    /** Uses the current settings and Core's isolated custom-stage execution path. */
    public CustomLevelSession(final int width, final int height) {
        this(width, height, new JsonLevelReader(), new JsonLevelWriter(),
                GameplayConfig::getMultiplier, Core::playCustomLevel);
    }

    /**
     * Allows a configured reader or a separately hosted game window. The player
     * must run only custom gameplay and must not submit official progression or
     * records. The difficulty supplier is sampled on each load and play.
     */
    public CustomLevelSession(final int width, final int height,
            final LevelReader reader, final LevelWriter writer,
            final DoubleSupplier difficulty, final Player player) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        this.width = width;
        this.height = height;
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.player = Objects.requireNonNull(player, "player");
    }

    /**
     * Reads and validates every wave before replacing the loaded definition.
     * IOException (including LevelFormatException) and gameplay incompatibility
     * leave the previous definition untouched. A UI cancellation should not call
     * this method; null is a programming error, not a cancellation signal.
     *
     * @throws IOException if reading or file validation fails
     * @throws IllegalArgumentException if the level cannot run in this viewport
     */
    public LevelDefinition load(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        LevelDefinition candidate = Objects.requireNonNull(reader.read(source), "reader result");
        validateGameplay(candidate);
        this.loaded = candidate;
        return candidate;
    }

    /** Empty until the first successful load; models are immutable and safe to inspect. */
    public Optional<LevelDefinition> getLoadedLevel() {
        return Optional.ofNullable(this.loaded);
    }

    /**
     * Exports the original definition, never runtime difficulty or progress.
     * The UI owns path selection and overwrite confirmation before this call.
     * The default writer may replace an existing regular file atomically.
     *
     * @throws IllegalStateException if no level is loaded
     * @throws IOException if the destination cannot be written
     */
    public void save(final Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        this.writer.write(destination, requireLoaded());
    }

    /**
     * Revalidates against current difficulty, then runs the original definition.
     * The default Core player blocks until completion; call it on the game's
     * controller thread, after the previous screen loop has returned, never
     * inside a Swing input callback. Completion/failure retains the definition.
     * The caller owns the next menu/result screen.
     *
     * @throws IllegalStateException if no level is loaded or Core has no window
     * @throws IllegalArgumentException if current settings are incompatible
     */
    public GameState play() {
        LevelDefinition definition = requireLoaded();
        validateGameplay(definition);
        return this.player.play(definition);
    }

    private void validateGameplay(final LevelDefinition definition) {
        LevelGameplayAdapter.prepare(definition, width, height, difficulty.getAsDouble());
    }

    private LevelDefinition requireLoaded() {
        if (this.loaded == null)
            throw new IllegalStateException("Load a custom level before saving or playing");
        return this.loaded;
    }

    @FunctionalInterface
    public interface Player {
        GameState play(LevelDefinition definition);
    }
}
