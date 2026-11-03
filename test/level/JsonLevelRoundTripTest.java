package level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Integration checks against the existing, unmodified JSON exporter. */
class JsonLevelRoundTripTest {
    @TempDir Path directory;
    private final LevelWriter writer = new JsonLevelWriter();

    @Test
    void exportsAndImportsEveryModelFieldWithoutCompactingTheGrid() throws Exception {
        EnemyCell[][] grid = {
            { null, new EnemyCell("A", 2), null },
            { null, null, null },
            { null, new EnemyCell("C", 10), null }
        };
        LevelDefinition original = new LevelDefinition(1, "custom-stage",
                "한글 \"stage\"\\\n\b\f\r\t\u0000\u001f\ud83d\ude80",
                Arrays.asList(
                    new WaveDefinition("wave-02", grid, 40, 120, 0, 100),
                    new WaveDefinition("wave-01",
                        new EnemyCell[][] { { new EnemyCell("B", 1) } },
                        0, Integer.MAX_VALUE, 600, 60000)));
        Path output = directory.resolve("round-trip.json");
        writer.write(output, original);
        byte[] exported = Files.readAllBytes(output);

        LevelDefinition imported = new JsonLevelReader().read(output);

        assertDefinitionEquals(original, imported);
        assertArrayEquals(exported, Files.readAllBytes(output));
        Path repeated = directory.resolve("repeated.json");
        writer.write(repeated, imported);
        assertArrayEquals(exported, Files.readAllBytes(repeated));
    }

    @Test
    void importsTheRepositorySampleAndRetainsItThroughExportAndReimport() throws Exception {
        Path sample = resource("/examples/levels/campaign-stage-01.json", "sample.json");
        LevelReader reader = new JsonLevelReader();
        LevelDefinition original = reader.read(sample);
        assertEquals("campaign-stage-01", original.getId());
        assertEquals("First Contact", original.getName());
        assertEquals(2, original.getWaves().size());
        assertEquals(20, original.getWaves().get(0).getEnemyCount());
        assertEquals(8, original.getWaves().get(1).getEnemyCount());
        Path output = directory.resolve("sample-export.json");

        writer.write(output, original);

        assertDefinitionEquals(original, reader.read(output));
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @ValueSource(strings = { "\n", "\r\n" })
    void importsTheExistingExporterFixtureWithExplicitPolicyOverrides(final String lineEnding)
            throws Exception {
        Path fixture = resource("/level/fixtures/export-expected.json", "export-fixture.json");
        String json = readWithLfLineEndings(fixture);
        Files.write(fixture, json.replace("\n", lineEnding).getBytes(StandardCharsets.UTF_8));
        assertThrows(LevelFormatException.class, () -> new JsonLevelReader().read(fixture));
        LevelImportPolicy policy = LevelImportPolicy.builder()
                .enemyTypes(Arrays.asList("A", "C", "enemy\"\\\n\t\u0001X"))
                .maxEnemyHealth(Integer.MAX_VALUE)
                .maxShootingIntervalMs(Integer.MAX_VALUE)
                .build();
        LevelReader reader = new JsonLevelReader(policy);
        LevelDefinition imported = reader.read(fixture);
        EnemyCell custom = imported.getWaves().get(0).getCell(2, 1);
        assertEquals("enemy\"\\\n\t\u0001X", custom.getEnemyType());
        assertEquals(Integer.MAX_VALUE, custom.getHealth());
        assertEquals(Integer.MAX_VALUE, imported.getWaves().get(0).getShootingIntervalMs());
        Path output = directory.resolve("fixture-export.json");

        writer.write(output, imported);

        byte[] exported = Files.readAllBytes(output);
        // Checkouts may use CRLF, while the exporter always writes LF.
        assertArrayEquals(readWithLfLineEndings(fixture).getBytes(StandardCharsets.UTF_8), exported);
        String exportedJson = new String(exported, StandardCharsets.UTF_8);
        assertFalse(exportedJson.contains("\r"), "Exported JSON must use LF line endings");
        assertTrue(exportedJson.endsWith("\n"), "Exported JSON must end with LF");
        assertDefinitionEquals(imported, reader.read(output));
    }

    private static String readWithLfLineEndings(final Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    private Path resource(final String name, final String filename) throws IOException {
        Path destination = directory.resolve(filename);
        try (InputStream input = getClass().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Include test/ and the repository root on the classpath: " + name);
            Files.copy(input, destination);
        }
        return destination;
    }

    private static void assertDefinitionEquals(final LevelDefinition expected,
            final LevelDefinition actual) {
        assertEquals(expected.getSchemaVersion(), actual.getSchemaVersion());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getWaves().size(), actual.getWaves().size());
        for (int index = 0; index < expected.getWaves().size(); index++) {
            WaveDefinition left = expected.getWaves().get(index);
            WaveDefinition right = actual.getWaves().get(index);
            assertEquals(left.getId(), right.getId());
            assertEquals(left.getInitialX(), right.getInitialX());
            assertEquals(left.getInitialY(), right.getInitialY());
            assertEquals(left.getMovementBaseFrames(), right.getMovementBaseFrames());
            assertEquals(left.getShootingIntervalMs(), right.getShootingIntervalMs());
            assertEquals(left.getRows(), right.getRows());
            assertEquals(left.getColumns(), right.getColumns());
            assertEquals(left.getEnemyCount(), right.getEnemyCount());
            for (int row = 0; row < left.getRows(); row++) {
                for (int column = 0; column < left.getColumns(); column++) {
                    EnemyCell cell = left.getCell(row, column);
                    EnemyCell other = right.getCell(row, column);
                    if (cell == null) assertNull(other);
                    else {
                        assertNotNull(other);
                        assertEquals(cell.getEnemyType(), other.getEnemyType());
                        assertEquals(cell.getHealth(), other.getHealth());
                    }
                }
            }
        }
    }
}
