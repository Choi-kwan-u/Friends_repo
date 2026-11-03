package level;

import java.io.IOException;
import java.nio.file.Path;

/** Contract for reading a complete level without modifying gameplay state. */
public interface LevelReader {
    /**
     * Reads UTF-8 JSON and returns a fully validated definition.
     * Validation covers the file contract, not engine-specific playability.
     * The caller must validate gameplay compatibility before applying the result.
     *
     * @param path non-null source path
     *
     * @throws LevelFormatException if JSON syntax, fields, or definition values
     *         are invalid; model constructor errors should be wrapped with context
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if path is null
     */
    LevelDefinition read(Path path) throws IOException;
}
