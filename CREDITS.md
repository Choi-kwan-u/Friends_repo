# Campaign theme credits

Existing music and sound credits remain in [BGM credits](res/audio/bgm/CREDITS.md)
and [SFX credits](res/audio/sfx/CREDITS.md).

## Campaign backgrounds

Files: `res/backgrounds/campaign/stage_01.png` through `stage_07.png`.

- Supplied locally by the project owner as `stage_01.png` through `stage_07.png`.
- Author, original source URL and license: **unconfirmed; owner confirmation required**.
- No license or source documentation accompanied the supplied images. Do not infer
  permission to redistribute them from their presence in this project.
- Changes: copied byte-for-byte. The game crops only at render time and preserves
  the aspect ratio. The original files remain in the supplied local folder.

## Campaign BGM

All seven supplied OGG files contain title/artist tags identifying **MintoDog**.
The corresponding author pages below list **CC0 1.0 Universal**:
https://creativecommons.org/publicdomain/zero/1.0/

| Output under `res/audio/bgm/campaign/` | Original title | Source |
| --- | --- | --- |
| `stage_01.wav` | Space City | https://opengameart.org/content/space-city |
| `stage_02.wav` | Space Adventure | https://opengameart.org/content/space-adventure |
| `stage_03.wav` | 8bit Action Miniboss Battle | https://opengameart.org/content/8bit-action-miniboss-battle |
| `stage_04.wav` | Trance City | https://opengameart.org/content/trance-city |
| `stage_05.wav` | Space Boss Battle | https://opengameart.org/content/space-boss-battle |
| `stage_06.wav` | Space Battle | https://opengameart.org/content/space-battle |
| `stage_07.wav` | Hard Boss Battle 1 | https://opengameart.org/content/hard-boss-battle-1 |

- Identification: embedded OGG tags matched to the author/title on OpenGameArt;
  licenses checked on 2026-11-24. Supplied files were not hash-compared to downloads.
- Changes: decoded with FFmpeg to actual WAV, 44,100 Hz, signed 16-bit
  little-endian PCM stereo (`pcm_s16le`, `-ar 44100 -ac 2`), with no trimming or
  volume adjustment. Original OGG files remain unchanged in the supplied folder.
- Levels 3, 5 and 7 use the respective miniboss/boss tracks listed above.
