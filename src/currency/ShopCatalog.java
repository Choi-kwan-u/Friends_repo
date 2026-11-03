package currency;

import java.util.HashMap;
import java.util.Map;

/** Prices and prerequisites for permanent upgrades and unlocks. */
public class ShopCatalog {
    public enum Category {
        UPGRADE,
        UNLOCKABLE
    }

    private static final class Product {
        final int price;
        final Category category;
        final String requiredUnlockId;

        Product(int price, Category category, String requiredUnlockId) {
            this.price = price;
            this.category = category;
            this.requiredUnlockId = requiredUnlockId;
        }
    }

    private final Map<String, Product> catalog = new HashMap<String, Product>();

    public boolean registerUpgrade(String id, int price) {
        return registerUpgrade(id, price, null);
    }

    public boolean registerUpgrade(String id, int price, String requiredUnlockId) {
        return register(id, price, Category.UPGRADE, requiredUnlockId);
    }

    public boolean registerUnlockable(String id, int price) {
        return registerUnlockable(id, price, null);
    }

    public boolean registerUnlockable(String id, int price, String requiredUnlockId) {
        return register(id, price, Category.UNLOCKABLE, requiredUnlockId);
    }

    private boolean register(String id, int price, Category category, String requiredUnlockId) {
        if (id == null || id.trim().isEmpty() || price <= 0 || catalog.containsKey(id)) return false;
        // Requiring an earlier registration prevents cycles in prerequisite chains.
        if (requiredUnlockId != null && !isUnlockable(requiredUnlockId)) return false;
        catalog.put(id, new Product(price, category, requiredUnlockId));
        return true;
    }

    public int getPrice(String id) {
        Product product = catalog.get(id);
        return product == null ? -1 : product.price;
    }

    public String getRequiredUnlockId(String id) {
        Product product = catalog.get(id);
        return product == null ? null : product.requiredUnlockId;
    }

    public boolean isUpgrade(String id) {
        Product product = catalog.get(id);
        return product != null && product.category == Category.UPGRADE;
    }

    /** Both shop categories grant permanent ownership. */
    public boolean isUnlockable(String id) {
        return catalog.containsKey(id);
    }
}
