package me.thanhtnt2.fluidcompat.compat.immersiveengineering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * What the Immersive Engineering Fluid Pump hooks answer. The pump has three habits that break finite water:
 *
 * <ol>
 *   <li>Its infinite rule: a source block next to the pump with two source neighbours gives 1000 mB for free. Its area
 *       search also skips every fluid that can turn into a source, which is water, so the pump never drained water at
 *       all. Both ask {@code FluidState.canConvertToSource}; for a finite fluid the answer is now "no".</li>
 *   <li>It only takes full source blocks, and compares fluids with {@code ==} (water and flowing water are two
 *       objects), so a pond with thin layers is not searched through. Fluids are compared by their source variant and
 *       thin cells are given to the pump as a stack of exactly the levels they hold, through {@link ThinFluidCells}.</li>
 *   <li>It puts cobblestone where it drained, even when the block was already empty. With finite fluids that only hides
 *       the drained space and replaces any thin layer left there, so it is skipped.</li>
 * </ol>
 *
 * <p>No IE class is referenced at compile time; the pump's own source-block drain is reached by name. Every method
 * fails open: a problem gives the answer the pump would have got without Fluid Compat.
 */
public final class IePumpBridge {

    private static final String UTILS_CLASS = "blusunrize.immersiveengineering.common.util.Utils";
    private static final String FIRST_USE_HOOK = "immersiveengineering.pump";
    private static final String FIRST_THIN_HOOK = "immersiveengineering.pump.thin";

    private static volatile MethodHandle originalDrain;

    private IePumpBridge() {
    }

    private static boolean pumpHooksOn() {
        return IePumpHealth.isActive() && CompatGate.masterEnabled() && FluidCompatApi.isReady()
                && FluidCompatModulesConfig.IE_FINITE_PUMP.get();
    }

    private static boolean isFinite(Fluid fluid) {
        return FluidCompatApi.service().isFinite(ThinFluidCells.normalised(fluid));
    }

    // ------------------------------------------------------------------ infinite rule and search filter

    /** Replaces {@code FluidState.canConvertToSource(level, pos)} inside the pump: false for a fluid that is kept finite. */
    public static boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
        boolean original = state.canConvertToSource(level, pos);
        if (!original) {
            return false;
        }
        try {
            if (pumpHooksOn() && isFinite(state.getType())) {
                markFirstUse("finite water: the infinite rule and the search filter now treat it as a normal fluid");
                IePumpHealth.succeeded();
                return false;
            }
        } catch (RuntimeException | LinkageError error) {
            IePumpHealth.failed("canConvertToSource", error);
        }
        return original;
    }

    /** The fluid the area search compares with {@code ==}: the source variant of a finite fluid, so thin layers match. */
    public static Fluid searchFluid(Fluid fluid) {
        try {
            if (pumpHooksOn() && FluidCompatModulesConfig.IE_DRAIN_THIN_FLUIDS.get() && isFinite(fluid)) {
                return ThinFluidCells.normalised(fluid);
            }
        } catch (RuntimeException | LinkageError error) {
            IePumpHealth.failed("searchFluid", error);
        }
        return fluid;
    }

    // ------------------------------------------------------------------ the drain of one block

    /**
     * Replaces {@code Utils.drainFluidBlock(level, pos, action)} inside the pump. A thin cell of a finite fluid is given
     * as a stack of exactly the levels it holds (SIMULATE only reports, EXECUTE takes them out of the world); anything
     * else is the pump's own code.
     *
     * @param shortfall receives how many mB the world gave less than the SIMULATE stack promised, so the pump's tank
     *                  can be given the same
     */
    public static FluidStack drainBlock(BlockEntity pump, Level level, BlockPos pos, IFluidHandler.FluidAction action,
                                        int[] shortfall) {
        try {
            if (pumpHooksOn() && FluidCompatModulesConfig.IE_DRAIN_THIN_FLUIDS.get()
                    && ThinFluidCells.isThinManagedCell(level, pos)) {
                FluidStack thin = drainThinCell(pump, level, pos, action, shortfall);
                IePumpHealth.succeeded();
                return thin;
            }
        } catch (RuntimeException | LinkageError error) {
            IePumpHealth.failed("drainThinCell", error);
        }
        return pumpsOwnDrain(level, pos, action);
    }

    private static FluidStack drainThinCell(BlockEntity pump, Level level, BlockPos pos, IFluidHandler.FluidAction action,
                                            int[] shortfall) {
        int levels = ThinFluidCells.levelsIn(level, pos);
        Fluid fluid = ThinFluidCells.normalised(level.getFluidState(pos).getType());
        FluidStack promised = new FluidStack(fluid, levels * FluidCompatApi.MILLIBUCKETS_PER_LEVEL);
        if (action.simulate() || !(level instanceof ServerLevel server)) {
            return promised;
        }
        int removed = ThinFluidCells.drainCell(server, pump.getBlockPos(), pos, machineId(pump), levels);
        shortfall[0] = (levels - removed) * FluidCompatApi.MILLIBUCKETS_PER_LEVEL;
        if (CompatMixinState.markHookReached(FIRST_THIN_HOOK)) {
            FluidCompatLog.info("[immersiveengineering] pump drained a thin {} layer at {} for the first time: {} of {} levels",
                    fluid.getFluidType().getDescriptionId(), pos.toShortString(), removed, levels);
        }
        return new FluidStack(fluid, removed * FluidCompatApi.MILLIBUCKETS_PER_LEVEL);
    }

    private static FluidStack pumpsOwnDrain(Level level, BlockPos pos, IFluidHandler.FluidAction action) {
        try {
            MethodHandle handle = originalDrain;
            if (handle == null) {
                handle = MethodHandles.publicLookup().findStatic(
                        Class.forName(UTILS_CLASS, true, IePumpBridge.class.getClassLoader()), "drainFluidBlock",
                        MethodType.methodType(FluidStack.class, Level.class, BlockPos.class, IFluidHandler.FluidAction.class));
                originalDrain = handle;
            }
            return (FluidStack) handle.invoke(level, pos, action);
        } catch (Throwable error) {
            // Nothing can be drained when the pump's own method cannot be reached; an empty stack is "nothing here"
            IePumpHealth.failed("ownDrain", error);
            return FluidStack.EMPTY;
        }
    }

    private static ResourceLocation machineId(BlockEntity pump) {
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(pump.getType());
        return key != null ? key : ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, "unknown_fluid_pump");
    }

    // ------------------------------------------------------------------ the tank fill that follows

    /** The stack the pump's tank receives: what the SIMULATE step promised, less what the world did not give. */
    public static FluidStack stackToFill(FluidStack promised, int shortfall) {
        if (shortfall <= 0) {
            return promised;
        }
        int amount = Math.max(0, promised.getAmount() - shortfall);
        return amount == 0 ? FluidStack.EMPTY : new FluidStack(promised, amount);
    }

    // ------------------------------------------------------------------ cobblestone

    /**
     * Replaces the pump's {@code setBlockAndUpdate(pos, cobblestone)}. Skipped when the pump drained a finite fluid,
     * so the world is not covered with cobblestone and no thin layer left in that block is deleted.
     */
    public static boolean placeCobble(Level level, BlockPos pos, BlockState cobble, Fluid searchFluid) {
        try {
            if (pumpHooksOn() && FluidCompatModulesConfig.IE_NO_COBBLE_FOR_FINITE.get()
                    && searchFluid != null && isFinite(searchFluid)) {
                IePumpHealth.succeeded();
                return false;
            }
        } catch (RuntimeException | LinkageError error) {
            IePumpHealth.failed("cobble", error);
        }
        return level.setBlockAndUpdate(pos, cobble);
    }

    private static void markFirstUse(String what) {
        if (CompatMixinState.markHookReached(FIRST_USE_HOOK)) {
            FluidCompatLog.info("[immersiveengineering] Fluid Pump hook reached for the first time: {}", what);
        }
    }
}
