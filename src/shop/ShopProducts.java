package shop;

import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import currency.ShopCatalog.Category;
import entity.ShipType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopProducts {
    private ShopProducts() { }

    public static List<ShopProduct> availableProducts() {
        List<ShopProduct> products = new ArrayList<ShopProduct>();
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            for (int level = 1; level <= upgrade.getMaxLevel(); level++) {
                products.add(new ShopProduct(upgrade.getProductId(level),
                        upgrade.getName() + " " + level + "/" + upgrade.getMaxLevel(),
                        Category.UPGRADE, upgrade.getPrice(level),
                        description(upgrade, level), upgrade.getRequiredUnlockId(level)));
            }
        }
        for (ShipType ship : ShipType.values()) {
            boolean available = ship != ShipType.SPEEDSTER;
            products.add(ShopProduct.ship(ship, available, available ? 0 : 200));
        }
        return Collections.unmodifiableList(products);
    }

    /** Keep one card per upgrade, advancing it only after a completed purchase. */
    static List<ShopProduct> visibleProducts(List<ShopProduct> products, PlayerAssetsState assets) {
        List<ShopProduct> visible = new ArrayList<ShopProduct>();
        for (ShopProduct product : products) {
            PermanentUpgrade upgrade = PermanentUpgrade.forProduct(product.getId());
            if (upgrade == null) {
                visible.add(product);
                continue;
            }
            int owned = upgrade.getLevel(assets);
            int next = Math.min(owned + 1, upgrade.getMaxLevel());
            if (!product.getId().equals(upgrade.getProductId(next))) continue;
            if (owned == upgrade.getMaxLevel()) {
                product = new ShopProduct(product.getId(), upgrade.getName() + " MAX",
                        product.getCategory(), product.getPrice(), product.getDescription(),
                        product.getRequiredUnlockId());
            }
            visible.add(product);
        }
        return Collections.unmodifiableList(visible);
    }

    private static String description(PermanentUpgrade upgrade, int level) {
        int bonus = upgrade.getBonus(level);
        switch (upgrade) {
        case HULL: return "+" + bonus + " max lives. Campaign.";
        case ENGINE: return "+" + bonus + " move speed. Campaign.";
        case RELOADER: return "Shot interval -" + bonus + "%. Campaign.";
        default: throw new IllegalArgumentException("Unknown upgrade.");
        }
    }
}
