package me.thanhtnt2.fluidcompat.core.sweep;

import java.util.Arrays;

/**
 * Horizontal offsets of a disc around a machine, nearest first, shared by every machine. This is what replaces a
 * per-machine flood fill: a search is just "the next few entries of this table", so it needs no queue and no visited
 * set, and the only thing a machine keeps is an int cursor.
 *
 * <p>The four horizontal neighbours and the centre are left out because the sweep always checks those first.
 * Pure arithmetic, no Minecraft types. The table for the current reach is built once and rebuilt only if the config
 * changes; its size is at most about 3.2 thousand entries for the largest allowed reach.
 */
final class ReachTable {

    private static volatile ReachTable current = build(0);

    private final int reach;
    private final byte[] dx;
    private final byte[] dz;

    private ReachTable(int reach, byte[] dx, byte[] dz) {
        this.reach = reach;
        this.dx = dx;
        this.dz = dz;
    }

    static ReachTable forReach(int reach) {
        ReachTable table = current;
        if (table.reach != reach) {
            table = build(reach);
            current = table;
        }
        return table;
    }

    int size() {
        return dx.length;
    }

    int dx(int index) {
        return dx[index];
    }

    int dz(int index) {
        return dz[index];
    }

    private static ReachTable build(int reach) {
        int maxCells = (2 * reach + 1) * (2 * reach + 1);
        long[] keyed = new long[maxCells];
        int count = 0;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                int squared = x * x + z * z;
                if (squared > reach * reach || Math.abs(x) + Math.abs(z) <= 1) {
                    continue;
                }
                // Distance in the high bits so one sort orders by distance; the offsets ride along in the low bits
                keyed[count++] = ((long) squared << 16) | ((x + 64L) << 8) | (z + 64L);
            }
        }
        long[] used = Arrays.copyOf(keyed, count);
        Arrays.sort(used);
        byte[] dx = new byte[count];
        byte[] dz = new byte[count];
        for (int index = 0; index < count; index++) {
            dx[index] = (byte) (((used[index] >> 8) & 0xFF) - 64);
            dz[index] = (byte) ((used[index] & 0xFF) - 64);
        }
        return new ReachTable(reach, dx, dz);
    }
}
