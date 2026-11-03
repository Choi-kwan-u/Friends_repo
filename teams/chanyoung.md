# <Team Name>

## Team Introduction

We are Team Chanyoung.<br/>
Our team is responsible for developing the Sound Effects and Background Music (BGM) system for the Space Invaders project, including gameplay sound effects, background music, and basic audio controls.

Team repository: [nnnrc/Invaders-SDP-24592](https://github.com/nnnrc/Invaders-SDP-24592)

## Members

| Name | Role | GitHub |
| --- | --- | --- |
| Chanyoung Lee | Team Leader / Audio Controls | [leechanyoung0710](https://github.com/leechanyoung0710) |
| Sihoon Kim | Audio Manager / Maintainer | [nnnrc](https://github.com/nnnrc) |
| Heyonmin Jeon | UI & Item Sound Effects | [oihsie](https://github.com/oihsie) |
| Jaesung Yoo | Audio Controls | [jaesung-rtp](https://github.com/jaesung-rtp) |
| Jiseok Byun | Game Sound Effects | [jisuk24](https://github.com/jisuk24) |
| Taesu Park | UI & Item Sound Effects | [ptsoo0602-dev](https://github.com/ptsoo0602-dev) |
| EunJi Park | Game Sound Effects | [ej040320](https://github.com/ej040320) |
| Lana MANGIN | Background Music | [LanaMANGIN](https://github.com/LanaMANGIN) |
| Chloé DESCAMPS | Background Music | [DescampsC](https://github.com/DescampsC) |

## Team Requirement

Our team will design and implement a Sound Effects and Background Music (BGM) System for the Space Invaders game. The system will provide a central Audio Manager for playing and managing BGM and sound effects, including looping, stopping, mute/unmute, and separate volume control. Sound effects will be added for major gameplay and UI events such as shooting, damage, explosions, item pickup, button clicks, level clear, and game over.

We will integrate the audio system with other game components so that sounds can be triggered by gameplay events without each team having to manage audio directly. We will also coordinate with the Player & Enemy Ship Variety team for shooting and destruction events, the Main Menu team for audio control UI, and the Level Design System team for level start, level clear, and game over events.

## Detailed Requirements

1. **Audio Manager**
   - Implement a central audio manager that can play, stop, and manage sound effects and background music.
   - Provide a common interface that allows other game components to play sounds and control audio settings without directly managing audio resources.
   - Implement event adapters that connect events from other systems, such as level transitions, player/enemy actions, and UI interactions, to the appropriate audio actions.

2. **Background Music**
   - Add background music that plays during gameplay.
   - The music should start when the game begins, loop while playing, and stop or change when the game ends.

3. **Game Sound Effects**
   - Add sound effects for common game events such as player shooting, enemy shooting, explosions, player damage, and enemy destruction.

4. **UI and Item Sound Effects**
   - Add simple sound effects for menu button clicks, item pickup, and other basic UI or interaction events.

5. **Audio Controls**
   - Provide basic audio controls such as mute/unmute and separate volume control for BGM and sound effects.

## Dependencies

1. **Player & Enemy Ship Variety - KimchiBaguette**
   - We depend on this team to provide game events such as shooting, taking damage, and destruction so that the appropriate sound effects can be played.

2. **Main Menu - Frenchies**
   - We depend on the Main Menu team to provide UI controls for audio settings, such as mute/unmute and separate volume controls for BGM and sound effects.
   - We also need menu interaction events, such as button clicks or menu selections, so that UI sound effects can be played through our Audio Manager.
   - The Main Menu can either notify our audio system when these interactions occur or directly call the appropriate Audio Manager methods to play UI sound effects and update audio settings.

3. **Level Design System - Octopus**
   - Our audio system depends on level lifecycle information to determine when background music should start, stop, or change.
   - The Level Design System should provide events for level start, level transition, and level end, and allow the Audio Manager to register listeners for these events.
   - Each event should also provide information about the current or target level, such as a level ID or level number, so that the Audio Manager can select and play the appropriate BGM for that level.
   - These events will be used to synchronize BGM playback with the current level state.
