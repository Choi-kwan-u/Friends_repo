# Records & Achievements: Development and Verification

The local implementation covers team requirements 3.1–3.7. Its initial integration baseline combined course main `f476f5b` (2026-10-15) with team main `2d5ce71`, preserving Campaign mode, multiple waves, items, Infinite Mode, Settings, BGM, and audio caching. The formal acceptance criteria remain defined in [`teams/Chinese_can_fly.md`](../teams/Chinese_can_fly.md).

## Usage

- On the title screen, use Up/Down to select **Records & Achievements**, then press Space to open it. Records uses screen code 9, preserving the Infinite Mode shortcut I and screen code 8.
- Use Left/Right to switch between **Run Records** and **Achievements**. Press Escape or Space to return to the menu.
- When an achievement unlocks during gameplay, a notification appears at the top of the HUD for approximately three seconds. The results screen lists all achievements unlocked during that run.
- The profile is stored at `.space-invaders/records-achievements.properties` under the user's home directory. Run records and achievement unlock times are saved together; personal bests are recalculated from the complete record history.

## Local Data Protection

The profile uses a versioned UTF-8 Properties file. Writes run on a separate thread: the system writes a temporary file, reads it back for validation, preserves the previous valid backup, and replaces the primary profile. When loading a corrupt profile, it preserves a copy of the corrupt data and attempts to restore a backup. If neither the primary profile nor its backup can be read successfully, the program reports the problem and disables automatic writes so the original data is not overwritten with an empty profile.

Abandoning a run does not create a completed-run record; achievements already unlocked are queued for saving immediately. Completed runs are saved asynchronously without blocking the results screen. Exiting through the menu, closing the window, or pressing Alt+F4 waits up to ten seconds for the background save queue. Timeouts or failures are reported. Forced process termination or power loss can still lose progress that has not yet been written.

After backup recovery, the page and log report the recovery and warn that recent progress may have been lost. Backup updates also use a validated temporary file before replacement. If the backup cannot be updated, the current write is aborted and the existing valid primary profile is preserved.

## Gameplay Integration and Shared Interfaces

Campaign mode covers normal and special enemies defeated by bullets, Orbital Annihilator beams, or Chameleon Hull contact. Infinite Mode covers Zigzag enemies defeated by bullets. All of these paths connect to requirement 3.5 through `EnemyDefeatedEvent` and the shared `GameplayEventPublisher`. Each enemy retains a stable event ID, and the service ignores duplicate events. Shield absorption, escaped enemies, and player collisions in Infinite Mode do not count as enemy defeats. The current main branch has no Boss implementation; future valid defeat paths must also use this interface.

The Records page can be opened from the title menu and returns to it, preserving Settings navigation and the Infinite Mode shortcut. Both modes display HUD unlock notifications and a summary of achievements unlocked during the run. Campaign victory titles, Infinite Mode replay navigation, existing item rendering, and BGM transitions are preserved. The achievement proposed by Octopus for completing a Campaign stage for the first time is outside the five achievements registered for this project, so it is not added to the current achievement set.

## Verification

Run the following commands from the game repository root:

```powershell
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\build.ps1
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\test-records.ps1
```

The records tests cover field validation, active-time accounting, duplicate completion handling, boundary conditions for all five achievements, ranking order and the ten-entry limit, recovery after restart, backup-recovery warnings, preservation of original files when recovery is unavailable, protection of the valid primary profile when backup writes fail, and event handling after a run has closed.

Verification on 2026-10-15: the build succeeded with 60 Java source files; 104 records checks, 35 gameplay integration checks, and the existing course wave-list checks passed. The added checks call the actual collision handlers and cover normal and special enemies across the three defeat paths, repeated calls, shield absorption, Infinite Mode defeats/escapes/collisions, exclusion of countdowns and ending animations, abandonment, the 60-second achievement trigger, natural-death records, the I shortcut, and replay in both modes.

The tests also cover recovery of 80 queued records after a separate process exits, achievement-threshold rechecks on the final frame, and profile protection. Offscreen renders produced by the actual drawing code were inspected for rankings, achievements, failure results, Infinite Mode results, and a Campaign victory summary with all five achievements. Outputs are stored in the ignored `out/` directory. These checks do not establish that every stage has been manually played through, and the gameplay difficulty of the 20-defeat and 60-second thresholds has not been evaluated.

## Compatibility Boundaries and Team Workflow

The implementation preserves Campaign victory/failure detection, the multi-wave constructor, difficulty settings, configured controls, audio caching, BGM, items, player assets, and Settings. A complete Campaign run and an Infinite Mode run ending in natural death share the same record history and rankings. Pressing Escape to abandon Infinite Mode preserves the upstream results screen and already unlocked achievements, but does not create a completed-run record. Both modes exclude startup waiting and ending animations from active play duration. The current course main branch has no gameplay pause; future pause support must call `RunSession.stopActiveSegment` and `RunSession.startActiveSegment`.

Main-branch synchronization was handled in a separate internal team PR. The seven requirement PRs were developed as a dependency chain from 3.1 through 3.7; later PRs included prerequisite commits until the earlier PRs were merged. All internal PRs targeted `clover0409/Invaders-SDP-24592:main`, using the course repository as a read-only upstream source for synchronization.
