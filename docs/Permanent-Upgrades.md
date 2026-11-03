# Permanent Campaign upgrades

The Shop's upper row sells three permanent upgrades in place of consumable items, each with three sequential purchase levels. The existing lower **Unlocks** row and shop controls are retained. A card shows the next level and its total effect; it becomes `MAX` after level 3. Each level costs 100, 250 and 500 Credits respectively (850 per completed upgrade).

| Upgrade | Level 1 | Level 2 | Level 3 |
| --- | --- | --- | --- |
| Hull | +1 maximum life | +2 maximum lives | +3 maximum lives |
| Engine | +1 movement unit/update | +2 movement units/update | +3 movement units/update |
| Reloader | 10% shorter shot interval | 20% shorter shot interval | 30% shorter shot interval |

These are provisional balance values in [`PermanentUpgrade`](../src/currency/PermanentUpgrade.java). Bonuses are totals relative to the selected ship's original stats. The game uses lives, not a separate HP pool. Purchases apply to the next Campaign, persist across restarts, and remain in effect when moving between stages or restoring stats after temporary effects. Upgrades do not apply to Endless or custom levels.

Campaign defeats award A/B/C enemies 10/15/20 Credits, bonus enemies 20, and either boss 50. Every defeat pays once. These rewards appear immediately in the HUD balance API and are settled and saved when the stage screen exits, even after a failed or interrupted stage. Amounts are defined in [`RewardSystem`](../src/currency/RewardSystem.java).

Campaign clears additionally award 50 Credits on Easy, 100 on Normal and 200 on Hard, using the difficulty captured for that stage. The clear bonus enters the balance only after the game screen and its end-of-stage delay finish. Failed stages and incomplete waves do not grant it. Custom levels and Infinite mode do not earn these Campaign rewards.

Endless awards a provisional **1 Credit per defeat event**, regardless of enemy type, with no clear or difficulty bonus. It uses the existing Endless defeat events and ignores duplicate event IDs within the run. Credits accumulate during play and settle into the same Shop balance when the screen exits after death or Escape. Escape still abandons the run for records, but keeps earned Credits. Offscreen enemies and contact damage without a defeat event do not pay. Pending rewards are included in the HUD balance API and displayed by the Endless HUD.

## Run and try it

With JDK 17 or newer, start **Invaders - Run** in IntelliJ, or use the existing run task:

```powershell
.\gradlew.bat run
```

On macOS/Linux: `sh ./gradlew run`.

1. Defeat enemies in Campaign or Endless to earn Credits; Campaign clears also grant a bonus. Open **Shop** from the main menu after the game screen exits.
2. Use Left/Right in the upper **Upgrades** row to select Hull, Engine or Reloader, then Space/Enter to buy the next level. Each new key press buys at most one level.
3. Start a new Campaign to use the purchased bonuses. Exit and run the game again to check that purchased levels and the remaining balance are restored.

The lower **Unlocks** row lists all three ships. Standard Ship and Heavy Tank remain free. Speedster starts locked and costs a provisional 200 Credits in [`ShopProducts`](../src/shop/ShopProducts.java). Buy it here, then select it in **Hangar > Choose ship**. Hangar blocks both keyboard and mouse selection until purchase. The `ship.speedster` unlock persists across restarts, and an existing saved unlock remains valid. The `shop.ShopPreview.main` helper retains separate sample unlocks with an isolated save.

## Integration and checks

Each level uses an ordinary permanent unlock ID, such as `upgrade.hull.1`. The existing asset save format is unchanged. Old item quantities are retained for compatibility, but consumable purchase and use services have been removed. Field pickups and temporary item effects remain with the Item System. Catalog prerequisites prevent skipping levels; rejected purchases do not charge or save. A failed save retains the purchase in memory, and **Retry save** writes the latest state without purchasing again.

If a reward save fails, the Credits remain available in memory. The game-end dialog offers **Retry** or **Continue**; retries save the latest balance and purchases without paying rewards again. Campaign and Endless pending rewards survive new runs within the session if the balance is full and are retried after leaving the Shop. Quit through the main menu to retry outstanding saves before exiting. Pending or unsaved rewards are not guaranteed to survive a forced process exit.

`Core` captures one immutable `CampaignUpgrades` snapshot per run. `GameScreen` uses its max lives and initializes new ships through the existing baseline-stat setters. Existing constructors default to no upgrades.

```powershell
.\gradlew.bat check
```

Tests cover ordered purchases, insufficient funds, repeat/max purchases, save reload/retry, held input, both shop rows, Speedster selection before and after purchase, stage transitions, Campaign defeat/clear rewards, and Endless defeat/death/Escape rewards through the HUD API, purchase and save flow. Run `build` for checks plus packaging.
