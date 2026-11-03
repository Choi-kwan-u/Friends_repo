# Level JSON Format — Draft v1

Status: draft contract. A shared model/interface prototype is available in
`src/level/`; JSON export is implemented by `JsonLevelWriter`, and custom gameplay
handoff is available through `GameScreen.forCustomLevel` / `Core.playCustomLevel`.
See `Level_JSON_Export.md` and `Custom_Level_Gameplay.md` for their behavior and
checks. JSON import and menu wiring remain separate work.
See `Level_Model_API.md` for the prototype API and remaining decisions.

## Review questions

Please review these decisions before treating this draft as an implementation
contract:

1. Does a rectangular cell grid with mixed enemy types and explicit empty cells
   cover the planned formations, or are arbitrary coordinates also needed?
2. This implementation proposes mode-independent definitions and removes
   `mode` from the model/sample. Review this choice with the mode owners; it is
   not yet a finalized cross-team decision. Is classic mode retained?
3. Review proposal: share `LevelDefinition`, `WaveDefinition`, and `EnemyCell`
   through `LevelReader` / `LevelWriter` interfaces. Confirm their package,
   signatures, implementation owners, and connection to the campaign controller.
4. What limits fit the planned stages and gameplay geometry? Suggested limits
   below are review proposals, not agreed validation rules.
5. Should unknown fields be rejected? Agree on compatibility behavior together
   with version handling.

Required fields, formation structure, and progression rules below describe the
proposed format. Current code behavior is identified separately in the mapping
section. Endless rules and achievement eligibility remain separate decisions.

## Purpose and scope

Define a shared file format for importing and exporting level definitions. The
first deliverable is one campaign stage containing an ordered list of waves.
Import and export must use the same contract before their implementations begin.

One UTF-8 JSON file represents one stage definition. The gameplay caller
selects the mode; this proposed format does not contain a `mode` field. The file contains design data, not a
saved play session. Score, remaining lives, current wave, achievement status,
and player identity do not belong in this format.

The example is `examples/levels/campaign-stage-01.json` at the repository root.
Its identifier and name are sample values, not an official level registration.

## Modes and progression

| Mode | Proposed meaning | v1 draft scope |
| --- | --- | --- |
| `campaign` | Play the listed waves in order; clear the stage after the final wave while the player has remaining lives. | Included; at least one wave. |
| `classic` | Represent the existing one-formation-per-stage behavior. | Optional mode; if retained, exactly one wave uses the same structure. |
| `endless` | Continue selecting or generating waves until the run ends. | Deferred pending agreement on repetition, selection, and difficulty rules. |

The model contains an ordered list of waves without requiring campaign/classic
membership. Campaign progression uses the list in order; classic mode can
choose a single-wave definition if retained. Endless mode may reuse wave data,
but its continuous spawning/selection policy is not implemented by this format.
Do not claim that the current endless-mode implementation already consumes it.

This removes the previous draft's required `mode` field and moves the
single-wave classic rule to gameplay. Coordinates are also added explicitly.
These changes are proposed together in the shared-model PR. No legacy JSON
reader has been implemented, and no backward-compatible loading is claimed.
Both reader and writer should implement the same reviewed v1 contract; a legacy
`mode` field is unknown under this proposal rather than silently ignored.

Array order determines wave order. Clearing all enemies in a wave advances to
the next wave; it must not clear a multi-wave stage early. Zero remaining player
lives fails the stage. Stage order and selection belong to the campaign manager
or a separate campaign index, not file names or the stage ID.

These are proposed integration rules, not behavior supplied by the JSON reader.
Issue #16 assigns the loaded-data handoff to hye6k and proposes multi-wave
progression ownership for rudwnssla123-ux. Confirm the receiving GameScreen
interface in PR review. Progression owners decide lives, bullets, countdowns,
and temporary effects across transitions.

## Fields

All fields below are required. No implicit defaults are applied.

| Path | Type | Meaning and proposed validation |
| --- | --- | --- |
| `schemaVersion` | Integer | Exactly `1`; reject unsupported versions. |
| `id` | String | Stable stage ID matching `[a-z0-9]+(?:-[a-z0-9]+)*`. Not proof of official content. |
| `name` | String | Nonblank display name. |
| `waves` | Array | At least one wave, preserved in array order; gameplay decides how to consume the list. |
| `waves[].id` | String | Same syntax as the stage ID; unique within this stage. |
| `waves[].formation` | Object | One rectangular grid and its explicit starting origin; cells may be empty. |
| `waves[].formation.initialX` | Integer | Proposed required nonnegative starting X coordinate in pixels; matches the stage placement setting from PR #15. |
| `waves[].formation.initialY` | Integer | Proposed required nonnegative starting Y coordinate in pixels; gameplay additionally checks screen fit and movement alignment. |
| `waves[].formation.grid` | Array of arrays | At least one row and column, with equal row lengths; at least one occupied cell per wave. |
| `waves[].formation.grid[][]` | Object or null | An enemy definition, or `null` for an empty cell. |
| `waves[].formation.grid[][].enemyType` | String | Required on occupied cells: proposed logical enemy identifier. The sample uses provisional `A` / `B` / `C`; final identifiers and supported values require cross-team agreement. |
| `waves[].formation.grid[][].health` | Integer | Required on occupied cells: positive initial hit points for that enemy. The baseline sample uses `1`. |
| `waves[].movementBaseFrames` | Integer | Nonnegative; maps to the existing `GameSettings.baseSpeed` parameter. See movement semantics below. |
| `waves[].shootingIntervalMs` | Integer | Positive nominal interval between formation shots in milliseconds. |

### Suggested limits — pending review

These are initial suggestions only. They are not requirements of the field
table and must not silently become importer validation rules before agreement.

| Field | Suggested limit | Reason to review |
| --- | --- | --- |
| Stage/wave ID | At most 64 characters | Convenience limit without a confirmed requirement. |
| Display name | At most 100 characters | Confirm against the eventual UI. |
| Waves per stage | At most 10 | Reviewer proposal replacing 100; confirm against planned campaign content. |
| Formation | At most 8 columns and 7 rows | Reviewer proposal matching the current largest stage; the earlier 10 × 8 proposal leaves little movement room. Confirm with gameplay owners. |
| Enemy health | At most 10 | Reviewer proposal replacing 100 pending an agreed balance and enemy-health design. |
| `movementBaseFrames` | At most 600 | Confirm intended movement timing and Java numeric safety. |
| `shootingIntervalMs` | 100–60000 | Confirm intended firing rates and cooldown numeric safety. |

Positive/nonnegative values alone do not establish that a definition is playable
or safe to allocate. Before implementation, agree on final resource limits,
numeric bounds, and formation-fit validation with the gameplay owners.

Rows are ordered top to bottom and cells within each row left to right.
Coordinates are zero-based: `grid[row][column]`. An occupied cell is an object
with both required enemy fields; `null` is the only representation of an empty
cell. An empty object, missing array entry, or enemy with health zero is invalid.

Grid height is `grid.length`; width is the common row length. Total enemies are
the number of non-null cells, not width times height. Do not also store width,
height, `enemyCount`, or `waveCount` in the file.

Entirely empty rows or columns are allowed and preserve placement. An entirely
empty wave is rejected to avoid an ambiguous immediate-clear wave. Import and
export must preserve null cells and row/column positions without compacting or
trimming the grid. Ragged rows are rejected rather than silently padded.

For example, this row has two enemies separated by an empty cell:

```json
[
  { "enemyType": "A", "health": 1 },
  null,
  { "enemyType": "C", "health": 1 }
]
```

The grid expresses mixed and sparse formations at the existing regular spacing.
Arbitrary pixel coordinates and per-cell spacing are outside this proposal.
The special bonus ship remains governed by gameplay code.

## Enemy identifiers — cross-team agreement required

`enemyType` is a proposed JSON field, not an existing shared project identifier.
The sample labels `A`, `B`, and `C` refer to the existing `EnemyShipA1/A2`,
`EnemyShipB1/B2`, and `EnemyShipC1/C2` sprite families; the suffixes identify
animation frames. This is an illustrative mapping, not a final enemy registry.

The Player & Enemy Ship Variety team (KimchiBaguette) explicitly lists level
integration for spawning new enemy types and mini-bosses in
`teams/KimchiBaguette.md`. Before merging the contract, the wave developers and
that team must agree on logical identifiers, supported enemy definitions, and
the spawning interface. Do not constrain their future implementation to A/B/C
or assume a sprite name alone defines an enemy's behavior. Also agree on whether
per-cell health overrides are supported by their enemy model.

Unknown enemy identifiers must be validated against the agreed registry when
implemented. The sample's provisional values are not that registry.

## Proposed shared Java contract — pending team discussion

| Name | Proposed responsibility |
| --- | --- |
| `LevelDefinition` | Store stage identity, display name, and ordered waves; no mode or trusted provenance. |
| `WaveDefinition` | Store wave identity, cell grid, and movement/shooting parameters. |
| `EnemyCell` | Store one occupied cell's logical enemy identifier and health; a null grid entry preserves an empty cell. |
| `LevelReader` | Read and validate a file into the agreed level definition. |
| `LevelWriter` | Write the agreed level definition while preserving wave order and grid positions. |

A reviewable prototype now implements these models and interfaces in `level`.
See `Level_Model_API.md` for proposed signatures and validation boundaries.
The concrete writer is `JsonLevelWriter`; the reader and interaction with
`campaign.CampaignStageData` are still pending. Confirm the contract through
issue #16 and PR review before implementing the two sides independently.

## Existing code mapping and required integration

| JSON data | Current code connection |
| --- | --- |
| `formation.grid[0].length` | Grid width; corresponds to `GameSettings.formationWidth` for a full grid. |
| `formation.grid.length` | Grid height; corresponds to `GameSettings.formationHeight` for a full grid. |
| `formation.initialX` / `initialY` | Starting coordinates added to `GameSettings` by PR #15. |
| `movementBaseFrames` | `GameSettings.baseSpeed` |
| `shootingIntervalMs` | `GameSettings.shootingFrecuency` (existing spelling) |
| Cell enemy types and null cells | Custom formations use the original grid and current A/B/C runtime adapter, preserving null positions. |
| Cell health | Custom enemies accept positive health; normal bullets damage once and award kills only on death. Legacy enemies default to one hit. |
| Multiple waves | Custom definitions use #21 progression; only the final wave clears the stage. |

Dimensions alone cannot represent a sparse grid through the current
`GameSettings` object. The shared wave data must also reach formation generation.
That code must preserve grid coordinates, select the lowest occupied enemy in
nonempty columns for shooting, handle empty rows/columns, and base remaining-enemy
proportions on the actual initial enemy count rather than total grid capacity.
The custom gameplay path now implements these behaviors; see
`Custom_Level_Gameplay.md` for runtime compatibility checks and remaining UI work.

`movementBaseFrames` is not pixels per second or a fixed movement interval.
The current formation calculates a threshold approximately as
`floor(remainingProportion² × movementBaseFrames) + 10` frames between movement
steps. Lower values move faster. Zero is valid because the code adds a minimum
threshold of 10 frames. This name preserves the existing parameter's behavior
without exposing the misleading name `baseSpeed` in the file.

`shootingIntervalMs` is the nominal formation interval, not each enemy's personal
rate. Existing code adds a variation of 20 percent. The format does not override
the game's movement step size, bullet speed, or shooting variation.

The sample's first wave reproduces the current first stage's 5-column,
4-row formation with row types **C, B, B, A** and its difficulty values.
`EnemyShipFormation` selects C when the row ratio is below 0.2 and B below
0.6; row index 2 of 4 has ratio 0.5, so it is B, not A. Both sample waves use health `1`, matching the current
one-hit enemy behavior. The second wave demonstrates mixed enemy types, empty
cells, an entirely empty column, and an entirely empty row. The custom gameplay
handoff supports these layouts and multi-wave progression. Loading this JSON
through a menu still requires the separate importer and UI wiring.

An extension could set a cell to `{ "enemyType": "C", "health": 2 }`. This
means two hit points for that enemy and requires health/damage handling;
it is not included in the baseline sample but is supported by custom gameplay.

## Import and export contract

- Use UTF-8 and standard JSON: no comments or trailing commas.
- Reject missing required fields, wrong types, unknown fields, duplicate object
  keys, unsupported versions/types, out-of-range values, duplicate
  wave IDs, ragged grids, and entirely empty waves. Integer fields do not accept
  strings or fractional values.
- Validate the entire file before applying any data. On failure, keep the
  previously loaded level unchanged and report the failing field or parse error.
- Keep stage and wave IDs, array order, and all null cell positions when
  exporting. Use two-space indentation and a final newline for readable diffs.
  Object key order is not semantically significant.
- Importing an exported valid definition must preserve its supported values.
  Formatting of the input text does not need to be preserved.
- File selection, paths, overwrite prompts, and application to the game belong
  to the caller/UI contract and must be coordinated separately.

## Official levels and achievements

An imported file is editable by its author. Its `id` cannot establish
that it is an official level or that achievements should be awarded. This
format contains no `official` or `achievementsEnabled` field.

The campaign/gameplay and achievement owners must agree on trusted level
provenance and achievement eligibility outside the imported JSON. The JSON
import/export implementation does not unlock achievements. A separate custom
mode or menu is a product decision, not part of this format proposal.

## Integration follow-up

After resolving the review questions above:

1. Agree on how validated stage data reaches the gameplay controller and on
   stage/wave transition behavior.
2. Divide import/export work with 331leo using the agreed shared Java data classes
   and interfaces.
3. Track endless-mode policy and achievement eligibility as separate decisions;
   do not claim that v1 already supports them.

## Implementation acceptance checks

- Load the example and preserve both waves, their order, all occupied cells,
  and every null position (including empty rows and columns).
- Export and re-import a definition without losing its supported data.
- Reject malformed JSON, unsupported versions, missing fields, invalid enemy
  types/health, duplicate wave IDs, ragged grids, entirely empty waves, and
  invalid grid dimensions without changing the active level.
- After gameplay integration, verify that wave one does not clear the stage,
  that wave two can complete it, and that zero lives prevents progression.

The implementation includes shared models, IO interfaces, JSON export, and
validated custom gameplay handoff. JSON import, menu wiring, and reader/writer
round trips remain separate work. See `Level_JSON_Export.md` and
`Custom_Level_Gameplay.md` for the checks performed and remaining limitations.
