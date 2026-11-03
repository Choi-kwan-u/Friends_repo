package boss;

import java.util.ArrayList;
import java.util.List;

import engine.DifficultyAdjustWhilePlaying;
import entity.BossBullet;

/**
 * Basic boss attack pattern (PRD 2.3): single, multi and spread shot.
 *
 * Boss Developer 2.
 */
public interface AttackPattern {

    String getName();

    List<BossBullet> fire(int originX, int originY, int targetX, int targetY,
            int damage);

    final class SingleShot implements AttackPattern {
        private final double speed;
        private final boolean aimed;
        private final double fixedAngle;

        public SingleShot(final double speed) {
            this(speed, true, 90);
        }

        public SingleShot(final double speed, final boolean aimed,
                final double fixedAngle) {
            this.speed = speed;
            this.aimed = aimed;
            this.fixedAngle = fixedAngle;
        }

        @Override
        public String getName() {
            return "single";
        }

        @Override
        public List<BossBullet> fire(final int originX, final int originY,
                final int targetX, final int targetY, final int damage) {
            double angle = this.aimed
                    ? BossBullet.angleTo(originX, originY, targetX, targetY)
                    : this.fixedAngle;
            double adjustedSpeed = DifficultyAdjustWhilePlaying.getInstance()
                    .adjustEnemyBulletSpeed(this.speed);
            List<BossBullet> bullets = new ArrayList<BossBullet>();
            bullets.add(BossBullet.fromAngle(originX, originY, angle,
                    adjustedSpeed, damage, false));
            return bullets;
        }
    }

    final class MultiShot implements AttackPattern {
        private final double speed;
        private final double[] angles;

        public MultiShot(final double speed, final double... angles) {
            if (angles.length == 0)
                throw new IllegalArgumentException(
                        "Multi shot needs at least one direction.");
            this.speed = speed;
            this.angles = angles.clone();
        }

        @Override
        public String getName() {
            return "multi";
        }

        @Override
        public List<BossBullet> fire(final int originX, final int originY,
                final int targetX, final int targetY, final int damage) {
            double adjustedSpeed = DifficultyAdjustWhilePlaying.getInstance()
                    .adjustEnemyBulletSpeed(this.speed);
            List<BossBullet> bullets = new ArrayList<BossBullet>();
            for (double angle : this.angles)
                bullets.add(BossBullet.fromAngle(originX, originY, angle,
                        adjustedSpeed, damage, false));
            return bullets;
        }
    }

    final class SpreadShot implements AttackPattern {
        private final double speed;
        private final int count;
        private final double spreadAngle;

        public SpreadShot(final double speed, final int count,
                final double spreadAngle) {
            if (count < 2)
                throw new IllegalArgumentException(
                        "Spread shot needs at least two bullets.");
            this.speed = speed;
            this.count = count;
            this.spreadAngle = spreadAngle;
        }

        @Override
        public String getName() {
            return "spread";
        }

        @Override
        public List<BossBullet> fire(final int originX, final int originY,
                final int targetX, final int targetY, final int damage) {
            double center = BossBullet.angleTo(originX, originY, targetX,
                    targetY);
            double step = this.spreadAngle / (this.count - 1);
            double first = center - this.spreadAngle / 2;
            double adjustedSpeed = DifficultyAdjustWhilePlaying.getInstance()
                    .adjustEnemyBulletSpeed(this.speed);
            List<BossBullet> bullets = new ArrayList<BossBullet>();
            for (int i = 0; i < this.count; i++)
                bullets.add(BossBullet.fromAngle(originX, originY,
                        first + i * step, adjustedSpeed, damage, false));
            return bullets;
        }
    }
}
