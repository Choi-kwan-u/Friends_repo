# Infinite Mode configured controls

Related issue: #23, item 2 only.

Infinite Mode now reads left, right, and fire actions from `ControlsConfig`,
matching campaign `GameScreen`. The settings menu remains the owner of key
selection and persistence; no second binding store is introduced.

- Without saved mappings: Left arrow, Right arrow, and Space remain the defaults.
- With saved mappings: those configured keys control the ship.
- A/D are no longer unconditional movement aliases. They work when explicitly
  assigned, just as in campaign gameplay.
- Escape remains the fixed exit action. Input-delay, destruction, movement
  boundary, and shooting-cooldown behavior are unchanged.
- Startup countdown changes are outside this PR.

## Regression checks

From the repository root, in a fresh JVM:

```sh
build_dir=$(mktemp -d)
javac --release 8 -d "$build_dir" $(find src tests -name '*.java')
java -Djava.awt.headless=true -cp "$build_dir:res" screen.InfiniteGameScreenControlsTest
rm -rf "$build_dir"
```

The classpath separator above is for macOS/Linux; use `;` on Windows.

The standalone test installs an in-memory preferences factory before any game
settings are initialized. It does not overwrite the user's saved bindings.
It sends key events through the actual `InputManager` and checks default and
remapped movement/fire, disabled old keys, both horizontal boundaries,
destroyed-ship controls, and Escape exit.

Player controls are exercised separately from rendering, through the same
method called by the normal update loop. This is not a full visual playthrough.
For manual verification, remap the three actions in Settings, start Infinite
Mode, check the configured keys and Escape, then restore your preferred mapping.
