# Level JSON import

`JsonLevelReader` implements the import responsibility in #16 using the shared
`LevelReader` interface and immutable models. It reads a whole v1 definition and
returns it only after syntax, structure, and import-policy validation succeeds.

```java
LevelReader reader = new JsonLevelReader();
LevelDefinition candidate = reader.read(java.nio.file.Paths.get("custom-stage.json"));
```

The proposed limits are defaults, not a finalized enemy registry or gameplay
contract. Callers can supply their own immutable policy:

```java
LevelImportPolicy policy = LevelImportPolicy.builder()
        .enemyTypes(java.util.Arrays.asList("A", "B", "C", "boss"))
        .maxEnemyHealth(20)
        .maxWaves(12)
        .maxRows(9)
        .maxColumns(10)
        .build();
LevelReader reader = new JsonLevelReader(policy);
```

See [Level_Import_Validation.md](Level_Import_Validation.md) for every default,
the remaining overrides, and the boundary between shared-model invariants and
import-policy validation. No missing JSON field receives an implicit default.

## Accepted input and errors

- The source must be a regular file. A symbolic link to a regular file is
  accepted. Relative paths use the caller's working directory.
- Input must be UTF-8; malformed bytes and unpaired Unicode surrogates are
  rejected. One leading UTF-8 BOM is accepted for editor compatibility.
- The reader checks file size before reading and counts bytes while reading,
  so growth cannot bypass `maxFileBytes`. The default is 1 MiB, including a BOM
  and whitespace. JSON nesting has a separate fixed safety limit of 16
  containers; the v1 schema requires only seven.
- Required fields, their exact JSON types, and schema version 1 are checked.
  Unknown fields, duplicate decoded object keys, duplicate wave IDs, comments,
  trailing commas, and trailing content are rejected.
- Integer fields accept JSON numbers whose exact value is a signed 32-bit
  integer, including `120.0` and `1.2e2`. Strings, booleans, nulls, fractions,
  overflow, and numbers outside the parser's supported decimal range are rejected.
- Each grid must be nonempty, rectangular, and contain at least one enemy.
  `null` cells and entirely empty rows/columns keep their original positions.
  Wave order, identifiers, names, coordinates, health, and base timings are
  preserved without trimming, compaction, or difficulty conversion.

Malformed content and policy violations throw `LevelFormatException` (an
`IOException`) with a field path when available. Syntax errors also include a
line and column. Missing files, unreadable files, and other filesystem failures
remain ordinary `IOException` subclasses. Null arguments are programming errors
and throw `NullPointerException`.

The reader does not retain an active definition or write to its source file.
A caller can keep its previously loaded definition when a read fails:

```java
try {
    LevelDefinition candidate = reader.read(path);
    // The gameplay owner must check runtime compatibility before starting it.
    loadedDefinition = candidate;
} catch (LevelFormatException invalid) {
    // Report invalid.getMessage(); loadedDefinition is unchanged.
} catch (java.io.IOException failure) {
    // Report the file error; loadedDefinition is unchanged.
}
```

This implements file import only. File selection, menu wiring, gameplay handoff,
formation fit, wave progression, and official unlock state remain with their
respective owners. Successful import does not establish official provenance or
playability. The shared models and exporter are used without modification.

## Checks

Tests use the existing `test/level/` directory. From the repository root, with
the JUnit Platform Console Standalone jar available as `junit_jar`:

```sh
build_dir=$(mktemp -d)
javac --release 8 -encoding UTF-8 -cp "$junit_jar" -d "$build_dir" \
  src/level/*.java test/level/*.java
java -jar "$junit_jar" execute --class-path "$build_dir:test:." \
  --scan-class-path --disable-banner
```

Both `test/` and the repository root must be on the runtime classpath for the
fixtures and existing sample. `JsonLevelRoundTripTest` uses the exporter merged
in #27 directly. It checks model -> export -> import, repository sample ->
import -> export -> import, and the existing export fixture with explicit
policy overrides for its custom identifier and large numeric values.

Reader tests also cover invalid UTF-8/JSON, unknown/missing/wrongly typed fields,
duplicate keys and IDs, numeric and resource boundaries, sparse grids, and a
late validation failure leaving the caller's previous definition untouched.
These are file/model checks; the complete file-selection-to-gameplay flow is
outside this implementation and has not been tested here.

Production additions use Java 7 APIs and syntax. Verification uses JDK 23 with
`--release 8`, because that compiler no longer supports a Java 7 target. JUnit
tests require Java 8 or later; execution on a Java 7 runtime is not verified.
