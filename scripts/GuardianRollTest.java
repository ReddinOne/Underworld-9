import dev.unstable.underworld.UnderworldLayout;

public class GuardianRollTest {
    public static void main(String[] args) {
        int successes = 0;
        final int trials = 100_000;
        for (int i = 0; i < trials; i++) {
            long stableHash = (0xD1E7F8AC2000L ^ (long)i * 0x9E3779B97F4A7C15L);
            boolean first = UnderworldLayout.guardianRoll(stableHash);
            boolean second = UnderworldLayout.guardianRoll(stableHash);
            if (first != second) throw new AssertionError("Ruin guardian roll changed on reload");
            if (first) successes++;
        }
        double fraction = successes / (double) trials;
        if (fraction < 0.49 || fraction > 0.51) {
            throw new AssertionError("Guardian roll statistically incorrect: " + fraction);
        }
        System.out.printf("PASS: 50%% deterministic guardian roll (%.2f%% in %d samples)%n", fraction * 100, trials);
    }
}
