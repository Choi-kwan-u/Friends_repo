# Frenchies

## Team Registration and Project Requirements

### Team Registration

| No. | Name | Members | Requirements |
|-----|------|---------|--------------|
| 1 | [Frenchies](https://github.com/greg-hue/Invaders-SDP-24592.git) | [Grégoire NOGIER](https://github.com/greg-hue), [Eloi GAILLARD](https://github.com/eloi-kgg), [Samuel KUTCHUKIAN](https://github.com/SamZTU), [Alexis BIHOUR](https://github.com/Alex0xB), [Arthur NEVANT](https://github.com/Arthurnev), [Loane GOSSELIN](https://github.com/Loanegosselin), [Lyanh RENKIN](https://github.com/renlahh), [Evangeline VUCHOT](https://github.com/EvangelineVuchot), [Chiara BICHON](https://github.com/Lawsiel)| [Frenchies.md](teams/Frenchies.md) |

### Team Introduction

Our ambition is to integrate all core game functionalities into a unified and functional main menu.

The vision is to provide players with a seamless entry point to every feature of Space Invaders SDP, ensuring accessibility, clarity, and immersion from the very first screen.

**Team Leader:** Grégoire NOGIER

**Developers:** Eloi GAILLARD, Samuel KUTCHUKIAN, Alexis BIHOUR, Arthur NEVANT, Loane GOSSELIN, Lyanh RENKIN, Evangeline VUCHOT, Chiara BICHON, Grégoire NOGIER

## Team Requirement

We are developing the **Main Menu** of the Space Invaders game.

This menu is the central hub that connects players to gameplay, settings, achievements, and customization options.

| ID | Requirement |
|---|---|
| **1.1** | **Implement Main Menu navigation:** Display all available menu options (Play, Settings, Shop, Hangar, Achievements, etc.) and allow the player to move between them using keyboard or controller input. Highlight the currently selected option and prevent invalid selections. |
| **1.2** | **Implement menu audio feedback:** Play a navigation sound when changing the selected option and a confirmation sound when selecting an option. Play a distinct sound when returning to the previous menu. Menu sounds must respect the audio volume configured by the player. |
| **1.3** | **Implement game mode selection:** Provide a game mode screen accessible from the Main Menu. Display the available modes, including Single Player and Two Player, allow the player to select a mode, and launch the corresponding game mode after confirmation. |
| **1.4** | **Implement Settings access:** Provide a direct link from the Main Menu to the Settings module. Display the available audio and visual settings, allow the player to modify them, apply the changes, and return to the Main Menu without losing the selected values. |
| **1.5** | **Implement Shop and Hangar access:** Provide direct links from the Main Menu to the Shop and Hangar modules. The Shop must allow access to available items and currency, while the Hangar must display the player's available ships and customization options. Return to the Main Menu must be possible from both modules. |

## Additional Agreed Scope

The following features are additional Frenchies team scope for PR #57. They do not replace or renumber the registered requirements above.

- **Configurable gameplay controls:** Allow players to rebind the left, right, and fire actions. Persist bindings between launches, allow Escape to cancel key capture, and reject duplicate bindings.
- **Gameplay difficulty selection:** Provide Easy, Normal, and Hard options and persist the selected difficulty. Apply the agreed difficulty behavior to campaign stage settings, including enemy movement and shooting intervals.

Integration details affecting campaign stages and enemy behavior will be coordinated with Octopus and KimchiBaguette before merging PR #57. Default movement keys will also be confirmed with KimchiBaguette.

## Dependencies

The Main Menu depends on the following modules:

- **Sound & Visual Settings** → [Modules 1](/teams/chanyoung.md) & [Modules 2](/teams/the-diversity-hires.md)
  The Main Menu must provide access to the game's audio and video settings.

- **Game Mode Selection** → [Module 10](/teams/friends.md)  
  The Main Menu must allow the player to select between single-player and two-player game modes.

- **Shop & Hangar** → [Modules 4](/teams/HanCode.md), [Module 5](/teams/Best-French.md) & [Module 9](/teams/KimchiBaguette.md) 
  The Main Menu must provide access to the shop, currency, available items, and ship customization.

