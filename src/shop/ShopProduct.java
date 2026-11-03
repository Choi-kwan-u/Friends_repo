package shop;

import currency.ShopCatalog.Category;
import entity.ShipType;
import java.util.Locale;

/** Immutable product details. Purchase prices are registered in ShopCatalog. */
public final class ShopProduct {
    private final String id;
    private final String name;
    private final Category category;
    private final int price;
    private final String description;
    private final String requiredUnlockId;
    private final ShipType shipType;
    private final boolean availableByDefault;

    public ShopProduct(String id, String name, Category category, int price) {
        this(id, name, category, price,
                category == Category.UPGRADE ? "Permanent stat upgrade" : "Permanent unlock");
    }

    public ShopProduct(String id, String name, Category category, int price,
            String description) {
        this(id, name, category, price, description, null);
    }

    public ShopProduct(String id, String name, Category category, int price,
            String description, String requiredUnlockId) {
        this(id, name, category, price, description, requiredUnlockId, null, false);
    }

    private ShopProduct(String id, String name, Category category, int price,
            String description, String requiredUnlockId, ShipType shipType,
            boolean availableByDefault) {
        if (requiredUnlockId != null && (requiredUnlockId.trim().isEmpty()
                || requiredUnlockId.equals(id))) {
            throw new IllegalArgumentException("Prerequisite must be a different, nonblank unlock ID.");
        }
        if (id == null || id.trim().isEmpty() || name == null
                || name.trim().isEmpty() || category == null
                || (availableByDefault ? price != 0 : price <= 0)
                || description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Product requires an ID, name, category, price and description.");
        }
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.requiredUnlockId = requiredUnlockId;
        this.shipType = shipType;
        this.availableByDefault = availableByDefault;
    }

    /** Default availability does not grant or persist permanent ownership. */
    public static ShopProduct ship(ShipType type, boolean availableByDefault, int price) {
        if (type == null) throw new IllegalArgumentException("Ship type is required.");
        return new ShopProduct("ship." + type.name().toLowerCase(Locale.ROOT),
                type.getName(), Category.UNLOCKABLE, price,
                availableByDefault ? "Select in Hangar." : "Unlock for Hangar.", null,
                type, availableByDefault);
    }

    public ShipType getShipType() {
        return shipType;
    }

    public boolean isAvailableByDefault() {
        return availableByDefault;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getRequiredUnlockId() {
        return requiredUnlockId;
    }
}
