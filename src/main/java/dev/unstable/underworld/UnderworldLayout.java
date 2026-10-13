package dev.unstable.underworld;

/** Pure Java, world-seed-stable chest layout shared by all chunk slices.
 * May be tested without any Fabric/Minecraft dependencies. */
public final class UnderworldLayout {
    public static final int CHESTS_PER_RUIN = 6;
    private UnderworldLayout() { }

    public static int radius(int kind) {
        return switch (kind) {
            case 0 -> 25; case 1 -> 22; case 2 -> 16; case 3 -> 30;
            case 4 -> 20; case 5 -> 25; case 6 -> 34; default -> 27;
        };
    }

    public static boolean insideFloor(int kind, int x, int z) {
        int ax=Math.abs(x), az=Math.abs(z);
        return switch(kind) {
            case 0 -> ax<=25 && az<=9;
            case 1 -> x*x+z*z<=22*22;
            case 2 -> ax<=16 && az<=16;
            case 3 -> ax<=30 && az<=30;
            case 4 -> ax<=20 && az<=20;
            case 5 -> ax<=25 && az<=25;
            case 6 -> ax<=34 && az<=14;
            default -> ax<=27 && az<=27;
        };
    }
    private static int chestOffset(int kind) {
        return switch (kind) {
            case 0 -> 13; case 1 -> 12; case 2 -> 8; case 3 -> 17;
            case 4 -> 11; case 5 -> 13; case 6 -> 17; default -> 16;
        };
    }
    public static int chestX(int kind, int slot) {
        int d = chestOffset(kind);
        return switch (slot) {
            case 0 -> -d; case 1 -> d; case 2 -> -d;
            case 3 -> d; case 4 -> -d + 5; default -> d - 5;
        };
    }
    public static int chestZ(int kind, int slot) {
        return switch (slot) {
            case 0 -> 3; case 1 -> -3; case 2 -> -3;
            case 3 -> 3; case 4 -> 8; default -> -8;
        };
    }
    /** Independently seeded portal chance: 200/10000 = 2% in every ruin family. */
    public static boolean portalRoll(long siteHash) {
        return Math.floorMod(UnderworldMath.mix(siteHash ^ 0x74511B028L), 10_000) < 200;
    }

    public static boolean chestRoll(long siteHash, int kind, int slot) {
        if (slot < 0 || slot >= CHESTS_PER_RUIN) return false;
        int[] chances = {85, 77, 64, 53, 39, 31};
        long h = UnderworldMath.mix(siteHash ^ ((long) (slot + 1) * 0x9E3779B97F4A7C15L));
        int bonus = kind == 3 || kind == 5 ? 8 : 0;
        return Math.floorMod(h, 100) < chances[slot] + bonus;
    }
    public static int facingX(int kind, int slot) {
        return chestX(kind,slot) > 0 ? -1 : 1;
    }
    /** Returns true when a block would obstruct a chest or its approach.
     * y is local offset above the structure floor (1..3). */
    public static boolean reserved(int kind, int x, int z, int y, long hash) {
        if (y < 1 || y > 3) return false;
        for (int slot = 0; slot < CHESTS_PER_RUIN; slot++) {
            if (!chestRoll(hash,kind,slot)) continue;
            int ax=chestX(kind,slot), az=chestZ(kind,slot);
            if (z==az && (x==ax || x==ax+facingX(kind,slot))) return true;
        }
        return false;
    }
    /** Exactly a binary 50% roll for each accepted ruin's stable hash. */
    public static boolean guardianRoll(long siteHash) {
        return (UnderworldMath.mix(siteHash ^ 0x6DC7854BF302AC19L) & 1L) == 0L;
    }

}
