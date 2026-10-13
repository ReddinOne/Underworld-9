package dev.unstable.underworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Underworld 3.6.1 for Java 26.3 / Fabric.
 *
 * The vanilla Nether terrain is NEVER generated, modified or replaced here.
 * Nether (X,Z,Y < -10) -> Underworld (same X,Z; Y 296), still falling.
 * V3 terrain sits near Y=67 and is only 30 blocks thick; void expands outward.
 * Underworld falling into the lower void -> vanilla void damage/death.
 * Underworld crossing the upper boundary (Y > 315) -> Nether Y=-7.
 * Underworld rare obsidian portal -> Overworld surface (custom redirect).
 * Nether remains physically unchanged. Fall from Y=-7 can re-enter Underworld.
 *
 * Per-player original gamemode is persisted in the player's vanilla entity
 * tags, surviving save/relog; it is restored upon leaving the Underworld.
 */
public final class UnderworldMod implements ModInitializer {
    public static final String MOD_ID = "underworld";
    public static final ResourceKey<Level> UNDERWORLD = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath(MOD_ID, "underworld"));

    private static final double NETHER_ENTRY_Y = -10.0;
    private static final double UNDERWORLD_DROP_Y = 296.0;
    private static final double UNDERWORLD_UPPER_EXIT_Y = 315.0;
    private static final double NETHER_RETURN_Y = -7.0;
    private static final String MODE_TAG_PREFIX = "underworld.prevmode.";
    private static int portalCheckTicker = 0;

    @Override
    public void onInitialize() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (world.dimension().equals(UNDERWORLD)) {
                UnderworldTerrain.enqueue(world, chunk);
                UnderworldGuardians.enqueue(world, chunk);
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (world.dimension().equals(UNDERWORLD)) {
                UnderworldTerrain.unload(world, chunk);
                UnderworldGuardians.unload(world, chunk);
            }
        });

        // Additional protection in case another mod alters Adventure permissions.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !world.dimension().equals(UNDERWORLD));

        ServerTickEvents.END_SERVER_TICK.register(UnderworldMod::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("underworld")
                    .then(Commands.literal("enter")
                            .executes(ctx -> commandTeleport(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("return")
                            .executes(ctx -> commandTeleport(ctx.getSource().getPlayerOrException(), false)))
                    .then(Commands.literal("queue")
                            .executes(ctx -> {
                                ctx.getSource().getPlayerOrException().sendSystemMessage(
                                        Component.literal("Underworld chunks waiting: " +
                                                UnderworldTerrain.pendingCount()));
                                return 1;
                            })));
        });
    }

    private static int commandTeleport(ServerPlayer player, boolean enter) {
        MinecraftServer server = player.level().getServer();
        ServerLevel target = server == null ? null : server.getLevel(enter ? UNDERWORLD : Level.NETHER);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Underworld dimension unavailable. Check datapack logs."));
            return 0;
        }
        boolean success = enter ? enterUnderworld(player, target) : returnToNether(player, target);
        if (success) player.sendSystemMessage(Component.literal(enter
                ? "The void opens beneath you..." : "Returned to the Nether."));
        return success ? 1 : 0;
    }

    private static void tick(MinecraftServer server) {
        // Complete a bounded number of queued chunk jobs before entity checks.
        UnderworldTerrain.tick(server);
        UnderworldGuardians.tick(server);
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerLevel underworld = server.getLevel(UNDERWORLD);
        if (nether == null || underworld == null) return;

        boolean scanPortals = (++portalCheckTicker % 5) == 0;
        List<Entity> entering = new ArrayList<>();
        for (Entity e : nether.getAllEntities()) {
            if (!e.isRemoved() && !e.isPassenger() && e.getY() < NETHER_ENTRY_Y) entering.add(e);
        }
        List<Entity> leaving = new ArrayList<>();
        List<Entity> usingPortal = new ArrayList<>();
        for (Entity e : underworld.getAllEntities()) {
            if (!e.isRemoved() && !e.isPassenger() && e.getY() > UNDERWORLD_UPPER_EXIT_Y) {
                leaving.add(e);
            } else if (scanPortals && !e.isRemoved() && !e.isPassenger()
                    && touchingForgottenPortal(underworld, e)) {
                usingPortal.add(e);
            }
        }

        for (Entity e : entering) enterUnderworld(e, underworld);
        for (Entity e : leaving) returnToNether(e, nether);
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            for (Entity e : usingPortal) goToOverworld(e, overworld);
        }

        // Never rescue downward-falling entities: the Underworld has no lower portal.
        // Minecraft handles normal void damage/death below the dimension floor.
        // Repair mode on entry/relog and after external dimension changes/death.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(UNDERWORLD)) {
                setAdventureMode(player);
            } else {
                restoreOriginalMode(player);
            }
        }
    }

    private static boolean enterUnderworld(Entity entity, ServerLevel dest) {
        if (entity.level().dimension().equals(UNDERWORLD)) return false;
        double x = entity.getX();
        double z = entity.getZ();
        Vec3 motion = entity.getDeltaMovement();
        // Chunk load triggers the independent Underworld geology. No landing search,
        // no fake spawn platform, no teleport to the origin or another X/Z position.
        UnderworldTerrain.ensureReady(dest, dest.getChunk(
                BlockPos.containing(x, 0, z).getX() >> 4,
                BlockPos.containing(x, 0, z).getZ() >> 4));
        double y = UNDERWORLD_DROP_Y;
        if (entity instanceof ServerPlayer player) {
            saveOriginalMode(player);
        }
        // The final bool uses the same native 26.3 teleport API as version 1.
        boolean moved = entity.teleportTo(dest, x, y, z,
                Set.of(), entity.getYRot(), entity.getXRot(), true);
        if (moved) {
            // Dimension teleports may otherwise zero out movement.
            entity.setDeltaMovement(new Vec3(motion.x, Math.min(-1.0, motion.y), motion.z));
            // Avoid inheriting a huge fall-distance counter from the Nether.
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) {
                setAdventureMode(player);
            }
            // Do NOT grant resistance: the initial fall must cause vanilla fall damage.
            // Fall distance is reset on teleport, then accumulates naturally in the Underworld.
        } else if (entity instanceof ServerPlayer player) {
            restoreOriginalMode(player);
        }
        return moved;
    }

    /** Exact Y=-7, original X and Z. An immediate fall and re-entry is intentional. */
    private static boolean returnToNether(Entity entity, ServerLevel nether) {
        boolean moved = entity.teleportTo(nether,
                entity.getX(), NETHER_RETURN_Y, entity.getZ(),
                Set.of(), entity.getYRot(), entity.getXRot(), true);
        if (moved) {
            entity.setDeltaMovement(new Vec3(0, -0.15, 0));
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) restoreOriginalMode(player);
        }
        return moved;
    }

    /** The mod intercepts these physical portal blocks BEFORE vanilla portal warmup. */
    private static boolean touchingForgottenPortal(ServerLevel level, Entity entity) {
        BlockPos feet = BlockPos.containing(entity.getX(), entity.getY() + 0.3, entity.getZ());
        return level.getBlockState(feet).is(Blocks.NETHER_PORTAL)
                || level.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL);
    }

    private static boolean goToOverworld(Entity entity, ServerLevel overworld) {
        BlockPos landing = overworldLanding(overworld,
                (int)Math.floor(entity.getX()), (int)Math.floor(entity.getZ()));
        if (landing == null) return false;
        boolean moved = entity.teleportTo(overworld,
                landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                Set.of(), entity.getYRot(), entity.getXRot(), true);
        if (moved) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) restoreOriginalMode(player);
        }
        return moved;
    }

    /** Exit on an existing solid surface; do not create or edit Overworld blocks. */
    private static BlockPos overworldLanding(ServerLevel world, int x, int z) {
        for (int radius = 0; radius <= 24; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos(x + dx, 0, z + dz));
                    BlockPos ground = top.below();
                    if (world.getBlockState(ground).isSolid()
                            && !world.getBlockState(ground).is(Blocks.MAGMA_BLOCK)
                            && !world.getBlockState(ground).is(Blocks.CACTUS)
                            && world.getBlockState(top).isAir()
                            && world.getBlockState(top.above()).isAir()) return top;
                }
            }
        }
        return null;
    }

    private static void saveOriginalMode(ServerPlayer player) {
        if (previousModeTag(player) != null) return;
        player.addTag(MODE_TAG_PREFIX + player.gameMode().getName());
    }

    private static void setAdventureMode(ServerPlayer player) {
        saveOriginalMode(player);
        if (player.gameMode() != GameType.ADVENTURE) player.setGameMode(GameType.ADVENTURE);
    }

    private static void restoreOriginalMode(ServerPlayer player) {
        String tag = previousModeTag(player);
        if (tag == null) return;
        String saved = tag.substring(MODE_TAG_PREFIX.length());
        GameType original = switch (saved) {
            case "creative" -> GameType.CREATIVE;
            case "spectator" -> GameType.SPECTATOR;
            case "adventure" -> GameType.ADVENTURE;
            default -> GameType.SURVIVAL;
        };
        player.setGameMode(original);
        player.removeTag(tag);
    }

    private static String previousModeTag(ServerPlayer player) {
        for (String tag : player.entityTags()) {
            if (tag.startsWith(MODE_TAG_PREFIX)) return tag;
        }
        return null;
    }

}
