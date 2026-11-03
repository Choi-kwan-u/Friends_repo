# Custom level session: integration contract

Related issue: #16. `engine.CustomLevelSession` connects the merged reader,
writer, and custom gameplay APIs. One session belongs to the menu/controller
for one fixed-size game window. It retains the last successfully loaded immutable
base definition in memory until that controller/session is discarded.

No menu entries, dialogs, screen codes, background workers, settings entries,
or automatic save files are added by this change.

## API for the Main Menu team

After Core initializes the window, create one session and retain it across menu
visits. Do not create a new session every time a button is pressed:

```java
CustomLevelSession customLevels = Core.createCustomLevelSession();
```

| Method | Responsibility |
| --- | --- |
| `load(Path source)` | Read JSON, validate file policy and every wave's gameplay compatibility, then replace the selection. Returns the accepted `LevelDefinition`. |
| `getLoadedLevel()` | Return an `Optional<LevelDefinition>` for the selected name/ID and whether Play/Save can be enabled. Empty before the first successful load. |
| `save(Path destination)` | Export the selected original definition through the existing writer. The caller must obtain overwrite confirmation first. |
| `play()` | Recheck gameplay compatibility using current difficulty, run through `Core.playCustomLevel`, and return `GameState` after completion/failure. The selection is retained. |

The default session uses `JsonLevelReader`, `JsonLevelWriter`, current settings,
and Core's custom-stage path. An explicit constructor also accepts a configured
reader/writer, difficulty supplier, and a custom `Player` for a separately hosted
window or tests. Configuring importer support does not add new enemy behavior.

The viewport is fixed when the session is created. Recreate the session for a
new/differently sized host window. Core's normal frame is not resizable.

## Requested UI integration

The level team supplies and tests the session/API. The Main Menu team owns:

1. Import, Play Custom Level, and Export entry points and their placement.
2. Source/destination file selection. Cancel means no session call and no state
   change; do not pass a null path as a cancellation signal.
3. Loaded-name/status display and disabled Play/Export actions before a load.
4. Overwrite confirmation before `save`; no confirmation is built into the API.
5. Error messages using the exceptions below, without claiming a failed load
   replaced the current selection. Update any displayed source path only after
   a successful `load`.
6. Routing into the custom screen on the game's controller thread and selecting
   the next menu/result screen when `play()` returns. Do not assign new screen
   codes without coordinating with Core's existing routing.
7. An integrated GUI check with the level team after wiring these actions.

The API is synchronous and not thread-safe. Use all session methods on the same
controller thread. A Swing file chooser can collect a path on the EDT, but
forward the chosen action to the controller rather than starting a second game
loop in the UI callback. Call `play()` only after the previous menu screen loop
has returned. Posting an action to the controller and returning from the menu
is different from recursively starting custom gameplay inside its update loop.

## Error handling and selection preservation

- `LevelFormatException`: malformed JSON, unsupported fields/values, or import
  policy failure. It is an `IOException` subtype and includes field context.
- Other `IOException`: file access or export failure.
- `IllegalArgumentException`: gameplay incompatibility (geometry, identifiers,
  timing, or current difficulty).
- `IllegalStateException`: Play/Save without a selection, or playing through Core
  before its window exists. Disable unavailable actions and respect startup order.
- `NullPointerException`: invalid API usage, not a user cancellation result.

`load` commits its selection only after both reader and gameplay checks finish.
Neither load nor save starts gameplay. Rejected loads, failed saves, and play
completion/failure leave the previous original definition selected. Play samples
current difficulty again; changes to settings cannot silently mutate the export.
The reader's policy, writer's atomic replacement, and Core's custom-run semantics
remain unchanged. A failure after gameplay itself begins is not a rollback of a
running game; the session only guarantees that its stored design is retained.

No official campaign progression or records are submitted by the default player.
A custom `Player` must preserve that boundary. Do not pass the returned custom
`GameState` into the official campaign advancement/high-score path.

## Example controller actions

These snippets run after the menu returns an action and a chosen path to the
controller. The UI owns cancellation and rendering the result:

```java
try {
    LevelDefinition accepted = customLevels.load(selectedSource);
    // Show accepted.getName(); enable Play and Export.
} catch (LevelFormatException invalidFile) {
    // Show invalidFile.getMessage(); keep previous selection/status.
} catch (IOException fileError) {
    // Show fileError.getMessage(); keep previous selection/status.
} catch (IllegalArgumentException incompatible) {
    // Show incompatible.getMessage(); keep previous selection/status.
}

// After choosing the destination and confirming any overwrite:
customLevels.save(selectedDestination);

// On the controller thread, after the old screen loop has returned:
GameState customResult = customLevels.play();
// Show the custom result or return to the menu. Do not advance official campaign.
```

## Checks and demo

`test/engine/CustomLevelSessionTest.java` contains ten session checks, including
empty state, successful replacement, malformed/missing files, later-wave gameplay
failure, expanded importer registry versus runtime support, changed settings,
failed saves/player execution, and original-data preservation.

`test/engine/CustomLevelSessionIntegrationTest.java` uses the real repository
sample, reader, writer, Core custom-run path, and GameScreen wave completion.
It checks both sample formations, completes the two waves, verifies unchanged
official unlocks, exports after playing, and reimports/exports without changing
base data. To avoid GUI input/rendering, the test defeats enemies and invokes the
same completion method used by the update loop. It is not a full visual playthrough.

Run these classes with JUnit Jupiter 5. Both `test/` and the repository root must
be on the runtime classpath for fixtures/sample, and `res/` for game resources.
Production sources, the new tests, and the file demo compile with Java 8
compatibility. The 72 relevant JUnit tests and three existing standalone model,
wave-list, and Infinite Mode control checks passed. Existing source warnings
are unrelated to this change. Audio playback is unavailable in the headless
check environment; a real GUI pass remains with UI integration.

For an optional visual check using actual JSON, run `manual.CustomLevelFileDemo`
from `test/manual/CustomLevelFileDemo.java`. With no arguments it reads
`examples/levels/campaign-stage-01.json` relative to the working directory.
Optional arguments are `source.json` and a new `destination.json`. It plays at
normal difficulty using existing configured controls. Export happens only when
a destination argument is supplied; after play it exports the original and
reimports it. The demo refuses an already-existing destination instead of asking
for overwrite confirmation. It does not create a production menu entry.

From the repository root on macOS/Linux:

```sh
build_dir=$(mktemp -d)
javac --release 8 -encoding UTF-8 -d "$build_dir" $(find src -name '*.java') test/manual/CustomLevelFileDemo.java
java -cp "$build_dir:res" manual.CustomLevelFileDemo
rm -rf "$build_dir"
```

Use `;` as the classpath separator on Windows. In IntelliJ, mark `test` as a Test
Sources Root and keep `res` on the runtime classpath, then run the demo's `main`.
The executable demo was compiled; its visual playthrough has not been performed
by the automated checks.

## Suggested GitHub handoff comment

The following can accompany the session PR; replace the PR placeholder after it
exists. This is a request for menu integration, not a claim that the complete UI
flow has already shipped:

> The custom-level session API is ready in [session PR]. It connects JSON import,
> gameplay compatibility checks, retained original definitions, export, and custom
> play. The API and runnable file demo are documented in
> `docs/Custom_Level_Session.md`.
>
> Could the Main Menu team take the Import / Play Custom Level / Export UI,
> file-selection dialogs, overwrite confirmation, error display, and screen
> routing? Please confirm the owner and integration target here. The controller
> should retain one `CustomLevelSession` and call `play()` on the game thread after
> the previous menu loop returns. I can help verify the integrated flow once the
> UI is connected.
