import dev.unstable.underworld.UnderworldMath;

/** Plain Java smoke tests. No Minecraft runtime or external libraries needed. */
public final class TerrainMathTest {
    public static void main(String[] args) {
        long seed = 0x4AD8C9BF0521DA5BL;
        int[] distanceBands = {0, 800, 1700, 2900, 4300, 5600, 6500};
        double[] cover = new double[distanceBands.length];
        for (int i = 0; i < distanceBands.length; i++) {
            int d = distanceBands[i], count = 0, total = 0;
            for (int x = -d; x <= d; x += Math.max(1, d / 22)) {
                int z = (int) Math.sqrt(Math.max(0L, (long) d * d - (long) x * x));
                for (int sign : new int[]{-1, 1}) {
                    if (UnderworldMath.hasLand(x, sign * z, seed)) count++;
                    total++;
                }
            }
            cover[i] = count / (double) total;
            System.out.printf("radius=%d terrain cover=%.1f%%%n", d, cover[i] * 100);
        }
        if (cover[0] != 1 || cover[1] != 1 || cover[cover.length - 1] != 0) {
            throw new AssertionError("Expected continuous land near origin and complete void after 6400");
        }
        if (cover[3] >= cover[1] || cover[5] >= cover[3]) {
            throw new AssertionError("Void gaps should grow with distance");
        }
        int lowest = 999, highest = -999;
        for (int x = -3000; x <= 3000; x += 51) {
            for (int z = -3000; z <= 3000; z += 53) {
                int y = UnderworldMath.surfaceY(x, z, seed);
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
                if (y - UnderworldMath.bottomY(x, z, seed) + 1 != 30) {
                    throw new AssertionError("Thickness must be 30 blocks");
                }
            }
        }
        if (lowest < 40 || highest > 110) throw new AssertionError("Hills too high: " + lowest + ".." + highest);
        System.out.println("PASS: shallow terrain at " + lowest + ".." + highest + ", 30 blocks thick; void expands to 100%.");
    }
}
