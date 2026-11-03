# Shop integration

The existing Shop layout is retained. **Upgrades** replaces the upper consumable-item row with Hull, Engine and Reloader. The lower **Unlocks** row remains available for permanent content. Upgrade levels, provisional prices and preview commands are documented in [Permanent Upgrades](Permanent-Upgrades.md).

## Purchases and ownership

- `ShopProducts.availableProducts()` defines the production upgrades and ships. `Category.UPGRADE` places upgrades in the upper row; `Category.UNLOCKABLE` places ships and other permanent products in the lower row.
- `Core.getShopController()` returns the controller for the shared `PlayerAssets`. Call `purchase(productId)` to buy a product using its catalog price.
- `PurchaseSystem` validates the ID, prerequisites, existing ownership and balance. Successful purchases deduct Credits and record a permanent unlock; rejections change nothing.
- Each upgrade card advances to its next level after purchase. All three levels owned displays `MAX`. `Core` captures the purchased bonuses when starting a Campaign.

Standard Ship and Heavy Tank are free; Speedster costs 200 Credits in the lower row. The entries use `ship.standard`, `ship.speedster` and `ship.tank`. Hangar checks `Core.getShopController().isShipAvailable(type)` against the same catalog and saved ownership, for both keyboard and mouse confirmation. Existing saved unlocks remain valid. Free availability does not create an ownership entry or write a save.

## Saves and rewards

`ShopController` saves after a successful purchase. Check `getSaveStatus()` separately from `PurchaseResult`. If saving fails, the purchase remains in memory; `retrySave()` writes the latest assets without charging or granting ownership again.

`CampaignRewards` subscribes to each official Campaign screen. `EnemyDefeatedEvent.getEnemyType()` selects the reward: A/B/C = 10/15/20, BONUS = 20, BOSS_A/BOSS_B = 50 Credits. The active run ID and opaque event ID prevent duplicate payouts; `UNKNOWN` and other runs are ignored. Defeat rewards accumulate immediately and settle when the screen exits, including on failure.

`LevelCompletedEvent` records a clear, but its bonus is added only after the game screen and end-of-stage delay finish. Save retries use the same shared assets as the Shop without paying twice. Custom levels and Infinite mode do not subscribe to Campaign rewards.

`EndlessRewards` subscribes separately to each `InfiniteGameScreen` and pays 1 Credit per unique defeat in the active run, including legacy events with `UNKNOWN` type. It grants no clear or difficulty bonus. Death and Escape both settle and save earned Credits; Escape continues to abandon records independently. `Core` retries both modes after Campaign or Endless, after leaving the Shop and before a normal menu exit. Failed settlements stay pending in memory; failed saves retry the latest shared assets without replaying defeats. No event or save-format changes are required.

The asset save format retains balance, permanent IDs and legacy item quantities. Legacy quantities are preserved on load/save only; consumable purchase and use services have been removed. Field pickups and temporary effects remain unchanged and do not use this Shop inventory.

## HUD balance API

On the gameplay thread after startup, read `long credits = Core.getAssetHudSnapshot().getCredits();`. Each call returns an immutable snapshot of the existing session; request a new snapshot when updating the HUD.

Credits combine the current balance and unpaid Campaign and Endless rewards, including defeats earned during play. The current Campaign stage's clear bonus appears only after its screen finishes. Pending rewards remain in memory for this session and cannot be spent before settlement; the display total can exceed the balance's `int` limit.

Reading does not load, save or settle rewards. Calling before assets and Campaign rewards are initialized throws `IllegalStateException`. The API exposes no consumable inventory, slots or item actions. Campaign and Endless HUDs read this API to display the shared Credits total, including pending defeat rewards.

## Verification

Run `.\gradlew.bat check` on Windows or `sh ./gradlew check` on macOS/Linux. Tests cover both shop rows, tier prerequisites, repeat purchases, save failures/retries and reloads. Use the existing `run` task or **Invaders - Run** in IntelliJ to try the upgrades in Campaign.
