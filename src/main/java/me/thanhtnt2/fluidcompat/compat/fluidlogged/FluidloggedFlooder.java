package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.api.DrainBackendIds;
import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import traben.flowing_fluids.api.FlowingFluidsAPI;

/**
 * Floods the listed blocks with water from the Flowing Fluids water next to them, thin layers included.
 *
 * <p>Flowing Fluids only moves water into a waterlogged cell when the water it gathers there adds up to a full
 * block, and Fluidlogged only floods a block through vanilla spreading, which Flowing Fluids replaces. So a listed
 * block sitting beside a thin pond stays dry even though water is right next to it. Here the block is flooded the
 * moment the connected water next to it adds up to one block (8 levels) and exactly those 8 levels are taken out of
 * the world first, all or nothing, so water is never made.
 *
 * <p>Blocks with a waterlogged state use that state, which is also what Flowing Fluids and the Alex's Caves Drain
 * understand. Others get the water through Fluidlogged's own storage. A cell that already holds any fluid is never
 * flooded again, so the two storages cannot add up to double.
 *
 * <p>Finding the blocks costs nothing while they are idle: they are registered when a chunk loads (a cheap palette
 * check per chunk section) and when one is placed, and only the registered positions are looked at, a few per pass.
 */
public final class FluidloggedFlooder {

    private static final String HOOK_ID = "fluidlogged.flood";
    private static final int LEVELS_FOR_A_BLOCK = 8;
    private static final int CHUNKS_SCANNED_PER_TICK = 2;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** What one level keeps between ticks: no Level object, only keys and numbers. */
    private static final class LevelState {
        final FloodQueue queue = new FloodQueue();
        final Queue<Long> chunksToScan = new ConcurrentLinkedQueue<>();
        long nextPassTick;
    }

    // Chunk events may arrive from another thread than the level tick, so the map and the queues are thread safe
    private final Map<ResourceKey<Level>, LevelState> states = new ConcurrentHashMap<>();

    /** Keeps the Flowing Fluids API wrapper: asking for it writes a log line each time. */
    private static final class FlowingFluidsHolder {
        static final FlowingFluidsAPI API = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID);
    }

    // ------------------------------------------------------------------ finding the blocks

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor level = event.getLevel();
        if (level instanceof ServerLevel server && isActive()) {
            state(server).chunksToScan.add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        long chunkKey = event.getChunk().getPos().toLong();
        LevelState state = states.get(server.dimension());
        if (state != null) {
            state.queue.removeIf(position -> chunkOf(position) == chunkKey);
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel server && isActive() && FloodBlocks.contains(event.getPlacedBlock())) {
            state(server).queue.add(event.getPos().asLong());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        states.clear();
    }

    private static long chunkOf(long blockPosition) {
        return ChunkPos.asLong(BlockPos.getX(blockPosition) >> 4, BlockPos.getZ(blockPosition) >> 4);
    }

    private LevelState state(ServerLevel level) {
        return states.computeIfAbsent(level.dimension(), key -> new LevelState());
    }

    private static boolean isActive() {
        try {
            return CompatGate.masterEnabled() && FluidCompatModulesConfig.FLUIDLOGGED_ENABLED.get() && FloodBlocks.isAnyListed();
        } catch (RuntimeException configNotLoaded) {
            return false;
        }
    }

    // ------------------------------------------------------------------ the pass

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !isActive()) {
            return;
        }
        try {
            LevelState state = state(level);
            scanPendingChunks(level, state);
            if (level.getGameTime() < state.nextPassTick) {
                return;
            }
            state.nextPassTick = level.getGameTime() + FluidCompatModulesConfig.FLUIDLOGGED_FLOOD_INTERVAL_TICKS.get();
            for (long position : state.queue.nextBatch(FluidCompatModulesConfig.FLUIDLOGGED_MAX_BLOCKS_PER_PASS.get())) {
                checkPosition(level, state, BlockPos.of(position));
            }
        } catch (RuntimeException | LinkageError error) {
            FluidCompatLog.errorThrottled("fluidlogged-pass", "Fluidlogged flooding pass failed", error);
        }
    }

    // A chunk is looked at a few ticks after it loaded, spread out so a burst of chunks never makes one slow tick
    private void scanPendingChunks(ServerLevel level, LevelState state) {
        for (int scanned = 0; scanned < CHUNKS_SCANNED_PER_TICK; scanned++) {
            Long chunkKey = state.chunksToScan.poll();
            if (chunkKey == null) {
                return;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk != null) {
                registerListedBlocks(level, chunk, state.queue);
            }
        }
    }

    private static void registerListedBlocks(ServerLevel level, LevelChunk chunk, FloodQueue queue) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            // The palette answers "might this section hold a listed block?" without looking at a single block
            if (section.hasOnlyAir() || !section.maybeHas(FloodBlocks::contains)) {
                continue;
            }
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (FloodBlocks.contains(section.getBlockState(x, y, z))) {
                            queue.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ one block

    private static void checkPosition(ServerLevel level, LevelState state, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return; // the unload event removes it; until then there is nothing to look at
        }
        BlockState block = level.getBlockState(pos);
        if (!FloodBlocks.contains(block)) {
            state.queue.remove(pos.asLong());
            return;
        }
        if (level.getFluidState(pos).isEmpty()) {
            tryFlood(level, pos, block);
        }
    }

    private static void tryFlood(ServerLevel level, BlockPos pos, BlockState block) {
        boolean usesWaterloggedState = block.hasProperty(BlockStateProperties.WATERLOGGED)
                && block.getBlock() instanceof SimpleWaterloggedBlock;
        if (!usesWaterloggedState && !FluidloggedStore.isAvailable()) {
            return;
        }
        BlockPos source = adjacentWater(level, pos);
        if (source == null || !canTakeWater(level)) {
            return;
        }
        // All or nothing: Flowing Fluids removes nothing at all when the connected water is below a full block
        int removed = FlowingFluidsHolder.API.removeFluidAmountFromPos(level, source, Fluids.WATER, LEVELS_FOR_A_BLOCK, LEVELS_FOR_A_BLOCK);
        if (removed <= 0) {
            return;
        }
        boolean placed = usesWaterloggedState
                ? level.setBlock(pos, block.setValue(BlockStateProperties.WATERLOGGED, true), 3)
                : FluidloggedStore.storeWater(level, pos);
        report(level, pos, block, source, removed, placed && !level.getFluidState(pos).isEmpty());
    }

    /** A free-standing water cell next to the block, however thin: Flowing Fluids looks for the rest of the body itself. */
    private static BlockPos adjacentWater(ServerLevel level, BlockPos pos) {
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.hasChunkAt(neighbour)) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.getBlock() instanceof LiquidBlock && !neighbourState.getFluidState().isEmpty()
                    && normalised(neighbourState.getFluidState().getType()) == Fluids.WATER) {
                return neighbour;
            }
        }
        return null;
    }

    private static Fluid normalised(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static boolean canTakeWater(ServerLevel level) {
        if (!FluidCompatApi.isReady()) {
            return false;
        }
        FiniteFluidService service = FluidCompatApi.service();
        if (!service.isFinite(Fluids.WATER)) {
            return false;
        }
        Optional<DrainBackend> backend = service.activeBackendFor(level, Fluids.WATER);
        return backend.isPresent() && DrainBackendIds.FLOWING_FLUIDS.equals(backend.get().id());
    }

    private static void report(ServerLevel level, BlockPos pos, BlockState block, BlockPos source, int removed, boolean stored) {
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(block.getBlock()));
        if (!stored) {
            // The levels were already taken; they are lost, never duplicated
            FluidCompatLog.errorThrottled("fluidlogged-lost", "Took " + removed + " levels of water for " + blockId + " at "
                    + pos.toShortString() + " but the block did not accept it; the water is gone, never duplicated", null);
            return;
        }
        if (CompatMixinState.markHookReached(HOOK_ID)) {
            FluidCompatLog.info("[fluidlogged] flooded {} at {} for the first time with {} levels of water taken from {}",
                    blockId, pos.toShortString(), removed, source.toShortString());
        } else {
            FluidCompatLog.debugThrottled("fluidlogged-flood", "Flooded {} at {} with {} levels from {}",
                    blockId, pos.toShortString(), removed, source.toShortString());
        }
    }
}
