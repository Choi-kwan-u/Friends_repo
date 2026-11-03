# Octopus

## Team Introduction

We are **Octopus**, responsible for Part 6: Level Design System in the Space Invaders IC-PBL project.

### Members

| Student ID | Name | GitHub |
| --- | --- | --- |
| 2024098695 | Donghyun Kim | [331leo](https://github.com/331leo) |
| 2022039807 | Donghyeok Kang | [hye6k](https://github.com/hye6k) |
| 2023096417 | Doyu Lee | [ddy105](https://github.com/ddy105) |
| 2023088277 | Sehwan Cheon | [iamsehwan](https://github.com/iamsehwan) |
| 2024042333 | Hanheum Lee | [snowinsummer1](https://github.com/snowinsummer1) |
| 2025039570 | Seongmin Lee | [LetsCubeSpin](https://github.com/LetsCubeSpin) |
| 2022074220 | Seongmin Lee | [coldfarmer10](https://github.com/coldfarmer10) |
| 2023092542 | KyungJun Park | [rudwnssla123-ux](https://github.com/rudwnssla123-ux) |
| 2022055250 | Hyunjin Hwang | [taeyanggye88-sys](https://github.com/taeyanggye88-sys) |

## Team Requirements

Our team will design and implement a Level Design System for Campaign Mode and Endless Mode. The system will manage level configurations, enemy waves, and campaign stage progression, including stage-clear and stage-failure conditions. Each wave will support different enemy types, counts, formations, and health values, and levels will be importable and exportable as JSON files.

We will implement difficulty adjustment based on the player's health and design playable campaign stages. We will also coordinate with the Records & Achievements System teams to unlock achievements when players clear a campaign stage for the first time.

## Detailed Requirements

- Support two game modes: Campaign Mode and Endless Mode.
  - Create varied levels with different enemy types, counts, formations, and health values for each wave.
  - Unlock achievements when players clear a stage for the first time in Campaign Mode.
- In Campaign Mode, clear the current stage and advance to the next stage when all enemies have been defeated and the player's ship is still alive. If the player's health reaches zero, mark the stage as failed and do not advance to the next stage.
- Support importing and exporting levels as JSON files.
-  Automatically decrease difficulty by reducing the projectile speed when the player's health is one and increase it when the player's health is two or higher.
- Design and create playable campaign stages.

## Dependencies on Other Teams

The following dependencies are proposed for coordination with the teams responsible for each system.

| Part | Details |
| --- | --- |
| **Part 3: Records & Achievements System** | Save gameplay progress and records, track first-time campaign stage clears, and unlock achievements when the player clears a campaign stage for the first time or reaches a predefined gameplay condition or milestone. |
| **Part 7: Main Menu** | Provide a UI that lets players access the levels appropriate to each game mode, and include controls that integrate with the Level Design System's level import and export functionality. |
| **Part 8: Gameplay HUD** | Adapt the HUD layout and displayed information to Endless Mode and Campaign Mode so that each mode presents the relevant gameplay and level progression information.|