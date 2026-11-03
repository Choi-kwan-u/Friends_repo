## 1. Team Introduction
Our ambition is to design and implement a dynamic, robust, and extensible Item System for Space Invaders SDP. The vision is to enhance gameplay depth and player engagement by introducing collectible items, power-ups, and sub-weapons that seamlessly interact with combat and progression.

### Members & Roles

| Name | Role | GitHub Profile |
| :--- | :--- | :--- |
| **Leo Enaux** | Team Leader, Core Architecture & Energy Shield Developer | [@leoenaux](https://github.com/leoenaux) |
| **Mathéo SUHR** | Collision & Spawning Developer | [@Matheo92i](https://github.com/Matheo92i) |
| **Clarisse Majourau** | Twin Cannon Developer | [@clarissemajourau](https://github.com/clarissemajourau) |
| **Brieuc VIOUGEAT** | Chameleon Hull Developer | [@BrieucVio](https://github.com/BrieucVio) |
| **Raphaelle Amar** | Cluster Grenade Developer | [@Raphaelle94](https://github.com/Raphaelle94) |
| **Inès Safir** | Hyperfire Buff Developer | [@ines-saphir](https://github.com/ines-saphir) |
| **Manel Belfedhal** | Orbital Annihilator Developer | [@belfedhal-manel](https://github.com/belfedhal-manel) |
| **Aurele Ouary** | Inventory & Buff State Developer | [@aureleouaryPro](https://github.com/aureleouaryPro) |

---

## 2. Team Requirements
We are developing the **Item System** (Requirement 5) of the Space Invaders game. This module manages the lifecycle, spawning logic, collection, and effects of all in-game items. It is built around three core classes:

- `Item` (`src/entity/item/Item.java`): abstract base class for every falling item, defining `applyEffect(Ship)`, `removeEffect(Ship)`, `getDuration()`, and `getName()`.
- `ItemManager`: spawns items in the game world, moves them, removes them when they leave the screen, and detects collection by the player ship.
- `BuffManager`: applies item effects, tracks the expiry time of timed buffs, removes expired effects, and clears all active buffs when the player loses a life.

Items with a duration of 0 are instant items and are not tracked by the `BuffManager`.

---

## 3. Detailed Requirements (Item Behaviors)

1. **Twin Cannon (Double Barrel):** Available from Level 3 onward. Appears once per level after 10 enemies have been destroyed in that level, falling from a random position at the top of the screen. The player collects it by shooting it. While active, the ship fires two parallel bullets instead of one for 15 seconds. If the player loses a life, the buff expires immediately.

2. **Chameleon Hull (Color-Shifter):** Can drop from a destroyed enemy (3% chance, at most once per level). The falling item is drawn as a star that cycles colors. On collection, the ship transforms into a ball for 5 seconds: it cycles through five colors (cyan, magenta, yellow, orange, blue) every 0.15 seconds, moves at twice its normal speed, and cannot be destroyed, but it cannot shoot or launch grenades. Collecting another Chameleon Hull while active restarts the 5-second timer without the old timer cancelling the new one.

3. **Cluster Grenade Launcher:** Drops from destroyed enemies (15% chance, at most 2 drops per level). On collection, the ship gains a grenade (maximum 1 carried at a time; the current count is shown on screen). The player launches it with the **G** key (800 ms cooldown). The grenade travels upward at half the speed of a normal bullet and detonates when it hits an enemy or reaches a fixed altitude, splitting into 12 fragments that spread in all directions and destroy the enemies they hit.

4. **Overclocked Fire Rate (Hyperfire):** Drops from the red special enemy ship (UFO) when it is destroyed, at most once per level. On collection, it reduces the ship's firing cooldown by 60% (from 400 ms to 160 ms between shots) for 8 seconds.

5. **Orbital Annihilator (Doomsday Laser):** A rare ultimate item available only in Level 7 (5% drop chance per destroyed enemy, at most once per level). Catching it arms the weapon for one use. The player triggers it by holding **Space** for 1 second, which starts a 1.5-second charging phase followed by a 2-second vertical piercing beam above the ship. The beam destroys every enemy in its corridor (points are awarded) and neutralizes incoming enemy projectiles. If the ship is hit while charging, the charge is cancelled but the weapon is not lost.

6. **Energy Shield:** Drops from a destroyed enemy, at most once per level (15% chance per kill, guaranteed when the last enemy of the formation is destroyed). On collection, a shield is drawn around the ship for 15 seconds and absorbs every enemy shot that hits it, so no life is lost.

---

## 4. Dependencies on Other Teams

1. **Player & Enemy Ship Variety (Requirement 9, KimchiBaguette):**
   * *Dependency:* Item effects directly modify player ship attributes and behavior in `Ship.java`: firing interval (Hyperfire), projectile spawn offsets (Twin Cannon), grenade stock (Cluster Grenade), shield state (Energy Shield), and size, color, and movement speed (Chameleon Hull). Changes to the ship's structure must remain compatible with these effects.

2. **Currency System (HanCode):**
   * *Dependency:* HanCode's Shop sells items as purchasable consumables. Best-French provides the item identity, description, duration, and a result-reporting activation path; HanCode handles prices, purchases, stored quantities, and saving. The first integrated item is the **Energy Shield**: buying it adds stock without activating it, and the player uses one unit with a dedicated key during Campaign gameplay. Field pickups keep working independently of purchased stock.
