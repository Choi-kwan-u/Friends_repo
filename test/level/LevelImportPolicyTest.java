package level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelImportPolicyTest {
    @Test
    void exposesTheProposedDefaultsWithoutChangingTheModels() {
        LevelImportPolicy policy = LevelImportPolicy.defaults();
        assertEquals(8, policy.getMaxColumns());
        assertEquals(7, policy.getMaxRows());
        assertEquals(10, policy.getMaxWaves());
        assertEquals(10, policy.getMaxEnemyHealth());
        assertEquals(600, policy.getMaxMovementBaseFrames());
        assertEquals(100, policy.getMinShootingIntervalMs());
        assertEquals(60000, policy.getMaxShootingIntervalMs());
        assertEquals(1024 * 1024, policy.getMaxFileBytes());
        assertEquals(64, policy.getMaxIdLength());
        assertEquals(100, policy.getMaxNameLength());
        assertEquals(3, policy.getEnemyTypes().size());
        assertTrue(policy.getEnemyTypes().containsAll(Arrays.asList("A", "B", "C")));
    }

    @Test
    void snapshotsCallerAndBuilderState() {
        List<String> types = new ArrayList<String>(Collections.singletonList("boss"));
        LevelImportPolicy.Builder builder = LevelImportPolicy.builder().enemyTypes(types);
        types.clear();
        LevelImportPolicy first = builder.maxEnemyHealth(20).build();
        LevelImportPolicy second = builder.maxEnemyHealth(30).enemyTypes(Arrays.asList("D")).build();
        assertEquals(20, first.getMaxEnemyHealth());
        assertEquals(Collections.singleton("boss"), first.getEnemyTypes());
        assertEquals(30, second.getMaxEnemyHealth());
        assertThrows(UnsupportedOperationException.class, () -> first.getEnemyTypes().clear());
    }

    @Test
    void rejectsUnusableConfigurationAtConstruction() {
        LevelImportPolicy.Builder[] invalid = {
            LevelImportPolicy.builder().maxFileBytes(0),
            LevelImportPolicy.builder().maxWaves(0),
            LevelImportPolicy.builder().maxRows(-1),
            LevelImportPolicy.builder().maxColumns(0),
            LevelImportPolicy.builder().maxEnemyHealth(0),
            LevelImportPolicy.builder().maxMovementBaseFrames(-1),
            LevelImportPolicy.builder().minShootingIntervalMs(0),
            LevelImportPolicy.builder().maxShootingIntervalMs(99),
            LevelImportPolicy.builder().maxIdLength(0),
            LevelImportPolicy.builder().maxNameLength(0),
            LevelImportPolicy.builder().enemyTypes(null),
            LevelImportPolicy.builder().enemyTypes(Collections.<String>emptyList()),
            LevelImportPolicy.builder().enemyTypes(Arrays.asList(" A ")),
            LevelImportPolicy.builder().enemyTypes(Arrays.asList((String) null))
        };
        for (LevelImportPolicy.Builder builder : invalid) {
            assertThrows(IllegalArgumentException.class, builder::build);
        }
        assertEquals(0, LevelImportPolicy.builder().maxMovementBaseFrames(0)
                .build().getMaxMovementBaseFrames());
    }
}
