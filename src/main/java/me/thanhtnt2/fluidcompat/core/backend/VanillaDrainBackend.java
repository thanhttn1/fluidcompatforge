package me.thanhtnt2.fluidcompat.core.backend;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.api.DrainBackendIds;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Fallback for worlds without a fluid-physics mod.
 *
 * <p>Vanilla fluids have no partial source blocks, so a small request turns the source into a
 * lower flowing block and a full request removes the source entirely. Vanilla's own
 * "two sources make a new source" rule can still refill water, which is why Flowing Fluids is
 * the recommended backend.
 */
public final class VanillaDrainBackend implements DrainBackend {

    private static final int PRIORITY = 0;

    @Override
    public ResourceLocation id() {
        return DrainBackendIds.VANILLA;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(ServerLevel level, Fluid fluid) {
        return true;
    }

    @Override
    public int drain(ServerLevel level, BlockPos sourcePos, Fluid fluid, int maxLevels) {
        BlockState state = level.getBlockState(sourcePos);
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isSource() || !fluidState.getType().isSame(fluid)) {
            return 0;
        }

        int levelsToTake = Math.min(maxLevels, FluidCompatApi.LEVELS_PER_BUCKET);
        if (levelsToTake <= 0) {
            return 0;
        }

        boolean partialDrain = levelsToTake < FluidCompatApi.LEVELS_PER_BUCKET;
        if (partialDrain && state.getBlock() instanceof LiquidBlock && fluid instanceof FlowingFluid flowingFluid) {
            return shrinkSource(level, sourcePos, flowingFluid, levelsToTake);
        }
        return removeWholeSource(level, sourcePos, state);
    }

    private int shrinkSource(ServerLevel level, BlockPos pos, FlowingFluid fluid, int levelsToTake) {
        int remainingLevels = FluidCompatApi.LEVELS_PER_BUCKET - levelsToTake;
        BlockState lowerBlock = fluid.getFlowing(remainingLevels, false).createLegacyBlock();
        level.setBlock(pos, lowerBlock, Block.UPDATE_ALL);
        return levelsToTake;
    }

    private int removeWholeSource(ServerLevel level, BlockPos pos, BlockState state) {
        // BucketPickup also handles waterlogged blocks, which a plain setBlock(AIR) would destroy.
        // On 1.20.1 pickupBlock takes no Player argument (that parameter arrived with 1.20.5)
        if (!(state.getBlock() instanceof BucketPickup bucketPickup)) {
            return 0;
        }
        boolean removed = !bucketPickup.pickupBlock(level, pos, state).isEmpty();
        return removed ? FluidCompatApi.LEVELS_PER_BUCKET : 0;
    }
}
