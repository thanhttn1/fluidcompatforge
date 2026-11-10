package me.thanhtnt2.fluidcompat.compat.alexscaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides where the fluid a Drain moves is poured out, before anything is taken from the world.
 *
 * <p>Alex's Caves moves fluid from above the Drain to below it. Planning the destination first is what keeps the
 * water finite: the Drain only takes as many levels as the destination can really hold, and it puts exactly the
 * levels it took into the planned cells. Nothing here touches Minecraft, so the whole decision can be tested alone.
 *
 * <p>The search follows the shape of the original: start at the lowest open cell under the Drain, spread sideways
 * and downwards, and when that region is full try again one cell higher, never reaching the Drain's own height.
 */
public final class DrainFillPlan {

    /** Most levels one cell can hold (a full block). */
    public static final int LEVELS_PER_CELL = 8;

    /** What the planner needs to know about the world, one cell at a time. */
    @FunctionalInterface
    public interface Terrain {
        /**
         * @return free levels in the cell (0 to 8), or -1 when the fluid cannot enter or pass through the cell
         *         (solid block, another fluid, a waterlogged block). A cell that is already full of the same fluid
         *         answers 0: nothing fits there, but the fluid may spread through it.
         */
        int freeLevels(int x, int y, int z);
    }

    /** Pour {@code levels} into the cell. */
    public record Placement(int x, int y, int z, int levels) {
    }

    private static final int[][] SPREAD = {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 0}, {0, 0, 1}};

    private DrainFillPlan() {
    }

    /**
     * @param ceilingY     cells at this height or above are never used (the Drain's own height)
     * @param levelsWanted most levels the caller would like to place
     * @param spread       how many steps away from the start cell the search may go
     * @param maxCells     most cells looked at in total, so a huge cave never costs a long scan
     * @return cells in pouring order whose levels add up to at most {@code levelsWanted}
     */
    public static List<Placement> plan(Terrain terrain, int startX, int startY, int startZ, int ceilingY,
                                       int levelsWanted, int spread, int maxCells) {
        List<Placement> placements = new ArrayList<>();
        if (levelsWanted <= 0) {
            return placements;
        }
        Set<Long> visited = new HashSet<>();
        int remaining = levelsWanted;
        for (int baseY = startY; baseY < ceilingY && remaining > 0 && visited.size() < maxCells; baseY++) {
            remaining = searchFrom(terrain, startX, baseY, startZ, ceilingY, remaining, spread, maxCells, visited, placements);
        }
        return placements;
    }

    private static int searchFrom(Terrain terrain, int x, int y, int z, int ceilingY, int remaining, int spread,
                                  int maxCells, Set<Long> visited, List<Placement> placements) {
        if (visited.contains(pack(x, y, z))) {
            return remaining;
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {x, y, z, 0});
        visited.add(pack(x, y, z));
        while (!queue.isEmpty() && remaining > 0) {
            int[] cell = queue.poll();
            int free = terrain.freeLevels(cell[0], cell[1], cell[2]);
            if (free < 0) {
                continue;
            }
            if (free > 0) {
                int poured = Math.min(free, remaining);
                placements.add(new Placement(cell[0], cell[1], cell[2], poured));
                remaining -= poured;
            }
            if (cell[3] >= spread) {
                continue;
            }
            for (int[] step : SPREAD) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                int nz = cell[2] + step[2];
                if (ny >= ceilingY || visited.size() >= maxCells || !visited.add(pack(nx, ny, nz))) {
                    continue;
                }
                queue.add(new int[] {nx, ny, nz, cell[3] + 1});
            }
        }
        return remaining;
    }

    /** @return the plan trimmed to the first {@code levels} levels, for when fewer were drained than planned */
    public static List<Placement> take(List<Placement> plan, int levels) {
        List<Placement> taken = new ArrayList<>();
        int remaining = levels;
        for (Placement placement : plan) {
            if (remaining <= 0) {
                break;
            }
            int part = Math.min(placement.levels(), remaining);
            taken.add(new Placement(placement.x(), placement.y(), placement.z(), part));
            remaining -= part;
        }
        return taken;
    }

    public static int totalLevels(List<Placement> plan) {
        int total = 0;
        for (Placement placement : plan) {
            total += placement.levels();
        }
        return total;
    }

    // x and z use 26 bits each and y 12 bits, so two cells of any real world never share a key
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
