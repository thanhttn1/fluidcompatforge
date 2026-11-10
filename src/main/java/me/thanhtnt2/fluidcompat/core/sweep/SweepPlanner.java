package me.thanhtnt2.fluidcompat.core.sweep;

import me.thanhtnt2.fluidcompat.api.sweep.SweepCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Finds the cell a sweep starts from, in two steps and with a hard cap on block reads per call.
 *
 * <ol>
 *   <li>The machine's reach point and its four horizontal neighbours, plus the cell below: where fluid appears first
 *       when it flows toward a drained spot. About six reads.</li>
 *   <li>Only if that finds nothing and the machine passed a {@link SweepCursor}: the next few entries of the shared
 *       {@link ReachTable} ring, on two layers, then stop. The cursor remembers where to continue, so a search over a
 *       whole disc is spread across many cycles instead of landing on one tick.</li>
 * </ol>
 *
 * <p>No flood fill lives here on purpose. Flowing Fluids' own collector already gathers the connected fluid around the
 * start cell with a hard cap on the cells it visits, so one cell is all that is needed. Chunks are never loaded.
 */
final class SweepPlanner {

    /** What a search found: a start cell, or the answer "nothing" together with whether the pass is complete. */
    record Search(BlockPos start, boolean passComplete) {
        static Search found(BlockPos start) {
            return new Search(start, false);
        }

        static Search nothing(boolean passComplete) {
            return new Search(null, passComplete);
        }
    }

    // The origin, its four horizontal neighbours, then the cell below: fluid thins out sideways and downhill
    private static final int[][] SEEDS = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
    // The ring is searched on the origin's layer and the one below it
    private static final int[] RING_LAYERS = {0, -1};

    private SweepPlanner() {
    }

    static Search search(ServerLevel level, BlockPos origin, Fluid fluid, SweepCursor cursor, int reach, int scanBudget) {
        BlockPos.MutableBlockPos cursorPos = new BlockPos.MutableBlockPos();
        BlockPos seed = findSeed(level, origin, fluid, cursorPos);
        if (seed != null) {
            return Search.found(seed);
        }
        if (cursor == null) {
            return Search.nothing(true);
        }
        return scanRing(level, origin, fluid, cursor, ReachTable.forReach(reach), scanBudget, cursorPos);
    }

    private static BlockPos findSeed(ServerLevel level, BlockPos origin, Fluid fluid, BlockPos.MutableBlockPos probe) {
        for (int[] offset : SEEDS) {
            probe.set(origin.getX() + offset[0], origin.getY() + offset[1], origin.getZ() + offset[2]);
            if (holdsFluidAt(level, probe, fluid)) {
                return probe.immutable();
            }
        }
        return null;
    }

    private static Search scanRing(ServerLevel level, BlockPos origin, Fluid fluid, SweepCursor cursor, ReachTable table,
            int scanBudget, BlockPos.MutableBlockPos probe) {
        int size = table.size();
        int index = cursor.nextOffset() >= size ? 0 : cursor.nextOffset();
        for (int looked = 0; looked < scanBudget; looked++) {
            for (int layer : RING_LAYERS) {
                probe.set(origin.getX() + table.dx(index), origin.getY() + layer, origin.getZ() + table.dz(index));
                if (holdsFluidAt(level, probe, fluid)) {
                    cursor.advanceTo(index); // Look at this spot again first: it may still hold more
                    return Search.found(probe.immutable());
                }
            }
            index++;
            if (index >= size) {
                cursor.restart();
                return Search.nothing(true); // A whole pass over the disc found nothing
            }
        }
        cursor.advanceTo(index);
        return Search.nothing(false);
    }

    private static boolean holdsFluidAt(ServerLevel level, BlockPos pos, Fluid fluid) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        FluidState state = level.getFluidState(pos);
        return !state.isEmpty() && state.getAmount() > 0 && state.getType().isSame(fluid);
    }
}
