# Team [friends]

## Team Introduction

We are Team [friends].  
Our goal is to develop and improve the Gameplay HUD system for the Space Invaders project so that players can easily understand important game information during gameplay.

## Members and Roles

| Name | Role | GitHub |
|---|---|---|
| Kim Doyoon | Team Leader | [kdymong064](https://github.com/kdymong064-design) |
| Choi Kwanwoo | Team Coordinator | [GitHub](https://github.com/choi-kwan-u) |
| Kim Minhyeong | Gameplay HUD Developer | [love4kmh1023](https://github.com/love4kmh1023) |
| Kim Jiwoo | Gameplay HUD Developer | [konjiwoo](https://github.com/konjiwoo) |
| Lee Chaehyeon | Gameplay HUD Developer | [chaechae44](https://github.com/chaechae44) |
| Son Donghyeon | Gameplay HUD Developer | [vepormilk](https://github.com/vepormilk) |
| Lee Seungwoo | Git / Integration | [zis1089-sudo](https://github.com/zis1089-sudo) |
| Eom Jeongin | QA / Tester | [jungini1123](https://github.com/jungini1123) |
| Lee Yunhwan | Git / Documentation | [YH1031](https://github.com/YH1031) |

## Team Requirements

### Gameplay HUD

Our team is responsible for implementing the Gameplay HUD system.

The HUD should provide players with important game information clearly and update the displayed information during gameplay.

## Detailed Requirements

1. **Score Display**
   - Maintain the existing score display.
   - Ensure that the displayed score reflects changes to the player's score during gameplay.
   - Improve the readability or layout of the score display when necessary.

2. **Lives Display**
   - Maintain the existing lives information displayed during gameplay.
   - Ensure that the HUD correctly reflects changes in the player's remaining lives.
   - Avoid redundant or unclear presentation of lives information.

3. **Level / Stage Display**
   - Display the current level or stage when the required information is available.
   - Update the displayed level or stage when game progression changes.

4. **Currency Display**
   - Display the player's current currency balance when the Currency System provides the required information.
   - Update the displayed balance when the currency value changes.

5. **Game Status Display**
   - Display temporary status information related to active gameplay, such as level transition messages.
   - The HUD will only display game-state information provided by existing game systems and will not implement or modify the underlying game-state logic.
   - Full-screen states such as the existing Game Over screen are outside the scope of the Gameplay HUD unless additional integration is required.

6. **Clear and Non-Intrusive HUD Layout**
   - Arrange HUD elements so that important information is easy to read.
   - HUD elements should not cover or interfere with important gameplay areas.

7. **Reuse Existing Game Data**
   - Use data already managed by the game or other systems instead of duplicating gameplay logic inside the HUD.
   - Keep HUD responsibilities focused on displaying gameplay information.

## Dependencies on Other Teams

1. **Level Design System**
   - The Gameplay HUD may display the current level or stage.
   - The Level Design System is responsible for providing or maintaining the level/stage information used by the HUD.
   - The exact integration method will be coordinated with the Level Design System team.

2. **Currency System**
   - The Gameplay HUD may display the player's current currency balance.
   - The Currency System is responsible for providing the currency value used by the HUD.
   - The exact data access and update method will be coordinated with the Currency System team.
