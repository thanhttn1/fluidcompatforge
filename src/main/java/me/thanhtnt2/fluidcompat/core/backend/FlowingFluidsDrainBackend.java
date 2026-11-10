package me.thanhtnt2.fluidcompat.core.backend;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.api.DrainBackendIds;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import traben.flowing_fluids.api.FlowingFluidsAPI;

/**
 * Drains through the public Flowing Fluids API, so partially filled blocks, connected bodies of
 * fluid and Flowing Fluids' own rules are respected.
 *
 * <p>Only ever instantiate this class after confirming that Flowing Fluids is loaded,
 * otherwise the JVM cannot resolve {@link FlowingFluidsAPI}.
 */
public final class FlowingFluidsDrainBackend implements DrainBackend {

    private static final int PRIORITY = 1000;

    private final FlowingFluidsAPI flowingFluidsApi = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID);

    @Override
    public ResourceLocation id() {
        return DrainBackendIds.FLOWING_FLUIDS;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(ServerLevel level, Fluid fluid) {
        return flowingFluidsApi.isModEnabled() && flowingFluidsApi.doesModifyThisFluid(fluid);
    }

    @Override
    public int drain(ServerLevel level, BlockPos sourcePos, Fluid fluid, int maxLevels) {
        // min = 1 so a nearly empty pool still yields whatever is left instead of nothing
        return flowingFluidsApi.removeFluidAmountFromPos(level, sourcePos, fluid, 1, maxLevels);
    }
}
