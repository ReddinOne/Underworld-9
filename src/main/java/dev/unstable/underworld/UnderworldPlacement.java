package dev.unstable.underworld;

/**
 * Deterministic jittered, rotated Poisson-like structure placement.
 * Does not load chunks and does not change with chunk loading order.
 * The optional thinning and local spacing check remove visible regular rows.
 */
public final class UnderworldPlacement {
    private static final int SPACING = 345;
    public static final int MAX_RADIUS = 36;
    private static final double COS = 0.813733471206735; // unit rotation, not world-aligned
    private static final double SIN = 0.581238193719096;
    private static final double MIN_DISTANCE_SQUARED = 175.0 * 175.0;

    public record Site(int x, int z, int kind, long hash) { }

    private UnderworldPlacement() { }

    static double random01(long hash) {
        return (hash >>> 11) / (double)(1L << 53);
    }

    private static Site candidate(int gx, int gz, long seed) {
        long h = UnderworldMath.mix(seed ^ (long)gx * 0x9E3779B97F4A7C15L
                                     ^ (long)gz * 0xC2B2AE3D27D4EB4FL
                                     ^ 0x17C7D36AFA4F016BL);
        // Not every region hosts a ruin; empty regions are intentional.
        if (Math.floorMod(h, 100) < 30) return null;
        double a = (gx + 0.5 + (random01(UnderworldMath.mix(h ^ 0x10101L)) - 0.5) * 0.96) * SPACING;
        double b = (gz + 0.5 + (random01(UnderworldMath.mix(h ^ 0x70707L)) - 0.5) * 0.96) * SPACING;
        int x = (int)Math.round(a * COS - b * SIN);
        int z = (int)Math.round(a * SIN + b * COS);
        return new Site(x, z, (int)Math.floorMod(h >>> 31, 8), h);
    }

    private static boolean accepted(int gx, int gz, long seed, Site candidate) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Site other = candidate(gx + dx, gz + dz, seed);
                if (other == null) continue;
                double dX = candidate.x - other.x;
                double dZ = candidate.z - other.z;
                if (dX*dX + dZ*dZ < MIN_DISTANCE_SQUARED
                        && (Long.compareUnsigned(other.hash, candidate.hash) > 0
                            || (other.hash == candidate.hash && (dx < 0 || (dx == 0 && dz < 0))))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Returns every site whose footprint MAY intersect this 16x16 chunk. */
    public static java.util.List<Site> inChunk(int minX, int minZ, long seed) {
        int gxMin = Integer.MAX_VALUE, gzMin = Integer.MAX_VALUE;
        int gxMax = Integer.MIN_VALUE, gzMax = Integer.MIN_VALUE;
        // Inverse rotation; include maximum jitter and maximum structure radius.
        for (int wx : new int[]{minX, minX + 15}) {
            for (int wz : new int[]{minZ, minZ + 15}) {
                double a = wx * COS + wz * SIN;
                double b = -wx * SIN + wz * COS;
                gxMin = Math.min(gxMin, (int)Math.floor(a / SPACING));
                gxMax = Math.max(gxMax, (int)Math.floor(a / SPACING));
                gzMin = Math.min(gzMin, (int)Math.floor(b / SPACING));
                gzMax = Math.max(gzMax, (int)Math.floor(b / SPACING));
            }
        }
        java.util.List<Site> result = new java.util.ArrayList<>();
        for (int gx = gxMin - 2; gx <= gxMax + 2; gx++) {
            for (int gz = gzMin - 2; gz <= gzMax + 2; gz++) {
                Site s = candidate(gx, gz, seed);
                if (s != null && accepted(gx, gz, seed, s)
                        && !(minX > s.x + MAX_RADIUS || minX + 15 < s.x - MAX_RADIUS
                             || minZ > s.z + MAX_RADIUS || minZ + 15 < s.z - MAX_RADIUS)) {
                    result.add(s);
                }
            }
        }
        return result;
    }
}
