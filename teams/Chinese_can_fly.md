# Chinese can fly

**Project:** 3. Records & Achievements System

**Course:** CSE2024 · Section 24592 · 2026 Semester 2

**Git workflow:** [Team Git workflow](../docs/Git_Workflow.md)

## 1. Team Introduction

We are a seven-member team developing a records and achievements system for Space Invaders. Our goal is to help players review performance, track progress, and unlock achievements.

| Member | GitHub | Responsibility |
| --- | --- | --- |
| Chen Huiqing | [clover0409](https://github.com/clover0409) | Team coordination, cross-team communication, and PR reviews. |
| Wei Junjie | [arjen12138](https://github.com/arjen12138) | Game-result capture and records data model. |
| Tan Zhaokun | [wrxtzk](https://github.com/wrxtzk) | Local saving, loading, and data recovery. |
| Sun Chendi | [sunchendi](https://github.com/sunchendi) | Personal bests and top-ten rankings. |
| He Hanjun | [Godovo666](https://github.com/Godovo666) | Achievement conditions and unlock logic. |
| Xu Linhao | [woshi777](https://github.com/woshi777) | Records screen and achievement notifications. |
| Yang Tianshi | [MiooYoung](https://github.com/MiooYoung) | Gameplay event integration and regression testing. |

## 2. Team Requirements

Record completed games, maintain personal bests and rankings, save progress locally, and display achievements. The initial version supports one local single-player profile; online rankings, two-player records, and currency/item rewards are outside its scope.

## 3. Detailed Requirements

The following are sub-requirements of **Project 3: Records & Achievements System**.

| ID | Requirement |
| --- | --- |
| 3.1 | Save one record per completed run: unique run ID, score, enemies defeated, active play duration excluding pauses, and completion time. Ignore duplicate completion events. |
| 3.2 | Track the highest score, most enemies defeated in one run, and longest survival time. Update only when a value exceeds the previous best. |
| 3.3 | Show up to ten completed runs by descending score. Break ties by earlier completion time, then run ID. Show an empty state when no records exist. |
| 3.4 | Save records and unlocked achievements locally and restore them after restart. Preserve invalid save files for recovery, report load/write failures, and avoid overwriting valid data on failure. |
| 3.5 | Implement five achievements: complete a first run, defeat a first enemy, defeat 20 enemies in one run, survive 60 active seconds in one run, and beat a previous run’s high score. Unlock each once per profile. |
| 3.6 | Show achievement names, conditions, status, and unlock times. Queue new-unlock notifications without blocking gameplay, and list newly unlocked achievements in the game-over summary. |
| 3.7 | Build our records and achievements screen showing personal bests, rankings, and achievement status, with open/close hooks for integration. |

Achievement thresholds will be checked against game difficulty. Each feature owner will test their module; integration checks will cover persistence, ranking ties, duplicate events, achievement thresholds, and navigation.

## 4. Dependencies on Other Teams

These are specific integration requests needed by our sub-requirements. Our team remains responsible only for the Records & Achievements System.

| Our sub-requirement | Support needed from other teams |
| --- | --- |
| 3.7: Open the records screen | The team responsible for menu navigation connects a menu action to our screen’s open hook and handles return navigation. |
| 3.6: Display unlock notifications | The team responsible for the gameplay interface provides a display hook or reserved area; our system supplies achievement notification content. |
| 3.1 and 3.5: Count enemy defeats | The team modifying enemy behavior exposes consistent defeat events, allowing our system to count each defeat once and evaluate achievements. |
