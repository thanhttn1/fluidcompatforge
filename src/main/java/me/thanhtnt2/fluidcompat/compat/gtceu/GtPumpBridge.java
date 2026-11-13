package me.thanhtnt2.fluidcompat.compat.gtceu;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepRequest;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepResult;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.core.thin.ThinFluidCells;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Lets the GregTech Pump drain what its own search cannot see.
 *
 * <p>GregTech's Pump takes full source blocks one by one: it asks the block for a bucket, puts 1000 mB in its tank and
 * sets the block to air. A thin Flowing Fluids layer is not a source block, so the pump walks past it and, once the
 * source blocks are gone, sits idle next to puddles that will never move. Those puddles are now swept: when a cycle ran
 * and the pump's queue has nothing left, the thin fluid around the pump head is taken out of the world through the
 * sweep service and the pump's tank receives exactly the levels the world gave up, at 125 mB each. The pump pays the
 * same energy it pays for a bucket.
 *
 * <p>Source blocks keep going through GregTech's own code, which was already finite. Every access to GregTech is by name.
 */
public final class GtPumpBridge {

    private static final String FIRST_USE_HOOK = "gtceu.pump.sweep";
    private static final int MAX_LEVELS_PER_SWEEP = FluidCompatApi.LEVELS_PER_BUCKET;

    private GtPumpBridge() {
    }

    /** Called after every pump cycle of a GregTech Pump. Never throws: a problem leaves the pump as GregTech made it. */
    public static void afterPumpCycle(Object machine, GtPumpState state) {
        try {
            sweep(machine, state);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FluidCompatLog.errorThrottled("gtceu-pump-sweep", "GregTech pump leftover sweep failed", error);
        }
    }

    private static void sweep(Object machine, GtPumpState state) throws ReflectiveOperationException {
        if (!CompatGate.masterEnabled() || !FluidCompatApi.isReady() || !FluidCompatModulesConfig.GTCEU_PUMP_ENABLED.get()
                || !FluidCompatModulesConfig.GTCEU_PUMP_DRAIN_LEFTOVERS.get()) {
            return;
        }
        GtPumpAccess access = GtPumpAccess.of(machine);
        if (access == null || !(access.level(machine) instanceof ServerLevel level) || !access.queueIsEmpty(machine)) {
            return; // the pump still has work of its own to do
        }
        if (state.consumeSkip()) {
            return;
        }
        BlockPos pumpPos = access.pos(machine);
        BlockPos head = pumpPos.below(access.pumpHeadY(machine) + 1);
        Fluid fluid = fluidAt(level, head);
        if (fluid == null) {
            return; // the pump head is not in a finite fluid: GregTech's own logic moves the head
        }
        int levels = levelsThatFit(access, machine, fluid);
        long cost = access.cycleCost(machine);
        if (levels <= 0 || access.energyStored(machine) < cost) {
            return;
        }
        FluidSweepResult result = FluidCompatApi.sweeps().sweep(
                new FluidSweepRequest(level, pumpPos, machineIdOf(machine), fluid, levels, head, state.cursor()));
        if (!result.drained()) {
            state.foundNothing(FluidCompatModulesConfig.SWEEP_IDLE_BACKOFF_MAX_CYCLES.get());
            return;
        }
        state.foundWater();
        giveToPump(access, machine, fluid, result.drainedLevels(), cost);
    }

    /** The finite fluid in the pump head's cell, thin or not, or null. */
    private static Fluid fluidAt(Level level, BlockPos head) {
        FluidState fluidState = level.getFluidState(head);
        if (fluidState.isEmpty()) {
            return null;
        }
        Fluid fluid = ThinFluidCells.normalised(fluidState.getType());
        return FluidCompatApi.service().isFinite(fluid) ? fluid : null;
    }

    // The largest number of levels (up to a bucket) the pump's tank would take right now, asked the tank itself
    private static int levelsThatFit(GtPumpAccess access, Object machine, Fluid fluid) throws ReflectiveOperationException {
        for (int levels = MAX_LEVELS_PER_SWEEP; levels >= 1; levels--) {
            int amount = levels * FluidCompatApi.MILLIBUCKETS_PER_LEVEL;
            if (access.fill(machine, new FluidStack(fluid, amount), IFluidHandler.FluidAction.SIMULATE) == amount) {
                return levels;
            }
        }
        return 0;
    }

    private static void giveToPump(GtPumpAccess access, Object machine, Fluid fluid, int levels, long cost)
            throws ReflectiveOperationException {
        int amount = levels * FluidCompatApi.MILLIBUCKETS_PER_LEVEL;
        int accepted = access.fill(machine, new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
        access.spendEnergy(machine, cost);
        if (accepted < amount) {
            // The tank said yes a moment ago; the world already paid, so the missing fluid is gone, never duplicated
            FluidCompatLog.errorThrottled("gtceu-pump-short", "GregTech pump tank took " + accepted + " of " + amount
                    + " mB after the world paid for all of it", null);
        }
        if (CompatMixinState.markHookReached(FIRST_USE_HOOK)) {
            FluidCompatLog.info("[gtceu] Pump leftover sweep reached for the first time: {} levels of thin fluid went into the pump", levels);
        }
    }

    private static ResourceLocation machineIdOf(Object machine) {
        if (machine instanceof BlockEntity entity) {
            ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
            if (key != null) {
                return key;
            }
        }
        return ResourceLocation.fromNamespaceAndPath("gtceu", "pump");
    }
}
