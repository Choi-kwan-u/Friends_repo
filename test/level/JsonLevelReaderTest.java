package level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonLevelReaderTest {
    @TempDir Path directory;
    private final LevelReader reader = new JsonLevelReader();

    @Test
    void readsEveryFieldAndRetainsWaveOrderAndEmptyPositions() throws Exception {
        Path input = write(fixture());
        byte[] previous = Files.readAllBytes(input);
        LevelDefinition level = reader.read(input);
        assertEquals(1, level.getSchemaVersion());
        assertEquals("custom-stage", level.getId());
        assertEquals("한글 \"Stage\" \\ \ud83d\ude80\n", level.getName());
        assertEquals(2, level.getWaves().size());
        WaveDefinition first = level.getWaves().get(0);
        assertEquals("wave-02", first.getId());
        assertEquals("wave-01", level.getWaves().get(1).getId());
        assertEquals(40, first.getInitialX());
        assertEquals(120, first.getInitialY());
        assertEquals(3, first.getRows());
        assertEquals(3, first.getColumns());
        assertEquals(2, first.getEnemyCount());
        assertEquals("A", first.getCell(0, 1).getEnemyType());
        assertEquals(2, first.getCell(0, 1).getHealth());
        assertEquals("C", first.getCell(2, 1).getEnemyType());
        assertEquals(0, first.getMovementBaseFrames());
        assertEquals(1000, first.getShootingIntervalMs());
        for (int row = 0; row < 3; row++) {
            assertNull(first.getCell(row, 0));
            assertNull(first.getCell(row, 2));
        }
        assertNull(first.getCell(1, 1));
        assertArrayEquals(previous, Files.readAllBytes(input));
    }

    @Test
    void acceptsEditorBomCrLfAndExactIntegralNumberNotation() throws Exception {
        String input = "\ufeff" + fixture().replace("\n", "\r\n")
                .replace("\"initialX\": 40", "\"initialX\": 4e1")
                .replace("\"initialY\": 120", "\"initialY\": 120.0");
        assertEquals(40, reader.read(write(input)).getWaves().get(0).getInitialX());
    }

    @Test
    void separatesFilesystemFailuresFromInvalidFileContent() throws Exception {
        assertThrows(NoSuchFileException.class, () -> reader.read(directory.resolve("missing.json")));
        IOException directoryError = assertThrows(IOException.class, () -> reader.read(directory));
        assertFalse(directoryError instanceof LevelFormatException);
        assertThrows(NullPointerException.class, () -> reader.read(null));
        assertThrows(NullPointerException.class, () -> new JsonLevelReader(null));
        Path malformed = write("not json");
        assertThrows(LevelFormatException.class, () -> reader.read(malformed));
    }

    @Test
    void rejectsMalformedUtf8InsteadOfReplacingCharacters() throws Exception {
        Path file = directory.resolve("bad-utf8.json");
        Files.write(file, new byte[] { (byte) 0xc3, (byte) 0x28 });
        LevelFormatException error = assertThrows(LevelFormatException.class, () -> reader.read(file));
        assertTrue(error.getMessage().contains("UTF-8"));
        assertNotNull(error.getCause());
    }

    private Path write(final String json) throws IOException {
        Path path = directory.resolve("input.json");
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    static String fixture() throws IOException {
        try (InputStream input = JsonLevelReaderTest.class.getResourceAsStream("/level/fixtures/import-valid.json")) {
            if (input == null) throw new IOException("Include test/ on the test resource classpath");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
