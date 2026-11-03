package level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable serialized level design, not player progression or level provenance.
 * Gameplay selects the mode; the same design can be reused by different modes.
 */
public final class LevelDefinition {
    public static final int SCHEMA_VERSION = 1;

    private final String id;
    private final String name;
    private final List<WaveDefinition> waves;

    public LevelDefinition(final int schemaVersion, final String id,
            final String name,
            final List<WaveDefinition> waves) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + schemaVersion);
        }
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid level id");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (waves == null || waves.isEmpty()) {
            throw new IllegalArgumentException("A level must contain at least one wave");
        }
        List<WaveDefinition> copy = new ArrayList<WaveDefinition>(waves);
        Set<String> ids = new HashSet<String>();
        for (WaveDefinition wave : copy) {
            if (wave == null || !ids.add(wave.getId())) {
                throw new IllegalArgumentException("Waves must be non-null with unique ids");
            }
        }
        this.id = id;
        this.name = name;
        this.waves = Collections.unmodifiableList(copy);
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Ordered, unmodifiable wave definitions. */
    public List<WaveDefinition> getWaves() {
        return waves;
    }
}
