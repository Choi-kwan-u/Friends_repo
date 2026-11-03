# Custom level gameplay handoff

Related issue: #16. Builds on the shared model (#19) and multi-wave progression
(#21). This branch does not depend on the export implementation or implement a
JSON importer.

## Entry point and ownership

After the importer and file-selection UI return a complete `LevelDefinition`,
the game controller can call:

```java
GameState result = Core.playCustomLevel(loadedDefinition);
```

Call this on the existing game/screen-running thread with the game window already
initialized, after the previous screen has returned. It blocks until the custom
stage finishes. The caller then chooses the next menu/result screen. Do not call
it from a Swing input callback while another screen loop is running.

`Core.playCustomLevel` validates all waves before switching the active screen.
It starts a fresh three-life stage with zero score and no bonus life, using the
selected difficulty. It runs only this custom stage and returns the resulting
`GameState`. It never enters Core's official campaign stage loop, updates campaign
unlocks, or submits the result to the official high-score screen. An editable
stage ID, even `campaign-stage-07`, cannot select the official progression path.

For a separately hosted game window, use the screen factory directly:

```java
GameScreen custom = GameScreen.forCustomLevel(
        new GameState(1, 0, 3, 0, 0), loadedDefinition,
        difficultyMultiplier, width, height, fps);
// Attach through the existing Frame after setting up its DrawManager.
```

The factory returns a screen only after whole-level compatibility validation.
It leaves initialization and the run loop to the host. The legacy single-wave
and `List<GameSettings>` campaign constructors continue to work.

Import/JSON validation is implemented by 331leo in #29/#30, including verified
reader/exporter round trips. `CustomLevelSession` now connects file IO, retained
selection, and this gameplay entry point; see `Custom_Level_Session.md` for the
menu team's API. File selection, menu routing, and error presentation remain UI
integration work. These APIs do not introduce a load button or complete #16.

## Runtime compatibility checks

`LevelGameplayAdapter.prepare` inspects every wave before screen creation:

- The full grid, including empty borders, must fit within the current screen's
  existing 20-pixel side and 80-pixel bottom margins. Grid spacing is 40 pixels;
  normal enemies are 24 by 16 pixels.
- Initial X must be at least 20. Initial Y must be at least 40 (below the HUD)
  and a multiple of the existing 20-pixel descent distance.
- The current adapter recognizes `A`, `B`, and `C`, mapped to the existing enemy
  sprite/behavior families. Unsupported identifiers are rejected with the wave
  and grid location. This is the current runtime capability, not a new universal
  cross-team identifier contract. Future enemy types require an adapter update.
- Positive cell health is supported without imposing the unagreed suggested
  health cap. Grid structure and positive-value checks also remain in the models.
- Difficulty must be finite and positive. Movement/shooting intervals use the
  current campaign multiplier and minima (1 frame / 250 ms). Unsafe integer
  ranges, including the formation's +10 threshold and variable-cooldown
  arithmetic, are rejected instead of overflowing.

`IllegalArgumentException` reports incompatibility; no partially accepted stage
is returned. The importer can accept a structurally valid definition that this
runtime cannot play. Treat that distinction as a user-facing loading error.

Original models are never mutated. Adjusted `GameSettings` carry timing and
geometry only; the original `WaveDefinition` also reaches formation creation,
so mixed cells, null gaps, and health cannot be lost in a dimension-only copy.
Always export the original definition, not runtime settings.

## Formation, health, and progression

Occupied cells spawn at `initialX + column * 40`, `initialY + row * 40`.
Empty cells, rows, and columns do not spawn enemies or compact neighboring cells.
Runtime bounds can shrink as enemies die, without changing their relative spacing
or modifying the source definition. Remaining-enemy speed uses the actual initial
occupied count, not rectangular grid capacity.

Only the lowest living enemy in each occupied column can shoot; death promotes
the next living enemy, and an empty formation cannot shoot. Empty-column cleanup
uses an iterator to avoid removing the wrong column or invalid shifting indexes.

Normal bullets consume one health point and are consumed after the first hit.
Score, kill count, and drops occur only on the fatal hit. Existing laser and
transformed-player contact effects remain instant kills. Repeated destruction is
ignored. Legacy enemies and the special bonus ship still default to one health;
Infinite Mode continues using its own spawning and collision implementation.

The existing GameScreen wave progression is reused. Clearing an intermediate wave
starts the next definition in order while retaining lives and stage state.
Only the final wave clears the stage. Zero lives takes precedence over an empty
formation. This change does not redefine per-wave countdowns, projectile resets,
or item-effect/drop reset policies; existing stage-wide behavior is retained.

Official campaign unlocks are excluded by the custom execution path. General
achievement/record eligibility still needs its owners' agreement before any
future event/persistence integration; this change adds no achievement events.

## Verification and manual demo

JUnit Jupiter checks live under `test/`:

- `entity.CustomFormationTest`: sparse coordinates/types/health, shooter promotion,
  inherited shooter cooldowns, bullet ownership, sparse HUD counts, explosion
  cleanup, actual-count speed, repeated destruction, and legacy layout.
- `engine.LevelGameplayAdapterTest`: difficulty preservation, wave order,
  unknown enemies, geometry/alignment, and numeric safety.
- `screen.CustomLevelGameScreenTest`: actual formation creation across ordered
  waves, final-wave completion, PlayerStatus failure precedence, bullet health/score
  logic, single-target bullet consumption, and the official boss gate/run timer.
- `engine.CustomLevelHandoffTest`: stub-reader handoff, official-looking IDs,
  unchanged campaign unlocks, and rejection before the active screen is changed.

These 18 tests and the existing Core difficulty test passed in headless mode.
Existing standalone model, wave-list, and Infinite Mode control checks also passed.
Production code and these tests compile with Java 8 compatibility. Gameplay tests
use muted audio in the headless environment; a visual playthrough is still manual.
Tests invoke actual collision/progression methods without a rendered frame; they
do not claim to exercise the entire rendering loop.

An optional visual demo uses two in-memory custom waves, requiring no importer:

```sh
build_dir=$(mktemp -d)
javac --release 8 -encoding UTF-8 -d "$build_dir" $(find src -name '*.java') test/manual/CustomLevelDemo.java
java -cp "$build_dir:res" manual.CustomLevelDemo
rm -rf "$build_dir"
```

Run from the repository root in a graphical desktop session. On Windows use `;`
as the runtime classpath separator. In an IDE, run `manual.CustomLevelDemo` with
`src` and the demo compiled and `res` present on the classpath. The demo uses normal
difficulty, existing configured controls, and a fresh session. Verify visible
gaps/mixed enemies, two-hit enemies, wave order, and the final completion. It prints
whether the stage cleared and whether the official unlock state stayed unchanged,
then closes its window without saving a result. It does not parse JSON or expose
a new production menu option.
