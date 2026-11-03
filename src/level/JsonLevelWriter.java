package level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/** Exports base level definitions using the shared v1 JSON contract. */
public final class JsonLevelWriter implements LevelWriter {
    private final FileReplacement replacement;

    public JsonLevelWriter() {
        this((temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    // Allows deterministic filesystem-failure checks without changing permissions.
    JsonLevelWriter(final FileReplacement replacement) {
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    @FunctionalInterface
    interface FileReplacement {
        void replace(Path temporary, Path target) throws IOException;
    }

    /**
     * Serializes before touching the destination, then atomically replaces it.
     * The parent directory must exist. Atomic replacement must be supported by
     * the filesystem; there is deliberately no non-atomic fallback. The caller
     * owns overwrite confirmation. Symbolic links and non-regular targets are
     * rejected. Temporary-file cleanup is attempted on write/replacement failure.
     */
    @Override
    public void write(final Path path, final LevelDefinition level) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(level, "level");
        byte[] json = serialize(level).getBytes(StandardCharsets.UTF_8);
        Path target = path.toAbsolutePath();
        Path parent = target.getParent();
        if (parent == null || (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Export target must be a regular file: " + path);
        }
        Path temporary = Files.createTempFile(parent, ".level-export-", ".tmp");
        try {
            Files.write(temporary, json);
            replacement.replace(temporary, target);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static String serialize(final LevelDefinition level) throws LevelFormatException {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": ").append(level.getSchemaVersion());
        json.append(",\n  \"id\": ");
        string(json, level.getId(), "id");
        json.append(",\n  \"name\": ");
        string(json, level.getName(), "name");
        json.append(",\n  \"waves\": [\n");
        List<WaveDefinition> waves = level.getWaves();
        for (int index = 0; index < waves.size(); index++) {
            WaveDefinition wave = waves.get(index);
            json.append("    {\n      \"id\": ");
            string(json, wave.getId(), "waves[" + index + "].id");
            json.append(",\n      \"formation\": {\n        \"initialX\": ")
                    .append(wave.getInitialX());
            json.append(",\n        \"initialY\": ").append(wave.getInitialY());
            json.append(",\n        \"grid\": [\n");
            for (int row = 0; row < wave.getRows(); row++) {
                json.append("          [\n");
                for (int column = 0; column < wave.getColumns(); column++) {
                    EnemyCell cell = wave.getCell(row, column);
                    json.append("            ");
                    if (cell == null) {
                        json.append("null");
                    } else {
                        json.append("{\n              \"enemyType\": ");
                        string(json, cell.getEnemyType(), "waves[" + index
                                + "].formation.grid[" + row + "][" + column + "].enemyType");
                        json.append(",\n              \"health\": ")
                                .append(cell.getHealth()).append("\n            }");
                    }
                    json.append(column + 1 < wave.getColumns() ? ",\n" : "\n");
                }
                json.append("          ]");
                json.append(row + 1 < wave.getRows() ? ",\n" : "\n");
            }
            json.append("        ]\n      },\n      \"movementBaseFrames\": ")
                    .append(wave.getMovementBaseFrames());
            json.append(",\n      \"shootingIntervalMs\": ")
                    .append(wave.getShootingIntervalMs()).append("\n    }");
            json.append(index + 1 < waves.size() ? ",\n" : "\n");
        }
        return json.append("  ]\n}\n").toString();
    }

    /** Escape JSON syntax, controls, and UTF-16 surrogates without data loss. */
    private static void string(final StringBuilder json, final String value,
            final String field)
            throws LevelFormatException {
        final char[] hex = "0123456789abcdef".toCharArray();
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if ((Character.isHighSurrogate(c) && (index + 1 == value.length()
                    || !Character.isLowSurrogate(value.charAt(index + 1))))
                    || (Character.isLowSurrogate(c) && (index == 0
                    || !Character.isHighSurrogate(value.charAt(index - 1))))) {
                throw new LevelFormatException(field
                        + ": unpaired UTF-16 surrogate at character index " + index);
            }
            switch (c) {
            case '"': json.append("\\\""); break;
            case '\\': json.append("\\\\"); break;
            case '\b': json.append("\\b"); break;
            case '\f': json.append("\\f"); break;
            case '\n': json.append("\\n"); break;
            case '\r': json.append("\\r"); break;
            case '\t': json.append("\\t"); break;
            default:
                if (c < 0x20 || Character.isSurrogate(c)) {
                    json.append("\\u").append(hex[(c >>> 12) & 15])
                            .append(hex[(c >>> 8) & 15])
                            .append(hex[(c >>> 4) & 15]).append(hex[c & 15]);
                } else {
                    json.append(c);
                }
            }
        }
        json.append('"');
    }
}
