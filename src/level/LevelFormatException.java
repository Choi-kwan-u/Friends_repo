package level;

import java.io.IOException;

/** A malformed or unsupported level file, distinct from filesystem failures. */
public final class LevelFormatException extends IOException {
    private static final long serialVersionUID = 1L;

    public LevelFormatException(final String message) {
        super(message);
    }

    public LevelFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
