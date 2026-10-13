package dev.unstable.underworld;

/**
 * Deterministic, chunk-boundary-independent Underworld geography.
 * Everything is computed directly from X/Z and world seed; no overworld
 * generator, neighbor chunk loads or block-access calls are involved.
 */
public final class UnderworldMath {
    public static final int VERTICAL_THICKNESS = 30;
    public static final int FRACTURE_START = 1150;
    public static final int COMPLETE_VOID = 6400;
    // Intentionally NO world-aligned pit grid: old 680-block pit cells
    // produced repeated square-like silhouettes at large distances.

    private UnderworldMath() { }

    /** Low mountains above a relatively level wasteland; 30 blocks of solid earth. */
    public static int surfaceY(int x, int z, long seed) {
        int wx = x + (int)(56 * noise(x, z, 650, seed ^ 0x619AL));
        int wz = z + (int)(56 * noise(x, z, 650, seed ^ 0xC9A1L));
        double broad = noise(wx, wz, 620, seed ^ 0x4E1DL) * 12.0;
        double rolling = noise(wx, wz, 215, seed ^ 0xB4A9L) * 5.0;
        double fine = noise(x, z, 75, seed ^ 0x9F1CL) * 2.4;
        // A small fraction of the terrain rises into mesas and low dark mountains.
        double peakNoise = noise(wx, wz, 440, seed ^ 0xCC11L);
        double lowMountain = Math.pow(Math.max(0, peakNoise - 0.15) / 0.85, 2) * 21.0;
        return 67 + (int)Math.round(broad + rolling + fine + lowMountain);
    }

    public static int bottomY(int x, int z, long seed) {
        return surfaceY(x, z, seed) - VERTICAL_THICKNESS + 1;
    }

    /**
     * Bottomless basins and branching erosion channels, defined by continuous
     * world-coordinate noise rather than regularly spaced rectangular cells.
     * Values use absolute coordinates so changing chunk loading order cannot
     * shift their boundaries. At distant radii all terrain vanishes.
     */
    public static boolean hasLand(int x, int z, long seed) {
        double distance = Math.hypot((double) x, (double) z);
        if (distance < FRACTURE_START) return true;
        if (distance >= COMPLETE_VOID) return false;
        double p = (distance - FRACTURE_START) / (COMPLETE_VOID - FRACTURE_START);

        // Two independent slow-changing fields bend crater outlines and faults,
        // breaking the visible horizontal/vertical lattice of value noise.
        int warpedX = x + (int)(425.0 * noise(x, z, 1379, seed ^ 0x6E4A11L)
                                + 135.0 * noise(x, z, 451, seed ^ 0xA37B91L));
        int warpedZ = z + (int)(425.0 * noise(x, z, 1379, seed ^ 0xA80CE5L)
                                + 135.0 * noise(x, z, 451, seed ^ 0x35D849L));

        // Overlapping broad basins (few hundred to several thousand blocks),
        // with branching small depressions rather than one pit per grid cell.
        double basin = 0.55 * noise(warpedX, warpedZ, 1241, seed ^ 0x735D1AL)
                     + 0.27 * noise(warpedX, warpedZ, 593, seed ^ 0xE13A62L)
                     + 0.18 * noise(warpedX, warpedZ, 277, seed ^ 0x1AC729L);
        double basinLimit = -0.80 + 1.55 * Math.pow(p, 1.43);
        if (basin < basinLimit) return false;

        // Meandering jagged canyons expand and join the basins as distance
        // increases. They have no 16-block or 680-block spacing.
        double channel = noise(warpedX, warpedZ, 397, seed ^ 0xC25F81L)
                       + 0.23 * noise(warpedX, warpedZ, 117, seed ^ 0x939BA1L);
        double crackWidth = 0.02 + 0.27 * Math.pow(p, 1.8);
        return Math.abs(channel) >= crackWidth;
    }

    /** Geographic provinces of soul sand, soul soil and blackstone. */
    public static double geologicalRegion(int x, int z, long seed) {
        double dx = 300 * noise(x, z, 860, seed ^ 0x813AL);
        double dz = 300 * noise(x, z, 860, seed ^ 0xAA51L);
        int nx = x + (int)dx, nz = z + (int)dz;
        return noise(nx, nz, 1650, seed ^ 0x32F5L) * 0.75
                + noise(nx, nz, 3200, seed ^ 0x1456L) * 0.25;
    }

    /** Checks the actual site footprint so structures cannot hover over a pit. */
    public static boolean structureSite(int x, int z, long seed, int radius) {
        int centerY = surfaceY(x, z, seed);
        for (int dx = -radius; dx <= radius; dx += 7) {
            for (int dz = -radius; dz <= radius; dz += 7) {
                int px = x + dx, pz = z + dz;
                if (!hasLand(px, pz, seed)) return false;
                if (Math.abs(surfaceY(px, pz, seed) - centerY) > 16) return false;
            }
        }
        return true;
    }

    static double noise(int x, int z, int scale, long seed) {
        int x0 = Math.floorDiv(x, scale), z0 = Math.floorDiv(z, scale);
        double fx = smooth(Math.floorMod(x, scale) / (double)scale);
        double fz = smooth(Math.floorMod(z, scale) / (double)scale);
        double a = hash(x0, z0, seed), b = hash(x0+1, z0, seed);
        double c = hash(x0, z0+1, seed), d = hash(x0+1, z0+1, seed);
        return (a + (b-a)*fx)*(1-fz) + (c+(d-c)*fx)*fz;
    }

    private static double smooth(double v) { return v*v*(3-2*v); }
    static long mix(long v) {
        v ^= v >>> 30; v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 27; v *= 0x94D049BB133111EBL;
        v ^= v >>> 31;
        return v;
    }
    private static double hash(long x, long z, long seed) {
        long v = mix(seed ^ x*0x9E3779B97F4A7C15L ^ z*0xC2B2AE3D27D4EB4FL);
        return ((v >>> 11)/(double)(1L << 53))*2.0-1.0;
    }
}
