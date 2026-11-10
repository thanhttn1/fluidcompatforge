package me.thanhtnt2.fluidcompat.api.sweep;

/**
 * Remembers how far a machine's search for leftover fluid has got, so a search can be spread over many cycles instead
 * of reading a whole area at once. One tiny object per machine (a single int); keep it in a field of the machine so it
 * disappears with it. It holds no world, no position and no machine reference.
 */
public final class SweepCursor {

    private int nextOffset;

    /** Index of the next ring position to look at, in the order Fluid Compat's reach table uses. */
    public int nextOffset() {
        return nextOffset;
    }

    /** Used by Fluid Compat's sweep service. */
    public void advanceTo(int offset) {
        this.nextOffset = Math.max(0, offset);
    }

    /** Starts the next search from the nearest ring again. */
    public void restart() {
        this.nextOffset = 0;
    }
}
