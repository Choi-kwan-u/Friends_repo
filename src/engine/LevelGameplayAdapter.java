package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import entity.EnemyShipFormation;
import level.LevelDefinition;
import level.WaveDefinition;

/** Checks runtime compatibility without changing the loaded design or active game. */
public final class LevelGameplayAdapter {
    private LevelGameplayAdapter() { }

    /**
     * Validates all waves, then supplies adjusted timing plus original geometry.
     * The grid remains in the definition and must be passed to formation creation.
     * Unknown enemies are rejected against current gameplay support (A/B/C), not
     * a proposed cross-team registry. No arbitrary balance limits are introduced.
     */
    public static List<GameSettings> prepare(final LevelDefinition definition,
            final int width, final int height, final double multiplier) {
        Objects.requireNonNull(definition, "definition");
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier <= 0)
            throw new IllegalArgumentException("Difficulty multiplier must be finite and positive");
        List<GameSettings> settings = new ArrayList<>();
        for (int index = 0; index < definition.getWaves().size(); index++) {
            WaveDefinition wave = definition.getWaves().get(index);
            try {
                EnemyShipFormation.validateWave(wave, width, height);
                long movement = Math.max(1L, Math.round(wave.getMovementBaseFrames() * multiplier));
                long shooting = Math.max(250L, Math.round(wave.getShootingIntervalMs() * multiplier));
                // Formation adds 10 frames; variable cooldown arithmetic can reach 2 * interval.
                if (movement > Integer.MAX_VALUE - 10 || shooting > Integer.MAX_VALUE / 2)
                    throw new IllegalArgumentException("Timing exceeds safe runtime integer range");
                settings.add(new GameSettings(wave.getColumns(), wave.getRows(),
                        (int) movement, (int) shooting, wave.getInitialX(), wave.getInitialY()));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("waves[" + index + "] (" + wave.getId()
                        + "): " + invalid.getMessage(), invalid);
            }
        }
        return Collections.unmodifiableList(settings);
    }
}
