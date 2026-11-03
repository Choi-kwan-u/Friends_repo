# Shared level model and IO API

Related issue: #16. This PR proposes a common contract for import, export, and
loaded-data handoff. Cross-team identifiers and gameplay limits remain subject
to review. It does not close the whole issue.

## Implemented scope

The `level` package contains immutable design models and IO interfaces:

- `LevelDefinition`: schema version, stage ID/name, and ordered waves.
- `WaveDefinition`: cell grid, formation origin, base movement/firing timing.
- `EnemyCell`: logical enemy identifier and positive initial health.
- `LevelReader` / `LevelWriter`: file-based reader/writer interfaces.
- `LevelFormatException`: invalid file error, extending `IOException`.

`JsonLevelWriter` implements UTF-8 JSON export; see `Level_JSON_Export.md` for
replacement/error behavior and checks. Custom gameplay handoff uses these models
through `GameScreen.forCustomLevel` and `Core.playCustomLevel`; see
`Custom_Level_Gameplay.md`. JSON import and menu wiring remain follow-up work.

Input arrays/lists are copied; grid getters return copies and wave lists are
unmodifiable. Null cells, empty rows/columns, wave order, and coordinates remain
intact. Runtime difficulty adjustments must not overwrite exported base values.

## Deliberate contract changes for review

- Remove `mode` from the model and JSON. Gameplay chooses the mode, keeping the
  same data reusable without accepting or rejecting it by mode membership.
- Add required `formation.initialX` and `formation.initialY`, matching the
  stage-placement data from PR #15. Units are game pixels; the origin is the
  upper-left of the full grid, including empty rows/columns.
- Keep trusted official/custom provenance and player progression outside the
  editable file and model. Imported definitions cannot authorize unlocks.

The merged draft/sample previously included `mode` and omitted coordinates.
These changes are proposals, not a claim of finalized agreement. No existing
JSON reader is being migrated. Review this contract with 331leo before implementing
import/export; legacy `mode` is an unknown field under the proposed strict v1.

## Reader/writer contract

```java
LevelDefinition read(Path path) throws IOException;
void write(Path path, LevelDefinition level) throws IOException;
```

Both paths and the writer's definition must be non-null. `LevelReader` returns
one complete file-contract-valid definition without applying it to gameplay.
Malformed JSON, unsupported fields/versions, and invalid values raise
`LevelFormatException`; filesystem failures remain `IOException`. Wrap model
constructor errors with field context, such as `waves[1].formation.grid`.

`LevelWriter` preserves supported values, wave order, and null positions without
mutating the input. Serialize before replacing an existing file so a
serialization failure does not truncate it. The concrete writer PR must specify
replacement/error behavior and verify failure handling. File selection and
overwrite confirmation belong to the caller.

## Validation ownership

| Layer | Checks/responsibility |
| --- | --- |
| Model (this PR) | Version 1; ID syntax; nonblank name; nonblank enemy identifier without surrounding whitespace; positive health/firing intervals; nonnegative origin/base movement; nonempty rectangular grids with at least one enemy; non-null waves with unique IDs. |
| Reader (331leo) | JSON syntax, required fields, exact JSON types, unknown fields, duplicate keys, integer conversion without overflow; wrap structural errors; enforce the reviewed registry/resource limits before returning. |
| Loaded-data handoff (hye6k) | Validate actual gameplay compatibility before changing active state: supported enemy behavior/health, formation fit and movement alignment, and preservation of definition data through difficulty conversion. |
| Wave progression (rudwnssla123-ux proposal) | Consume ordered waves and finish the stage only after the last wave; decide transition state with the relevant owners. |

Constructor success alone does not establish playability. Registry checks,
file/grid size caps, health/timing upper bounds, screen dimensions, and Y
alignment must use the agreed policy; the prototype does not hard-code the
unagreed 8×7 / 10-wave / 10-health suggestions. A generic model must not become
an excuse to apply unvalidated imported files.

## Gameplay handoff proposal

```java
List<WaveDefinition> waves = loadedLevel.getWaves();
// The custom-level factory validates the entire definition, preserving these grids.
// GameScreen.forCustomLevel(state, loadedLevel, multiplier, width, height, fps);
```

The getter supplies the ordered list requested in issue #16. Each wave contains
its grid and coordinates, which a temporary `List<GameSettings>` cannot fully
represent. The custom adapter carries both runtime settings and original wave grids.
The existing coordinate reconstruction bug was separately fixed in #24.

Track official/custom provenance through the trusted loading path, separately
from `LevelDefinition`. Only official campaign clears should update the unlock
state from PR #17. Definitions do not store live score, lives, or unlocked stage.

## JSON mapping

| JSON path | Java accessor |
| --- | --- |
| `schemaVersion`, `id`, `name`, `waves` | Corresponding `LevelDefinition` getters |
| `waves[].formation.grid` | `WaveDefinition.getGrid()` |
| `waves[].formation.initialX` / `initialY` | `getInitialX()` / `getInitialY()` |
| `waves[].movementBaseFrames` | `getMovementBaseFrames()` |
| `waves[].shootingIntervalMs` | `getShootingIntervalMs()` |
| Occupied cell `enemyType` / `health` | `EnemyCell` getters |

## Construction example

```java
EnemyCell[][] grid = {
    { new EnemyCell("A", 1), null, new EnemyCell("C", 1) },
    { null, null, null }
};
WaveDefinition wave = new WaveDefinition("wave-01", grid, 20, 100, 60, 2000);
LevelDefinition level = new LevelDefinition(1, "stage-01", "Example",
        java.util.Collections.singletonList(wave));
LevelWriter writer = new JsonLevelWriter();
// writer.write(path, level); // Caller selects path and confirms overwrite.
```

## Reproducible model checks

From the repository root, with a JDK supporting `--release 8`:

```sh
build_dir=$(mktemp -d)
javac --release 8 -d "$build_dir" src/level/*.java tests/level/*.java
java -cp "$build_dir" level.LevelModelTest
rm -rf "$build_dir"
```

The checks cover input/output mutation isolation, multiple-wave order, mixed
cells/null positions, empty rows/columns, non-default origins, zero base movement,
invalid negative origins/timing, nonpositive firing intervals, unsupported
versions, null/duplicate waves, ragged/all-empty grids, and invalid health/IDs.

The sample was also checked by constructing models from its field values
(20 enemies in wave 1 and 8 in wave 2). This does not exercise a Java JSON reader.

These are model contract checks, not JSON IO round trips or a running-game test.
Export checks are documented in `Level_JSON_Export.md`. Import/export round trips
and gameplay application will be verified in later PRs.
