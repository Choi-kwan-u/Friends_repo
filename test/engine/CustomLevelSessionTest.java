package engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomLevelSessionTest {
    @TempDir Path directory;
    private final AtomicReference<Double> difficulty = new AtomicReference<>(1.0);
    private final AtomicInteger plays = new AtomicInteger();
    private final AtomicReference<LevelDefinition> played = new AtomicReference<>();

    private CustomLevelSession session() {
        return new CustomLevelSession(448, 520, new JsonLevelReader(), new JsonLevelWriter(),
                () -> difficulty.get(), definition -> {
                    plays.incrementAndGet();
                    played.set(definition);
                    return new GameState(1, 123, 2, 4, 2);
                });
    }

    private static WaveDefinition wave(String id, int x, int y, String enemy) {
        return new WaveDefinition(id, new EnemyCell[][] {
            {null, new EnemyCell(enemy, 2), null}, {null, null, null}
        }, x, y, 60, 2000);
    }

    private static LevelDefinition level(String name) {
        return new LevelDefinition(1, "custom-stage", name,
                Arrays.asList(wave("second", 40, 100, "B"), wave("first", 80, 120, "C")));
    }

    private Path write(String filename, LevelDefinition definition) throws IOException {
        Path path = directory.resolve(filename);
        new JsonLevelWriter().write(path, definition);
        return path;
    }

    @Test
    void startsEmptyAndRefusesSaveOrPlayBeforeLoading() {
        CustomLevelSession session = session();
        Path output = directory.resolve("not-created.json");
        assertFalse(session.getLoadedLevel().isPresent());
        assertThrows(IllegalStateException.class, session::play);
        assertThrows(IllegalStateException.class, () -> session.save(output));
        assertFalse(Files.exists(output));
        assertEquals(0, plays.get());
    }

    @Test
    void actualReaderWriterAndPlayerShareTheOriginalDefinition() throws IOException {
        CustomLevelSession session = session();
        Path source = write("source.json", level("한글 레벨"));
        LevelDefinition loaded = session.load(source);
        assertSame(loaded, session.getLoadedLevel().get());
        assertEquals("한글 레벨", loaded.getName());
        assertEquals("second", loaded.getWaves().get(0).getId());
        assertNull(loaded.getWaves().get(0).getCell(1, 1));
        difficulty.set(1.25);
        GameState result = session.play();
        assertEquals(123, result.getScore());
        assertSame(loaded, played.get());
        assertEquals(1, plays.get());
        assertEquals(60, played.get().getWaves().get(0).getMovementBaseFrames());
        assertEquals(2000, played.get().getWaves().get(0).getShootingIntervalMs());
        Path output = directory.resolve("saved.json");
        session.save(output);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(output));
        CustomLevelSession reopened = session();
        LevelDefinition reimported = reopened.load(output);
        assertEquals(2, reimported.getWaves().size());
        assertEquals(2, reimported.getWaves().get(0).getCell(0, 1).getHealth());
        assertSame(loaded, session.getLoadedLevel().get());
    }

    @Test
    void malformedMissingAndNullSourcesKeepThePreviousSelection() throws IOException {
        CustomLevelSession session = session();
        LevelDefinition previous = session.load(write("good.json", level("Previous")));
        Path malformed = directory.resolve("broken.json");
        byte[] content = "{\"waves\": [".getBytes(StandardCharsets.UTF_8);
        Files.write(malformed, content);
        assertThrows(LevelFormatException.class, () -> session.load(malformed));
        assertThrows(IOException.class, () -> session.load(directory.resolve("missing.json")));
        assertThrows(NullPointerException.class, () -> session.load(null));
        assertSame(previous, session.getLoadedLevel().get());
        assertArrayEquals(content, Files.readAllBytes(malformed));
        assertEquals(0, plays.get());
    }

    @Test
    void validJsonWithIncompatibleLaterWaveCannotReplaceThePreviousSelection() throws IOException {
        CustomLevelSession session = session();
        LevelDefinition previous = session.load(write("good.json", level("Previous")));
        LevelDefinition incompatible = new LevelDefinition(1, "invalid-stage", "Invalid",
                Arrays.asList(wave("valid", 20, 100, "A"), wave("invalid", 20, 101, "A")));
        Path source = write("incompatible.json", incompatible);
        // Prove this is a gameplay rejection, not a JSON/reader failure.
        assertEquals(2, new JsonLevelReader().read(source).getWaves().size());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> session.load(source));
        assertTrue(error.getMessage().contains("waves[1]"));
        assertSame(previous, session.getLoadedLevel().get());
        assertEquals(0, plays.get());
    }

    @Test
    void expandedImportRegistryDoesNotBypassActualEnemySupport() throws IOException {
        JsonLevelReader reader = new JsonLevelReader(LevelImportPolicy.builder()
                .enemyTypes(Arrays.asList("A", "B", "C", "boss")).build());
        CustomLevelSession session = new CustomLevelSession(448, 520, reader,
                new JsonLevelWriter(), () -> 1.0, definition -> {
                    fail("Incompatible level must not run"); return null;
                });
        LevelDefinition previous = session.load(write("good.json", level("Previous")));
        LevelDefinition unsupported = new LevelDefinition(1, "boss-stage", "Boss",
                Collections.singletonList(wave("wave", 20, 100, "boss")));
        Path source = write("boss.json", unsupported);
        assertEquals("boss", reader.read(source).getWaves().get(0).getCell(0, 1).getEnemyType());
        assertThrows(IllegalArgumentException.class, () -> session.load(source));
        assertSame(previous, session.getLoadedLevel().get());
    }

    @Test
    void changedSettingsAreRevalidatedBeforePlayingOrReplacingSelection() throws IOException {
        CustomLevelSession session = session();
        Path source = write("source.json", level("Previous"));
        LevelDefinition previous = session.load(source);
        difficulty.set(Double.NaN);
        assertThrows(IllegalArgumentException.class, session::play);
        assertThrows(IllegalArgumentException.class, () -> session.load(source));
        assertEquals(0, plays.get());
        assertSame(previous, session.getLoadedLevel().get());
        // Saving design data does not depend on transient runtime settings.
        Path output = directory.resolve("saved.json");
        session.save(output);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(output));
    }

    @Test
    void failedSaveKeepsTheLoadedDefinitionAndDoesNotAffectTheSource() throws IOException {
        CustomLevelSession session = session();
        Path source = write("source.json", level("Previous"));
        byte[] original = Files.readAllBytes(source);
        LevelDefinition previous = session.load(source);
        assertThrows(IOException.class, () -> session.save(directory));
        assertThrows(NullPointerException.class, () -> session.save(null));
        assertSame(previous, session.getLoadedLevel().get());
        assertArrayEquals(original, Files.readAllBytes(source));
        // The UI may explicitly confirm overwriting the original source path.
        session.save(source);
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void failedPlayerKeepsTheLoadedDefinition() throws IOException {
        CustomLevelSession session = new CustomLevelSession(448, 520, new JsonLevelReader(),
                new JsonLevelWriter(), () -> 1.0, definition -> {
                    throw new IllegalStateException("Simulated unavailable game window");
                });
        LevelDefinition loaded = session.load(write("source.json", level("Previous")));
        assertThrows(IllegalStateException.class, session::play);
        assertSame(loaded, session.getLoadedLevel().get());
    }

    @Test
    void successfulSecondLoadReplacesSelectionWithoutPlayingOrWritingItsSource() throws IOException {
        CustomLevelSession session = session();
        session.load(write("first.json", level("First")));
        Path source = write("second.json", level("Second"));
        byte[] original = Files.readAllBytes(source);
        LevelDefinition next = session.load(source);
        assertEquals("Second", session.getLoadedLevel().get().getName());
        assertSame(next, session.getLoadedLevel().get());
        assertEquals(0, plays.get());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void rejectsInvalidViewportAndMissingCollaborators() {
        assertThrows(IllegalArgumentException.class, () -> new CustomLevelSession(0, 520));
        assertThrows(NullPointerException.class,
                () -> new CustomLevelSession(448, 520, null, new JsonLevelWriter(), () -> 1, d -> null));
        assertThrows(NullPointerException.class,
                () -> new CustomLevelSession(448, 520, new JsonLevelReader(), null, () -> 1, d -> null));
        assertThrows(NullPointerException.class,
                () -> new CustomLevelSession(448, 520, new JsonLevelReader(), new JsonLevelWriter(), null, d -> null));
        assertThrows(NullPointerException.class,
                () -> new CustomLevelSession(448, 520, new JsonLevelReader(), new JsonLevelWriter(), () -> 1, null));
    }
}
