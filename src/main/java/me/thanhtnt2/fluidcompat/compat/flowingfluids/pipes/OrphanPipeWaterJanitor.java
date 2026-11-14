package me.thanhtnt2.fluidcompat.compat.flowingfluids.pipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.core.balance.AutoBalance;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Removes the water left inside a waterlogged pipe when no neighbouring block outside the pipe network holds water
 * any more. This is what frees the water that stays stuck at the end of a pipe after the pond around it was pumped
 * dry. Water is only ever deleted here, never created, so it can lose at most one bucket per cleaned pipe and can
 * never duplicate water.
 *
 * <p>Only loaded chunks near players are scanned, and only every few ticks. A scan is first turned into a list of
 * chunks and then worked off a few chunks per tick, so the cost never lands in a single tick.
 */
final class OrphanPipeWaterJanitor {

    // Upper bound of chunks examined in one tick: a full 81-chunk scan is spread over about ten ticks
    private static final int CHUNKS_PER_TICK = 8;
    // Chunks differ hugely in cost, so the work is also limited by block entities visited
    private static final int BLOCK_ENTITIES_PER_TICK = 2048;

    private final Set<Block> pipes;
    // Chunks still waiting for the current scan, per dimension. Plain chunk coordinates, never a level
    private final Map<ResourceKey<Level>, LongArrayFIFOQueue> pendingChunks = new HashMap<>();

    private OrphanPipeWaterJanitor(Set<Block> pipes) {
        this.pipes = pipes;
    }

    static void register(Set<Block> pipes) {
        OrphanPipeWaterJanitor janitor = new OrphanPipeWaterJanitor(pipes);
        MinecraftForge.EVENT_BUS.addListener(janitor::onLevelTick);
        // The integrated server is restarted inside one game session, so leftover coordinates are dropped on shutdown
        MinecraftForge.EVENT_BUS.addListener(janitor::onServerStopped);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        pendingChunks.clear();
    }

    private void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        try {
            LongArrayFIFOQueue pending = pendingChunks.get(level.dimension());
            if (pending != null && !pending.isEmpty()) {
                cleanPendingChunks(level, pending);
            } else if (isScanDue(level)) {
                cleanPendingChunks(level, startScan(level));
            }
        } catch (RuntimeException exception) {
            // A cleanup problem must never crash the server tick; dropping the list avoids repeating the failure every tick
            pendingChunks.remove(level.dimension());
            FluidCompatLog.errorThrottled("orphan-pipe-janitor", "Orphan pipe water cleanup failed", exception);
        }
    }

    private boolean isScanDue(ServerLevel level) {
        int baseInterval = FluidCompatModulesConfig.FF_PIPES_CLEANUP_INTERVAL_TICKS.get();
        // Cheap exit for almost every tick; the stretched interval below is always a multiple of the base interval
        if (level.getGameTime() % baseInterval != 0L) {
            return false;
        }
        // Auto Balanced Compat: the cleanup is optional housekeeping, so it waits longer while the server lags
        int interval = AutoBalance.scaleTicks(level.getServer(), baseInterval);
        return level.getGameTime() % interval == 0L;
    }

    /** Lists the chunks around every player once (a shared chunk only once) and returns the queue to work off. */
    private LongArrayFIFOQueue startScan(ServerLevel level) {
        LongArrayFIFOQueue pending = pendingChunks.computeIfAbsent(level.dimension(), dimension -> new LongArrayFIFOQueue());
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return pending;
        }
        int radius = FluidCompatModulesConfig.FF_PIPES_CLEANUP_CHUNK_RADIUS.get();
        LongSet queued = new LongOpenHashSet();
        for (ServerPlayer player : players) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long chunk = ChunkPos.asLong(center.x + dx, center.z + dz);
                    if (queued.add(chunk)) {
                        pending.enqueue(chunk);
                    }
                }
            }
        }
        return pending;
    }

    private void cleanPendingChunks(ServerLevel level, LongArrayFIFOQueue pending) {
        int cleaned = 0;
        // Housekeeping steps back while the server lags: the same slowdown that stretches the scan interval
        CleanupBudget budget = new CleanupBudget(CHUNKS_PER_TICK, BLOCK_ENTITIES_PER_TICK, AutoBalance.slowdown(level.getServer()));
        while (!pending.isEmpty() && budget.mayTakeChunk()) {
            long chunk = pending.dequeueLong();
            cleaned += cleanChunk(level, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), budget);
        }
        if (cleaned > 0) {
            FluidCompatLog.debug("[flowing_fluids_pipes] cleaned {} orphan waterlogged pipe(s) in {}", cleaned, level.dimension().location());
        }
    }

    private int cleanChunk(ServerLevel level, int chunkX, int chunkZ, CleanupBudget budget) {
        // getChunkNow never loads or generates a chunk; a chunk that unloaded while it waited is simply skipped
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null || chunk.getBlockEntities().isEmpty()) {
            budget.tookChunk(0);
            return 0;
        }
        budget.tookChunk(chunk.getBlockEntities().size());
        int cleaned = 0;
        for (BlockPos pos : findOrphans(level, chunk)) {
            cleaned += drainPipe(level, pos);
        }
        return cleaned;
    }

    private List<BlockPos> findOrphans(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> orphans = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockState state = blockEntity.getBlockState();
            if (isWaterloggedPipe(state) && !hasWaterOutsidePipes(level, blockEntity.getBlockPos())) {
                orphans.add(blockEntity.getBlockPos().immutable());
            }
        }
        return orphans;
    }

    private boolean isWaterloggedPipe(BlockState state) {
        return pipes.contains(state.getBlock())
                && state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private boolean hasWaterOutsidePipes(ServerLevel level, BlockPos pipePos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pipePos.relative(direction);
            if (!level.hasChunkAt(neighbour)) {
                return true; // unknown neighbour: keep the water rather than guess
            }
            // Pipes next to pipes would keep each other "wet" forever, so they do not count as a water source
            if (pipes.contains(level.getBlockState(neighbour).getBlock())) {
                continue;
            }
            if (level.getFluidState(neighbour).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private int drainPipe(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!isWaterloggedPipe(current)) {
            return 0;
        }
        level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        FluidCompatLog.trace("Cleaned orphan pipe water block={} pos={}", BuiltInRegistries.BLOCK.getKey(current.getBlock()), pos.toShortString());
        return 1;
    }
}
