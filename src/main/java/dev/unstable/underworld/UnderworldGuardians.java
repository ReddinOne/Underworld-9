package dev.unstable.underworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * A single persistent, specially equipped Wither Skeleton has an exact 50%
 * deterministic chance to appear at each accepted ruin site.
 *
 * The attempt marker is stored in the CENTER chunk, at y=-188 (outside
 * playable terrain). It survives world saves, even if the skeleton dies.
 * This also supports existing 3.6 ruins without overwriting their blocks.
 * No repeating chunk-load spawn loops and no per-tick scan of all entities.
 * All work runs on the server thread, after UnderworldTerrain has decorated.
 */
final class UnderworldGuardians {
    private static final int ATTEMPT_Y = -188;
    private static final int MAX_ATTEMPTS_PER_TICK = 2;

    private record Job(ServerLevel level, LevelChunk chunk, UnderworldPlacement.Site site) { }
    private static final ArrayDeque<Job> PENDING = new ArrayDeque<>();
    private static final Set<LevelChunk> QUEUED = new HashSet<>();

    private UnderworldGuardians() { }

    /** 50% of independent site hashes; stable across chunk reloads. */
    static boolean shouldSpawn(long siteHash) {
        return UnderworldLayout.guardianRoll(siteHash);
    }

    static void enqueue(ServerLevel world, LevelChunk chunk) {
        if (!world.dimension().equals(UnderworldMod.UNDERWORLD)) return;
        if (QUEUED.contains(chunk)) return;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        // Check only the central chunk of a ruin, not all chunks containing
        // one of its outlying walls. This prevents duplicate spawns.
        long seed = world.getSeed() ^ 0x4AD8C9BF0521DA5BL;
        for (UnderworldPlacement.Site site : UnderworldPlacement.inChunk(minX, minZ, seed)) {
            if ((site.x() >> 4) != (minX >> 4) || (site.z() >> 4) != (minZ >> 4)) continue;
            if (!UnderworldMath.structureSite(site.x(), site.z(), seed,
                    UnderworldStructures.radius(site.kind()))) continue;
            if (chunk.getBlockState(marker(minX, minZ)).is(Blocks.GILDED_BLACKSTONE)) return;
            if (QUEUED.add(chunk)) PENDING.addLast(new Job(world, chunk, site));
            return; // Ruins are spaced hundreds of blocks apart.
        }
    }

    static void unload(ServerLevel world, LevelChunk chunk) {
        if (QUEUED.remove(chunk)) PENDING.removeIf(job -> job.chunk() == chunk);
    }

    static void tick(MinecraftServer server) {
        ServerLevel active = server.getLevel(UnderworldMod.UNDERWORLD);
        if (active == null) return;
        int processed = 0;
        // A single delayed job must not stall the terrain generation queue.
        int toCheck = Math.min(PENDING.size(), MAX_ATTEMPTS_PER_TICK);
        while (toCheck-- > 0) {
            Job job = PENDING.removeFirst();
            if (job.level() != active || !QUEUED.contains(job.chunk())) continue;
            LevelChunk chunk = job.chunk();
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            if (!chunk.getBlockState(new BlockPos(minX + 1, -187, minZ + 1))
                    .is(Blocks.CHISELED_POLISHED_BLACKSTONE)) {
                // Terrain may still be waiting in the v3.4 bounded generation queue.
                PENDING.addLast(job);
                continue;
            }
            QUEUED.remove(chunk);
            if (chunk.getBlockState(marker(minX, minZ)).is(Blocks.GILDED_BLACKSTONE)) continue;
            if (shouldSpawn(job.site().hash())) {
                trySpawn(job.level(), job.chunk(), job.site());
            }
            // Always record the attempt, even if terrain space was insufficient,
            // so reloading a chunk never creates extra guardians.
            active.setBlock(marker(minX, minZ),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
            chunk.markUnsaved();
            processed++;
            if (processed >= MAX_ATTEMPTS_PER_TICK) break;
        }
    }

    private static BlockPos marker(int minX, int minZ) {
        return new BlockPos(minX + 2, ATTEMPT_Y, minZ + 2);
    }

    private static void trySpawn(ServerLevel world, LevelChunk chunk, UnderworldPlacement.Site site) {
        int y = UnderworldMath.surfaceY(site.x(), site.z(),
                world.getSeed() ^ 0x4AD8C9BF0521DA5BL) + 1;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        // Only inspect blocks in the ALREADY-loaded, already-decorated center
        // chunk. Search its courtyard for solid footing and 3 blocks of air.
        BlockPos spawn = null;
        for (int dz = 2; dz <= 13 && spawn == null; dz++) {
            for (int dx = 2; dx <= 13; dx++) {
                int x = minX + dx, z = minZ + dz;
                BlockPos feet = new BlockPos(x, y, z);
                if (!chunk.getBlockState(feet.below()).isSolid()) continue;
                if (!chunk.getBlockState(feet).isAir()
                        || !chunk.getBlockState(feet.above()).isAir()
                        || !chunk.getBlockState(feet.above(2)).isAir()) continue;
                spawn = feet;
                break;
            }
        }
        if (spawn == null) return;

        WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(world, EntitySpawnReason.STRUCTURE);
        if (skeleton == null) return;
        skeleton.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        skeleton.setPersistenceRequired();
        skeleton.setCanPickUpLoot(false);
        skeleton.setItemSlot(EquipmentSlot.HEAD, armor(world, Items.NETHERITE_HELMET));
        skeleton.setItemSlot(EquipmentSlot.CHEST, armor(world, Items.NETHERITE_CHESTPLATE));
        skeleton.setItemSlot(EquipmentSlot.LEGS, armor(world, Items.NETHERITE_LEGGINGS));
        skeleton.setItemSlot(EquipmentSlot.FEET, armor(world, Items.NETHERITE_BOOTS));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
            skeleton.setDropChance(slot, 0.085F);
        }
        world.addFreshEntity(skeleton);
    }

    private static ItemStack armor(ServerLevel world, net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(world.registryAccess().getOrThrow(Enchantments.PROTECTION), 2);
        stack.set(DataComponents.TRIM, new ArmorTrim(
                world.registryAccess().getOrThrow(TrimMaterials.EMERALD),
                world.registryAccess().getOrThrow(TrimPatterns.FLOW)));
        return stack;
    }
}
