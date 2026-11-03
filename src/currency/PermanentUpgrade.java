package currency;

/** Tier IDs and provisional balance values for permanent Campaign upgrades. */
public enum PermanentUpgrade {
    HULL("upgrade.hull", "Hull", new int[] {1, 2, 3}),
    ENGINE("upgrade.engine", "Engine", new int[] {1, 2, 3}),
    RELOADER("upgrade.reloader", "Reloader", new int[] {10, 20, 30});

    private static final int[] PRICES = {100, 250, 500};
    private final String id;
    private final String name;
    private final int[] bonuses;

    PermanentUpgrade(String id, String name, int[] bonuses) {
        this.id = id;
        this.name = name;
        this.bonuses = bonuses;
    }

    public String getName() {
        return name;
    }

    public int getMaxLevel() {
        return bonuses.length;
    }

    public String getProductId(int level) {
        requirePurchasableLevel(level);
        return id + "." + level;
    }

    public String getRequiredUnlockId(int level) {
        requirePurchasableLevel(level);
        return level == 1 ? null : getProductId(level - 1);
    }

    public int getPrice(int level) {
        requirePurchasableLevel(level);
        return PRICES[level - 1];
    }

    /** Returns the total bonus at this level, not another bonus to accumulate. */
    public int getBonus(int level) {
        if (level == 0) return 0;
        requirePurchasableLevel(level);
        return bonuses[level - 1];
    }

    /** Higher saved tiers do not take effect until all earlier tiers are owned. */
    public int getLevel(PlayerAssetsState assets) {
        if (assets == null) throw new IllegalArgumentException("Assets cannot be null.");
        int level = 0;
        while (level < getMaxLevel()
                && assets.getUnlockedContentIds().contains(getProductId(level + 1))) {
            level++;
        }
        return level;
    }

    public static PermanentUpgrade forProduct(String productId) {
        for (PermanentUpgrade upgrade : values()) {
            for (int level = 1; level <= upgrade.getMaxLevel(); level++) {
                if (upgrade.getProductId(level).equals(productId)) return upgrade;
            }
        }
        return null;
    }

    private void requirePurchasableLevel(int level) {
        if (level < 1 || level > getMaxLevel()) {
            throw new IllegalArgumentException("Upgrade level is outside the configured range.");
        }
    }
}
