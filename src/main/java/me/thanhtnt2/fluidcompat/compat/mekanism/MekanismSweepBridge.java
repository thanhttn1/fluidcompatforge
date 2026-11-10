package me.thanhtnt2.fluidcompat.compat.mekanism;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepRequest;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepResult;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Lets an Electric Pump drain what its own search cannot see: Mekanism only picks up full source blocks, and Flowing
 * Fluids spreads a draining pond into thin layers that are never sources. Without this the pump idles for ever beside
 * water, and again when new water flows in.
 *
 * <p>Called by the mixin only when Mekanism's own search found nothing. No Mekanism class is referenced: the tank is
 * read through the Forge fluid capability, and the pump's own insertion code does the actual filling.
 *
 * <p>Accounting: the world is asked for no more than the tank can take, and the pump receives exactly the levels the
 * world gave up, converted at the same rate Mekanism uses for a full bucket (so a Filter Upgrade still yields its Heavy
 * Water share). Water is never created and never lost.
 */
public final class MekanismSweepBridge {

    private static final String FIRST_SWEEP_HOOK_ID = "mekanism.electric_pump.sweep";
    private static final Direction[] TANK_SIDES = {Direction.UP, null, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST};

    private MekanismSweepBridge() {
    }

    /** Cheap checks done before anything is allocated; also spends one skipped cycle when the pump is backing off. */
    public static boolean shouldTry(BlockEntity pump, PumpSweepState state) {
        if (!(pump.getLevel() instanceof ServerLevel) || !FluidCompatApi.isReady()) {
            return false;
        }
        if (!CompatGate.masterEnabled() || !FluidCompatModulesConfig.ELECTRIC_PUMP_ENABLED.get()
                || !FluidCompatModulesConfig.ELECTRIC_PUMP_WATER.get()
                || !FluidCompatModulesConfig.ELECTRIC_PUMP_DRAIN_LEFTOVERS.get()) {
            return false;
        }
        return !state.consumeSkip();
    }

    /**
     * @param bucketYield what Mekanism would put into the tank for one full water source (water, or Heavy Water)
     * @return what the pump may insert now, empty when nothing was drained
     */
    public static FluidStack drainLeftoverWater(BlockEntity pump, PumpSweepState state, FluidStack bucketYield) {
        try {
            return drain(pump, state, bucketYield);
        } catch (RuntimeException | LinkageError error) {
            // Fail open: the pump keeps working exactly as Mekanism made it
            FluidCompatLog.errorThrottled("mekanism-electric-pump-sweep", "Mekanism pump leftover sweep failed", error);
            return FluidStack.EMPTY;
        }
    }

    private static FluidStack drain(BlockEntity pump, PumpSweepState state, FluidStack bucketYield) {
        ServerLevel level = (ServerLevel) pump.getLevel();
        if (bucketYield.isEmpty() || bucketYield.getAmount() < FluidCompatApi.LEVELS_PER_BUCKET) {
            return FluidStack.EMPTY; // A yield below 1 mB per level could not be paid out exactly
        }
        int maxLevels = levelsThatFit(findTank(pump), bucketYield);
        if (maxLevels <= 0) {
            return FluidStack.EMPTY;
        }
        BlockPos pumpPos = pump.getBlockPos();
        FluidSweepResult result = FluidCompatApi.sweeps().sweep(
                new FluidSweepRequest(level, pumpPos, machineIdOf(pump), Fluids.WATER, maxLevels, pumpPos.below(), state.cursor()));
        reportFirstUse(result);
        updateState(state, result);
        return result.drained() ? new FluidStack(bucketYield, amountFor(bucketYield, result.drainedLevels())) : FluidStack.EMPTY;
    }

    private static void updateState(PumpSweepState state, FluidSweepResult result) {
        switch (result.status()) {
            case DRAINED -> state.foundWater();
            case THROTTLED, SEARCHING -> { } // Busy guard or a search still under way: try again on the very next cycle
            default -> state.foundNothing(FluidCompatModulesConfig.SWEEP_IDLE_BACKOFF_MAX_CYCLES.get());
        }
    }

    private static IFluidHandler findTank(BlockEntity pump) {
        for (Direction side : TANK_SIDES) {
            IFluidHandler handler = pump.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
            if (handler != null && handler.getTanks() > 0) {
                return handler;
            }
        }
        return null;
    }

    /** Most levels (up to a full bucket) whose payout the tank can accept; 0 when it cannot take any or holds another fluid. */
    private static int levelsThatFit(IFluidHandler tank, FluidStack bucketYield) {
        if (tank == null) {
            return 0; // Fail closed: without seeing the tank the world must not be charged
        }
        FluidStack inside = tank.getFluidInTank(0);
        if (!inside.isEmpty() && !inside.isFluidEqual(bucketYield)) {
            return 0;
        }
        int freeSpace = tank.getTankCapacity(0) - inside.getAmount();
        for (int levels = FluidCompatApi.LEVELS_PER_BUCKET; levels >= 1; levels--) {
            int payout = amountFor(bucketYield, levels);
            if (payout >= 1 && payout <= freeSpace) {
                return levels;
            }
        }
        return 0;
    }

    private static int amountFor(FluidStack bucketYield, int levels) {
        return (int) ((long) bucketYield.getAmount() * levels / FluidCompatApi.LEVELS_PER_BUCKET);
    }

    private static ResourceLocation machineIdOf(BlockEntity pump) {
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(pump.getType());
        return key != null ? key : ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, "unknown_electric_pump");
    }

    private static void reportFirstUse(FluidSweepResult result) {
        if (!CompatMixinState.markHookReached(FIRST_SWEEP_HOOK_ID)) {
            return;
        }
        FluidCompatLog.info("[mekanism] Electric Pump leftover sweep reached for the first time: {} ({} levels)",
                result.status(), result.drainedLevels());
    }
}
