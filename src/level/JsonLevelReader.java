package level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads a complete v1 definition without changing any active game state. */
public final class JsonLevelReader implements LevelReader {
    private final LevelImportPolicy policy;
    private final LevelValidator validator;

    public JsonLevelReader() {
        this(LevelImportPolicy.defaults());
    }

    public JsonLevelReader(final LevelImportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.validator = new LevelValidator(policy);
    }

    @Override
    public LevelDefinition read(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        String json = readUtf8(path);
        // Accept one leading UTF-8 BOM from text editors, never inside JSON.
        if (json.startsWith("\ufeff")) json = json.substring(1);
        Map<?, ?> root = object(new LevelJsonParser(json).parse(), "$",
                "schemaVersion", "id", "name", "waves");
        int version = integer(root.get("schemaVersion"), "schemaVersion");
        if (version != LevelDefinition.SCHEMA_VERSION) {
            throw new LevelFormatException("schemaVersion: unsupported version " + version);
        }
        String id = id(root.get("id"), "id");
        String name = string(root.get("name"), "name");
        if (name.trim().isEmpty()) throw new LevelFormatException("name: must not be blank");
        validator.validateName(name);
        List<?> inputWaves = array(root.get("waves"), "waves");
        if (inputWaves.isEmpty()) throw new LevelFormatException("waves: at least one wave is required");
        LevelValidator.maximum(inputWaves.size(), policy.getMaxWaves(), "waves");
        List<WaveDefinition> waves = new ArrayList<WaveDefinition>();
        Set<String> waveIds = new HashSet<String>();
        for (int index = 0; index < inputWaves.size(); index++) {
            String field = "waves[" + index + "]";
            WaveDefinition wave = wave(inputWaves.get(index), field);
            if (!waveIds.add(wave.getId())) throw new LevelFormatException(field + ".id: duplicate wave ID");
            waves.add(wave);
        }
        LevelDefinition result;
        try {
            result = new LevelDefinition(version, id, name, waves);
        } catch (IllegalArgumentException failure) {
            throw new LevelFormatException("$: invalid level definition", failure);
        }
        validator.validate(result);
        return result;
    }

    private String readUtf8(final Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) throw new IOException("Import source must be a regular file: " + path);
        if (attributes.size() > policy.getMaxFileBytes()) throw oversized();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                // Recheck while reading: the file can grow after the size check.
                if (count > policy.getMaxFileBytes() - bytes.size()) throw oversized();
                bytes.write(buffer, 0, count);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException failure) {
            throw new LevelFormatException("$: malformed UTF-8 input", failure);
        }
    }

    private LevelFormatException oversized() {
        return new LevelFormatException("$: file exceeds configured maximum "
                + policy.getMaxFileBytes() + " bytes");
    }

    private WaveDefinition wave(final Object value, final String path) throws LevelFormatException {
        Map<?, ?> wave = object(value, path, "id", "formation", "movementBaseFrames", "shootingIntervalMs");
        String id = id(wave.get("id"), path + ".id");
        String placement = path + ".formation";
        Map<?, ?> formation = object(wave.get("formation"), placement, "initialX", "initialY", "grid");
        int x = range(formation.get("initialX"), placement + ".initialX", 0, Integer.MAX_VALUE);
        int y = range(formation.get("initialY"), placement + ".initialY", 0, Integer.MAX_VALUE);
        EnemyCell[][] grid = grid(formation.get("grid"), placement + ".grid");
        int movement = range(wave.get("movementBaseFrames"), path + ".movementBaseFrames",
                0, policy.getMaxMovementBaseFrames());
        int shooting = range(wave.get("shootingIntervalMs"), path + ".shootingIntervalMs",
                policy.getMinShootingIntervalMs(), policy.getMaxShootingIntervalMs());
        try {
            return new WaveDefinition(id, grid, x, y, movement, shooting);
        } catch (IllegalArgumentException failure) {
            throw new LevelFormatException(path + ": invalid wave definition", failure);
        }
    }

    private EnemyCell[][] grid(final Object value, final String path) throws LevelFormatException {
        List<?> rows = array(value, path);
        if (rows.isEmpty()) throw new LevelFormatException(path + ": at least one row is required");
        LevelValidator.maximum(rows.size(), policy.getMaxRows(), path);
        EnemyCell[][] grid = new EnemyCell[rows.size()][];
        int columns = -1;
        boolean occupied = false;
        for (int row = 0; row < rows.size(); row++) {
            String rowPath = path + "[" + row + "]";
            List<?> cells = array(rows.get(row), rowPath);
            if (cells.isEmpty()) throw new LevelFormatException(rowPath + ": at least one column is required");
            LevelValidator.maximum(cells.size(), policy.getMaxColumns(), rowPath);
            if (columns != -1 && columns != cells.size()) {
                throw new LevelFormatException(rowPath + ": grid rows must have equal lengths");
            }
            columns = cells.size();
            grid[row] = new EnemyCell[columns];
            for (int column = 0; column < columns; column++) {
                if (cells.get(column) == null) continue;
                String cellPath = rowPath + "[" + column + "]";
                Map<?, ?> cell = object(cells.get(column), cellPath, "enemyType", "health");
                String type = string(cell.get("enemyType"), cellPath + ".enemyType");
                if (!policy.getEnemyTypes().contains(type)) {
                    throw new LevelFormatException(cellPath + ".enemyType: unsupported enemy type");
                }
                int health = range(cell.get("health"), cellPath + ".health", 1, policy.getMaxEnemyHealth());
                try {
                    grid[row][column] = new EnemyCell(type, health);
                } catch (IllegalArgumentException failure) {
                    throw new LevelFormatException(cellPath + ".enemyType: invalid enemy identifier", failure);
                }
                occupied = true;
            }
        }
        if (!occupied) throw new LevelFormatException(path + ": a wave must contain at least one enemy");
        return grid;
    }

    private String id(final Object value, final String path) throws LevelFormatException {
        String id = string(value, path);
        // Bound imported IDs before invoking the existing model constructors.
        validator.validateId(id, path);
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new LevelFormatException(path + ": invalid identifier");
        }
        return id;
    }

    private static Map<?, ?> object(final Object value, final String path, final String... fields)
            throws LevelFormatException {
        if (!(value instanceof Map)) throw new LevelFormatException(path + ": expected an object");
        Map<?, ?> object = (Map<?, ?>) value;
        List<String> allowed = Arrays.asList(fields);
        for (Object key : object.keySet()) {
            if (!allowed.contains(key)) throw new LevelFormatException(child(path, key) + ": unknown field");
        }
        for (String field : fields) {
            if (!object.containsKey(field)) throw new LevelFormatException(child(path, field) + ": required field missing");
        }
        return object;
    }

    private static String child(final String path, final Object field) {
        return "$".equals(path) ? field.toString() : path + "." + field;
    }

    private static List<?> array(final Object value, final String path) throws LevelFormatException {
        if (!(value instanceof List)) throw new LevelFormatException(path + ": expected an array");
        return (List<?>) value;
    }

    private static String string(final Object value, final String path) throws LevelFormatException {
        if (!(value instanceof String)) throw new LevelFormatException(path + ": expected a string");
        return (String) value;
    }

    private static int integer(final Object value, final String path) throws LevelFormatException {
        if (!(value instanceof LevelJsonParser.NumberValue)) {
            throw new LevelFormatException(path + ": expected an integer, not a string, boolean, or null");
        }
        return ((LevelJsonParser.NumberValue) value).integer(path);
    }

    private static int range(final Object value, final String path, final int minimum, final int maximum)
            throws LevelFormatException {
        int number = integer(value, path);
        if (number < minimum || number > maximum) {
            throw new LevelFormatException(path + ": expected " + minimum + ".." + maximum);
        }
        return number;
    }
}
