package level;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class JsonLevelWriterTest {
    @TempDir
    Path directory;

    private final LevelWriter writer = new JsonLevelWriter();

    @Test
    void preservesEveryFieldAndGridGapUsingUtf8Json() throws IOException {
        Path output = directory.resolve("stage.json");
        LevelDefinition level = definition("한글 \"stage\"\\\n\b\f\r\t\u0000\u001f\ud83d\ude80");
        byte[] expected;
        try (InputStream resource = JsonLevelWriterTest.class.getResourceAsStream(
                "/level/fixtures/export-expected.json")) {
            assertNotNull(resource, "Include test/ as a test resource root on the classpath");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                String text = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                expected = (text + "\n").getBytes(StandardCharsets.UTF_8);
            }
        }

        writer.write(output, level);

        assertArrayEquals(expected, Files.readAllBytes(output));
        assertEquals(2, level.getWaves().size());
        WaveDefinition wave = level.getWaves().get(0);
        assertEquals(3, wave.getRows());
        assertEquals(3, wave.getColumns());
        assertEquals(2, wave.getEnemyCount());
        assertNull(wave.getCell(0, 0));
        assertNull(wave.getCell(1, 1));
        assertNull(wave.getCell(2, 2));
        assertEquals(0, wave.getMovementBaseFrames());
        assertEquals(Integer.MAX_VALUE, wave.getShootingIntervalMs());
        assertOnly(output);
    }

    @Test
    void replacesAnExistingFileAndProducesDeterministicOutput() throws IOException {
        Path output = directory.resolve("stage.json");
        Files.write(output, "old contents that must disappear".getBytes(StandardCharsets.UTF_8));
        LevelDefinition level = definition("Stage");
        writer.write(output, level);
        byte[] first = Files.readAllBytes(output);
        writer.write(output, level);
        assertArrayEquals(first, Files.readAllBytes(output));
        assertTrue(new String(first, StandardCharsets.UTF_8).endsWith("\n"));
        assertOnly(output);
    }

    @Test
    void serializationFailurePreservesExistingFileAndLeavesNoTemporaryFile() throws IOException {
        Path output = directory.resolve("stage.json");
        byte[] previous = "keep me".getBytes(StandardCharsets.UTF_8);
        Files.write(output, previous);
        for (String name : new String[] { "Bad\ud800", "Bad\udc00", "Bad\ud800X" }) {
            LevelFormatException error = assertThrows(LevelFormatException.class,
                    () -> writer.write(output, definition(name)));
            assertTrue(error.getMessage().startsWith("name:"));
            assertTrue(error.getMessage().contains("character index 3"));
            assertArrayEquals(previous, Files.readAllBytes(output));
            assertOnly(output);
        }
    }

    @Test
    void invalidEnemyIdentifierReportsItsGridLocation() throws IOException {
        Path output = directory.resolve("stage.json");
        WaveDefinition wave = new WaveDefinition("wave-01",
                new EnemyCell[][] { { null, new EnemyCell("Bad\ud800", 1) } },
                20, 100, 60, 2000);
        LevelDefinition level = new LevelDefinition(1, "stage", "Stage",
                Collections.singletonList(wave));
        LevelFormatException error = assertThrows(LevelFormatException.class,
                () -> writer.write(output, level));
        assertTrue(error.getMessage().startsWith("waves[0].formation.grid[0][1].enemyType:"));
        assertFalse(Files.exists(output));
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void rejectsNullArgumentsBeforeChangingFiles() throws IOException {
        Path output = directory.resolve("stage.json");
        Files.write(output, new byte[] { 42 });
        assertThrows(NullPointerException.class, () -> writer.write(null, definition("Stage")));
        assertThrows(NullPointerException.class, () -> writer.write(output, null));
        assertArrayEquals(new byte[] { 42 }, Files.readAllBytes(output));
        assertOnly(output);
    }

    @Test
    void missingParentFailsWithoutCreatingDirectories() throws IOException {
        Path missing = directory.resolve("missing");
        assertThrows(IOException.class,
                () -> writer.write(missing.resolve("stage.json"), definition("Stage")));
        assertFalse(Files.exists(missing));
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void rejectsDirectoryAndSymbolicLinkTargets() throws IOException {
        Path target = directory.resolve("target");
        Files.createDirectory(target);
        assertThrows(IOException.class, () -> writer.write(target, definition("Stage")));
        assertTrue(Files.isDirectory(target));
        Path original = directory.resolve("original.json");
        Files.write(original, new byte[] { 7 });
        Path link = directory.resolve("link.json");
        try {
            Files.createSymbolicLink(link, original);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Symbolic links unavailable: " + unavailable.getMessage());
        }
        assertThrows(IOException.class, () -> writer.write(link, definition("Stage")));
        assertTrue(Files.isSymbolicLink(link));
        assertArrayEquals(new byte[] { 7 }, Files.readAllBytes(original));
    }

    @Test
    void failedAtomicReplacementCleansUpTheTemporaryFile() throws IOException {
        Path output = directory.resolve("stage.json");
        Files.write(output, new byte[] { 9 });
        LevelWriter failingWriter = new JsonLevelWriter((temporary, target) -> {
            assertEquals(directory, temporary.getParent());
            assertEquals(output, target);
            assertTrue(Files.size(temporary) > 0);
            throw new java.nio.file.AtomicMoveNotSupportedException(
                    temporary.toString(), target.toString(), "Injected filesystem failure");
        });
        assertThrows(java.nio.file.AtomicMoveNotSupportedException.class,
                () -> failingWriter.write(output, definition("Stage")));
        assertArrayEquals(new byte[] { 9 }, Files.readAllBytes(output));
        assertOnly(output);
    }

    private void assertOnly(final Path output) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(Collections.singletonList(output),
                    entries.collect(java.util.stream.Collectors.toList()));
        }
    }

    private static LevelDefinition definition(final String name) {
        EnemyCell[][] grid = {
            { null, new EnemyCell("A", 1), null },
            { null, null, null },
            { null, new EnemyCell("enemy\"\\\n\t\u0001X", Integer.MAX_VALUE), null }
        };
        WaveDefinition first = new WaveDefinition("wave-02", grid, 40, 120,
                0, Integer.MAX_VALUE);
        WaveDefinition second = new WaveDefinition("wave-01",
                new EnemyCell[][] { { new EnemyCell("C", 2) } }, 0, 0, 60, 2000);
        return new LevelDefinition(1, "custom-stage", name, Arrays.asList(first, second));
    }
}
