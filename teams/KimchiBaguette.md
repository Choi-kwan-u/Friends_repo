# KimchiBaguette

## Team Introduction
Our team's goal is to diversify the player and enemy ship systems in Space Invaders. We aim to implement selectable player ships with unique stats, varied enemy movement and attack patterns, and a bonus system (such as extra lives and shields) as well as a penalty system (such as obstacles).

## Members
| Name | Role | GitHub |
| --- | --- | --- |
| Julie Znaor | Team leader & Enemy Ship&Pattern Developer | [Julie Znaor](https://github.com/JulieZnaor) |
| Felicie Pene | Weapon Developer & projectile developer 1| [Felicie Pene](https://github.com/feliciepene) |
| Lena Matejic | player ship Developer | [Lena Matejic](https://github.com/Lenamtj) |
| BRAMI Édouard | Enemy Movement Developer | [BRAMI Édouard](https://github.com/EdouardBrami) |
| Yongwon Kim | Collaborator & Player Status&Buff/Debuff 1 | [Yongwon Kim](https://github.com/YitWub) |
| Chaemin Oh | Boss System Developer 1| [Chaemin Oh](https://github.com/Littlecocoa) |
| Jiye Kwon | Player Status&Buff/Debuff 2| [Jiye Kwon](https://github.com/kwon-jiye) |
| Yoon-ah Ji | Boss System Developer 2 | [Yoon-ah Ji](https://github.com/jiyoonah) |
| Junah noh | Weapon Developer & projectile developer 2| [Junah noh](https://github.com/noh1216) |


## Team Requirements
We are developing the **Player & Enemy Ship Variety** system of the Space Invaders game. Our team is responsible for giving players a choice of ships with distinct stats, giving enemies varied movement and attack patterns (including bosses), and adding bonus and penalty elements that change the moment-to-moment gameplay.

## Detailed Requirements
The five requirements below are the scope our team commits to for the initial
implementation. The figures are the values fixed in our code, and each requirement
states the condition under which we treat it as complete. The project has test
classes under `tests/`, but they are plain `main` methods run by hand rather than
a JUnit suite on CI, and they cover calculations rather than whole systems. Every
completion condition is therefore written so that it can also be checked by
building and running `engine.Core`.

**R1. Selectable player ships with distinct stats.**
Three ships are selectable before the game starts: Standard (speed 2, 750 ms between
shots, 4 lives), Speedster (speed 4, 400 ms, 3 lives) and Heavy Tank (speed 1,
1000 ms, 5 lives, shield-capable).
*Complete when* the selection screen offers all three, the chosen ship carries its own
speed, fire interval and life count into the game screen, and only the shield-capable
ship can hold a shield.

**R2. Player projectile types and aimed enemy fire.**
Three projectile types on the player's side, one per selectable ship: Normal (one shot),
Rapid (two shots 100 ms apart) and Multi (three shots in one spreading volley). Enemies
keep a single kind of bullet. What varies on their side is who fires and where: the
formation holds one shooting cooldown that decides when a shot is taken, each bottom-row
enemy holds its own cooldown that decides which of them is eligible to take it, and the
shot is aimed at the player's position rather than straight down. Several attack methods
per enemy are out of scope.
*Complete when* each of the three projectile types is observable from the ship it belongs
to, several enemies in one formation take turns firing — the shooter is drawn at random
from those whose own cooldown is ready, so the same enemy may well fire twice in a row,
and does so every time only one is left in the bottom row — and an enemy bullet visibly
travels at an angle towards the player instead of vertically.

**R3. Enemy ships with distinct movement trajectories.**
At least two movement patterns in addition to the baseline formation sweep. The interval
between steps is deliberately fixed so that a path reads as a line rather than a series
of jumps; the pace is carried by the step length, which grows on the harder stage
settings and as enemies are defeated.
*Complete when* two formations of the same size visibly follow different paths, and a
formation visibly covers more ground per second on a later stage and as it is thinned
out, without its motion breaking into jumps.

**R4. Bosses with difficulty levels and varied projectile attacks.**
Two boss types (850 HP slow, 800 HP fast), each with three phases driven by remaining
HP and three attack patterns (single shot, multi shot, spread shot), plus one special
attack reserved for the final phase. Bosses appear on stages 3, 5 and 7.
*Complete when* a boss stage cannot be cleared while its boss is alive, the attack
pattern changes as the boss loses HP, and a defeated boss stops moving and attacking
and does not respawn.

**R5. Bonus and penalty elements.**
Bonus element: an extra life, granted on entry to every third stage while the chosen
ship is still below its own maximum. A timed shield of our own is out of scope as a
bonus element — the only shield a player can pick up in play is the Energy Shield
item, which belongs to another team. The 3-second shield on PlayerStatus is still in
the code, but only as a debug-only facility: it is offered to shield-capable ships
alone, the F2 debug key is its only source, and the one way it touches play is that it
blocks the obstacle slow while it is up. Either removing it or giving it an in-game
source is left to a follow-up issue.
Penalty element: an obstacle that falls through the play area and halves the player's
movement speed for 2 seconds on contact. It blocks nothing — it deals no damage, costs
no life and leaves firing untouched.
*Complete when* the life count rises by one on entry to a stage that grants it and never
passes the chosen ship's maximum, and a touched obstacle visibly slows the ship sideways
for about two seconds without costing a life or interrupting its fire.

## Dependencies on Other Teams
The items below are the support our own requirements depend on, and that we therefore
request from the teams named.

* **Team 3 (Level Design System)**: We ask the Level Design team to let each level's
  configuration declare which enemy ship types appear in a wave and which stages spawn
  a boss, so that enemy types and bosses are placed by level data instead of being
  hardcoded in the game screen. Without this, R2, R3 and R4 cannot be arranged into a
  difficulty curve across the levels, and the stages 3, 5 and 7 named in R4 stay fixed
  in our own code.
* **Team 8 (Gameplay HUD)**: We ask the Gameplay HUD team for HUD elements that show
  per-ship state: a remaining health and shield gauge, the icon of the selected ship,
  and a special ability gauge. Without this, the stat differences in R1 and the shield
  duration in R5 are not observable on screen, so neither completion condition can be
  checked in play.
* **Team 5 (Visual Effect System)**: We ask the Visual Effect team for per-ship and
  per-boss visual effects: engine thrust particles bound to the selected ship, and
  boss-specific hit and explosion animations. Without this, the three ships in R1 and
  the two boss types in R4 are visually indistinguishable, and the phase changes in R4
  have no visible feedback.

## Git Workflow
The team's Git workflow — branch strategy, commit rules, pull request and review
rules, merge strategy and the overall process diagram — is documented separately
in [`docs/KimchiBaguette_Git_Workflow.md`](../docs/KimchiBaguette_Git_Workflow.md).
