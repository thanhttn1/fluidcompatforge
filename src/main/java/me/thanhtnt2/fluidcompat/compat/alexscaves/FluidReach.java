package me.thanhtnt2.fluidcompat.compat.alexscaves;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds the nearest fluid cell a few blocks away from the cell above a Drain.
 *
 * <p>Flowing Fluids spreads water in thin layers that stop moving at a small height difference, so water often sits a
 * block or two beside the Drain without ever touching the cell above it. The original Drain does nothing then. This
 * walks outwards through open cells and cells that already hold fluid, never through solid blocks, and stops at the
 * first free-standing fluid cell. Nothing here touches Minecraft, so the search can be tested alone.
 */
public final class FluidReach {

    /** What a cell is, as far as the search cares. */
    public static final int OPEN = 0;
    public static final int FLUID = 1;
    public static final int BLOCKED = 2;

    @FunctionalInterface
    public interface Probe {
        /** @return {@link #OPEN} to walk through, {@link #FLUID} for a cell to take fluid from, {@link #BLOCKED} otherwise */
        int kind(int x, int y, int z);
    }

    private static final int[][] STEPS = {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {0, -1, 0}};

    private FluidReach() {
    }

    /**
     * @param maxDepth  how many steps away from the start the search may go
     * @param maxCells  most cells looked at, so a large open cave never costs a long scan
     * @param skipX/Y/Z a cell the walk must not enter (the Drain itself)
     * @return {x, y, z} of the nearest fluid cell, or null when there is none within reach
     */
    public static int[] findNearest(Probe probe, int startX, int startY, int startZ, int skipX, int skipY, int skipZ,
                                    int maxDepth, int maxCells) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {startX, startY, startZ, 0});
        seen.add(pack(startX, startY, startZ));
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int kind = probe.kind(cell[0], cell[1], cell[2]);
            if (kind == FLUID) {
                return new int[] {cell[0], cell[1], cell[2]};
            }
            if (kind == BLOCKED || cell[3] >= maxDepth) {
                continue;
            }
            for (int[] step : STEPS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                int nz = cell[2] + step[2];
                boolean isSkipped = nx == skipX && ny == skipY && nz == skipZ;
                if (isSkipped || seen.size() >= maxCells || !seen.add(pack(nx, ny, nz))) {
                    continue;
                }
                queue.add(new int[] {nx, ny, nz, cell[3] + 1});
            }
        }
        return null;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
