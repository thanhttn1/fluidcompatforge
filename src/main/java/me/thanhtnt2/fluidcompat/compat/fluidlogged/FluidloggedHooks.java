package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The answers Fluidlogged's block lists get from Fluid Compat. Called from {@code FluidloggedConfigMixin} in hot paths
 * (every fluid check asks), so the cheap question goes first and a problem always means "no change".
 */
public final class FluidloggedHooks {

    private FluidloggedHooks() {
    }

    /** True for a listed block when Fluid Compat should add it to Fluidlogged's "can hold a fluid" list. */
    public static boolean addsFluidloggable(BlockState state) {
        return isListedAndEnabled(state) && FluidCompatModulesConfig.FLUIDLOGGED_MAKE_FLUIDLOGGABLE.get();
    }

    /** True for a listed block when Fluid Compat should add it to Fluidlogged's "fluid passes through" list. */
    public static boolean addsPermeable(BlockState state) {
        return isListedAndEnabled(state) && FluidCompatModulesConfig.FLUIDLOGGED_MAKE_PERMEABLE.get();
    }

    private static boolean isListedAndEnabled(BlockState state) {
        try {
            return FloodBlocks.contains(state)
                    && CompatGate.masterEnabled()
                    && FluidCompatModulesConfig.FLUIDLOGGED_ENABLED.get();
        } catch (RuntimeException unavailable) {
            // The config is not loaded yet: leave Fluidlogged's own answer alone
            return false;
        }
    }
}
