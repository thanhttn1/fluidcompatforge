package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The one decision behind the "erase only fluid blocks" patch, kept free of logging and mod calls so it can be
 * tested on its own.
 *
 * <p>{@code removeAllFluidAtPos} means "take all fluid out of this cell". A cell that is air already has nothing to
 * take, and a cell that holds fluid (a liquid block, a waterlogged block, kelp, a bubble column) is exactly what the
 * method is for. A solid block that holds no fluid at all is neither: the fluid code decided on stale information,
 * and turning that block into air would delete something that has nothing to do with fluid.
 */
final class RemovalGuard {

    private RemovalGuard() {
    }

    static boolean wouldEraseABlockWithoutFluid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
