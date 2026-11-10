package me.thanhtnt2.fluidcompat.compat.alexscaves;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.api.DrainBackendIds;
import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import traben.flowing_fluids.api.FlowingFluidsAPI;

/**
 * Replaces the logic of the Alex's Caves Drain while its fluid is finite.
 *
 * <p>The original sucks fluid out of the world above the Drain and pours fluid out below it, but it counts every
 * liquid cell it touches as one bucket, whatever its level. With Flowing Fluids a pond is full of thin layers, so the
 * original takes a sip from many cells and pours whole blocks, which makes water. This version asks Fluid Compat to
 * drain exactly as many levels as the cells below can hold and pours exactly the levels it got.
 *
 * <p>Order matters for finite water: the destination is planned first, the world is charged second, and the fluid is
 * poured last. A failure in between can only lose fluid, never create it. Before the first change the code fails
 * open: it answers "not handled" and the original logic runs unchanged. It has no reference to Alex's Caves classes.
 */
public final class AlexsCavesDrainBridge {

    static final String VERDICT_ID = "alexscaves_drain";
    private static final String HOOK_ID = "alexscaves.drain";
    private static final String MACHINE_FALLBACK_ID = "unknown_drain";
    // Level scans that never touch an unloaded chunk; the search can only look at a bounded number of cells
    private static final int MAX_SCANNED_CELLS = 512;
    private static final int MAX_REACH_CELLS = 256;
    private static final int IDLE_BACKOFF_FACTOR = 4;
    private static final Direction[] AROUND = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private AlexsCavesDrainBridge() {
    }

    /** Keeps the Flowing Fluids API wrapper: asking for it writes a log line each time. */
    private static final class FlowingFluidsHolder {
        static final FlowingFluidsAPI API = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID);
    }

    /** What phase one worked out; read only, so dropping it changes nothing in the world. */
    private record Work(ServerLevel level, BlockPos drainPos, Block block, Fluid fluid, List<BlockPos> sources,
                        List<DrainFillPlan.Placement> plan) {
    }

    /** @return true when this version did the Drain's work this tick, false to let the original logic run */
    public static boolean handle(BlockState state, Level level, BlockPos pos) {
        Work work;
        try {
            work = prepare(state, level, pos);
        } catch (RuntimeException | LinkageError error) {
            FluidCompatLog.errorThrottled("alexscaves-drain-prepare", "Drain hook failed at " + pos.toShortString(), error);
            return false;
        }
        if (work == null) {
            return false;
        }
        try {
            execute(work);
        } catch (RuntimeException | LinkageError error) {
            // Something may already have moved; running the original now could pour a second time
            FluidCompatLog.errorThrottled("alexscaves-drain-execute", "Drain hook failed at " + pos.toShortString(), error);
        }
        return true;
    }

    // ------------------------------------------------------------------ phase one: read only

    private static Work prepare(BlockState state, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !FluidCompatApi.isReady() || !CompatGate.masterEnabled()
                || !FluidCompatModulesConfig.ALEXSCAVES_DRAIN_ENABLED.get()) {
            return null;
        }
        BlockPos fillStart = isOpenOrFluid(server, pos.below()) ? lowestOpenCellBelow(server, pos) : null;
        if (fillStart == null) {
            return null; // the original does nothing here either
        }
        int reach = FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTAKE_REACH.get();
        BlockPos above = pos.above();
        FluidState aboveFluid = server.getFluidState(above);
        Fluid known = aboveFluid.isEmpty() ? null : normalised(aboveFluid.getType());
        BlockPos start = findIntakeCell(server, pos, above, known, reach);
        if (start == null) {
            return idleWork(state, server, pos, known, reach);
        }
        Fluid fluid = normalised(server.getFluidState(start).getType());
        if (!canHandle(server, fluid)) {
            return null; // not our business: the original logic keeps its exact behaviour
        }
        ScanTerrain terrain = new ScanTerrain(server, fluid);
        int wanted = FluidCompatModulesConfig.ALEXSCAVES_DRAIN_MAX_LEVELS.get();
        List<DrainFillPlan.Placement> plan = DrainFillPlan.plan(terrain, fillStart.getX(), fillStart.getY(), fillStart.getZ(),
                pos.getY(), wanted, FluidCompatModulesConfig.ALEXSCAVES_DRAIN_FILL_SPREAD.get(), MAX_SCANNED_CELLS);
        return new Work(server, pos.immutable(), state.getBlock(), fluid, sourcesFrom(server, start, fluid), plan);
    }

    private static boolean canHandle(ServerLevel level, Fluid fluid) {
        FiniteFluidService service = FluidCompatApi.service();
        return service.isFinite(fluid) && flowingFluidsHandles(service, level, fluid);
    }

    /**
     * Where to take fluid from: the cell above, or the fluid inside a waterlogged block there, or when none of those
     * has free-standing fluid, the nearest fluid cell within reach (thin layers often stop short of the Drain).
     */
    private static BlockPos findIntakeCell(ServerLevel level, BlockPos drainPos, BlockPos above, Fluid known, int reach) {
        if (known != null) {
            BlockPos near = firstRealFluidCell(level, above, known);
            if (near != null) {
                return near;
            }
        }
        if (reach <= 0) {
            return null;
        }
        FluidReach.Probe probe = (x, y, z) -> reachKind(level, new BlockPos(x, y, z), known);
        int[] found = FluidReach.findNearest(probe, above.getX(), above.getY(), above.getZ(),
                drainPos.getX(), drainPos.getY(), drainPos.getZ(), reach, MAX_REACH_CELLS);
        return found == null ? null : new BlockPos(found[0], found[1], found[2]);
    }

    private static int reachKind(ServerLevel level, BlockPos pos, Fluid known) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() || !level.hasChunkAt(pos)) {
            return FluidReach.BLOCKED;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = state.getFluidState();
        if (state.getBlock() instanceof LiquidBlock && !fluidState.isEmpty()) {
            Fluid fluid = normalised(fluidState.getType());
            boolean wanted = known != null ? fluid.isSame(known) : FluidCompatApi.service().isFinite(fluid);
            return wanted ? FluidReach.FLUID : FluidReach.BLOCKED;
        }
        return state.isAir() || !fluidState.isEmpty() ? FluidReach.OPEN : FluidReach.BLOCKED;
    }

    /**
     * Nothing to take this time. When the Drain is switched on and water is finite, keep it awake with a slow poll:
     * the original goes to sleep as soon as the cell above is dry and only wakes on a redstone change.
     */
    private static Work idleWork(BlockState state, ServerLevel level, BlockPos pos, Fluid known, int reach) {
        if (known != null || reach <= 0 || !canHandle(level, Fluids.WATER)) {
            return null;
        }
        return new Work(level, pos.immutable(), state.getBlock(), Fluids.WATER, List.of(), List.of());
    }

    private static boolean flowingFluidsHandles(FiniteFluidService service, ServerLevel level, Fluid fluid) {
        Optional<DrainBackend> backend = service.activeBackendFor(level, fluid);
        return backend.isPresent() && DrainBackendIds.FLOWING_FLUIDS.equals(backend.get().id());
    }

    private static Fluid normalised(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static boolean isRealFluid(ServerLevel level, BlockPos pos, Fluid fluid) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LiquidBlock && normalised(state.getFluidState().getType()).isSame(fluid);
    }

    /**
     * The cell above the Drain, or when that holds the fluid inside a waterlogged block (a Copper Valve, for example)
     * the first free-standing fluid cell next to it. Flowing Fluids ignores waterlogging, so it needs a real cell.
     */
    private static BlockPos firstRealFluidCell(ServerLevel level, BlockPos above, Fluid fluid) {
        if (isRealFluid(level, above, fluid)) {
            return above;
        }
        for (Direction direction : AROUND) {
            BlockPos neighbour = above.relative(direction);
            if (isRealFluid(level, neighbour, fluid)) {
                return neighbour;
            }
        }
        return null;
    }

    /** Surface first, like the original, then the cell it started from, so both ends of the column can be tried. */
    private static List<BlockPos> sourcesFrom(ServerLevel level, BlockPos start, Fluid fluid) {
        BlockPos top = start;
        while (top.getY() + 1 < level.getMaxBuildHeight() && isRealFluid(level, top.above(), fluid)) {
            top = top.above();
        }
        List<BlockPos> sources = new ArrayList<>(2);
        sources.add(top.immutable());
        if (!top.equals(start)) {
            sources.add(start.immutable());
        }
        return sources;
    }

    private static boolean isOpenOrFluid(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    /** The bottom of the open column under the Drain, where pouring starts. */
    private static BlockPos lowestOpenCellBelow(ServerLevel level, BlockPos drainPos) {
        BlockPos.MutableBlockPos cursor = drainPos.mutable().move(Direction.DOWN);
        if (!isOpenOrFluid(level, cursor)) {
            return null;
        }
        while (cursor.getY() > level.getMinBuildHeight() && isOpenOrFluid(level, cursor.below())) {
            cursor.move(Direction.DOWN);
        }
        return cursor.immutable();
    }

    /** Free levels of a cell for {@code fluid}; stops the scan at unloaded chunks and the world's height limits. */
    private record ScanTerrain(ServerLevel level, Fluid fluid) implements DrainFillPlan.Terrain {
        @Override
        public int freeLevels(int x, int y, int z) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return -1;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return -1;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return DrainFillPlan.LEVELS_PER_CELL;
            }
            FluidState cellFluid = state.getFluidState();
            boolean sameFreeStandingFluid = state.getBlock() instanceof LiquidBlock
                    && !cellFluid.isEmpty() && normalised(cellFluid.getType()).isSame(fluid);
            return sameFreeStandingFluid ? DrainFillPlan.LEVELS_PER_CELL - cellFluid.getAmount() : -1;
        }
    }

    // ------------------------------------------------------------------ phase two: change the world

    private static void execute(Work work) {
        ServerLevel level = work.level();
        int planned = DrainFillPlan.totalLevels(work.plan());
        int drained = 0;
        if (planned > 0) {
            FluidIntakeResult result = FluidCompatApi.service().requestIntake(new FluidIntakeRequest(
                    level, work.drainPos(), machineId(work.block()), work.fluid(), planned, work.sources()));
            drained = result.managed() ? Math.min(result.drainedLevels(), planned) : 0;
            reportFirstUse(result, planned);
        }
        int poured = drained > 0 ? pour(work, DrainFillPlan.take(work.plan(), drained)) : 0;
        if (drained > 0 && FluidCompatModulesConfig.ALEXSCAVES_DRAIN_WORLD_EFFECTS.get()) {
            DrainWorldEffects.play(DrainWorldEffects.SUCK, work.drainPos());
            if (poured > 0) {
                DrainWorldEffects.play(DrainWorldEffects.POUR, work.drainPos());
            }
        }
        if (poured < drained) {
            FluidCompatLog.errorThrottled("alexscaves-drain-lost", "Drain at " + work.drainPos().toShortString()
                    + " drained " + drained + " levels but could pour only " + poured + "; the rest is gone, never duplicated", null);
        }
        scheduleNextCycle(work, drained);
    }

    private static int pour(Work work, List<DrainFillPlan.Placement> placements) {
        FlowingFluidsAPI api = FlowingFluidsHolder.API;
        int poured = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (DrainFillPlan.Placement placement : placements) {
            cursor.set(placement.x(), placement.y(), placement.z());
            int before = currentLevels(work.level(), cursor, work.fluid());
            int target = Math.min(DrainFillPlan.LEVELS_PER_CELL, before + placement.levels());
            if (api.modifyFluidAmountAtPos(work.level(), cursor.immutable(), work.fluid(), target)) {
                // Read back what is really there, so a cell that took less is not counted as poured
                poured += Math.max(0, Math.min(placement.levels(), currentLevels(work.level(), cursor, work.fluid()) - before));
            }
        }
        return poured;
    }

    private static int currentLevels(ServerLevel level, BlockPos pos, Fluid fluid) {
        FluidState state = level.getFluidState(pos);
        return !state.isEmpty() && normalised(state.getType()).isSame(fluid) ? state.getAmount() : 0;
    }

    // A Drain that moved nothing waits longer: it costs a few block reads each time it looks
    private static void scheduleNextCycle(Work work, int drained) {
        int interval = FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTERVAL_TICKS.get();
        int delay = drained > 0 ? interval : interval * IDLE_BACKOFF_FACTOR;
        work.level().scheduleTick(work.drainPos(), work.block(), delay);
    }

    private static ResourceLocation machineId(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null ? key : ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, MACHINE_FALLBACK_ID);
    }

    private static void reportFirstUse(FluidIntakeResult result, int planned) {
        if (CompatMixinState.markHookReached(HOOK_ID)) {
            FluidCompatLog.info("[alexscaves] Drain hook reached for the first time: managed={}, drained {} of {} planned levels",
                    result.managed(), result.drainedLevels(), planned);
        }
    }
}
