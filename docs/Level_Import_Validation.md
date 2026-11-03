# Level import validation

Related to #16. `LevelImportPolicy` and `LevelValidator` implement the import
validation owned by 331leo. They do not change the shared models, export code,
enemy implementation, menu, progression, or official campaign unlocks.

The defaults below adopt the issue/spec proposals as configurable import
defaults. They are not a finalized cross-team registry or a guarantee that a
definition is playable. A caller can replace every value before reading a file.

| Setting | Default |
| --- | --- |
| Supported enemy identifiers | `A`, `B`, `C` (sample identifiers) |
| File size | 1 MiB (bounded input, including whitespace) |
| Waves per definition | 10 |
| Grid dimensions | 7 rows by 8 columns |
| Enemy health | 1 through 10 |
| Base movement frames | 0 through 600 |
| Shooting interval | 100 through 60000 ms |
| Stage/wave ID length | 64 ASCII characters |
| Name length | 100 Unicode code points |

```java
LevelImportPolicy policy = LevelImportPolicy.builder()
        .enemyTypes(java.util.Arrays.asList("A", "B", "C", "boss"))
        .maxEnemyHealth(20)
        .maxWaves(12)
        .maxFileBytes(2 * 1024 * 1024)
        .build();
new LevelValidator(policy).validate(definition);
```

Policy objects copy their registry and are immutable. Invalid configurations
fail at construction. The shared model still owns structural invariants such
as nonnegative coordinates, rectangular grids, positive health, and unique wave
IDs; the validator adds policy bounds, supported enemy types, and well-formed
Unicode. Errors are `LevelFormatException` instances with paths such as
`waves[1].formation.grid[2][3].enemyType`.

Validation does not trim, reorder, or mutate a definition. Null cells, empty
rows/columns, coordinates, health, and base timing values are preserved. The
reader is responsible for enforcing `maxFileBytes` while reading bytes, before
JSON decoding or model construction. Calling `LevelValidator` on a model alone
does not perform a file-size check.

Screen fit, Y alignment, actual enemy behavior/health support, difficulty
conversion, and applying a whole definition belong to the gameplay handoff.
Passing validation must not be treated as official-level provenance. Only the
caller may replace its active definition after a successful read and gameplay
compatibility check; these classes have no game-state or filesystem side effects.

## Checks

The JUnit 5 tests are in the existing `test/level/` location. With the JUnit
Platform Console Standalone jar available as `junit_jar`:

```sh
build_dir=$(mktemp -d)
javac --release 8 -encoding UTF-8 -cp "$junit_jar" -d "$build_dir" \
  src/level/*.java test/level/*.java
java -jar "$junit_jar" execute --class-path "$build_dir" \
  --select-class level.LevelImportPolicyTest \
  --select-class level.LevelValidatorTest
```

The tests cover defaults, caller overrides, immutable policy state, invalid
configuration, exact boundaries, unsupported enemies, Unicode, field paths,
and unchanged sparse definition data. Production additions use Java 7 APIs and
syntax; the JUnit tests require Java 8 or later. Import/export round trips and
gameplay integration are separate checks, not supplied by this validator.
