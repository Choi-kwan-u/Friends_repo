package boss;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import entity.Boss;
import entity.BossBullet;

/**
 * Connects the boss made by Boss Developer 1 with the attack system,
 * sends boss events and gives boss information to the Level Design System
 * and Gameplay HUD.
 *
 * Changes in health, phase and defeat are found by comparing the boss
 * values with the previous ones, as suggested in Boss.takeDamage().
 *
 * Boss Developer 2.
 */
public final class BossManager {

    /** Sends events to other systems. */
    private final BossEventManager eventManager = new BossEventManager();
    /** Random generator used to pick attack patterns. */
    private final Random random;
    /** Bosses already defeated, so they are not spawned again. */
    private final Set<String> defeatedBosses = new HashSet<String>();

    /** Current boss, or null. */
    private Boss boss;
    /** Attack system of the current boss. */
    private BossAttackSystem attackSystem;
    /** Key of the current boss (type, level, wave). */
    private String currentKey;
    /** Health seen in the previous check. */
    private int lastHealth;
    /** Phase seen in the previous check. */
    private int lastPhase;
    /** True once the current boss battle is over. */
    private boolean finished;
    /** True if this class set the boss state to ATTACKING. */
    private boolean attackingStateSet;

    /** Constructor. */
    public BossManager() {
        this(new Random());
    }

    /**
     * Constructor with a given random generator.
     *
     * @param random
     *            Random generator used to pick attack patterns.
     */
    public BossManager(final Random random) {
        this.random = random;
    }

    /**
     * Spawns a boss when the Level Design System says the conditions are
     * met. The boss itself is created with Boss Developer 1's constructor.
     *
     * @param request
     *            Boss type, position, level and wave.
     * @return The new boss, or null if a boss is already fighting or this
     *         boss was already defeated.
     */
    public Boss spawn(final BossSpawnRequest request) {
        if (this.boss != null && !this.finished)
            return null;
        String key = request.getBossType() + ":" + request.getLevel() + ":"
                + request.getWave();
        if (this.defeatedBosses.contains(key))
            return null;

        this.boss = new Boss(request.getPositionX(), request.getPositionY(),
                request.getBossType());
        this.attackSystem = new BossAttackSystem(
                BossAttackConfig.forType(request.getBossType()), this.random);
        this.currentKey = key;
        this.lastHealth = this.boss.getCurrentHealth();
        this.lastPhase = this.boss.getPhase();
        this.finished = false;
        this.attackingStateSet = false;
        this.eventManager.publish(BossEvent.spawned(this.boss));
        return this.boss;
    }

    /**
     * Checks the boss for damage, phase change and defeat, and sends the
     * events. Called by {@link #update}; call it right after
     * Boss.takeDamage() too, to send the events in the same frame.
     */
    public void sync() {
        if (this.boss == null || this.finished)
            return;

        int health = this.boss.getCurrentHealth();
        if (health < this.lastHealth)
            this.eventManager.publish(
                    BossEvent.damaged(this.boss, this.lastHealth - health));
        this.lastHealth = health;

        // Each threshold is sent once, even if a phase is skipped.
        int phase = this.boss.getPhase();
        if (phase > this.lastPhase) {
            for (int newPhase = this.lastPhase + 1; newPhase <= phase;
                    newPhase++)
                this.eventManager.publish(BossEvent.phaseChanged(this.boss,
                        newPhase - 1, newPhase));
            this.attackSystem.setPhase(phase);
            this.lastPhase = phase;
        }

        if (this.boss.isDefeated()) {
            this.attackSystem.stop();
            this.finished = true;
            this.defeatedBosses.add(this.currentKey);
            this.eventManager.publish(BossEvent.defeated(this.boss));
        }
    }

    /**
     * Runs the boss attacks. Call once per frame, after Boss.update().
     *
     * @param targetX
     *            Horizontal center of the player.
     * @param targetY
     *            Top of the player.
     * @param bossBullets
     *            Set the new boss bullets are added to.
     */
    public void update(final int targetX, final int targetY,
            final Collection<BossBullet> bossBullets) {
        sync();
        if (this.boss == null || this.finished)
            return;

        // The attacking pose lasts one frame, or the whole special windup.
        if (this.attackingStateSet && !this.attackSystem.isWindingUp()) {
            if (this.boss.getState() == Boss.State.ATTACKING)
                this.boss.setState(Boss.State.MOVING);
            this.attackingStateSet = false;
        }

        int originX = this.boss.getPositionX() + this.boss.getWidth() / 2;
        int originY = this.boss.getPositionY() + this.boss.getHeight();
        BossAttackSystem.AttackResult result = this.attackSystem.update(
                originX, originY, targetX, targetY);
        if (result == null)
            return;

        if (this.boss.getState() == Boss.State.MOVING
                || this.boss.getState() == Boss.State.IDLE) {
            this.boss.setState(Boss.State.ATTACKING);
            this.attackingStateSet = true;
        }
        bossBullets.addAll(result.getBullets());
        this.eventManager.publish(BossEvent.attack(this.boss,
                result.getAttackType(), result.isSpecial(), result.isWindup(),
                result.getBullets().size()));
    }

    /** @return Boss information for the Level Design System. */
    public BossStatus getStatus() {
        if (this.boss == null)
            return new BossStatus(false, false, null,
                    BossStatus.BattleState.NONE);
        if (this.finished)
            return new BossStatus(false, true, this.boss.getType(),
                    BossStatus.BattleState.ENDED);
        return new BossStatus(true, false, this.boss.getType(),
                BossStatus.BattleState.IN_BATTLE);
    }

    /** @return Boss information for the HUD, or null if no boss. */
    public BossHudInfo getHudInfo() {
        if (this.boss == null)
            return null;
        return new BossHudInfo(this.boss);
    }

    /** @return Event manager where other systems register listeners. */
    public BossEventManager getEventManager() {
        return this.eventManager;
    }

    /** @return Current boss, or null. */
    public Boss getBoss() {
        return this.boss;
    }

    /** @return Attack system of the current boss, or null. */
    public BossAttackSystem getAttackSystem() {
        return this.attackSystem;
    }
}
