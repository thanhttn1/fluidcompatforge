package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * The only entry points the patch mixins call. Everything that can fail is caught here, so a problem in a patch hands
 * control back to the original Flowing Fluids code instead of crashing a fluid tick.
 */
public final class PatchHooks {

    private PatchHooks() {
    }

    /**
     * @return true when the listeners were notified and the original method must be skipped; false when something
     *         went wrong and the original method should run as if there were no patch
     */
    public static boolean notifyFlowListeners(
            LevelAccessor level, BlockPos fluidPos, Direction direction, Fluid fluid, boolean downAlso) {
        try {
            FlowListenerDispatch.notifyListeners(level, fluidPos, direction, fluid, downAlso);
            return true;
        } catch (RuntimeException | LinkageError error) {
            // LinkageError covers a Flowing Fluids interface that changed shape
            FluidCompatLog.errorThrottled("ff-patch-flow-listeners", "Flowing Fluids flow listener patch failed; the original code runs instead", error);
            return false;
        }
    }

    /**
     * @return true when a neighbour update of this liquid block was only a level change in that neighbour, so the
     *         (costly) fluid interaction check can be skipped; false on any doubt or error
     */
    public static boolean canSkipInteractionCheckForNeighbour(
            BlockState state, Level level, Block neighbourBefore, BlockPos neighbourPos) {
        try {
            Block thisLiquid = state.getBlock();
            // Cheapest test first: most updates come from a different block and need no world read at all
            return thisLiquid == neighbourBefore
                    && InteractionShortcut.neighbourOnlyChangedItsLevel(thisLiquid, neighbourBefore, level.getBlockState(neighbourPos).getBlock());
        } catch (RuntimeException | LinkageError error) {
            return false; // Fail open: the original check runs
        }
    }

    /** @return true when this liquid block was only placed over its own earlier state (a level change) */
    public static boolean canSkipInteractionCheckForPlacement(BlockState newState, BlockState oldState) {
        return InteractionShortcut.placementOnlyChangedTheLevel(oldState.getBlock(), newState.getBlock());
    }

    /**
     * @return true when the cell holds a block without any fluid, so "remove all fluid here" must leave it alone
     *         instead of turning it into air
     */
    public static boolean refuseToEraseBlockWithoutFluid(LevelAccessor level, BlockPos pos, Fluid fluid) {
        try {
            BlockState state = level.getBlockState(pos);
            if (!RemovalGuard.wouldEraseABlockWithoutFluid(state)) {
                return false;
            }
            // Evidence for the Flowing Fluids "air artifacts" report: which block, where, for which fluid
            FluidCompatLog.errorThrottled("ff-patch-erase-nonfluid",
                    "Flowing Fluids asked to remove all " + fluid + " at " + pos.toShortString() + " but the block there ("
                            + state.getBlock() + ") holds no fluid. The block was kept.",
                    new IllegalStateException("removeAllFluidAtPos on a block without fluid"));
            return true;
        } catch (RuntimeException | LinkageError error) {
            return false; // Fail open: the original code runs as before
        }
    }

    /** @return true when the request must be refused because it asks to remove a negative amount */
    public static boolean isNegativeRemoval(BlockPos pos, int maxAmount) {
        if (maxAmount >= 0) {
            return false;
        }
        // The stack trace is the point: it shows which mod made the bad call
        FluidCompatLog.errorThrottled("ff-patch-negative-removal",
                "A mod asked Flowing Fluids to remove " + maxAmount + " level(s) of fluid at " + (pos == null ? "unknown position" : pos.toShortString())
                        + " (a negative amount would create fluid). The request was refused.",
                new IllegalArgumentException("negative maxAmount " + maxAmount));
        return true;
    }
}
