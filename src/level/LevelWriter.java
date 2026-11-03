package level;

import java.io.IOException;
import java.nio.file.Path;

/** Contract for exporting base level design data, not adjusted runtime state. */
public interface LevelWriter {
    /**
     * Writes UTF-8 JSON preserving definition values, order, and grid gaps.
     * The caller owns file selection and overwrite confirmation. Writes must
     * leave the definition unchanged and must not truncate the target on a
     * serialization failure; serialize before replacing an existing file.
     *
     * @param path non-null target path
     * @param level non-null definition to export
     * @throws IOException if the file cannot be written
     * @throws NullPointerException if path or level is null
     */
    void write(Path path, LevelDefinition level) throws IOException;
}
