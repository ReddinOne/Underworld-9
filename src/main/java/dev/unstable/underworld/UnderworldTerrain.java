package dev.unstable.underworld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.HashSet;


/**
 * Version 3.4: bounded on-tick terrain generation from an EMPTY flat dimension.
 * The Nether is never accessed/mutated here. Existing v1/v2 Underworld chunks
 * are erased once and regenerated with the v3 layout; player builds in the
 * Underworld are NOT preserved during migration. Back up the world first.
 */
public final class UnderworldTerrain {
    private static final int VERSION_MARKER_Y = -187;
    private static final BlockState VERSION_MARKER = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SOUL_SAND = Blocks.SOUL_SAND.defaultBlockState();
    private static final BlockState SOUL_SOIL = Blocks.SOUL_SOIL.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    private static final BlockState NETHERRACK = Blocks.NETHERRACK.defaultBlockState();

    // Work stays on the server thread; never modify loaded chunks on an async thread.
    // At most two chunk jobs per tick, with a soft 8 ms budget between jobs.
    // An individual chunk can still exceed this budget (no mid-chunk interruption).
    private static final int MAX_JOBS_PER_TICK = 2;
    private static final long SOFT_BUDGET_NS = 8_000_000L;
    private record Job(ServerLevel level, LevelChunk chunk) { }
    private static final ArrayDeque<Job> PENDING = new ArrayDeque<>();
    private static final HashSet<LevelChunk> QUEUED = new HashSet<>();

    private UnderworldTerrain() { }

    /** Chunk load only schedules work, rather than blocking for every chunk. */
    public static void enqueue(ServerLevel world, LevelChunk chunk) {
        if (!world.dimension().equals(UnderworldMod.UNDERWORLD)) return;
        if (isCurrent(chunk)) return;
        if (QUEUED.add(chunk)) PENDING.addLast(new Job(world, chunk));
    }

    /** Do not mutate chunks after Fabric announces that they are unloading. */
    public static void unload(ServerLevel world, LevelChunk chunk) {
        if (!world.dimension().equals(UnderworldMod.UNDERWORLD)) return;
        if (QUEUED.remove(chunk)) {
            PENDING.removeIf(job -> job.chunk() == chunk);
        }
    }

    /** Complete the destination chunk before teleporting a falling entity into it. */
    public static void ensureReady(ServerLevel world, LevelChunk chunk) {
        if (!world.dimension().equals(UnderworldMod.UNDERWORLD)) return;
        if (QUEUED.remove(chunk)) PENDING.removeIf(job -> job.chunk() == chunk);
        decorate(world, chunk);
    }

    /** Called once per server tick. Limits bursts from new chunk loads. */
    public static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        ServerLevel active = server.getLevel(UnderworldMod.UNDERWORLD);
        int finished = 0;
        while (finished < MAX_JOBS_PER_TICK && !PENDING.isEmpty()) {
            Job job = PENDING.removeFirst();
            if (!QUEUED.remove(job.chunk())) continue;
            if (job.level() != active) continue;
            decorate(job.level(), job.chunk());
            finished++;
            if (System.nanoTime() - start >= SOFT_BUDGET_NS) break;
        }
    }

    public static int pendingCount() { return QUEUED.size(); }

    private static boolean isCurrent(LevelChunk chunk) {
        int x = chunk.getPos().getMinBlockX() + 1;
        int z = chunk.getPos().getMinBlockZ() + 1;
        return chunk.getBlockState(new BlockPos(x, VERSION_MARKER_Y, z)).equals(VERSION_MARKER);
    }

    public static void decorate(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(UnderworldMod.UNDERWORLD)) return;

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos marker = new BlockPos(startX + 1, VERSION_MARKER_Y, startZ + 1);
        if (chunk.getBlockState(marker).equals(VERSION_MARKER)) return;

        long seed = level.getSeed() ^ 0x4AD8C9BF0521DA5BL;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // A whole 16x16 chunk far beyond COMPLETE_VOID cannot contain terrain
        // or valid structure sites. Avoid hundreds of noise evaluations there.
        int nearestX = startX > 0 ? startX : (startX + 15 < 0 ? startX + 15 : 0);
        int nearestZ = startZ > 0 ? startZ : (startZ + 15 < 0 ? startZ + 15 : 0);
        boolean emptyVoid = Math.hypot((double) nearestX, (double) nearestZ)
                >= UnderworldMath.COMPLETE_VOID;

        // Newly created chunks from the empty FLAT generator cost almost
        // nothing to clear. Older v1/v2 chunks still have Overworld-derived
        // blocks, ores and caves, so we clear the WHOLE column before painting.
        boolean v32 = chunk.getBlockState(pos.set(startX + 1, -186, startZ + 1)).is(Blocks.RESPAWN_ANCHOR);
        boolean v31 = chunk.getBlockState(pos.set(startX + 1, -185, startZ + 1)).is(Blocks.LODESTONE);
        boolean v3 = chunk.getBlockState(pos.set(startX + 1, -184, startZ + 1)).is(Blocks.CRYING_OBSIDIAN);
        boolean legacy = !v3 && !v31 && !v32 && isLegacy(chunk, startX, startZ, pos);
        if (legacy) {
            for (int y = -192; y < 320; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        pos.set(startX + lx, y, startZ + lz);
                        if (!chunk.getBlockState(pos).isAir()) {
                            level.setBlock(pos, AIR, 2);
                        }
                    }
                }
            }
        }

        // Replace v3.0/v3.1/v3.2 terrain and structures once. The old marker is removed
        // and every former v3 structure block is cleared before drawing v3.3.
        if (v3 || v31 || v32) {
            for (int y = 12; y <= 152; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        pos.set(startX + lx, y, startZ + lz);
                        if (!chunk.getBlockState(pos).isAir()) level.setBlock(pos, AIR, 2);
                    }
                }
            }
            if (v32) level.setBlock(new BlockPos(startX + 1, -186, startZ + 1), AIR, 2);
            if (v3) level.setBlock(new BlockPos(startX + 1, -184, startZ + 1), AIR, 2);
            if (v31) level.setBlock(new BlockPos(startX + 1, -185, startZ + 1), AIR, 2);
        }

        if (!emptyVoid) for (int lx = 0; lx < 16; lx++) {
            int x = startX + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = startZ + lz;
                if (!UnderworldMath.hasLand(x, z, seed)) continue;

                int top = UnderworldMath.surfaceY(x, z, seed);
                int bottom = top - UnderworldMath.VERTICAL_THICKNESS + 1;
                double region = UnderworldMath.geologicalRegion(x, z, seed);
                double detail = UnderworldMath.noise(x, z, 90, seed ^ 0xD34AL);

                for (int y = bottom; y <= top; y++) {
                    BlockState state;
                    int below = top - y;
                    if (below <= 3) {
                        // Wide, visually identifiable surface zones.
                        if (region > 0.14) state = BLACKSTONE;
                        else if (region < -0.14) state = SOUL_SAND;
                        else state = (detail > 0.30) ? SOUL_SAND : SOUL_SOIL;
                    } else if (below <= 9) {
                        if (region > 0.14) state = detail > 0.68 ? BASALT : BLACKSTONE;
                        else if (region < -0.14) state = SOUL_SOIL;
                        else state = SOUL_SOIL;
                    } else {
                        if (region > 0.14) state = BLACKSTONE;
                        else if (region < -0.14) state = (detail < -0.72 ? NETHERRACK : SOUL_SOIL);
                        else state = detail > 0.15 ? BASALT : BLACKSTONE;
                    }
                    pos.set(x, y, z);
                    // ServerLevel.setBlock sends updates to clients even when the
                    // chunk was already sent before its queued decoration runs.
                    // It also maintains lighting and heightmaps correctly.
                    if (!chunk.getBlockState(pos).equals(state)) {
                        level.setBlock(pos, state, 2);
                    }
                }
            }
        }

        // Multi-chunk ruins are computed per chunk, with no recursive loading.
        if (!emptyVoid) UnderworldStructures.generate(level, chunk, seed);
        level.setBlock(marker, VERSION_MARKER, 2);
        chunk.markUnsaved();
    }

    /** Detect previous versions without scanning every new empty chunk. */
    private static boolean isLegacy(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos p) {
        // v2 saved a gilded-blackstone marker at -182.
        if (chunk.getBlockState(p.set(x + 1, -182, z + 1)).is(Blocks.GILDED_BLACKSTONE)) return true;
        // Old Overworld-generated chunks have bedrock at -64 or terrain at Y~64.
        int[] xs = {2, 7, 13};
        int[] zs = {2, 8, 13};
        int[] ys = {-64, -63, 0, 40, 65, 80, 100};
        for (int dx : xs) for (int dz : zs) for (int y : ys) {
            if (!chunk.getBlockState(p.set(x + dx, y, z + dz)).isAir()) return true;
        }
        return false;
    }
}
