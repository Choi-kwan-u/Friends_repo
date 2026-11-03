package hud;

/** Immutable HUD data containing the balance plus rewards awaiting settlement. */
public final class AssetHudSnapshot {
    private final long credits;

    public AssetHudSnapshot(long credits) {
        if (credits < 0) throw new IllegalArgumentException("Credits cannot be negative.");
        this.credits = credits;
    }

    /** Display total; pending rewards cannot be spent until settlement succeeds. */
    public long getCredits() {
        return credits;
    }
}
