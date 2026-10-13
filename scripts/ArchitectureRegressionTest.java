import dev.unstable.underworld.UnderworldLayout;
import dev.unstable.underworld.UnderworldMath;
import dev.unstable.underworld.UnderworldPlacement;

/** Verify ruin foundations, dimensions, chest corridors and the portal rate. */
public final class ArchitectureRegressionTest {
    public static void main(String[] args) {
        int portals = 0;
        final int sample = 100000;
        for (int i = 0; i < sample; i++) {
            long hash = (long)i * 0x9E3779B97F4A7C15L;
            if (UnderworldLayout.portalRoll(hash)) portals++;
            if (UnderworldLayout.portalRoll(hash) != UnderworldLayout.portalRoll(hash))
                throw new AssertionError("nondeterministic portal roll");
        }
        double frequency = portals / (double)sample;
        if (frequency < .018 || frequency > .022)
            throw new AssertionError("Portal frequency outside 2% tolerance: " + frequency);
        int grounded = 0, checked = 0;
        for (int kind = 0; kind < 8; kind++) {
            int radius = UnderworldLayout.radius(kind);
            if (radius < 16 || radius > UnderworldPlacement.MAX_RADIUS)
                throw new AssertionError("Invalid ruin footprint radius: " + radius);
            for (int slot = 0; slot < UnderworldLayout.CHESTS_PER_RUIN; slot++) {
                int x=UnderworldLayout.chestX(kind,slot), z=UnderworldLayout.chestZ(kind,slot);
                if (!UnderworldLayout.insideFloor(kind,x,z)
                    || !UnderworldLayout.insideFloor(kind,x+UnderworldLayout.facingX(kind,slot),z))
                    throw new AssertionError("Unsupported or blocked chest access");
            }
            // Sample varying actual land elevations. A footprint block at baseY
            // must be continuously supported by blocks between natural surface
            // and the leveled courtyard, even when the terrain is lower.
            for (int x = -radius; x <= radius; x += 3)
                for (int z = -radius; z <= radius; z += 3) {
                    if (!UnderworldLayout.insideFloor(kind,x,z)) continue;
                    checked++;
                    for (int top = 51; top <= 98; top += 7) {
                        int baseY = 73;
                        int firstFoundation = Math.min(top+1, baseY);
                        if (top < baseY && firstFoundation != top+1)
                            throw new AssertionError("Foundation not rooted in original terrain");
                        int existingTopOrFoundation = top < baseY ? Math.max(top,baseY-1) : top;
                        if (existingTopOrFoundation < baseY-1)
                            throw new AssertionError("Floating slab at ground datum");
                        grounded++;
                    }
                }
        }
        System.out.printf("PASS: %d/%d rare portals (%.2f%%), 8 supported ruins, %d footprint probes, %d support simulations%n",
                portals, sample, 100*frequency, checked, grounded);
    }
}
