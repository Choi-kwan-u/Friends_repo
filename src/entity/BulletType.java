package entity;

public enum BulletType {
    NORMAL(-6, 1,0),
    RAPID(-6,2,100),
    MULTI(-6,3,0);

    private final int speed;
    private final int shotCount;
    private final int shotDelay;

    BulletType(int speed, int shot_count, int shot_delay) {
        this.speed = speed;
        this.shotCount = shot_count;
        this.shotDelay = shot_delay;
    }

    public int getSpeed() {
        return speed;
    }

    public int getShotCount() {
        return shotCount;
    }

    public int getShotDelay() {
        return shotDelay;
    }
}
