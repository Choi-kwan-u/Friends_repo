package level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonLevelReaderValidationTest {
    private static final String CELL = "{\"enemyType\":\"A\",\"health\":1}";
    @TempDir Path directory;
    private final LevelReader reader = new JsonLevelReader();

    @Test
    void rejectsUnknownFieldsAtEveryObjectLevel() throws Exception {
        invalid(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"mode\":\"campaign\""), "mode");
        invalid(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"official\":true"), "official");
        invalid(valid().replace("\"id\":\"wave\"", "\"id\":\"wave\",\"extra\":null"), "waves[0].extra");
        invalid(valid().replace("\"initialX\":20", "\"initialX\":20,\"width\":1"), "waves[0].formation.width");
        invalid(valid().replace("\"health\":1", "\"health\":1,\"score\":10"), "waves[0].formation.grid[0][0].score");
    }

    @Test
    void rejectsMissingFieldsAtEveryObjectLevel() throws Exception {
        invalid(valid().replace("\"name\":\"Stage\",", ""), "name");
        invalid(valid().replace("\"id\":\"wave\",", ""), "waves[0].id");
        invalid(valid().replace("\"initialX\":20,", ""), "waves[0].formation.initialX");
        invalid(valid().replace("\"enemyType\":\"A\",", ""), "waves[0].formation.grid[0][0].enemyType");
    }

    @Test
    void rejectsIncorrectJsonTypesInsteadOfCoercingThem() throws Exception {
        invalid("[]", "$");
        invalid("null", "$");
        invalid(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""), "schemaVersion");
        invalid(valid().replace("\"id\":\"stage\"", "\"id\":true"), "id");
        invalid(valid().replace("\"name\":\"Stage\"", "\"name\":null"), "name");
        invalid(valid().replace("\"health\":1", "\"health\":\"1\""), "waves[0].formation.grid[0][0].health");
        invalid(valid().replace("\"initialX\":20", "\"initialX\":false"), "waves[0].formation.initialX");
        invalid(document("{}"), "waves[0].formation.grid");
        invalid(document("[null]"), "waves[0].formation.grid[0]");
        invalid(document("[[false]]"), "waves[0].formation.grid[0][0]");
    }

    @Test
    void rejectsUnsupportedVersionsInvalidIdentityAndDuplicateWaveIds() throws Exception {
        invalid(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "schemaVersion");
        invalid(valid().replace("\"id\":\"stage\"", "\"id\":\"Invalid ID\""), "id");
        invalid(valid().replace("\"id\":\"wave\"", "\"id\":\"wave--1\""), "waves[0].id");
        invalid(valid().replace("\"name\":\"Stage\"", "\"name\":\"  \""), "name");
        invalid(JsonLevelReaderTest.fixture().replace("wave-01", "wave-02"), "waves[1].id");
    }

    @Test
    void rejectsDuplicateKeysBeforeTheyCanBeOverwritten() throws Exception {
        invalid(valid().replace("\"id\":\"stage\"", "\"id\":null,\"\\u0069d\":\"stage\""), "id");
        invalid(valid().replace("\"health\":1", "\"health\":1,\"health\":2"), "waves[0].formation.grid[0][0].health");
    }

    @Test
    void rejectsEmptyAndRaggedFormations() throws Exception {
        invalid(document("[]"), "waves[0].formation.grid");
        invalid(document("[[]]"), "waves[0].formation.grid[0]");
        invalid(document("[[null,null],[null,null]]"), "waves[0].formation.grid");
        invalid(document("[[" + CELL + ",null],[null]]"), "waves[0].formation.grid[1]");
        invalid("{\"schemaVersion\":1,\"id\":\"stage\",\"name\":\"Stage\",\"waves\":[]}", "waves");
    }

    @Test
    void rejectsUnsupportedEnemiesAndOutOfRangeValues() throws Exception {
        invalid(valid().replace("\"A\"", "\"unknown\""), "waves[0].formation.grid[0][0].enemyType");
        for (String health : new String[] { "0", "-1", "11", "1.5", "2147483648", "1e1000000" }) {
            invalid(valid().replace("\"health\":1", "\"health\":" + health), "waves[0].formation.grid[0][0].health");
        }
        invalid(valid().replace("\"initialY\":100", "\"initialY\":-1"), "waves[0].formation.initialY");
        invalid(valid().replace("\"initialX\":20", "\"initialX\":2147483648"), "waves[0].formation.initialX");
        invalid(valid().replace("\"movementBaseFrames\":60", "\"movementBaseFrames\":-1"), "waves[0].movementBaseFrames");
        invalid(valid().replace("\"movementBaseFrames\":60", "\"movementBaseFrames\":601"), "waves[0].movementBaseFrames");
        for (int shooting : new int[] { 0, 99, 60001 }) {
            invalid(valid().replace("\"shootingIntervalMs\":1000", "\"shootingIntervalMs\":" + shooting), "waves[0].shootingIntervalMs");
        }
    }

    @Test
    void enforcesCallerRegistryAndLimitsBeforeReturningADefinition() throws Exception {
        String custom = valid().replace("\"A\"", "\"boss\"").replace("\"health\":1", "\"health\":20");
        JsonLevelReader configured = new JsonLevelReader(LevelImportPolicy.builder()
                .enemyTypes(Arrays.asList("boss")).maxEnemyHealth(20).build());
        EnemyCell cell = configured.read(write(custom)).getWaves().get(0).getCell(0, 0);
        assertEquals("boss", cell.getEnemyType());
        assertEquals(20, cell.getHealth());
    }

    @Test
    void enforcesGridAndWaveBudgets() throws Exception {
        invalid(document("[[" + CELL + repeat(",null", 8) + "]]"), "waves[0].formation.grid[0]");
        invalid(document("[[" + CELL + "]" + repeat(",[null]", 7) + "]"), "waves[0].formation.grid");
        String wave = valid().substring(valid().indexOf("[{\"id\":") + 1, valid().length() - 2);
        StringBuilder waves = new StringBuilder();
        for (int index = 0; index < 11; index++) {
            if (index > 0) waves.append(',');
            waves.append(wave.replace("\"wave\"", "\"wave-" + index + "\""));
        }
        String many = "{\"schemaVersion\":1,\"id\":\"stage\",\"name\":\"Stage\",\"waves\":[" + waves + "]}";
        invalid(many, "waves");
        assertEquals(11, new JsonLevelReader(LevelImportPolicy.builder().maxWaves(11).build())
                .read(write(many)).getWaves().size());
    }

    @Test
    void boundsFileBytesAndTextBeforeConstructingModels() throws Exception {
        String json = JsonLevelReaderTest.fixture();
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes > json.length());
        new JsonLevelReader(LevelImportPolicy.builder().maxFileBytes(bytes).build()).read(write(json));
        assertThrows(LevelFormatException.class,
                () -> new JsonLevelReader(LevelImportPolicy.builder().maxFileBytes(bytes - 1).build()).read(write(json)));
        invalid(valid().replace("\"id\":\"stage\"", "\"id\":\"" + repeat("a-", 10000) + "a\""), "id");
        invalid(valid().replace("\"name\":\"Stage\"", "\"name\":\"" + repeat("x", 101) + "\""), "name");
    }

    @Test
    void lateFailureLeavesTheCallersPreviouslyLoadedDefinitionUnchanged() throws Exception {
        String json = JsonLevelReaderTest.fixture();
        final LevelDefinition[] active = { reader.read(write(json)) };
        LevelDefinition previous = active[0];
        Path bad = write(json.replace("\"health\": 10", "\"health\": 11"));
        byte[] before = Files.readAllBytes(bad);
        assertThrows(LevelFormatException.class, () -> active[0] = reader.read(bad));
        assertSame(previous, active[0]);
        assertEquals(10, active[0].getWaves().get(1).getCell(0, 0).getHealth());
        assertArrayEquals(before, Files.readAllBytes(bad));
        assertEquals("custom-stage", reader.read(write(json)).getId());
    }

    private void invalid(final String json, final String path) throws Exception {
        Path file = write(json);
        LevelFormatException error = assertThrows(LevelFormatException.class, () -> reader.read(file));
        assertTrue(error.getMessage().startsWith(path + ":"), error.getMessage());
    }

    private Path write(final String json) throws IOException {
        Path file = directory.resolve("input.json");
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String valid() { return document("[[" + CELL + "]]"); }

    private static String document(final String grid) {
        return "{\"schemaVersion\":1,\"id\":\"stage\",\"name\":\"Stage\",\"waves\":["
                + "{\"id\":\"wave\",\"formation\":{\"initialX\":20,\"initialY\":100,\"grid\":" + grid
                + "},\"movementBaseFrames\":60,\"shootingIntervalMs\":1000}]}";
    }

    private static String repeat(final String text, final int count) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < count; index++) output.append(text);
        return output.toString();
    }
}
